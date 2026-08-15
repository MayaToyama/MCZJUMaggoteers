package io.mczju.maggoteers.plan;

import io.mczju.maggoteers.config.*;
import io.mczju.maggoteers.wave.Vec3;
import io.mczju.maggoteers.world.MapLibrary;
import io.mczju.maggoteers.world.MapRepository.MapEntry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/** 强怪池加载与 RunPlanner 是否写入 strategyId。 */
class StrongWaveRollTest {

    @Test
    void strongPoolHasMultipleEntries() {
        WaveDefinitions defs = loadBundledWaves();
        assertTrue(defs.pool("act1", "strong").size() >= 2);
        assertTrue(defs.pool("act2", "strong").size() >= 2);
    }

    @Test
    void strongRollsUseStrategyIdsFromPool() {
        WaveDefinitions defs = loadBundledWaves();
        RunConfig rc = productionWaveCounts();
        Set<String> seen = new HashSet<>();
        String[] acts = {"act1", "act2", "act3"};
        for (long seed = 0; seed < 500; seed++) {
            List<ActPlan> plans = RunPlanner.plan(seed, 2, defs, oneMap(), scaling(), affixes(), rc);
            for (int ai = 0; ai < plans.size(); ai++) {
                int weak = rc.weak(acts[ai]);
                int strong = rc.strong(acts[ai]);
                for (int wi = weak; wi < weak + strong; wi++) {
                    String id = plans.get(ai).waves().get(wi).strategyId();
                    assertFalse(id.isBlank(), "seed=" + seed + " act=" + ai + " wave=" + wi);
                    seen.add(id);
                }
            }
        }
        assertTrue(seen.size() >= 2, "500 局强怪应出现至少 2 种 strategy: " + seen);
    }

    private static WaveDefinitions loadBundledWaves() {
        File f = new File("src/main/resources/waves.yml");
        if (!f.isFile()) f = new File("src/main/resources/waves.yml").getAbsoluteFile();
        var cfg = YamlConfiguration.loadConfiguration(f);
        Map<String, SpawnStrategyCfg> strategies = new HashMap<>();
        var stratSec = cfg.getConfigurationSection("strategies");
        if (stratSec != null) {
            for (String sid : stratSec.getKeys(false)) {
                strategies.put(sid, parseStrategy(sid, stratSec.getConfigurationSection(sid)));
            }
        }
        Map<String, Map<String, List<WavesConfig.PoolEntry>>> pools = new HashMap<>();
        var poolsSec = cfg.getConfigurationSection("pools");
        if (poolsSec != null) {
            for (String act : poolsSec.getKeys(false)) {
                Map<String, List<WavesConfig.PoolEntry>> tierMap = new HashMap<>();
                var actSec = poolsSec.getConfigurationSection(act);
                if (actSec == null) continue;
                for (String tier : List.of("weak", "strong", "boss")) {
                    List<WavesConfig.PoolEntry> entries = new ArrayList<>();
                    for (var m : actSec.getMapList(tier)) {
                        entries.add(new WavesConfig.PoolEntry((String) m.get("strategy"),
                                m.get("weight") instanceof Number n ? n.doubleValue() : 1.0));
                    }
                    tierMap.put(tier, entries);
                }
                pools.put(act, tierMap);
            }
        }
        return new WaveDefinitions(strategies, pools);
    }

    private static RunConfig productionWaveCounts() {
        Map<String, Vec3> o = new HashMap<>();
        o.put("act1", new Vec3(0, 64, 0));
        o.put("act2", new Vec3(1024, 64, 0));
        o.put("act3", new Vec3(2048, 64, 0));
        // Match src/main/resources/config.yml waves_per_act (2026-08-15)
        Map<String, int[]> wpa = new HashMap<>();
        wpa.put("act1", new int[]{3, 3});
        wpa.put("act2", new int[]{3, 3});
        wpa.put("act3", new int[]{1, 4});
        return new RunConfig(o, wpa);
    }

    private static MapLibrary oneMap() {
        MapPoints pts = new MapPoints(new Vec3(0.5, 65, 0.5),
                Map.of("1", new Vec3(4, 65, 4), "2", new Vec3(-4, 65, 4),
                        "boss", new Vec3(0, 65, 0)));
        // Act1 strong global has only 2 ids; production maps add specials so need=3 is feasible.
        MapEntry act1 = new MapEntry("desert", pts, false, null, Map.of(
                "strong", List.of(
                        new WavesConfig.PoolEntry("s1_desert_four_camels", 2),
                        new WavesConfig.PoolEntry("s1_desert_prisoners", 2),
                        new WavesConfig.PoolEntry("s1_desert_plague", 2))));
        MapEntry plain = new MapEntry("test", pts, false, null, Map.of());
        Map<String, List<MapEntry>> byAct = new HashMap<>();
        byAct.put("act1", List.of(act1));
        byAct.put("act2", List.of(plain));
        byAct.put("act3", List.of(plain));
        return new MapLibrary(byAct);
    }

    private static ScalingConfig scaling() {
        return ScalingConfig.forTesting(
                new double[]{1, 1.3, 1.6, 2.0}, new double[]{1, 1.15, 1.3, 1.5},
                new double[]{1, 1, 1, 1}, new double[]{1, 1.4, 1.8, 2.2}, new double[]{1, 1, 1.2, 1.5});
    }

    private static AffixService affixes() {
        return loadBundledAffixes();
    }

    private static AffixService loadBundledAffixes() {
        File f = new File("src/main/resources/affixes.yml");
        if (!f.isFile()) f = new File("src/main/resources/affixes.yml").getAbsoluteFile();
        var cfg = YamlConfiguration.loadConfiguration(f);
        Map<String, Affix> map = new HashMap<>();
        var sec = cfg.getConfigurationSection("affixes");
        if (sec != null) {
            for (String id : sec.getKeys(false)) {
                map.put(id, new Affix(id, 1, 1, 1, 1, List.of(), id));
            }
        }
        return AffixService.forTesting(map);
    }

    private static SpawnStrategyCfg parseStrategy(String id, ConfigurationSection s) {
        if (s == null) throw new IllegalStateException("strategy " + id + " 配置缺失");
        int repeat = s.getInt("repeat", 1);
        List<StepCfg> steps = new ArrayList<>();
        for (var m : s.getMapList("steps")) {
            String point = String.valueOf(m.get("point"));
            EntityType type = EntityType.valueOf(String.valueOf(m.get("type")).trim().toUpperCase(Locale.ROOT));
            int count = m.get("count") instanceof Number n ? n.intValue() : 1;
            Map<?, ?> c = m.get("coeff") instanceof Map<?, ?> cm ? cm : Map.of();
            double hp = c.get("hp") instanceof Number hn ? hn.doubleValue() : 1.0;
            double dmg = c.get("dmg") instanceof Number dn ? dn.doubleValue() : 1.0;
            double spd = c.get("speed") instanceof Number sn ? sn.doubleValue() : 1.0;
            int delay = m.get("delay") instanceof Number dln ? dln.intValue() : 0;
            List<String> affixes = new ArrayList<>();
            Object affRaw = m.get("affixes");
            if (affRaw instanceof List<?> al) for (Object o : al) affixes.add(String.valueOf(o));
            steps.add(new StepCfg(point, type, count, new CoeffCfg(hp, dmg, spd), delay, affixes));
        }
        List<RewardItemCfg> rewards = new ArrayList<>();
        for (var m : s.getMapList("clearReward")) {
            rewards.add(new RewardItemCfg((String) m.get("item"), m.get("amount") instanceof Number an ? an.intValue() : 1));
        }
        return new SpawnStrategyCfg(id, steps, repeat, rewards);
    }
}
