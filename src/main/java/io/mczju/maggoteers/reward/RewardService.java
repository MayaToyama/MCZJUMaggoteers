package io.mczju.maggoteers.reward;

import com.github.mczjuops.mczjugamecore.player.PlayerExt;
import io.mczju.maggoteers.MaggoteersPlugin;
import io.mczju.maggoteers.effect.EffectKeys;
import io.mczju.maggoteers.effect.PlayerEffect;
import io.mczju.maggoteers.effect.Stack;
import io.mczju.maggoteers.item.ItemService;
import io.mczju.maggoteers.state.PlayerStateManager;
import io.mczju.maggoteers.wave.RewardItem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.*;
import java.util.logging.Logger;

/** 奖励池加载 + 清波发放 + 选项应用（测试版，Plan 6 扩展 GUI/解锁）。 */
public final class RewardService {
    private static final Logger LOG = Logger.getLogger("Maggoteers");
    private static final Map<String, RewardPool> POOLS = new HashMap<>();

    private RewardService() {}

    public static void load(MaggoteersPlugin plugin) {
        POOLS.clear();
        File f = new File(plugin.getDataFolder(), "rewards.yml");
        if (!f.exists()) plugin.saveResource("rewards.yml", false);
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(f);
        ConfigurationSection root = yaml.getConfigurationSection("reward_pools");
        if (root == null) return;
        for (String poolId : root.getKeys(false)) {
            ConfigurationSection sec = root.getConfigurationSection(poolId);
            if (sec == null) continue;
            int cost = sec.getInt("cost", 1);
            String currency = sec.getString("currency", "normal");
            List<RewardOption> options = new ArrayList<>();
            for (var m : sec.getMapList("options")) {
                options.add(RewardOption.fromMap(m));
            }
            POOLS.put(poolId, new RewardPool(poolId, cost, currency, options));
        }
        LOG.info("RewardService 已加载 " + POOLS.size() + " 个奖励池。");
    }

    public static RewardPool pool(String id) { return POOLS.get(id); }

    /** 所有奖励池 id（局外商店枚举 requires_unlock 商品用）。 */
    public static java.util.Set<String> allPoolIds() { return POOLS.keySet(); }

    public static void grantClearRewards(Collection<? extends Player> players, List<RewardItem> rewards) {
        if (rewards == null || rewards.isEmpty()) return;
        for (Player p : players) {
            for (RewardItem r : rewards) {
                ItemService.give(p, r.itemId(), r.amount());
            }
        }
    }

    /** 从池中随机抽 n 个不同选项（不足则全给）。unique 已选过的排除；requires_unlock 暂恒可见（Plan 7 接 unlocks）。 */
    public static List<RewardOption> draw(String poolId, int n, Random rng, Player viewer) {
        RewardPool pool = POOLS.get(poolId);
        if (pool == null || pool.options().isEmpty()) return List.of();
        Set<String> acquired = Set.of();
        if (viewer != null) {
            var pe = new PlayerExt(viewer);
            var ps = pe.isInGame() ? PlayerStateManager.get(pe.getGame(), viewer.getUniqueId()) : null;
            if (ps != null) acquired = ps.acquiredUnique();
        }
        List<RewardOption> visible = new ArrayList<>();
        for (RewardOption o : pool.options()) {
            if (o.unique() && acquired.contains(o.id())) continue;     // §12.2 unique 过滤
            // requires_unlock 过滤留 Plan 7（unlocks 持久化前恒可见）
            visible.add(o);
        }
        Collections.shuffle(visible, rng);
        return visible.subList(0, Math.min(n, visible.size()));
    }

    /** 向后兼容重载（无 viewer → 不过滤）。 */
    public static List<RewardOption> draw(String poolId, int n, Random rng) {
        return draw(poolId, n, rng, null);
    }

    public static void apply(Player player, RewardOption opt) {
        if (opt == null) return;
        switch (opt.category()) {
            case SUPPLY, WEAPON -> {
                ItemService.give(player, opt.item(), opt.amount());
            }
            case STAT -> {
                if (opt.effect() == io.mczju.maggoteers.effect.Effect.GRANT_REVIVE) {
                    var gpe = new com.github.mczjuops.mczjugamecore.player.PlayerExt(player);
                    if (gpe.isInGame()) {
                        int count = opt.params() == null ? 1
                                : opt.params().getOrDefault(io.mczju.maggoteers.effect.EffectKeys.COUNT, 1);
                        io.mczju.maggoteers.state.PlayerStateManager.addReviveCount(
                                gpe.getGame(), player.getUniqueId(), count);
                    }
                } else {
                    applyStat(player, opt);
                }
            }
        }
        // 记入本局已选（unique 用于 draw 过滤；UPGRADE_LEVEL 用于 level 计数）
        var pe = new PlayerExt(player);
        if (pe.isInGame()) {
            var ps = PlayerStateManager.get(pe.getGame(), player.getUniqueId());
            if (ps != null) ps.acquiredUnique().add(opt.id());
        }
        player.sendMessage(Component.text(
                "获得：" + opt.displayPlain(), NamedTextColor.GREEN));
    }

    /** STAT 选项 → 建成 PlayerEffect → EffectService.apply。 */
    private static void applyStat(Player player, RewardOption opt) {
        if (!opt.isStatEffect()) return;   // 配置不全则不发
        int level = 1;
        io.mczju.maggoteers.effect.EffectContext params = opt.params().copy();   // C1: 不污染共享 RewardOption
        PlayerEffect pe;
        // UPGRADE_LEVEL：按玩家已 acquired 次数定 level（amp/value 随 level 缩放）
        if (opt.stack() == Stack.UPGRADE_LEVEL) {
            final int UPGRADE_MAX = 4;
            var pExt = new PlayerExt(player);
            var ps = pExt.isInGame() ? PlayerStateManager.get(pExt.getGame(), player.getUniqueId()) : null;
            int curLevel = ps == null ? 0
                    : ps.effects().stream().filter(e -> e.id().equals(opt.id()))
                            .mapToInt(io.mczju.maggoteers.effect.PlayerEffect::level).max().orElse(0);
            level = Math.min(curLevel + 1, UPGRADE_MAX);
            // 药水 amp 随 level：amp = baseAmp + (level-1)
            Integer baseAmp = params.get(EffectKeys.AMP);
            if (baseAmp != null) params.put(EffectKeys.AMP, baseAmp + (level - 1));
            pe = new PlayerEffect(
                    opt.id(), opt.effect(), params, opt.trigger(),
                    null, 0, 0, null, opt.stack(), UPGRADE_MAX, 0);
        } else {
            pe = new PlayerEffect(
                    opt.id(), opt.effect(), params, opt.trigger(),
                    null, 0, 0, null, opt.stack(), 0, 0);
        }
        pe.setLevel(level);
        io.mczju.maggoteers.effect.EffectService.apply(player, pe);
    }

    /** 根据层/波类型解析池 id（weak 波→weak 池，boss 步→boss 池）。 */
    public static String poolForWave(int actIndex, String tier) {
        return "act" + (actIndex + 1) + "_" + tier;
    }
}
