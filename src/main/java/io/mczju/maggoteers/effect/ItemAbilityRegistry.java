package io.mczju.maggoteers.effect;

import io.mczju.maggoteers.MaggoteersPlugin;
import io.mczju.maggoteers.item.fx.MagicFxConfig;
import io.mczju.maggoteers.item.fx.MagicUseFx;
import io.mczju.maggoteers.util.ItemYaml;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;

/** Loads weapon {@code use_ability} blocks from {@code items/*.yml}. */
public final class ItemAbilityRegistry {

    private static final Logger LOG = Logger.getLogger("Maggoteers");
    private static final Map<String, ItemAbility> BY_ITEM_ID = new HashMap<>();

    private static final Set<Trigger> DEFERRED_EXPIRY_WHITELIST = Set.of(
            Trigger.ON_DAMAGE_DEALT, Trigger.ON_DAMAGE_TAKEN, Trigger.ON_KILL);

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

    /**
     * Package-visible for unit tests: omitted stack defaults to REPLACE.
     * <p>有意不一致：本 helper 只服务武器 {@code use_ability} 路径（deferred 默认 REPLACE）；
     * {@code held_effects} 路径在 WeaponHeldRegistry 有自己默认（常驻 ADD_ATTRIBUTE → ADD）。
     * 两条路径语义不同，不要统一（会改变行为）。
     */
    static Stack defaultStackForStep(Stack parsed) {
        if (parsed != null) {
            return parsed;
        }
        return Stack.REPLACE;
    }

    /** Package-visible for unit tests. */
    static List<AbilityStep> parseStepsForTest(String itemId, ConfigurationSection abilitySec) {
        return parseSteps(itemId, abilitySec);
    }

    private static void loadItemsDir(MaggoteersPlugin plugin, File dir) {
        ItemYaml.forEachYaml(dir, yaml -> parseYaml(plugin, yaml));
    }

    private static void parseYaml(MaggoteersPlugin plugin, YamlConfiguration yaml) {
        for (String key : yaml.getKeys(false)) {
            ConfigurationSection itemSec = yaml.getConfigurationSection(key);
            if (itemSec == null) {
                continue;
            }
            String itemId = ItemYaml.normalizeItemId(key);
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

        boolean hasEffectsList = hasEffectsList(abilitySec);
        String topEffect = abilitySec.getString("effect");
        boolean hasTopEffect = topEffect != null && !topEffect.isBlank();
        if (hasEffectsList && hasTopEffect) {
            LOG.warning("items: " + itemId + " use_ability has both effects[] and effect: — skipped");
            return;
        }

        List<AbilityStep> steps = parseSteps(itemId, abilitySec);
        if (steps.isEmpty()) {
            LOG.warning("items: " + itemId + " use_ability has no valid steps — skipped");
            return;
        }

        java.util.List<BuffPotionSpec> selfPotions = abilitySec.contains("self_potions")
                ? BuffPotionParser.parseList(abilitySec.getList("self_potions"))
                : java.util.List.of();

        Optional<String> fakeSelf = Fake255Rules.findFakeClearInPotionList(
                abilitySec.getList("self_potions"), itemId + " self_potions");
        if (fakeSelf.isPresent()) {
            LOG.severe("items: " + itemId + " forbidden fake amp-255 clear: " + fakeSelf.get()
                    + " — use self_clear_potions; ability skipped");
            return;
        }

        java.util.List<org.bukkit.potion.PotionEffectType> selfClear = java.util.List.of();
        if (abilitySec.contains("self_clear_potions")) {
            ClearPotionsParser.ParseResult selfClearPr = ClearPotionsParser.parse(
                    abilitySec.getList("self_clear_potions"), itemId + " self_clear_potions");
            if (!selfClearPr.ok()) {
                for (String err : selfClearPr.errors()) {
                    LOG.severe("items: " + err + " — ability skipped");
                }
                return;
            }
            selfClear = selfClearPr.types();
        }

        boolean consume = abilitySec.getBoolean("consume", false);
        ConfigurationSection fxSec = abilitySec.getConfigurationSection("fx");
        if (fxSec == null && legacyUseFx != null) {
            plugin.getLogger().warning("items: " + itemId
                    + " use_fx is deprecated; move under use_ability.fx");
            fxSec = legacyUseFx;
        }
        AbilityStep legacyStepOrNull = (!hasEffectsList && steps.size() == 1) ? steps.get(0) : null;
        MagicUseFx fx = MagicFxConfig.mergedFxForAbilityMulti(itemId, fxSec, legacyUseFx, legacyStepOrNull);
        MagicUseFx empowerFx = MagicFxConfig.parseOptionalFx(abilitySec.getConfigurationSection("empower_fx"));

        BY_ITEM_ID.put(itemId, new ItemAbility(
                itemId, cooldownSec, consume, fx, empowerFx, selfPotions, selfClear, steps));
    }

    private static boolean hasEffectsList(ConfigurationSection abilitySec) {
        if (abilitySec.isList("effects")) {
            return true;
        }
        if (!abilitySec.getMapList("effects").isEmpty()) {
            return true;
        }
        return abilitySec.isConfigurationSection("effects");
    }

    private static List<AbilityStep> parseSteps(String itemId, ConfigurationSection abilitySec) {
        boolean hasList = hasEffectsList(abilitySec);
        String topEffect = abilitySec.getString("effect");
        boolean hasTopEffect = topEffect != null && !topEffect.isBlank();
        if (hasList && hasTopEffect) {
            LOG.warning("items: " + itemId + " use_ability has both effects[] and effect: — skipped");
            return List.of();
        }
        if (hasList) {
            return parseEffectsList(itemId, abilitySec);
        }
        if (!hasTopEffect) {
            return List.of();
        }
        AbilityStep step = parseOneStep(itemId, abilitySec, "");
        return step == null ? List.of() : List.of(step);
    }

    private static List<AbilityStep> parseEffectsList(String itemId, ConfigurationSection abilitySec) {
        List<AbilityStep> out = new ArrayList<>();
        List<Map<?, ?>> mapList = abilitySec.getMapList("effects");
        if (!mapList.isEmpty() || abilitySec.isList("effects")) {
            int i = 0;
            for (Map<?, ?> raw : mapList) {
                ConfigurationSection stepSec = mapToSection(raw);
                AbilityStep step = parseOneStep(itemId, stepSec, "[" + i + "]");
                if (step != null) {
                    out.add(step);
                }
                i++;
            }
            return out;
        }
        ConfigurationSection effectsSec = abilitySec.getConfigurationSection("effects");
        if (effectsSec == null) {
            return out;
        }
        List<String> keys = new ArrayList<>(effectsSec.getKeys(false));
        keys.sort((a, b) -> {
            try {
                return Integer.compare(Integer.parseInt(a), Integer.parseInt(b));
            } catch (NumberFormatException e) {
                return a.compareTo(b);
            }
        });
        for (String key : keys) {
            ConfigurationSection stepSec = effectsSec.getConfigurationSection(key);
            if (stepSec == null) {
                Object raw = effectsSec.get(key);
                if (raw instanceof Map<?, ?> map) {
                    stepSec = mapToSection(map);
                } else {
                    LOG.warning("items: " + itemId + " effects." + key + " invalid — skipped step");
                    continue;
                }
            }
            AbilityStep step = parseOneStep(itemId, stepSec, "[" + key + "]");
            if (step != null) {
                out.add(step);
            }
        }
        return out;
    }

    private static ConfigurationSection mapToSection(Map<?, ?> map) {
        YamlConfiguration y = new YamlConfiguration();
        if (map == null) {
            return y;
        }
        for (Map.Entry<?, ?> e : map.entrySet()) {
            if (e.getKey() == null) {
                continue;
            }
            String key = String.valueOf(e.getKey());
            Object val = e.getValue();
            // getMapList nests params/expiry as Map; y.set(Map) leaves getConfigurationSection null
            if (val instanceof Map<?, ?> nested) {
                y.createSection(key, nested);
            } else {
                y.set(key, val);
            }
        }
        return y;
    }

    private static AbilityStep parseOneStep(String itemId, ConfigurationSection stepSec, String stepLabel) {
        String where = itemId + (stepLabel == null || stepLabel.isEmpty() ? "" : " " + stepLabel);

        if ((stepLabel != null && !stepLabel.isEmpty()) && stepSec.contains("cooldown_sec")) {
            LOG.warning("items: " + where + " step-level cooldown_sec ignored (use top-level only)");
        }

        String effectRaw = stepSec.getString("effect");
        if (effectRaw == null || effectRaw.isBlank()) {
            LOG.warning("items: " + where + " step missing effect — skipped");
            return null;
        }
        Effect effect;
        try {
            effect = Effect.valueOf(effectRaw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            LOG.warning("items: " + where + " unknown effect " + effectRaw + " — skipped");
            return null;
        }

        if (effect == Effect.AURA || effect == Effect.BOUND_EQUIP) {
            LOG.warning("items: " + where + " " + effect + " forbidden in use_ability — skipped");
            return null;
        }

        ConfigurationSection paramsSec = stepSec.getConfigurationSection("params");
        if (paramsSec != null) {
            Optional<String> fakePotions = Fake255Rules.findFakeClearInPotionList(
                    paramsSec.getList("potions"), where + " params.potions");
            if (fakePotions.isPresent()) {
                LOG.severe("items: " + where + " forbidden fake amp-255 clear: " + fakePotions.get()
                        + " — use clear_potions; step skipped");
                return null;
            }
            if (paramsSec.contains("clear_potions")) {
                ClearPotionsParser.ParseResult clearPr = ClearPotionsParser.parse(
                        paramsSec.getList("clear_potions"), where + " params.clear_potions");
                if (!clearPr.ok()) {
                    for (String err : clearPr.errors()) {
                        LOG.severe("items: " + err + " — step skipped");
                    }
                    return null;
                }
            }
        }

        EffectContext params = EffectParamsParser.parse(paramsSec);
        params = MagicEffectParams.withDefaults(effect, params);

        if (paramsSec != null && paramsSec.contains("clear_potions")) {
            ClearPotionsParser.ParseResult clearPr = ClearPotionsParser.parse(
                    paramsSec.getList("clear_potions"), where + " params.clear_potions");
            if (clearPr.ok()) {
                params.put(EffectKeys.CLEAR_POTIONS, clearPr.types());
            }
        }

        if (Boolean.TRUE.equals(params.get(EffectKeys.IMMUNITY))) {
            LOG.warning("items: " + where + " immunity: true is STAT-only — ignored on weapons");
        }

        ConfigurationSection expirySec = stepSec.getConfigurationSection("expiry");
        Trigger expiryTrigger = null;
        int expiryCharges = 0;
        if (expirySec != null) {
            String trigRaw = expirySec.getString("trigger");
            if (trigRaw == null || trigRaw.isBlank()) {
                LOG.warning("items: " + where + " expiry missing trigger — skipped");
                return null;
            }
            try {
                expiryTrigger = Trigger.valueOf(trigRaw.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                LOG.warning("items: " + where + " unknown expiry.trigger " + trigRaw + " — skipped");
                return null;
            }
            expiryCharges = expirySec.getInt("charges", 1);
            if (!DEFERRED_EXPIRY_WHITELIST.contains(expiryTrigger)) {
                LOG.warning("items: " + where + " deferred expiry.trigger " + expiryTrigger
                        + " not in whitelist — skipped");
                return null;
            }
        }
        boolean deferred = expiryTrigger != null;

        if (effect == Effect.HEAL_AREA) {
            Optional<String> healErr = MagicEffectParams.validateHealAreaTargets(params);
            if (healErr.isPresent()) {
                LOG.warning("items: " + where + " HEAL_AREA " + healErr.get() + " — skipped");
                return null;
            }
        }
        if (effect == Effect.BUFF_AREA) {
            if (params.has(EffectKeys.MARK_ON)) {
                LOG.warning("items: " + where + " mark_on ignored on weapons");
            }
            if (!deferred) {
                String targets = params.get(EffectKeys.TARGETS);
                if (targets != null && BuffAreaTargets.TARGET_HIT_TARGET.equalsIgnoreCase(targets)) {
                    LOG.warning("items: " + where + " targets=hit_target ineffective on weapon right-click");
                }
            }
            Optional<String> buffErr = MagicEffectParams.validateBuffArea(
                    params, deferred ? expiryTrigger : null, true);
            if (buffErr.isPresent()) {
                LOG.warning("items: " + where + " BUFF_AREA " + buffErr.get() + " — skipped");
                return null;
            }
        }
        if (effect == Effect.DISABLE_AI) {
            if (params.has(EffectKeys.MARK_ON)) {
                LOG.warning("items: " + where + " mark_on ignored on weapons");
            }
            if (!deferred) {
                String targets = params.get(EffectKeys.TARGETS);
                if (targets != null) {
                    String t = targets.trim().toLowerCase(Locale.ROOT);
                    if (BuffAreaTargets.TARGET_HIT_TARGET.equals(t)
                            || BuffAreaTargets.TARGET_ATTACKER.equals(t)) {
                        LOG.warning("items: " + where + " targets=" + t
                                + " ineffective on weapon right-click");
                    }
                }
            }
            Optional<String> err = MagicEffectParams.validateDisableAi(
                    params, deferred ? expiryTrigger : null, true);
            if (err.isPresent()) {
                LOG.warning("items: " + where + " DISABLE_AI " + err.get() + " — skipped");
                return null;
            }
        }
        if (effect == Effect.GRANT_ITEM) {
            if (GrantItemParams.parse(params).isEmpty()) {
                LOG.warning("items: " + where + " GRANT_ITEM missing item — skipped");
                return null;
            }
        }
        if (effect == Effect.SUMMON) {
            Optional<String> summonErr = SummonParamsParser.validate(params, null, true);
            if (summonErr.isPresent()) {
                LOG.warning("items: " + where + " SUMMON " + summonErr.get() + " — skipped");
                return null;
            }
            String anchor = params.get(EffectKeys.ANCHOR);
            if (anchor != null && !BuffAreaTargets.TARGET_SELF.equalsIgnoreCase(anchor)) {
                LOG.warning("items: " + where + " weapon SUMMON coerces anchor to self");
            }
        }

        if (effect == Effect.ADD_ATTRIBUTE || effect == Effect.ADD_POTION) {
            int durationTicks = params.getOrDefault(EffectKeys.DURATION_TICKS, 0);
            if (effect == Effect.ADD_POTION) {
                Optional<String> potionPathErr = WeaponEffectValidator.validateUseAbilityPotion(
                        deferred, durationTicks);
                if (potionPathErr.isPresent()) {
                    LOG.warning("items: " + where + " ADD_POTION " + potionPathErr.get() + " — skipped");
                    return null;
                }
                if (!deferred && !hasPotion(params)) {
                    LOG.warning("items: " + where + " ADD_POTION missing potion — skipped");
                    return null;
                }
            }
            if (effect == Effect.ADD_ATTRIBUTE && !deferred) {
                LOG.warning("items: " + where + " ADD_ATTRIBUTE requires expiry — skipped");
                return null;
            }
            if (deferred) {
                Optional<String> tempErr = WeaponTempEffect.validate(
                        effect, params, expiryTrigger, expiryCharges);
                if (tempErr.isPresent()) {
                    LOG.warning("items: " + where + " " + effect + " " + tempErr.get() + " — skipped");
                    return null;
                }
            }
        } else if (deferred) {
            Optional<String> tempErr = WeaponTempEffect.validate(
                    effect, params, expiryTrigger, expiryCharges);
            if (tempErr.isPresent()) {
                LOG.warning("items: " + where + " " + effect + " " + tempErr.get() + " — skipped");
                return null;
            }
        }

        Stack parsedStack = null;
        if (stepSec.contains("stack")) {
            String stackRaw = stepSec.getString("stack");
            if (stackRaw != null && !stackRaw.isBlank()) {
                try {
                    parsedStack = Stack.valueOf(stackRaw.trim().toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException ex) {
                    LOG.warning("items: " + where + " unknown stack " + stackRaw + " — skipped");
                    return null;
                }
            }
        }

        Stack stack = defaultStackForStep(parsedStack);
        return new AbilityStep(effect, params, expiryTrigger, expiryCharges, stack);
    }

    private static boolean hasPotion(EffectContext params) {
        if (params == null) {
            return false;
        }
        if (params.get(EffectKeys.POTION) != null) {
            return true;
        }
        String name = params.get(EffectKeys.POTION_NAME);
        return name != null && !name.isBlank();
    }
}
