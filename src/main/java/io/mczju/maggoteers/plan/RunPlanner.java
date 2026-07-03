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
            out.add(planAct(root.derive(i), ACTS[i], defs, maps, snap, affixes, cfg));
        }
        return out;
    }

    private static ActPlan planAct(SeededRng actRng, String act, WaveDefinitions defs, MapLibrary maps,
                                   ScalingConfig.Scaling snap, AffixService affixes, RunConfig cfg) {
        List<MapEntry> avail = maps.maps(act);
        if (avail.isEmpty()) throw new IllegalStateException(act + " 无可用地图");
        MapEntry map = avail.get(actRng.nextInt(avail.size()));
        Vec3 origin = cfg.origin(act);

        List<WaveSpec> waves = new ArrayList<>();
        for (int i = 0; i < cfg.weak(act); i++)
            waves.add(rollWave(actRng.derive(100 + i), act, "weak", map, origin, defs, snap, affixes));
        for (int i = 0; i < cfg.strong(act); i++)
            waves.add(rollWave(actRng.derive(200 + i), act, "strong", map, origin, defs, snap, affixes));
        waves.add(rollWave(actRng.derive(300), act, "boss", map, origin, defs, snap, affixes));

        return new ActPlan(map.mapId(), Coords.resolve(origin, map.points().playerSpawn()), waves);
    }

    private static WaveSpec rollWave(SeededRng rng, String act, String tier, MapEntry map, Vec3 origin,
                                     WaveDefinitions defs, ScalingConfig.Scaling snap, AffixService affixes) {
        List<WavesConfig.PoolEntry> base = new ArrayList<>(defs.pool(act, tier));
        base.addAll(map.specialWaves().getOrDefault(tier, List.of()));
        if (base.isEmpty()) throw new IllegalStateException(act + "/" + tier + " 池为空");

        double[] weights = base.stream().mapToDouble(WavesConfig.PoolEntry::weight).toArray();
        SpawnStrategyCfg strat = defs.strategy(base.get(rng.weightedIndex(weights)).strategy());
        if (strat == null) throw new IllegalStateException("strategy 未定义: " + base);

        List<StepCfg> ordered = new ArrayList<>(strat.steps());
        ordered.sort(Comparator.comparingInt(StepCfg::delaySec));

        List<SpawnStep> steps = new ArrayList<>();
        for (StepCfg sc : ordered) {
            Vec3 rel = map.points().point(sc.point());
            if (rel == null) throw new IllegalStateException(
                    "刷怪点 " + sc.point() + " 在 " + map.mapId() + "/points.yml 未定义（strategy=" + strat.id() + "，G3）");
            List<Affix> resolved = affixes.resolve(sc.affixes());
            Compose.MobScale ms = Compose.compose(sc.coeff(), resolved, snap);
            int resolvedCount = ScalingConfig.rollCount(sc.count(), snap.mobCount(), rng);
            steps.add(new SpawnStep(
                    Coords.resolve(origin, rel), sc.type(), resolvedCount,
                    ms.hp(), ms.dmg(), ms.speed(), ms.drop(),
                    sc.delaySec() * 20,
                    sc.affixes(),
                    resolved.stream().flatMap(a -> a.potions().stream()).toList()));
        }
        List<RewardItem> rewards = strat.clearReward().stream()
                .map(r -> new RewardItem(r.item(), r.amount())).toList();
        return new WaveSpec(steps, strat.repeat(), rewards);
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
