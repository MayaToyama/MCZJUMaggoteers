package io.mczju.maggoteers.util;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;

/** 粒子效果工具：给范围效果提供视觉范围提示。 */
public final class ParticleEffects {
    private ParticleEffects() {}

    /** 加粗水平圆环（魔法物品默认）。 */
    public static void playThickRing(World world, Location center, double radius, Particle particle, int points) {
        if (world == null || center == null || radius <= 0 || particle == null || points <= 0) return;
        double y = center.getY() + 0.5;
        double cx = center.getX(), cz = center.getZ();
        int n = Math.max(points, (int) (radius * 12));
        for (int i = 0; i < n; i++) {
            double angle = 2 * Math.PI * i / n;
            double x = cx + radius * Math.cos(angle);
            double z = cz + radius * Math.sin(angle);
            world.spawnParticle(particle, x, y, z, 3, 0.04, 0.06, 0.04, 0.01);
        }
    }

    /** 简便重载：默认用 FLAME + 加粗密度。 */
    public static void playAreaRing(World world, Location center, double radius) {
        playThickRing(world, center, radius, Particle.FLAME, Math.max(32, (int) (radius * 12)));
    }
}
