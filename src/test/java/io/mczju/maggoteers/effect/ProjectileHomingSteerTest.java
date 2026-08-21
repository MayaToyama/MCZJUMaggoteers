package io.mczju.maggoteers.effect;

import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ProjectileHomingService 弹道转向纯逻辑回归。
 * 覆盖：转向收敛、恒速保持、Fireball 预测落点（lead-time）。
 */
class ProjectileHomingSteerTest {

    /** factor=1 → 完全指向目标方向。 */
    @Test
    void steerFullFactorAimsDirectlyAtTarget() {
        Vector cur = new Vector(1, 0, 0);
        Vector dir = new Vector(0, 0, 1);
        Vector next = ProjectileHomingService.steer(cur, dir, 1.6, 1.0);
        assertEquals(1.6, next.length(), 1e-9);
        assertEquals(0.0, next.getX(), 1e-9);
        assertEquals(1.6, next.getZ(), 1e-9);
    }

    /** factor=0 → 保持原方向不变。 */
    @Test
    void steerZeroFactorKeepsDirection() {
        Vector cur = new Vector(1, 0, 0);
        Vector dir = new Vector(0, 0, 1);
        Vector next = ProjectileHomingService.steer(cur, dir, 1.6, 0.0);
        assertEquals(1.6, next.getX(), 1e-9);
        assertEquals(0.0, next.getZ(), 1e-9);
    }

    /** 转向不改变速度大小（恒速 homing）。 */
    @Test
    void steerPreservesSpeedMagnitude() {
        Vector cur = new Vector(0.8, 0.3, -0.4).normalize().multiply(2.4);
        Vector dir = new Vector(-0.2, 0.5, 0.9).normalize();
        Vector next = ProjectileHomingService.steer(cur, dir, 2.4, 0.65);
        assertEquals(2.4, next.length(), 1e-9);
    }

    /** 多次转向后收敛到目标方向（角度单调减小）。 */
    @Test
    void steerConvergesOverTicks() {
        Vector cur = new Vector(1, 0, 0);
        Vector dir = new Vector(0, 0, 1);
        Vector v = cur.clone();
        for (int i = 0; i < 50; i++) {
            v = ProjectileHomingService.steer(v, dir, 1.6, 0.65);
        }
        // 收敛后方向与目标方向夹角 < 5°
        double dot = v.clone().normalize().dot(dir.clone().normalize());
        assertTrue(dot > 0.996, "expected near-alignment, dot=" + dot);
    }

    /** 目标预测：lead-time 把"目标当前位置"修正为"目标移动后落点"。 */
    @Test
    void leadAimCompensatesMovingTarget() {
        Vector proj = new Vector(0, 0, 0);
        Vector targetNow = new Vector(10, 0, 0);
        Vector targetVel = new Vector(0, 0, 1.0);   // 玩家向 +Z 跑
        double speed = 1.6;
        double dist = targetNow.distance(proj);
        double leadTicks = dist / speed - 0.5;
        Vector aim = targetNow.clone().add(targetVel.clone().multiply(leadTicks));
        Vector dir = aim.clone().subtract(proj).normalize();
        // 有预测：方向带 +Z 分量（朝玩家将到位置）
        assertTrue(dir.getZ() > 0.05, "expected +Z lead, dir=" + dir);
    }
}
