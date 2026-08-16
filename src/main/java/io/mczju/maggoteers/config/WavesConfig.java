package io.mczju.maggoteers.config;

import io.mczju.maggoteers.util.GameRegistries;
import io.mczju.maggoteers.util.PluginFiles;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 加载并缓存 waves.yml（strategies + pools）。onEnable 调一次。 */
public final class WavesConfig {
    private static WavesConfig INSTANCE;

    private final Map<String, SpawnStrategyCfg> strategies = new HashMap<>();
    /** act -> tier(weak/strong/boss) -> 池条目 */
    private final Map<String, Map<String, List<PoolEntry>>> pools = new HashMap<>();

    public record PoolEntry(String strategy, double weight) {}

    /** 从 waves.yml 文件加载（不混入 config.yml）。onEnable 调一次。 */
    public static void loadFromFile(JavaPlugin plugin) {
        PluginFiles.saveResourceIfMissing(plugin, "waves.yml");
        var file = new java.io.File(plugin.getDataFolder(), "waves.yml");
        var cfg = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file);
        INSTANCE = new WavesConfig();

        var stratSec = cfg.getConfigurationSection("strategies");
        if (stratSec != null) {
            for (String id : stratSec.getKeys(false)) {
                INSTANCE.strategies.put(id, parseStrategy(id, stratSec.getConfigurationSection(id)));
            }
        }
        var poolsSec = cfg.getConfigurationSection("pools");
        if (poolsSec != null) {
            for (String act : poolsSec.getKeys(false)) {
                Map<String, List<PoolEntry>> tierMap = new HashMap<>();
                var actSec = poolsSec.getConfigurationSection(act);
                if (actSec == null) continue;
                for (String tier : List.of("weak", "strong", "boss")) {
                    var tierList = actSec.getMapList(tier);
                    List<PoolEntry> entries = new ArrayList<>();
                    for (var m : tierList) {
                        entries.add(new PoolEntry((String) m.get("strategy"),
                                num(m.get("weight"), 1).doubleValue()));
                    }
                    tierMap.put(tier, entries);
                }
                INSTANCE.pools.put(act, tierMap);
            }
        }
        plugin.getLogger().info("WavesConfig 已加载：" + INSTANCE.strategies.size() + " strategies, "
                + INSTANCE.pools.size() + " acts。");

        // 校验：池里引用的 strategy 必须在 strategies 里定义
        for (var actEntry : INSTANCE.pools.entrySet()) {
            String act = actEntry.getKey();
            for (var tierEntry : actEntry.getValue().entrySet()) {
                String tier = tierEntry.getKey();
                for (var pe : tierEntry.getValue()) {
                    if (!INSTANCE.strategies.containsKey(pe.strategy())) {
                        throw new IllegalStateException(
                            "waves.yml: 池 " + act + "/" + tier + " 引用了未定义的 strategy: " + pe.strategy());
                    }
                    // 校验 steps 里引用的 affix 必须在 AffixService 里（affixes.yml 已加载）
                    SpawnStrategyCfg strat = INSTANCE.strategies.get(pe.strategy());
                    if (strat != null) for (StepCfg step : strat.steps()) {
                        for (String affixId : step.affixes()) {
                            if (AffixService.getInstance().get(affixId) == null) {
                                plugin.getLogger().severe("waves.yml: strategy " + pe.strategy()
                                    + " 引用了未定义的词缀: " + affixId + "（继续，但该词缀无效）");
                            }
                        }
                    }
                }
            }
        }
    }

    private static SpawnStrategyCfg parseStrategy(String id, ConfigurationSection s) {
        if (s == null) throw new IllegalStateException("strategy " + id + " 配置缺失");
        int repeat = s.getInt("repeat", 1);
        List<StepCfg> steps = new ArrayList<>();
        for (var m : s.getMapList("steps")) {
            String point = String.valueOf(m.get("point"));
            EntityType type = io.mczju.maggoteers.util.GameRegistries.entityType(String.valueOf(m.get("type")));
            int count = num(m.get("count"), 1).intValue();
            CoeffCfg coeff = MobYamlParser.parseCoeff(m);
            int delay = num(m.get("delay"), 0).intValue();
            int stepRepeat = num(m.get("repeat"), 1).intValue();
            List<String> affixes = MobYamlParser.parseAffixes(m);
            InfernalCfg infernal = MobYamlParser.parseInfernal(m);
            List<PassengerCfg> passengers = MobYamlParser.parsePassengers(m.get("passengers"));
            String ctx = id + "/" + point + "/" + type.name();
            String name = MobYamlParser.parseName(m, ctx);
            boolean bossBar = MobYamlParser.parseBossBar(m, ctx);
            steps.add(new StepCfg(point, type, count, coeff, delay, affixes, infernal,
                    MobYamlParser.parseEquipment(m.get("equipment")),
                    MobYamlParser.parseOnDeath(m.get("on_death")),
                    passengers, stepRepeat, name, bossBar));
        }
        List<RewardItemCfg> rewards = new ArrayList<>();
        for (var m : s.getMapList("clearReward")) {
            rewards.add(new RewardItemCfg((String) m.get("item"), num(m.get("amount"), 1).intValue()));
        }
        return new SpawnStrategyCfg(id, steps, repeat, rewards);
    }

    public static WavesConfig getInstance() {
        if (INSTANCE == null) throw new IllegalStateException("WavesConfig 未加载！");
        return INSTANCE;
    }

    public SpawnStrategyCfg getStrategy(String id) { return strategies.get(id); }

    public List<PoolEntry> getPool(String act, String tier) {
        return pools.getOrDefault(act, Map.of()).getOrDefault(tier, List.of());
    }

    public WaveDefinitions definitions() {
        return new WaveDefinitions(strategies, pools);
    }

    private static Number num(Object v, Number fallback) {
        return v instanceof Number n ? n : fallback;
    }

    private WavesConfig() {}
}
