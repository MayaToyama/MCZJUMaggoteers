package io.mczju.maggoteers.util;

import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.EntityType;
import org.bukkit.potion.PotionEffectType;

import java.util.Locale;
import java.util.Map;

/** Paper 26.x Registry 解析（替代 getByName / Attribute.valueOf / EntityType.valueOf）。 */
public final class GameRegistries {

    private static final Map<String, String> ATTRIBUTE_LEGACY = Map.ofEntries(
            Map.entry("MAX_HEALTH", "generic.max_health"),
            Map.entry("ATTACK_DAMAGE", "generic.attack_damage"),
            Map.entry("MOVEMENT_SPEED", "generic.movement_speed"),
            Map.entry("ATTACK_SPEED", "generic.attack_speed")
    );

    private GameRegistries() {}

    public static PotionEffectType potionEffect(String name) {
        if (name == null || name.isBlank()) return null;
        String u = name.trim().toUpperCase(Locale.ROOT);
        PotionEffectType t = Registry.POTION_EFFECT_TYPE.get(NamespacedKey.minecraft(u.toLowerCase(Locale.ROOT)));
        if (t != null) return t;
        t = Registry.POTION_EFFECT_TYPE.get(NamespacedKey.minecraft(u.toLowerCase(Locale.ROOT).replace('_', '.')));
        return t;
    }

    public static Attribute attribute(String name) {
        if (name == null || name.isBlank()) return null;
        String u = name.trim().toUpperCase(Locale.ROOT);
        String key = ATTRIBUTE_LEGACY.getOrDefault(u, u.toLowerCase(Locale.ROOT).replace('_', '.'));
        return Registry.ATTRIBUTE.get(NamespacedKey.minecraft(key));
    }

    public static EntityType entityType(String name) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("entity type blank");
        String u = name.trim().toUpperCase(Locale.ROOT);
        EntityType t = Registry.ENTITY_TYPE.get(NamespacedKey.minecraft(u.toLowerCase(Locale.ROOT)));
        if (t == null) throw new IllegalArgumentException("Unknown entity type: " + name);
        return t;
    }
}
