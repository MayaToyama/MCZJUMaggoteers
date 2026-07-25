package io.mczju.maggoteers.reward;

import com.github.mczjuops.mczjugamecore.game.AbstractGame;
import com.github.mczjuops.mczjugamecore.player.PlayerExt;
import io.mczju.maggoteers.MaggoteersPlugin;
import io.mczju.maggoteers.effect.Effect;
import io.mczju.maggoteers.effect.EffectKeys;
import io.mczju.maggoteers.effect.EffectService;
import io.mczju.maggoteers.effect.PlayerEffect;
import io.mczju.maggoteers.effect.Stack;
import io.mczju.maggoteers.game.MaggoteersGame;
import io.mczju.maggoteers.item.ItemService;
import io.mczju.maggoteers.state.PlayerState;
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

        for (var pool : POOLS.values()) {
            for (var opt : pool.options()) {
                if (opt.item() != null && !opt.item().isBlank()
                        && !ItemService.hasItem(opt.item())
                        && opt.category() != RewardOption.Category.STAT) {
                    plugin.getLogger().warning("rewards.yml: 池 " + pool.id() + " 的选项 " + opt.id()
                        + " 引用了未知物品: " + opt.item() + "（可能 ItemCreator 未装/物品未定义）");
                }
            }
        }
    }

    public static RewardPool pool(String id) { return POOLS.get(id); }

    public static java.util.Set<String> allPoolIds() { return java.util.Collections.unmodifiableSet(POOLS.keySet()); }

    public static void grantClearRewards(Collection<? extends Player> players, List<RewardItem> rewards) {
        if (rewards == null || rewards.isEmpty()) return;
        for (Player p : players) {
            for (RewardItem r : rewards) {
                ItemService.give(p, r.itemId(), r.amount());
            }
        }
    }

    public static List<RewardOption> draw(String poolId, int n, Random rng, Player viewer) {
        RewardPool pool = POOLS.get(poolId);
        if (pool == null || pool.options().isEmpty()) return List.of();
        Set<String> acquired = Set.of();
        if (viewer != null) {
            var pe = new PlayerExt(viewer);
            var ps = pe.isInGame() ? PlayerStateManager.get(pe.getGame(), viewer.getUniqueId()) : null;
            if (ps != null) acquired = ps.acquiredUnique();
        }
        java.util.Set<String> unlocked = viewer == null ? java.util.Set.of()
                : io.mczju.maggoteers.unlock.UnlockRegistry.unlocksOf(viewer);
        List<RewardOption> visible = new ArrayList<>();
        for (RewardOption o : pool.options()) {
            if (o.unique() && acquired.contains(o.id())) continue;
            if (o.requiresUnlock() && !unlocked.contains(o.id())) continue;
            visible.add(o);
        }
        Collections.shuffle(visible, rng);
        return visible.subList(0, Math.min(n, visible.size()));
    }

    public static List<RewardOption> draw(String poolId, int n, Random rng) {
        return draw(poolId, n, rng, null);
    }

    public static boolean apply(Player player, RewardOption opt) {
        var pe = new PlayerExt(player);
        if (!pe.isInGame()) return false;
        return apply(player, opt, pe.getGame());
    }

    public static boolean apply(Player player, RewardOption opt, AbstractGame game) {
        if (opt == null || game == null) return false;
        PlayerState ps = PlayerStateManager.get(game, player.getUniqueId());
        if (ps == null) {
            LOG.warning("RewardService.apply: 无 PlayerState option=" + opt.id() + " uuid=" + player.getUniqueId());
            player.sendMessage(Component.text("奖励未能应用（本局状态异常）", NamedTextColor.RED));
            return false;
        }
        try {
            switch (opt.category()) {
                case SUPPLY, WEAPON -> ItemService.give(player, opt.item(), opt.amount());
                case STAT -> {
                    if (opt.effect() == Effect.GRANT_REVIVE) {
                        int count = opt.params() == null ? 1
                                : opt.params().getOrDefault(EffectKeys.COUNT, 1);
                        PlayerStateManager.addReviveCount(game, player.getUniqueId(), count);
                    } else if (!applyStat(player, opt, game)) {
                        player.sendMessage(Component.text("奖励未能应用（效果配置无效）", NamedTextColor.RED));
                        return false;
                    }
                }
            }
        } catch (Exception ex) {
            LOG.warning("RewardService.apply 失败 " + opt.id() + ": " + ex.getMessage());
            player.sendMessage(Component.text("奖励应用失败：" + opt.id(), NamedTextColor.RED));
            return false;
        }
        ps.acquiredUnique().add(opt.id());
        player.sendMessage(Component.text("获得：" + opt.displayPlain(), NamedTextColor.GREEN));
        return true;
    }

    private static boolean applyStat(Player player, RewardOption opt, AbstractGame game) {
        if (!opt.isStatEffect()) {
            LOG.warning("RewardService: STAT 配置不完整 id=" + opt.id());
            return false;
        }
        if (!(game instanceof MaggoteersGame mg)) return false;
        int level = 1;
        io.mczju.maggoteers.effect.EffectContext params = opt.params().copy();
        PlayerEffect pe;
        if (opt.stack() == Stack.UPGRADE_LEVEL) {
            final int UPGRADE_MAX = 4;
            PlayerState ps = PlayerStateManager.get(game, player.getUniqueId());
            int curLevel = ps == null ? 0
                    : ps.effects().stream().filter(e -> e.id().equals(opt.id()))
                            .mapToInt(PlayerEffect::level).max().orElse(0);
            level = Math.min(curLevel + 1, UPGRADE_MAX);
            Integer baseAmp = params.get(EffectKeys.AMP);
            if (baseAmp != null) params.put(EffectKeys.AMP, baseAmp + (level - 1));
            pe = new PlayerEffect(
                    opt.id(), opt.effect(), params, opt.trigger(),
                    opt.expiryTrigger(), opt.expiryCharges(), 0, null, opt.stack(), UPGRADE_MAX, 0);
        } else {
            pe = new PlayerEffect(
                    opt.id(), opt.effect(), params, opt.trigger(),
                    opt.expiryTrigger(), opt.expiryCharges(), 0, null, opt.stack(), 0, 0);
        }
        pe.setLevel(level);
        EffectService.apply(player, pe, mg);
        return true;
    }

    public static String poolForWave(int actIndex, String tier) {
        return "act" + (actIndex + 1) + "_" + tier;
    }
}
