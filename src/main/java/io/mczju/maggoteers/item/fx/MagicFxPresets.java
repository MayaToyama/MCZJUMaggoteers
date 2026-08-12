package io.mczju.maggoteers.item.fx;

import io.mczju.maggoteers.MaggoteersPlugin;
import io.mczju.maggoteers.util.ParticleEffects;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

public final class MagicFxPresets {
    private MagicFxPresets() {}

    public static void play(Player player, MagicUseFx fx) {
        if (player == null || fx == null) return;
        switch (fx.preset()) {
            case NONE -> { }
            case SPIRAL_RADIUS -> spiralRadius(player, fx);
            case RIPPLE_RINGS -> rippleRings(player, fx);
            case AIM_RAY -> aimRay(player, fx);
            case SMOOTH_EXPAND_RING -> smoothExpandRing(player, fx);
            case DOT_ABOVE -> dotAbove(player.getLocation(), fx);
            case THICK_RING -> ParticleEffects.playThickRing(
                    player.getWorld(), player.getLocation(), fx.radius(), fx.particle(), fx.density());
        }
    }

    /** 沿方向绘制加粗光束（Ex咖喱棒等），起点 eye，长度 length。 */
    public static void playBeamAlong(org.bukkit.Location eye, Vector dir, double length, MagicUseFx fx) {
        if (eye.getWorld() == null || dir == null) return;
        Vector d = dir.clone().normalize();
        Particle p = fx.particle();
        int steps = Math.max(20, fx.density());
        double step = length / steps;
        for (int i = 0; i <= steps; i++) {
            org.bukkit.Location pt = eye.clone().add(d.clone().multiply(step * i));
            eye.getWorld().spawnParticle(p, pt, 10, 0.12, 0.12, 0.12, 0.02);
        }
    }

    /** 在实体上方周期性播放 mark 粒子，持续 {@code durationSec} 秒。 */
    public static void followMarkAbove(LivingEntity entity, MagicUseFx fx, int durationSec) {
        if (entity == null || fx == null || durationSec <= 0) return;
        int maxTicks = durationSec * 20;
        new BukkitRunnable() {
            int elapsed = 0;

            @Override
            public void run() {
                if (!entity.isValid() || entity.isDead() || elapsed >= maxTicks) {
                    cancel();
                    return;
                }
                dotAbove(entity.getLocation(), fx);
                elapsed += 4;
            }
        }.runTaskTimer(MaggoteersPlugin.getInstance(), 0L, 4L);
    }

    /** 实体正上方约 1 格：加粗圆点。 */
    public static void dotAbove(Location entityLoc, MagicUseFx fx) {
        if (entityLoc.getWorld() == null) return;
        Location c = entityLoc.clone().add(0, 1.05, 0);
        Particle p = fx.particle();
        int n = Math.max(12, fx.density() / 3);
        double dotR = 0.45;
        for (int i = 0; i < n; i++) {
            double a = 2 * Math.PI * i / n;
            c.getWorld().spawnParticle(p,
                    c.getX() + dotR * Math.cos(a), c.getY(), c.getZ() + dotR * Math.sin(a),
                    5, 0.03, 0.04, 0.03, 0.012);
        }
        c.getWorld().spawnParticle(p, c, 8, 0.08, 0.05, 0.08, 0.01);
    }

    /** 沿视线加粗射线，长度 {@link MagicUseFx#rayLength()}。 */
    private static void aimRay(Player player, MagicUseFx fx) {
        Location eye = player.getEyeLocation();
        Vector dir = eye.getDirection().normalize();
        double len = fx.rayLength();
        Particle p = fx.particle();
        int steps = Math.max(20, fx.density() / 2);
        double step = len / steps;
        for (int i = 0; i <= steps; i++) {
            Location pt = eye.clone().add(dir.clone().multiply(step * i));
            eye.getWorld().spawnParticle(p, pt, 8, 0.08, 0.08, 0.08, 0.015);
        }
    }

    /**
     * 波浪式同心环：环数 {@code ripple_rings}，每步绘制当前环并淡化重绘旧环（残留可见）。
     */
    private static void rippleRings(Player player, MagicUseFx fx) {
        new BukkitRunnable() {
            int ring = 0;
            final int rings = Math.max(2, fx.rippleRings());

            @Override public void run() {
                if (!player.isOnline() || ring >= rings) {
                    cancel();
                    return;
                }
                double rNew = fx.radius() * (ring + 1) / rings;
                for (int k = 0; k <= ring; k++) {
                    double r = fx.radius() * (k + 1) / rings;
                    int dens = k == ring ? fx.density() : Math.max(8, fx.density() / 3);
                    ParticleEffects.playThickRing(player.getWorld(), player.getLocation(), r, fx.particle(), dens);
                }
                ring++;
            }
        }.runTaskTimer(MaggoteersPlugin.getInstance(), 0L, 5L);
    }

    /** 单环平滑放大，步数 {@code expand_steps}，同一时刻主环最亮。 */
    private static void smoothExpandRing(Player player, MagicUseFx fx) {
        new BukkitRunnable() {
            int step = 0;
            final int steps = Math.max(8, fx.expandSteps());

            @Override public void run() {
                if (!player.isOnline() || step > steps) {
                    cancel();
                    return;
                }
                double t = step / (double) steps;
                double r = fx.radius() * t;
                if (r > 0.05) {
                    ParticleEffects.playThickRing(
                            player.getWorld(), player.getLocation(), r, fx.particle(), fx.density() / 2 + 8);
                }
                step++;
            }
        }.runTaskTimer(MaggoteersPlugin.getInstance(), 0L, 1L);
    }

    /** 2–3 条弧段绕玩家旋转的剑气半径粒子。 */
    private static void spiralRadius(Player player, MagicUseFx fx) {
        new BukkitRunnable() {
            int tick = 0;
            final int duration = Math.max(8, fx.spiralTicks());

            @Override public void run() {
                if (!player.isOnline() || tick >= duration) {
                    cancel();
                    return;
                }
                Location base = player.getLocation();
                double yawRad = Math.toRadians(base.getYaw());
                double spin = 2 * Math.PI * tick / duration;
                Particle p = fx.particle();
                int arcs = 3;
                double arcWidth = Math.toRadians(28);
                for (int a = 0; a < arcs; a++) {
                    double baseAngle = spin + a * (2 * Math.PI / arcs);
                    int steps = Math.max(6, fx.density() / 10);
                    for (int i = 0; i <= steps; i++) {
                        double t = i / (double) steps;
                        double r = fx.radius() * t;
                        double angle = baseAngle + (t - 0.5) * arcWidth;
                        double x = base.getX() + r * Math.sin(angle - yawRad);
                        double z = base.getZ() + r * Math.cos(angle - yawRad);
                        base.getWorld().spawnParticle(p, x, base.getY() + 0.9, z, 1, 0.02, 0.02, 0.02, 0.01);
                    }
                }
                tick++;
            }
        }.runTaskTimer(MaggoteersPlugin.getInstance(), 0L, 1L);
    }
}
