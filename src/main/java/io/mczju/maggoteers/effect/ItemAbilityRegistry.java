package io.mczju.maggoteers.effect;

import io.mczju.maggoteers.MaggoteersPlugin;
import io.mczju.maggoteers.item.fx.MagicFxConfig;
import io.mczju.maggoteers.item.fx.MagicUseFx;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

/** Loads weapon {@code use_ability} blocks from {@code items/*.yml}. */
public final class ItemAbilityRegistry {

    private static final Logger LOG = Logger.getLogger("Maggoteers");
    private static final Map<String, ItemAbility> BY_ITEM_ID = new HashMap<>();

    private ItemAbilityRegistry() {}

    public static void load(MaggoteersPlugin plugin) {
        BY_ITEM_ID.clear();
        loadItemsDir(plugin, new File(plugin.getDataFolder(), "items"));
        if (plugin.getResource("items/maggoteers.yml") != null) {
            try (var in = plugin.getResource("items/maggoteers.yml")) {
                if (in != null) {
                    YamlConfiguration yaml = YamlConfiguration.loadConfiguration(
                            new InputStreamReader(in, StandardCharsets.UTF_8));
                    parseYaml(plugin, yaml);
                }
            } catch (Exception e) {
                plugin.getLogger().warning("读取内置 items/maggoteers.yml use_ability 失败: " + e.getMessage());
            }
        }
        LOG.info("ItemAbilityRegistry: loaded " + BY_ITEM_ID.size() + " use_ability entries");
    }

    public static Optional<ItemAbility> get(String itemId) {
        if (itemId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(BY_ITEM_ID.get(itemId));
    }

    public static Map<String, ItemAbility> snapshot() {
        return Map.copyOf(BY_ITEM_ID);
    }

    private static void loadItemsDir(MaggoteersPlugin plugin, File dir) {
        if (dir == null || !dir.isDirectory()) {
            return;
        }
        File[] files = dir.listFiles((d, n) -> n.endsWith(".yml"));
        if (files == null) {
            return;
        }
        for (File f : files) {
            parseYaml(plugin, YamlConfiguration.loadConfiguration(f));
        }
    }

    private static void parseYaml(MaggoteersPlugin plugin, YamlConfiguration yaml) {
        for (String key : yaml.getKeys(false)) {
            ConfigurationSection itemSec = yaml.getConfigurationSection(key);
            if (itemSec == null) {
                continue;
            }
            String itemId = normalizeItemId(key);
            ConfigurationSection abilitySec = itemSec.getConfigurationSection("use_ability");
            if (abilitySec == null) {
                continue;
            }
            parseAbility(plugin, itemId, abilitySec, itemSec.getConfigurationSection("use_fx"));
        }
    }

    private static void parseAbility(MaggoteersPlugin plugin, String itemId,
                                     ConfigurationSection abilitySec, ConfigurationSection legacyUseFx) {
        if (!abilitySec.contains("cooldown_sec")) {
            LOG.warning("items: " + itemId + " use_ability missing cooldown_sec — skipped");
            return;
        }
        int cooldownSec = abilitySec.getInt("cooldown_sec", 0);
        if (cooldownSec <= 0) {
            LOG.warning("items: " + itemId + " use_ability invalid cooldown_sec — skipped");
            return;
        }
        String effectRaw = abilitySec.getString("effect");
        if (effectRaw == null || effectRaw.isBlank()) {
            LOG.warning("items: " + itemId + " use_ability missing effect — skipped");
            return;
        }
        Effect effect;
        try {
            effect = Effect.valueOf(effectRaw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            LOG.warning("items: " + itemId + " unknown effect " + effectRaw + " — skipped");
            return;
        }
        EffectContext params = parseParams(abilitySec.getConfigurationSection("params"));
        params = MagicEffectParams.withDefaults(effect, params);

        if (effect == Effect.HEAL_AREA) {
            Optional<String> healErr = MagicEffectParams.validateHealAreaTargets(params);
            if (healErr.isPresent()) {
                LOG.warning("items: " + itemId + " HEAL_AREA " + healErr.get() + " — skipped");
                return;
            }
        }

        ConfigurationSection fxSec = abilitySec.getConfigurationSection("fx");
        if (fxSec == null && legacyUseFx != null) {
            plugin.getLogger().warning("items: " + itemId
                    + " use_fx is deprecated; move under use_ability.fx");
            fxSec = legacyUseFx;
        }
        MagicUseFx fx = MagicFxConfig.mergedFxForAbility(itemId, fxSec);
        BY_ITEM_ID.put(itemId, new ItemAbility(itemId, cooldownSec, effect, params, fx));
    }

    private static EffectContext parseParams(ConfigurationSection sec) {
        EffectContext ctx = new EffectContext();
        if (sec == null) {
            return ctx;
        }
        for (String k : sec.getKeys(false)) {
            Object v = sec.get(k);
            switch (k) {
                case "radius" -> ctx.put(EffectKeys.RADIUS, sec.getDouble(k));
                case "damage" -> ctx.put(EffectKeys.DAMAGE, sec.getDouble(k));
                case "amount" -> ctx.put(EffectKeys.AMOUNT, sec.getDouble(k));
                case "ray_length" -> ctx.put(EffectKeys.RAY_LENGTH, sec.getDouble(k));
                case "beam_radius" -> ctx.put(EffectKeys.BEAM_RADIUS, sec.getDouble(k));
                case "targets" -> ctx.put(EffectKeys.TARGETS, String.valueOf(v).toLowerCase(Locale.ROOT));
                case "enemy_scope" -> ctx.put(EffectKeys.ENEMY_SCOPE, String.valueOf(v).toLowerCase(Locale.ROOT));
                case "include_self" -> ctx.put(EffectKeys.INCLUDE_SELF, sec.getBoolean(k));
                default -> { }
            }
        }
        return ctx;
    }

    private static String normalizeItemId(String key) {
        if (key.contains(":")) {
            return key;
        }
        return "maggoteers:" + key.replaceFirst("^maggoteers:", "");
    }
}