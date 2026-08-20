package io.mczju.maggoteers.config;

import io.mczju.maggoteers.plan.SeededRng;
import org.bukkit.plugin.java.JavaPlugin;

/** 人数缩放配置（借鉴前代 VampireSurvivor ScalingConfig）。onEnable 调一次。 */
public final class ScalingConfig {

    /** 某人数下的缩放快照。 */
    public record Scaling(double mobHp, double mobDamage, double mobSpeed, double mobCount) {}

    private static ScalingConfig INSTANCE;
    private final double[] mobHp, mobDamage, mobSpeed, mobCount;

    public static void load(JavaPlugin plugin) {
        INSTANCE = new ScalingConfig(
                read(plugin, "scaling.mob_hp",        new double[]{1.0, 1.3, 1.6, 2.0}),
                read(plugin, "scaling.mob_damage",   new double[]{1.0, 1.15, 1.3, 1.5}),
                read(plugin, "scaling.mob_speed",    new double[]{1.0, 1.0, 1.0, 1.0}),
                read(plugin, "scaling.mob_count",    new double[]{1.0, 1.0, 1.2, 1.5}));
        plugin.getLogger().info("ScalingConfig 已加载。");
    }

    private static double[] read(JavaPlugin plugin, String path, double[] def) {
        var list = plugin.getConfig().getDoubleList(path);
        return list.isEmpty() ? def : list.stream().mapToDouble(Double::doubleValue).toArray();
    }

    public static ScalingConfig getInstance() {
        if (INSTANCE == null) throw new IllegalStateException("ScalingConfig 未加载！");
        return INSTANCE;
    }

    /** 1–4 人，越界 clamp 到端点。 */
    public Scaling scaleFor(int playerCount) {
        return new Scaling(
                at(mobHp, playerCount), at(mobDamage, playerCount), at(mobSpeed, playerCount),
                at(mobCount, playerCount));
    }

    private static double at(double[] arr, int playerCount) {
        int idx = Math.max(0, Math.min(playerCount - 1, arr.length - 1));
        return arr[idx];
    }

    /**
     * count-roll（D6）：{@code floor(base × m) + (rng.nextDouble() < frac ? 1 : 0)}。
     * 补 1 的随机走 {@link SeededRng}，保证同 seed 可重放。
     */
    public static int rollCount(int base, double mobCountMult, SeededRng rng) {
        double scaled = base * mobCountMult;
        int floor = (int) Math.floor(scaled);
        double frac = scaled - floor;
        return Math.max(0, floor + (rng.nextDouble() < frac ? 1 : 0));
    }

    /** 测试用：直接构造（绕过 Bukkit）。 */
    public static ScalingConfig forTesting(double[] hp, double[] dmg, double[] spd, double[] count) {
        return new ScalingConfig(hp, dmg, spd, count);
    }

    private ScalingConfig(double[] mobHp, double[] mobDamage, double[] mobSpeed, double[] mobCount) {
        this.mobHp = mobHp;
        this.mobDamage = mobDamage;
        this.mobSpeed = mobSpeed;
        this.mobCount = mobCount;
    }
}
