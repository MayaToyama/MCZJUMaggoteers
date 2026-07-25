package io.mczju.maggoteers.util;

import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.EntityType;
import org.bukkit.potion.PotionEffectType;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

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
        Set<String> candidates = new LinkedHashSet<>();
        if (ATTRIBUTE_LEGACY.containsKey(u)) candidates.add(ATTRIBUTE_LEGACY.get(u));
        String dotted = u.toLowerCase(Locale.ROOT).replace('_', '.');
        candidates.add(dotted);
        if (dotted.startsWith("generic.")) {
            candidates.add(dotted.substring("generic.".length()));
        }
        for (String key : candidates) {
            Attribute a = Registry.ATTRIBUTE.get(NamespacedKey.minecraft(key));
            if (a != null) return a;
        }
        var plugin = io.mczju.maggoteers.MaggoteersPlugin.getInstance();
        if (plugin != null) {
            plugin.getLogger().warning("未知属性: " + name);
        }
        return null;
    }

    public static EntityType entityType(String name) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("entity type blank");
        String u = name.trim().toUpperCase(Locale.ROOT);
        EntityType t = Registry.ENTITY_TYPE.get(NamespacedKey.minecraft(u.toLowerCase(Locale.ROOT)));
        if (t == null) throw new IllegalArgumentException("Unknown entity type: " + name);
        return t;
    }

    /** 解析 Sound：{@code ENTITY_PLAYER_ATTACK_SWEEP} 或 {@code minecraft:entity.player.attack.sweep}。 */
    public static org.bukkit.Sound sound(String name) {
        if (name == null || name.isBlank()) return null;
        String raw = name.trim();
        String u = raw.contains(":") ? raw.substring(raw.indexOf(':') + 1) : raw;
        u = u.toUpperCase(Locale.ROOT).replace('.', '_').replace('-', '_');
        try {
            return org.bukkit.Sound.valueOf(u);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static final java.util.Set<Particle> PARTICLE_ALLOWLIST = java.util.Set.of(
            Particle.FLAME, Particle.SOUL_FIRE_FLAME, Particle.CRIT, Particle.ENCHANT,
            Particle.DUST, Particle.ELECTRIC_SPARK, Particle.WITCH, Particle.DRAGON_BREATH,
            Particle.END_ROD, Particle.TOTEM_OF_UNDYING, Particle.HAPPY_VILLAGER, Particle.INSTANT_EFFECT
    );

    /** 解析 Particle；失败时返回 null（调用方保留默认）。 */
    public static Particle particle(String name) {
        if (name == null || name.isBlank()) return null;
        String u = name.trim().toUpperCase(Locale.ROOT);
        try {
            return Particle.valueOf(u);
        } catch (IllegalArgumentException e) {
            for (Particle p : PARTICLE_ALLOWLIST) {
                if (p.name().equalsIgnoreCase(u)) return p;
            }
            return null;
        }
    }
}
