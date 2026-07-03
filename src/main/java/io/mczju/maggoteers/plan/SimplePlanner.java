package io.mczju.maggoteers.plan;

import io.mczju.maggoteers.config.SpawnStrategyCfg;
import io.mczju.maggoteers.config.StepCfg;
import io.mczju.maggoteers.config.WavesConfig;
import io.mczju.maggoteers.util.Coords;
import io.mczju.maggoteers.util.Origins;
import io.mczju.maggoteers.wave.*;
import io.mczju.maggoteers.world.MapRepository;
import io.mczju.maggoteers.world.MapRepository.MapEntry;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

/**
 * Plan 2 同步规划器：plain-Random 抽图 + 加权 roll 波次 + 展开 strategy + 解析绝对坐标（coeff-only）。
 * <p><b>Plan 3 替换点</b>：{@link RunPlanner}（异步、种子化、coeff×affix×scaling）产出同一 {@code List<ActPlan>} 契约。
 */
public final class SimplePlanner {

    public static List<ActPlan> plan(JavaPlugin plugin, int playerCount) {
        Random rng = new Random();
        List<ActPlan> acts = new ArrayList<>();
        for (String act : List.of("act1", "act2", "act3")) {
            acts.add(planAct(plugin, act, rng));
        }
        return acts;
    }

    private static ActPlan planAct(JavaPlugin plugin, String act, Random rng) {
        MapEntry map = MapRepository.pickRandom(act);
        if (map == null) throw new IllegalStateException("act " + act + " 无可用地图");
        Vec3 origin = readOrigin(plugin, act);

        int weakN = plugin.getConfig().getInt("waves_per_act." + act + ".weak", 3);
        int strongN = plugin.getConfig().getInt("waves_per_act." + act + ".strong", 2);

        List<WaveSpec> waves = new ArrayList<>();
        for (int i = 0; i < weakN; i++) waves.add(rollWave(act, "weak", map, origin, rng));
        for (int i = 0; i < strongN; i++) waves.add(rollWave(act, "strong", map, origin, rng));
        waves.add(rollWave(act, "boss", map, origin, rng));

        Vec3 playerSpawnAbs = Coords.resolve(origin, map.points().playerSpawn());
        return new ActPlan(map.mapId(), playerSpawnAbs, waves);
    }

    /** 加权随机 roll 一条 strategy，解析成 WaveSpec（绝对坐标 + coeff 倍率）。 */
    private static WaveSpec rollWave(String act, String tier, MapEntry map, Vec3 origin, Random rng) {
        var pool = WavesConfig.getInstance().getPool(act, tier);
        if (pool.isEmpty()) throw new IllegalStateException(act + "/" + tier + " 池为空");
        String strategyId = weightedPick(pool, rng).strategy();
        SpawnStrategyCfg strat = WavesConfig.getInstance().getStrategy(strategyId);
        if (strat == null) throw new IllegalStateException("strategy 未定义: " + strategyId);

        List<StepCfg> ordered = new ArrayList<>(strat.steps());
        ordered.sort(Comparator.comparingInt(StepCfg::delaySec));
        List<SpawnStep> steps = new ArrayList<>();
        for (StepCfg sc : ordered) {
            Vec3 rel = map.points().point(sc.point());
            if (rel == null) throw new IllegalStateException(
                    "刷怪点 " + sc.point() + " 在 " + map.mapId() + "/points.yml 未定义（G3）");
            Vec3 abs = Coords.resolve(origin, rel);
            steps.add(new SpawnStep(abs, sc.type(), sc.count(),
                    sc.coeff().hp(), sc.coeff().dmg(), sc.coeff().speed(),
                    sc.delaySec() * 20, List.of(), List.of()));
        }
        List<RewardItem> rewards = strat.clearReward().stream()
                .map(r -> new RewardItem(r.item(), r.amount())).toList();
        return new WaveSpec(steps, strat.repeat(), rewards);
    }

    static WavesConfig.PoolEntry weightedPick(List<WavesConfig.PoolEntry> pool, Random rng) {
        double total = pool.stream().mapToDouble(WavesConfig.PoolEntry::weight).sum();
        double r = rng.nextDouble() * total;
        double acc = 0;
        for (var e : pool) {
            acc += e.weight();
            if (r <= acc) return e;
        }
        return pool.get(pool.size() - 1);
    }

    private static Vec3 readOrigin(JavaPlugin plugin, String act) {
        return Origins.parse(plugin.getConfig().getIntegerList("act_origins." + act), 64);
    }

    private SimplePlanner() {}
}
