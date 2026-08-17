package io.mczju.maggoteers.plan;

import io.mczju.maggoteers.MaggoteersPlugin;
import io.mczju.maggoteers.config.*;
import io.mczju.maggoteers.util.Coords;
import io.mczju.maggoteers.util.Origins;
import io.mczju.maggoteers.wave.*;
import io.mczju.maggoteers.world.MapLibrary;
import io.mczju.maggoteers.world.MapRepository;
import io.mczju.maggoteers.world.MapRepository.MapEntry;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

/**
 * 异步种子化预生成（§6）。同 seed + 同 playerCount → 同 {@code List<ActPlan>}（可重放）。
 */
public final class RunPlanner {

    private static final String[] ACTS = {"act1", "act2", "act3"};

    /** 生产入口：onGameStart 异步线程调用。 */
    public static List<ActPlan> plan(long seed, int playerCount) {
        JavaPlugin plugin = MaggoteersPlugin.getInstance();
        return plan(seed, playerCount,
                WavesConfig.getInstance().definitions(),
                MapRepository.library(),
                ScalingConfig.getInstance(),
                AffixService.getInstance(),
                loadRunConfig(plugin));
    }

    /** 纯函数：完全注入，不碰 Bukkit。 */
    public static List<ActPlan> plan(long seed, int playerCount, WaveDefinitions defs,
                                     MapLibrary maps, ScalingConfig scaling,
                                     AffixService affixes, RunConfig cfg) {
        SeededRng root = new SeededRng(seed);
        ScalingConfig.Scaling snap = scaling.scaleFor(playerCount);
        List<ActPlan> out = new ArrayList<>(3);
        for (int i = 0; i < ACTS.length; i++) {
            out.add(planAct(root, i, ACTS[i], defs, maps, snap, affixes, cfg));
        }
        return out;
    }

    private static ActPlan planAct(SeededRng root, int actIndex, String act, WaveDefinitions defs, MapLibrary maps,
                                   ScalingConfig.Scaling snap, AffixService affixes, RunConfig cfg) {
        List<MapEntry> avail = maps.maps(act);
        if (avail.isEmpty()) throw new IllegalStateException(act + " 无可用地图");
        SeededRng actRng = root.derive(1_000L + actIndex);
        MapEntry map = avail.get(actRng.nextInt(avail.size()));
        Vec3 origin = cfg.origin(act);

        List<WaveSpec> waves = new ArrayList<>();
        Set<String> usedWeak = new HashSet<>();
        for (int i = 0; i < cfg.weak(act); i++)
            waves.add(rollWave(root.derive(wavePickSalt(actIndex, "weak", i)), act, "weak",
                    map, origin, defs, snap, affixes, usedWeak, cfg.weak(act)));
        Set<String> usedStrong = new HashSet<>();
        for (int i = 0; i < cfg.strong(act); i++)
            waves.add(rollWave(root.derive(wavePickSalt(actIndex, "strong", i)), act, "strong",
                    map, origin, defs, snap, affixes, usedStrong, cfg.strong(act)));
        Set<String> usedBoss = new HashSet<>();
        waves.add(rollWave(root.derive(wavePickSalt(actIndex, "boss", 0)), act, "boss",
                map, origin, defs, snap, affixes, usedBoss, 1));

        return new ActPlan(map.mapId(), Coords.resolve(origin, map.points().playerSpawn()), waves);
    }

    private static WaveSpec rollWave(SeededRng rng, String act, String tier, MapEntry map, Vec3 origin,
                                     WaveDefinitions defs, ScalingConfig.Scaling snap, AffixService affixes,
                                     Set<String> used, int need) {
        List<WavesConfig.PoolEntry> base = new ArrayList<>(defs.pool(act, tier));
        base.addAll(map.specialWaves().getOrDefault(tier, List.of()));
        if (base.isEmpty()) throw new IllegalStateException(act + "/" + tier + " 池为空");

        List<WavesConfig.PoolEntry> candidates = new ArrayList<>();
        for (WavesConfig.PoolEntry e : base) {
            if (!used.contains(e.strategy())) candidates.add(e);
        }
        if (candidates.isEmpty()) {
            throw new IllegalStateException(
                    act + "/" + tier + " 池在去重后为空（已用=" + used + "，需求=" + need + "）");
        }

        double[] weights = candidates.stream().mapToDouble(WavesConfig.PoolEntry::weight).toArray();
        int picked = rng.weightedIndex(weights);
        String pickedId = candidates.get(picked).strategy();
        used.add(pickedId);
        SpawnStrategyCfg strat = defs.strategy(pickedId);
        if (strat == null) throw new IllegalStateException("strategy 未定义: " + pickedId);

        SeededRng countRng = rng.derive(50_000L);

        List<StepCfg> ordered = new ArrayList<>(strat.steps());

        List<SpawnStep> steps = new ArrayList<>();
        for (StepCfg sc : ordered) {
            Vec3 rel = map.points().resolvePointOrBossFallback(sc.point());
            if (rel == null) throw new IllegalStateException(
                    "刷怪点 " + sc.point() + " 在 " + map.mapId() + "/points.yml 未定义"
                            + (map.points().point("boss") == null ? "且无 boss 可回退" : "且非 boss 点数≥9 不可回退")
                            + "（strategy=" + strat.id() + "，G3）");
            List<Affix> resolved = affixes.resolve(sc.affixes());
            Compose.MobScale ms = Compose.compose(sc.coeff(), resolved, snap);
            int resolvedCount = ScalingConfig.rollCount(sc.count(), snap.mobCount(), countRng);
            List<PassengerSpawn> passengerSpawns = buildPassengerTrees(sc.passengers(), affixes, snap, countRng);
            List<DeathSpawn> onDeath = buildDeathSpawns(sc.onDeath(), affixes, snap, countRng);
            steps.add(new SpawnStep(
                    Coords.resolve(origin, rel), sc.type(), resolvedCount,
                    ms.hp(), ms.dmg(), ms.speed(),
                    ms.scale(), ms.followRange(),
                    sc.delaySec() * 20,
                    sc.affixes(),
                    resolved.stream().flatMap(a -> a.potions().stream()).toList(),
                    sc.infernal(),
                    sc.equipment(),
                    onDeath,
                    passengerSpawns,
                    sc.repeat(),
                    sc.name(),
                    sc.bossBar()));
        }
        List<RewardItem> rewards = strat.clearReward().stream()
                .map(r -> new RewardItem(r.item(), r.amount())).toList();
        return new WaveSpec(pickedId, strat.displayName(), steps, strat.repeat(), rewards);
    }

    /** 全局剧本 seed 下每次池 roll 的唯一盐（含层序 / tier / 层内序号）。 */
    private static long wavePickSalt(int actIndex, String tier, int waveIndexInTier) {
        int tierCode = switch (tier) {
            case "weak" -> 1;
            case "strong" -> 2;
            case "boss" -> 3;
            default -> 0;
        };
        return (actIndex + 1L) * 1_000_000L + tierCode * 10_000L + waveIndexInTier;
    }

    private static List<PassengerSpawn> buildPassengerTrees(List<PassengerCfg> configs,
                                                            AffixService affixes,
                                                            ScalingConfig.Scaling snap,
                                                            SeededRng countRng) {
        if (configs == null || configs.isEmpty()) return List.of();
        List<PassengerSpawn> out = new ArrayList<>();
        for (PassengerCfg pc : configs) {
            List<Affix> pa = affixes.resolve(pc.affixes());
            Compose.MobScale pms = Compose.compose(pc.coeff(), pa, snap);
            int pcnt = ScalingConfig.rollCount(pc.count(), snap.mobCount(), countRng);
            List<PassengerSpawn> nested = buildPassengerTrees(pc.passengers(), affixes, snap, countRng);
            List<DeathSpawn> onDeath = buildDeathSpawns(pc.onDeath(), affixes, snap, countRng);
            for (int i = 0; i < pcnt; i++) {
                out.add(new PassengerSpawn(
                        pc.type(), pms.hp(), pms.dmg(), pms.speed(),
                        pms.scale(), pms.followRange(),
                        pc.affixes(),
                        pa.stream().flatMap(a -> a.potions().stream()).toList(),
                        pc.infernal(),
                        pc.equipment(),
                        onDeath,
                        nested,
                        pc.name(),
                        pc.bossBar()));
            }
        }
        return out;
    }

    private static List<DeathSpawn> buildDeathSpawns(List<DeathSpawnCfg> configs,
                                                     AffixService affixes,
                                                     ScalingConfig.Scaling snap,
                                                     SeededRng countRng) {
        if (configs == null || configs.isEmpty()) return List.of();
        List<DeathSpawn> out = new ArrayList<>();
        for (DeathSpawnCfg dc : configs) {
            List<Affix> da = affixes.resolve(dc.affixes());
            Compose.MobScale dms = Compose.compose(dc.coeff(), da, snap);
            int dcnt = ScalingConfig.rollCount(dc.count(), snap.mobCount(), countRng);
            List<PassengerSpawn> passengers = buildPassengerTrees(dc.passengers(), affixes, snap, countRng);
            List<DeathSpawn> nested = buildDeathSpawns(dc.onDeath(), affixes, snap, countRng);
            for (int i = 0; i < dcnt; i++) {
                out.add(new DeathSpawn(
                        dc.type(),
                        dms.hp(), dms.dmg(), dms.speed(),
                        dms.scale(), dms.followRange(),
                        dc.affixes(),
                        da.stream().flatMap(a -> a.potions().stream()).toList(),
                        dc.infernal(),
                        dc.equipment(),
                        passengers,
                        nested,
                        dc.name(),
                        dc.bossBar()));
            }
        }
        return out;
    }

    /** 从 config.yml 读层原点 + 每层波数，构造纯 {@link RunConfig}。 */
    public static RunConfig loadRunConfig(JavaPlugin plugin) {
        Map<String, Vec3> origins = new HashMap<>();
        Map<String, int[]> wpa = new HashMap<>();
        for (String act : ACTS) {
            origins.put(act, Origins.parse(plugin.getConfig().getIntegerList("act_origins." + act), 64));
            wpa.put(act, new int[]{
                    plugin.getConfig().getInt("waves_per_act." + act + ".weak", 3),
                    plugin.getConfig().getInt("waves_per_act." + act + ".strong", 2)});
        }
        return new RunConfig(origins, wpa);
    }

    private RunPlanner() {}
}
