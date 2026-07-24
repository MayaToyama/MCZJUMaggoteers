package io.mczju.maggoteers.plan;

import io.mczju.maggoteers.config.*;
import io.mczju.maggoteers.wave.Vec3;
import io.mczju.maggoteers.world.MapLibrary;
import io.mczju.maggoteers.world.MapRepository.MapEntry;
import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class RunPlannerTest {

    private WaveDefinitions simpleDefs() {
        SpawnStrategyCfg swarm = new SpawnStrategyCfg("w_swarm",
                List.of(new StepCfg("1", EntityType.ZOMBIE, 5, new CoeffCfg(1, 1, 1), 0, List.of())), 1,
                List.of(new RewardItemCfg("cur", 2)));
        SpawnStrategyCfg boss = new SpawnStrategyCfg("b_boss",
                List.of(new StepCfg("boss", EntityType.IRON_GOLEM, 1, new CoeffCfg(8, 1.5, 1), 0, List.of("armored"))),
                1, List.of(new RewardItemCfg("cur", 1)));
        Map<String, SpawnStrategyCfg> strat = new HashMap<>();
        strat.put("w_swarm", swarm);
        strat.put("s_strong", swarm);
        strat.put("b_boss", boss);
        Map<String, Map<String, List<WavesConfig.PoolEntry>>> pools = new HashMap<>();
        for (String act : List.of("act1", "act2", "act3")) {
            Map<String, List<WavesConfig.PoolEntry>> t = new HashMap<>();
            t.put("weak", List.of(new WavesConfig.PoolEntry("w_swarm", 3)));
            t.put("strong", List.of(new WavesConfig.PoolEntry("s_strong", 2)));
            t.put("boss", List.of(new WavesConfig.PoolEntry("b_boss", 1)));
            pools.put(act, t);
        }
        return new WaveDefinitions(strat, pools);
    }

    private MapLibrary oneMapLib() {
        MapPoints pts = new MapPoints(new Vec3(0.5, 65, 0.5), Map.of(
                "1", new Vec3(8, 65, 8), "boss", new Vec3(0, 65, 0)));
        MapEntry entry = new MapEntry("m1", pts, false, null, Map.of());
        Map<String, List<MapEntry>> byAct = new HashMap<>();
        for (String act : List.of("act1", "act2", "act3")) byAct.put(act, List.of(entry));
        return new MapLibrary(byAct);
    }

    private RunConfig cfg() {
        Map<String, Vec3> o = new HashMap<>();
        o.put("act1", new Vec3(0, 64, 0));
        o.put("act2", new Vec3(1024, 64, 0));
        o.put("act3", new Vec3(2048, 64, 0));
        Map<String, int[]> w = new HashMap<>();
        for (String act : List.of("act1", "act2", "act3")) w.put(act, new int[]{3, 2});
        return new RunConfig(o, w);
    }

    private ScalingConfig scaling() {
        return ScalingConfig.forTesting(
                new double[]{1, 1.3, 1.6, 2.0}, new double[]{1, 1.15, 1.3, 1.5},
                new double[]{1, 1, 1, 1}, new double[]{1, 1.4, 1.8, 2.2}, new double[]{1, 1, 1.2, 1.5});
    }

    private AffixService affixes() {
        Affix armored = new Affix("armored", 2, 1, 1, 1, List.of(), null, "装甲");
        return AffixService.forTesting(Map.of("armored", armored));
    }

    @Test
    void sameSeedProducesSamePlan() {
        List<ActPlan> a = RunPlanner.plan(999L, 2, simpleDefs(), oneMapLib(), scaling(), affixes(), cfg());
        List<ActPlan> b = RunPlanner.plan(999L, 2, simpleDefs(), oneMapLib(), scaling(), affixes(), cfg());
        assertEquals(a.size(), b.size());
        for (int i = 0; i < a.size(); i++) {
            assertEquals(a.get(i).waves().size(), b.get(i).waves().size());
            for (int w = 0; w < a.get(i).waves().size(); w++) {
                var wa = a.get(i).waves().get(w).expand();
                var wb = b.get(i).waves().get(w).expand();
                assertEquals(wa.size(), wb.size());
                for (int s = 0; s < wa.size(); s++) {
                    assertEquals(wa.get(s).count(), wb.get(s).count());
                    assertEquals(wa.get(s).hpMult(), wb.get(s).hpMult(), 1e-9);
                    assertEquals(wa.get(s).point(), wb.get(s).point());
                }
            }
        }
    }

    @Test
    void waveCountIsMPlusNPlusOne() {
        List<ActPlan> acts = RunPlanner.plan(1L, 1, simpleDefs(), oneMapLib(), scaling(), affixes(), cfg());
        acts.forEach(a -> assertEquals(6, a.waves().size()));
        assertEquals(3, acts.size());
    }

    @Test
    void bossWaveHasAffixComposed() {
        List<ActPlan> acts = RunPlanner.plan(1L, 1, simpleDefs(), oneMapLib(), scaling(), affixes(), cfg());
        var bossStep = acts.get(0).waves().get(5).expand().get(0);
        assertEquals(16.0, bossStep.hpMult(), 1e-9);
        assertTrue(bossStep.affixes().contains("armored"));
    }

    @Test
    void absoluteCoordsShiftByActOrigin() {
        List<ActPlan> acts = RunPlanner.plan(1L, 1, simpleDefs(), oneMapLib(), scaling(), affixes(), cfg());
        var act2Step0 = acts.get(1).waves().get(0).expand().get(0);
        assertEquals(1032.0, act2Step0.point().x(), 1e-9);
    }

    @Test
    void missingSpawnFallsBackToBossWhenFewerThanNineNonBoss() {
        SpawnStrategyCfg bad = new SpawnStrategyCfg("bad",
                List.of(new StepCfg("9", EntityType.ZOMBIE, 1, new CoeffCfg(1, 1, 1), 0, List.of())), 1, List.of());
        Map<String, SpawnStrategyCfg> strat = new HashMap<>(simpleDefs().strategies());
        strat.put("bad", bad);
        Map<String, Map<String, List<WavesConfig.PoolEntry>>> pools = new HashMap<>();
        for (String act : List.of("act1", "act2", "act3"))
            pools.put(act, Map.of("weak", List.of(new WavesConfig.PoolEntry("bad", 99)),
                    "strong", List.of(new WavesConfig.PoolEntry("s_strong", 1)),
                    "boss", List.of(new WavesConfig.PoolEntry("b_boss", 1))));
        WaveDefinitions defs = new WaveDefinitions(strat, pools);
        List<ActPlan> acts = RunPlanner.plan(1L, 1, defs, oneMapLib(), scaling(), affixes(), cfg());
        // oneMapLib: 仅 "1"+boss → 点 "9" 回退 boss 相对 (0,65,0) + act1 原点 (0,64,0)
        var step = acts.get(0).waves().get(0).expand().get(0);
        assertEquals(0.0, step.point().x(), 1e-9);
        assertEquals(129.0, step.point().y(), 1e-9);
        assertEquals(0.0, step.point().z(), 1e-9);
    }

    @Test
    void g3MissingSpawnPointThrowsWhenNineNonBossDefined() {
        Map<String, Vec3> pts = new HashMap<>();
        for (int i = 1; i <= 9; i++) pts.put(String.valueOf(i), new Vec3(i, 65, i));
        pts.put("boss", new Vec3(0, 65, 0));
        MapEntry entry = new MapEntry("full", new MapPoints(new Vec3(0.5, 65, 0.5), pts), false, null, Map.of());
        Map<String, List<MapEntry>> byAct = new HashMap<>();
        for (String act : List.of("act1", "act2", "act3")) byAct.put(act, List.of(entry));

        SpawnStrategyCfg bad = new SpawnStrategyCfg("bad",
                List.of(new StepCfg("missing", EntityType.ZOMBIE, 1, new CoeffCfg(1, 1, 1), 0, List.of())),
                1, List.of());
        Map<String, SpawnStrategyCfg> strat = new HashMap<>(simpleDefs().strategies());
        strat.put("bad", bad);
        Map<String, Map<String, List<WavesConfig.PoolEntry>>> pools = new HashMap<>();
        for (String act : List.of("act1", "act2", "act3"))
            pools.put(act, Map.of("weak", List.of(new WavesConfig.PoolEntry("bad", 99)),
                    "strong", List.of(new WavesConfig.PoolEntry("s_strong", 1)),
                    "boss", List.of(new WavesConfig.PoolEntry("b_boss", 1))));
        WaveDefinitions defs = new WaveDefinitions(strat, pools);
        assertThrows(IllegalStateException.class,
                () -> RunPlanner.plan(1L, 1, defs, new MapLibrary(byAct), scaling(), affixes(), cfg()));
    }

    @Test
    void specialWavesMergedIntoPool() {
        SpawnStrategyCfg special = new SpawnStrategyCfg("s_special",
                List.of(new StepCfg("1", EntityType.SKELETON, 3, new CoeffCfg(1, 1, 1), 0, List.of())), 1, List.of());
        Map<String, SpawnStrategyCfg> strat = new HashMap<>(simpleDefs().strategies());
        strat.put("s_special", special);
        WaveDefinitions defs = new WaveDefinitions(strat, simpleDefs().pools());

        MapEntry entry = new MapEntry("m1",
                new MapPoints(new Vec3(0.5, 65, 0.5), Map.of("1", new Vec3(8, 65, 8), "boss", new Vec3(0, 65, 0))),
                false, null, Map.of("strong", List.of(new WavesConfig.PoolEntry("s_special", 999))));
        Map<String, List<MapEntry>> byAct = new HashMap<>();
        byAct.put("act1", List.of(entry));
        byAct.put("act2", oneMapLib().maps("act2"));
        byAct.put("act3", oneMapLib().maps("act3"));

        int specialHits = 0;
        for (int s = 0; s < 50; s++) {
            List<ActPlan> acts = RunPlanner.plan(s, 1, defs, new MapLibrary(byAct), scaling(), affixes(), cfg());
            if (acts.get(0).waves().get(3).expand().get(0).type() == EntityType.SKELETON) specialHits++;
        }
        assertTrue(specialHits > 45, "specialHits=" + specialHits);
    }
}
