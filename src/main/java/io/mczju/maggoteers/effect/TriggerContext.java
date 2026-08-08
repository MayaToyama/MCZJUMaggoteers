package io.mczju.maggoteers.effect;

import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;

public record TriggerContext(
        Trigger fired,
        LivingEntity hitTarget,
        LivingEntity attacker,
        Location eventLocation
) {
    public TriggerContext(Trigger fired, LivingEntity hitTarget, LivingEntity attacker) {
        this(fired, hitTarget, attacker, null);
    }

    public static TriggerContext empty() {
        return new TriggerContext(null, null, null, null);
    }

    /** Event anchor (e.g. death site) with optional trigger tag. */
    public static TriggerContext atEvent(Trigger fired, Location location) {
        Location loc = location == null ? null : location.clone();
        return new TriggerContext(fired, null, null, loc);
    }

    /** Prefer {@link #eventLocation()} when resolving area/summon origins. */
    public static Location resolveOrigin(org.bukkit.entity.Player player, TriggerContext ctx) {
        if (ctx != null && ctx.eventLocation() != null && ctx.eventLocation().getWorld() != null) {
            return ctx.eventLocation();
        }
        return player != null ? player.getLocation() : null;
    }
}
