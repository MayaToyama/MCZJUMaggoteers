package io.mczju.maggoteers.effect;

import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;

/**
 * Magic / ability damage that must land even if the target is still in a vanilla
 * invulnerability window from a prior hit (melee, self-cost, etc.).
 */
public final class MagicDamage {
    private MagicDamage() {}

    /**
     * Clear no-damage ticks and last-damage so LivingEntity#damage applies fully.
     */
    public static void applyIndependent(LivingEntity target, double amount, Entity source) {
        if (target == null || amount <= 0) return;
        target.setNoDamageTicks(0);
        target.setLastDamage(0.0);
        if (source != null) {
            target.damage(amount, source);
        } else {
            target.damage(amount);
        }
    }
}