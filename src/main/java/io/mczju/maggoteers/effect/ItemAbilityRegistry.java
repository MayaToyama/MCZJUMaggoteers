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
        EffectContext params = EffectParamsParser.parse(abilitySec.getConfigurationSection("params"));
        params = MagicEffectParams.withDefaults(effect, params);

        if (effect == Effect.HEAL_AREA) {
            Optional<String> healErr = MagicEffectParams.validateHealAreaTargets(params);
            if (healErr.isPresent()) {
                LOG.warning("items: " + itemId + " HEAL_AREA " + healErr.get() + " — skipped");
                return;
            }
        }
        if (effect == Effect.BUFF_AREA) {
            if (params.has(EffectKeys.MARK_ON)) {
                LOG.warning("items: " + itemId + " mark_on ignored on weapons");
            }
            String targets = params.get(EffectKeys.TARGETS);
            if (targets != null && BuffAreaTargets.TARGET_HIT_TARGET.equalsIgnoreCase(targets)) {
                LOG.warning("items: " + itemId + " targets=hit_target ineffective on weapon right-click");
            }
            Optional<String> buffErr = MagicEffectParams.validateBuffArea(params, null, true);
            if (buffErr.isPresent()) {
                LOG.warning("items: " + itemId + " BUFF_AREA " + buffErr.get() + " — skipped");
                return;
            }
        }
        if (effect == Effect.DISABLE_AI) {
            if (params.has(EffectKeys.MARK_ON)) {
                LOG.warning("items: " + itemId + " mark_on ignored on weapons");
            }
            String targets = params.get(EffectKeys.TARGETS);
            if (targets != null) {
                String t = targets.trim().toLowerCase(Locale.ROOT);
                if (BuffAreaTargets.TARGET_HIT_TARGET.equals(t)
                        || BuffAreaTargets.TARGET_ATTACKER.equals(t)) {
                    LOG.warning("items: " + itemId + " targets=" + t
                            + " ineffective on weapon right-click");
                }
            }
            Optional<String> err = MagicEffectParams.validateDisableAi(params, null, true);
            if (err.isPresent()) {
                LOG.warning("items: " + itemId + " DISABLE_AI " + err.get() + " — skipped");
                return;
            }
        }
        if (effect == Effect.GRANT_ITEM) {
            if (GrantItemParams.parse(params).isEmpty()) {
                LOG.warning("items: " + itemId + " GRANT_ITEM missing item — skipped");
                return;
            }
        }
        if (effect == Effect.SUMMON) {
            Optional<String> summonErr = SummonParamsParser.validate(params, null, true);
            if (summonErr.isPresent()) {
                LOG.warning("items: " + itemId + " SUMMON " + summonErr.get() + " — skipped");
                return;
            }
            String anchor = params.get(EffectKeys.ANCHOR);
            if (anchor != null && !BuffAreaTargets.TARGET_SELF.equalsIgnoreCase(anchor)) {
                LOG.warning("items: " + itemId + " weapon SUMMON coerces anchor to self");
            }
        }

        Trigger expiryTrigger = null;
        int expiryCharges = 0;
        Stack stack = null;
        if (effect == Effect.ADD_ATTRIBUTE || effect == Effect.ADD_POTION) {
            ConfigurationSection expirySec = abilitySec.getConfigurationSection("expiry");
            int durationTicks = params.getOrDefault(EffectKeys.DURATION_TICKS, 0);
            if (effect == Effect.ADD_POTION) {
                Optional<String> potionPathErr = WeaponEffectValidator.validateUseAbilityPotion(
                        expirySec != null, durationTicks);
                if (potionPathErr.isPresent()) {
                    LOG.warning("items: " + itemId + " ADD_POTION " + potionPathErr.get() + " — skipped");
                    return;
                }
                if (expirySec == null) {
                    if (params.get(EffectKeys.POTION) == null) {
                        LOG.warning("items: " + itemId + " ADD_POTION missing potion — skipped");
                        return;
                    }
                }
            }
            if (effect == Effect.ADD_ATTRIBUTE && expirySec == null) {
                LOG.warning("items: " + itemId + " ADD_ATTRIBUTE requires expiry — skipped");
                return;
            }
            if (expirySec != null) {
                String trigRaw = expirySec.getString("trigger");
                if (trigRaw == null || trigRaw.isBlank()) {
                    LOG.warning("items: " + itemId + " expiry missing trigger — skipped");
                    return;
                }
                try {
                    expiryTrigger = Trigger.valueOf(trigRaw.trim().toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException ex) {
                    LOG.warning("items: " + itemId + " unknown expiry.trigger " + trigRaw + " — skipped");
                    return;
                }
                expiryCharges = expirySec.getInt("charges", 1);
                String stackRaw = abilitySec.getString("stack", "REPLACE");
                try {
                    stack = Stack.valueOf(stackRaw.trim().toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException ex) {
                    LOG.warning("items: " + itemId + " unknown stack " + stackRaw + " — skipped");
                    return;
                }
                Optional<String> tempErr = WeaponTempEffect.validate(
                        effect, params, expiryTrigger, expiryCharges);
                if (tempErr.isPresent()) {
                    LOG.warning("items: " + itemId + " " + effect + " " + tempErr.get() + " — skipped");
                    return;
                }
            }
        }

        boolean consume = abilitySec.getBoolean("consume", false);
        ConfigurationSection fxSec = abilitySec.getConfigurationSection("fx");
        if (fxSec == null && legacyUseFx != null) {
            plugin.getLogger().warning("items: " + itemId
                    + " use_fx is deprecated; move under use_ability.fx");
            fxSec = legacyUseFx;
        }
        MagicUseFx fx = MagicFxConfig.mergedFxForAbility(itemId, fxSec);
        BY_ITEM_ID.put(itemId, new ItemAbility(
                itemId, cooldownSec, effect, params, fx, consume, expiryTrigger, expiryCharges, stack));
    }

    private static String normalizeItemId(String key) {
        if (key.contains(":")) {
            return key;
        }
        return "maggoteers:" + key.replaceFirst("^maggoteers:", "");
    }
}