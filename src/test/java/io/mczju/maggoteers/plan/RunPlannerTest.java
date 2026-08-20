package io.mczju.maggoteers.plan;

import io.mczju.maggoteers.config.*;
import io.mczju.maggoteers.wave.*;
import io.mczju.maggoteers.world.MapLibrary;
import io.mczju.maggoteers.world.MapRepository.MapEntry;
import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class RunPlannerTest {

    private WaveDefinitions simpleDefs() {
        Map<String, SpawnStrategyCfg> strat = new HashMap<>();
        for (String id : List.of("w_a", "w_b", "w_c", "s_a", "s_b", "b_boss")) {
            boolean boss = id.startsWith("b_");
            List<StepCfg> steps = List.of(new StepCfg(
                    boss ? "boss" : "1",
                    boss ? EntityType.IRON_GOLEM : EntityType.ZOMBIE,
                    boss ? 1 : 5,
                    new CoeffCfg(boss ? 8 : 1, boss ? 1.5 : 1, 1),
                    0,
                    boss ? List.of("armored") : List.of()));
            strat.put(id, new SpawnStrategyCfg(id, id, steps, 1,
                    List.of(new RewardItemCfg("cur", boss ? 1 : 2))));
        }
        Map<String, Map<String, List<WavesConfig.PoolEntry>>> pools = new HashMap<>();
        for (String act : List.of("act1", "act2", "act3")) {
            pools.put(act, Map.of(
                    "weak", List.of(
                            new WavesConfig.PoolEntry("w_a", 1),
                            new WavesConfig.PoolEntry("w_b", 1),
                            new WavesConfig.PoolEntry("w_c", 1)),
                    "strong", List.of(
                            new WavesConfig.PoolEntry("s_a", 1),
                            new WavesConfig.PoolEntry("s_b", 1)),
                    "boss", List.of(new WavesConfig.PoolEntry("b_boss", 1))));
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
        return cfg(3, 2);
    }

    private RunConfig cfg(int weak, int strong) {
        Map<String, Vec3> o = new HashMap<>();
        o.put("act1", new Vec3(0, 64, 0));
        o.put("act2", new Vec3(1024, 64, 0));
        o.put("act3", new Vec3(2048, 64, 0));
        Map<String, int[]> w = new HashMap<>();
        for (String act : List.of("act1", "act2", "act3")) w.put(act, new int[]{weak, strong});
        return new RunConfig(o, w);
    }

    private ScalingConfig scaling() {
        return ScalingConfig.forTesting(
                new double[]{1, 1.3, 1.6, 2.0}, new double[]{1, 1.15, 1.3, 1.5},
                new double[]{1, 1, 1, 1}, new double[]{1, 1, 1.2, 1.5});
    }

    private static void assertDistinct(List<WaveSpec> slice) {
        Set<String> ids = new HashSet<>();
        for (WaveSpec w : slice) {
            assertTrue(ids.add(w.strategyId()), "duplicate strategy in tier: " + w.strategyId());
        }
    }

    @Test
    void sameSeedProducesSamePlan() {
        List<ActPlan> a = RunPlanner.plan(999L, 2, simpleDefs(), oneMapLib(), scaling(), cfg());
        List<ActPlan> b = RunPlanner.plan(999L, 2, simpleDefs(), oneMapLib(), scaling(), cfg());
        assertEquals(a.size(), b.size());
        for (int i = 0; i < a.size(); i++) {
            assertEquals(a.get(i).waves().size(), b.get(i).waves().size());
            for (int w = 0; w < a.get(i).waves().size(); w++) {
                assertEquals(a.get(i).waves().get(w).strategyId(), b.get(i).waves().get(w).strategyId());
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
        List<ActPlan> acts = RunPlanner.plan(1L, 1, simpleDefs(), oneMapLib(), scaling(), cfg());
        acts.forEach(a -> assertEquals(6, a.waves().size()));
        assertEquals(3, acts.size());
    }

    @Test
    void sameTierStrategiesAreUniqueWithinAct() {
        List<ActPlan> acts = RunPlanner.plan(42L, 1, simpleDefs(), oneMapLib(), scaling(), cfg());
        for (ActPlan act : acts) {
            List<WaveSpec> waves = act.waves();
            assertEquals(6, waves.size());
            assertDistinct(waves.subList(0, 3));
            assertDistinct(waves.subList(3, 5));
        }
    }

    @Test
    void poolTooFewDistinctIdsHardFails() {
        Map<String, SpawnStrategyCfg> strat = new HashMap<>(simpleDefs().strategies());
        Map<String, Map<String, List<WavesConfig.PoolEntry>>> pools = new HashMap<>();
        for (String act : List.of("act1", "act2", "act3")) {
            pools.put(act, Map.of(
                    "weak", List.of(new WavesConfig.PoolEntry("w_a", 1)),
                    "strong", List.of(
                            new WavesConfig.PoolEntry("s_a", 1),
                            new WavesConfig.PoolEntry("s_b", 1)),
                    "boss", List.of(new WavesConfig.PoolEntry("b_boss", 1))));
        }
        WaveDefinitions defs = new WaveDefinitions(strat, pools);
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> RunPlanner.plan(1L, 1, defs, oneMapLib(), scaling(), cfg()));
        String msg = ex.getMessage();
        assertTrue(msg.contains("weak") || msg.contains("act1"), msg);
        assertTrue(msg.contains("w_a") || msg.contains("已用") || msg.contains("空"), msg);
    }

    @Test
    void bossWaveCarriesSkills() {
        List<ActPlan> acts = RunPlanner.plan(1L, 1, simpleDefs(), oneMapLib(), scaling(), cfg());
        var bossStep = acts.get(0).waves().get(5).expand().get(0);
        assertEquals(8.0, bossStep.hpMult(), 1e-9);   // 8 × scaling.mobHp(1)=8，无 affix 乘法
        assertTrue(bossStep.skills().contains("armored"));
    }

    @Test
    void absoluteCoordsShiftByActOrigin() {
        List<ActPlan> acts = RunPlanner.plan(1L, 1, simpleDefs(), oneMapLib(), scaling(), cfg());
        var act2Step0 = acts.get(1).waves().get(0).expand().get(0);
        assertEquals(1032.0, act2Step0.point().x(), 1e-9);
    }

    @Test
    void missingSpawnFallsBackToBossWhenFewerThanNineNonBoss() {
        SpawnStrategyCfg bad = new SpawnStrategyCfg("bad", "bad",
                List.of(new StepCfg("9", EntityType.ZOMBIE, 1, new CoeffCfg(1, 1, 1), 0, List.of())), 1, List.of());
        Map<String, SpawnStrategyCfg> strat = new HashMap<>(simpleDefs().strategies());
        strat.put("bad", bad);
        Map<String, Map<String, List<WavesConfig.PoolEntry>>> pools = new HashMap<>();
        for (String act : List.of("act1", "act2", "act3"))
            pools.put(act, Map.of(
                    "weak", List.of(new WavesConfig.PoolEntry("bad", 99)),
                    "strong", List.of(new WavesConfig.PoolEntry("s_a", 1)),
                    "boss", List.of(new WavesConfig.PoolEntry("b_boss", 1))));
        WaveDefinitions defs = new WaveDefinitions(strat, pools);
        List<ActPlan> acts = RunPlanner.plan(1L, 1, defs, oneMapLib(), scaling(), cfg(1, 1));
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

        SpawnStrategyCfg bad = new SpawnStrategyCfg("bad", "bad",
                List.of(new StepCfg("missing", EntityType.ZOMBIE, 1, new CoeffCfg(1, 1, 1), 0, List.of())),
                1, List.of());
        Map<String, SpawnStrategyCfg> strat = new HashMap<>(simpleDefs().strategies());
        strat.put("bad", bad);
        Map<String, Map<String, List<WavesConfig.PoolEntry>>> pools = new HashMap<>();
        for (String act : List.of("act1", "act2", "act3"))
            pools.put(act, Map.of(
                    "weak", List.of(new WavesConfig.PoolEntry("bad", 99)),
                    "strong", List.of(new WavesConfig.PoolEntry("s_a", 1)),
                    "boss", List.of(new WavesConfig.PoolEntry("b_boss", 1))));
        WaveDefinitions defs = new WaveDefinitions(strat, pools);
        assertThrows(IllegalStateException.class,
                () -> RunPlanner.plan(1L, 1, defs, new MapLibrary(byAct), scaling(), cfg(1, 1)));
    }

    @Test
    void specialWavesMergedIntoPool() {
        SpawnStrategyCfg special = new SpawnStrategyCfg("s_special", "s_special",
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
            List<ActPlan> acts = RunPlanner.plan(s, 1, defs, new MapLibrary(byAct), scaling(), cfg());
            List<WaveSpec> strong = acts.get(0).waves().subList(3, 5);
            if (strong.stream().anyMatch(w -> "s_special".equals(w.strategyId()))) specialHits++;
        }
        assertTrue(specialHits > 45, "specialHits=" + specialHits);
    }

    @Test
    void carriesIndependentSkillsForAllSpawnKinds() {
        PassengerCfg passenger = new PassengerCfg(
                EntityType.SKELETON, 1, new CoeffCfg(1, 1, 1), List.of("archer"));
        DeathSpawnCfg death = new DeathSpawnCfg(
                EntityType.SILVERFISH, 1, new CoeffCfg(1, 1, 1), List.of("berserk"));
        StepCfg root = new StepCfg(
                "1", EntityType.ZOMBIE, 1, new CoeffCfg(1, 1, 1), 0, List.of("sprint"),
                List.of(), List.of(death), List.of(passenger));
        SpawnStrategyCfg im = new SpawnStrategyCfg("w_im", "w_im", List.of(root), 1, List.of());

        Map<String, SpawnStrategyCfg> strategies = new HashMap<>(simpleDefs().strategies());
        strategies.put("w_im", im);
        Map<String, Map<String, List<WavesConfig.PoolEntry>>> pools = new HashMap<>();
        for (String act : List.of("act1", "act2", "act3")) {
            pools.put(act, Map.of(
                    "weak", List.of(new WavesConfig.PoolEntry("w_im", 1)),
                    "strong", List.of(new WavesConfig.PoolEntry("s_a", 1)),
                    "boss", List.of(new WavesConfig.PoolEntry("b_boss", 1))));
        }

        SpawnStep step = RunPlanner.plan(
                1L, 1, new WaveDefinitions(strategies, pools),
                oneMapLib(), scaling(), cfg(1, 1))
                .get(0).waves().get(0).steps().get(0);

        assertEquals(List.of("sprint"), step.skills());
        assertEquals(List.of("archer"), step.passengers().get(0).skills());
        assertEquals(List.of("berserk"), step.onDeath().get(0).skills());
    }

    @Test
    void deathSpawnCarriesPassengers() {
        PassengerCfg rider = new PassengerCfg(
                EntityType.VINDICATOR, 1, new CoeffCfg(1, 1, 1), List.of(),
                List.of(), List.of(), List.of());
        DeathSpawnCfg camel = new DeathSpawnCfg(
                EntityType.CAMEL, 1, new CoeffCfg(1, 1, 1), List.of(),
                List.of(), List.of(rider), List.of());
        StepCfg root = new StepCfg(
                "1", EntityType.SLIME, 1, new CoeffCfg(1, 1, 1), 0, List.of(),
                List.of(), List.of(camel), List.of());
        SpawnStrategyCfg strat = new SpawnStrategyCfg("w_rhine", "w_rhine", List.of(root), 1, List.of());

        Map<String, SpawnStrategyCfg> strategies = new HashMap<>(simpleDefs().strategies());
        strategies.put("w_rhine", strat);
        Map<String, Map<String, List<WavesConfig.PoolEntry>>> pools = new HashMap<>();
        for (String act : List.of("act1", "act2", "act3")) {
            pools.put(act, Map.of(
                    "weak", List.of(new WavesConfig.PoolEntry("w_rhine", 1)),
                    "strong", List.of(new WavesConfig.PoolEntry("s_a", 1)),
                    "boss", List.of(new WavesConfig.PoolEntry("b_boss", 1))));
        }

        DeathSpawn death = RunPlanner.plan(
                2L, 1, new WaveDefinitions(strategies, pools),
                oneMapLib(), scaling(), cfg(1, 1))
                .get(0).waves().get(0).steps().get(0).onDeath().get(0);

        assertEquals(EntityType.CAMEL, death.type());
        assertEquals(1, death.passengers().size());
        assertEquals(EntityType.VINDICATOR, death.passengers().get(0).type());
    }
}
