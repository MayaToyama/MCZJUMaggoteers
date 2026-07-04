package io.mczju.maggoteers.util;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;

/** 粒子效果工具：给范围效果提供视觉范围提示。 */
public final class ParticleEffects {
    private ParticleEffects() {}

    /**
     * 在 center 周围画一个水平粒子圆环（半径 radius），用于范围效果的范围提示。
     * 中心高度抬 0.5（约腰部），points 为采样点数（建议 ~半径*4）。
     */
    public static void playAreaRing(World world, Location center, double radius, Particle particle, int points) {
        if (world == null || center == null || radius <= 0 || particle == null || points <= 0) return;
        double y = center.getY() + 0.5;
        double cx = center.getX(), cz = center.getZ();
        for (int i = 0; i < points; i++) {
            double angle = 2 * Math.PI * i / points;
            double x = cx + radius * Math.cos(angle);
            double z = cz + radius * Math.sin(angle);
            world.spawnParticle(particle, new Location(world, x, y, z), 1, 0, 0, 0, 0);
        }
    }

    /** 简便重载：默认用 FLAME + radius*4 个点。 */
    public static void playAreaRing(World world, Location center, double radius) {
        playAreaRing(world, center, radius, Particle.FLAME, Math.max(12, (int) (radius * 4)));
    }
}
