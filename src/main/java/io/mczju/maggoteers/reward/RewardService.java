package io.mczju.maggoteers.reward;

import io.mczju.maggoteers.MaggoteersPlugin;
import io.mczju.maggoteers.item.ItemService;
import io.mczju.maggoteers.wave.RewardItem;
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

    public static void grantClearRewards(Collection<? extends Player> players, List<RewardItem> rewards) {
        if (rewards == null || rewards.isEmpty()) return;
        for (Player p : players) {
            for (RewardItem r : rewards) {
                ItemService.give(p, r.itemId(), r.amount());
            }
        }
    }

    /** 从池中随机抽 n 个不同选项（不足则全给）。 */
    public static List<RewardOption> draw(String poolId, int n, Random rng) {
        RewardPool pool = POOLS.get(poolId);
        if (pool == null || pool.options().isEmpty()) return List.of();
        List<RewardOption> copy = new ArrayList<>(pool.options());
        Collections.shuffle(copy, rng);
        return copy.subList(0, Math.min(n, copy.size()));
    }

    public static void apply(Player player, RewardOption opt) {
        if (opt == null) return;
        switch (opt.category()) {
            case SUPPLY, WEAPON -> {
                ItemService.give(player, opt.item(), opt.amount());
                if ("maggoteers:supply_healing".equals(opt.item())) {
                    var attr = player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
                    double max = attr != null ? attr.getValue() : 20.0;
                    player.setHealth(Math.min(max, player.getHealth() + 12.0));
                }
            }
            case STAT -> {
                var attr = player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
                if (attr != null) {
                    attr.setBaseValue(attr.getBaseValue() + 4.0);
                    player.setHealth(Math.min(player.getHealth() + 4.0, attr.getValue()));
                }
            }
        }
        player.sendMessage(net.kyori.adventure.text.Component.text(
                "获得：" + opt.displayPlain(), net.kyori.adventure.text.format.NamedTextColor.GREEN));
    }

    /** 根据层/波类型解析池 id（weak 波→weak 池，boss 步→boss 池）。 */
    public static String poolForWave(int actIndex, String tier) {
        return "act" + (actIndex + 1) + "_" + tier;
    }
}
