package io.mczju.maggoteers.item.fx;

import io.mczju.maggoteers.MaggoteersPlugin;
import io.mczju.maggoteers.util.GameRegistries;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 从 {@code config.yml → magic_fx} 与 {@code items/*.yml → use_fx} 加载魔法释放配置。
 * <p>合并顺序：defaults → config {@code magic_fx.weapons.<id>} → 物品 {@code use_fx}（后者覆盖前者）。
 */
public final class MagicFxConfig {
    private static MagicUseFx defaults = MagicUseFx.defaults();
    private static final Map<String, MagicUseFx> BY_WEAPON = new HashMap<>();

    private MagicFxConfig() {}

    public static void load(MaggoteersPlugin plugin) {
        defaults = parseSection(plugin.getConfig().getConfigurationSection("magic_fx.defaults"), MagicUseFx.defaults());
        BY_WEAPON.clear();
        ConfigurationSection weapons = plugin.getConfig().getConfigurationSection("magic_fx.weapons");
        if (weapons != null) {
            for (String id : weapons.getKeys(false)) {
                BY_WEAPON.put(id, merge(defaults, parseSection(weapons.getConfigurationSection(id), defaults)));
            }
        }
        loadItemYamlFx(new File(plugin.getDataFolder(), "items"));
        if (plugin.getResource("items/maggoteers.yml") != null) {
            try (var in = plugin.getResource("items/maggoteers.yml")) {
                if (in != null) {
                    YamlConfiguration yaml = YamlConfiguration.loadConfiguration(
                            new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));
                    mergeYamlItems(yaml);
                }
            } catch (Exception e) {
                plugin.getLogger().warning("读取内置 items/maggoteers.yml use_fx 失败：" + e.getMessage());
            }
        }
        plugin.getLogger().info("MagicFxConfig：默认 preset=" + defaults.preset()
                + "，已绑定 " + BY_WEAPON.size() + " 个武器 FX。");
    }

    private static void loadItemYamlFx(File dir) {
        if (dir == null || !dir.isDirectory()) return;
        File[] files = dir.listFiles((d, n) -> n.endsWith(".yml"));
        if (files == null) return;
        for (File f : files) {
            mergeYamlItems(YamlConfiguration.loadConfiguration(f));
        }
    }

    private static void mergeYamlItems(YamlConfiguration yaml) {
        for (String key : yaml.getKeys(false)) {
            ConfigurationSection itemSec = yaml.getConfigurationSection(key);
            if (itemSec == null) continue;
            ConfigurationSection fx = itemSec.getConfigurationSection("use_fx");
            if (fx == null) continue;
            String itemId = key.contains(":") ? key : "maggoteers:" + key.replaceFirst("^maggoteers:", "");
            MagicUseFx base = BY_WEAPON.getOrDefault(itemId, defaults);
            BY_WEAPON.put(itemId, merge(base, parseSection(fx, base)));
        }
    }

    /** FX merge for weapon abilities: defaults -> config.weapons -> ability fx section. */
    public static MagicUseFx mergedFxForAbility(String itemId, ConfigurationSection abilityFx) {
        MagicUseFx base = forWeapon(itemId);
        if (abilityFx == null) {
            return base;
        }
        return merge(base, parseSection(abilityFx, base));
    }
    public static MagicUseFx forWeapon(String itemId) {
        if (itemId == null) return defaults;
        return BY_WEAPON.getOrDefault(itemId, defaults);
    }

    /** 是否在配置里显式绑定了 FX（非仅靠 defaults）。 */
    public static boolean hasExplicitBinding(String itemId) {
        return itemId != null && BY_WEAPON.containsKey(itemId);
    }

    public static Set<String> boundWeaponIds() {
        return Set.copyOf(BY_WEAPON.keySet());
    }

    private static MagicUseFx merge(MagicUseFx base, MagicUseFx over) {
        return new MagicUseFx(
                over.sound() != null ? over.sound() : base.sound(),
                over.soundVolume() > 0 ? over.soundVolume() : base.soundVolume(),
                over.soundPitch() > 0 ? over.soundPitch() : base.soundPitch(),
                over.preset() != null ? over.preset() : base.preset(),
                over.particle() != null ? over.particle() : base.particle(),
                over.radius() > 0 ? over.radius() : base.radius(),
                over.rayLength() > 0 ? over.rayLength() : base.rayLength(),
                over.density() > 0 ? over.density() : base.density(),
                over.rippleRings() > 0 ? over.rippleRings() : base.rippleRings(),
                over.expandSteps() > 0 ? over.expandSteps() : base.expandSteps(),
                over.spiralTicks() > 0 ? over.spiralTicks() : base.spiralTicks()
        );
    }

    private static MagicUseFx parseSection(ConfigurationSection sec, MagicUseFx fallback) {
        if (sec == null) return fallback;
        Sound sound = fallback.sound();
        String soundRaw = sec.getString("sound");
        if (soundRaw != null && !soundRaw.isBlank()) {
            Sound parsed = GameRegistries.sound(soundRaw);
            if (parsed != null) sound = parsed;
        }
        MagicFxPreset preset = fallback.preset();
        String presetRaw = sec.getString("preset");
        if (presetRaw != null && !presetRaw.isBlank()) {
            try {
                preset = MagicFxPreset.valueOf(presetRaw.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                MaggoteersPlugin.getInstance().getLogger().warning("未知 magic_fx preset: " + presetRaw);
            }
        }
        Particle particle = fallback.particle();
        String particleRaw = sec.getString("particle");
        if (particleRaw != null && !particleRaw.isBlank()) {
            Particle p = GameRegistries.particle(particleRaw);
            if (p != null) particle = p;
            else MaggoteersPlugin.getInstance().getLogger().warning("无法解析 particle: " + particleRaw);
        }
        float vol = (float) sec.getDouble("sound_volume", fallback.soundVolume());
        float pitch = (float) sec.getDouble("sound_pitch", fallback.soundPitch());
        double radius = sec.getDouble("radius", fallback.radius());
        double ray = sec.getDouble("ray_length", fallback.rayLength());
        int density = sec.getInt("density", fallback.density());
        int ripple = sec.getInt("ripple_rings", fallback.rippleRings());
        int expand = sec.getInt("expand_steps", fallback.expandSteps());
        int spiral = sec.getInt("spiral_ticks", fallback.spiralTicks());
        return new MagicUseFx(sound, vol, pitch, preset, particle, radius, ray, density, ripple, expand, spiral);
    }
}
