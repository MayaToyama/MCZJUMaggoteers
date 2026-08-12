package io.mczju.maggoteers.effect;

import io.mczju.maggoteers.item.fx.MagicFxBuilder;
import io.mczju.maggoteers.util.GameRegistries;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class EffectParamsParser {

    private EffectParamsParser() {}

    public static EffectContext parse(ConfigurationSection sec) {
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
                case "potions" -> ctx.put(EffectKeys.POTIONS, BuffPotionParser.parseList(sec.getList(k)));
                case "clear_potions" -> {
                    ClearPotionsParser.ParseResult pr = ClearPotionsParser.parse(sec.getList(k), "params.clear_potions");
                    if (!pr.ok()) {
                        // leave empty; callers/validators treat missing clear as absent; errors logged by registry
                        for (String err : pr.errors()) {
                            java.util.logging.Logger.getLogger("Maggoteers").warning(err);
                        }
                    } else {
                        ctx.put(EffectKeys.CLEAR_POTIONS, pr.types());
                    }
                }
                case "immunity" -> ctx.put(EffectKeys.IMMUNITY, sec.getBoolean(k));
                case "mark_fx" -> ctx.put(EffectKeys.MARK_FX, parseFxContext(sec.getConfigurationSection(k)));
                case "mark_duration_sec" -> ctx.put(EffectKeys.MARK_DURATION_SEC, sec.getInt(k));
                case "fx" -> ctx.put(EffectKeys.FX, parseFxContext(sec.getConfigurationSection(k)));
                case "carrier_fx" -> ctx.put(EffectKeys.CARRIER_FX, parseFxContext(sec.getConfigurationSection(k)));
                case "mark_on" -> ctx.put(EffectKeys.MARK_ON, String.valueOf(v).toLowerCase(Locale.ROOT));
                case "item" -> ctx.put(EffectKeys.ITEM_ID, String.valueOf(v));
                case "entity" -> ctx.put(EffectKeys.ENTITY, String.valueOf(v));
                case "anchor" -> ctx.put(EffectKeys.ANCHOR, String.valueOf(v).toLowerCase(Locale.ROOT));
                case "cleanup" -> ctx.put(EffectKeys.CLEANUP, String.valueOf(v).toLowerCase(Locale.ROOT));
                case "duration_sec" -> ctx.put(EffectKeys.DURATION_SEC, sec.getInt(k));
                case "offset_y" -> ctx.put(EffectKeys.OFFSET_Y, sec.getDouble(k));
                case "count" -> ctx.put(EffectKeys.COUNT, sec.getInt(k));
                case "tamed" -> ctx.put(EffectKeys.TAMED, sec.getBoolean(k));
                case "friendly_fire" -> ctx.put(EffectKeys.FRIENDLY_FIRE, sec.getBoolean(k));
                case "attributes" -> ctx.put(EffectKeys.ATTRIBUTES, parseAttributesMap(sec.getConfigurationSection(k)));
                case "projectile" -> ctx.put(EffectKeys.PROJECTILE, parseProjectileContext(sec.getConfigurationSection(k)));
                case "grant_pulse_sec" -> ctx.put(EffectKeys.GRANT_PULSE_SEC, sec.getInt(k));
                case "grant" -> {
                    ConfigurationSection gs = sec.getConfigurationSection(k);
                    if (gs != null) {
                        parseGrantBlock(ctx, gs);
                    } else if (v instanceof Map<?, ?> gm) {
                        parseGrantBlock(ctx, gm);
                    }
                }
                case "attr" -> putAttr(ctx, v);
                case "op" -> ctx.put(EffectKeys.OP, String.valueOf(v).toUpperCase(Locale.ROOT));
                case "value" -> ctx.put(EffectKeys.VALUE, sec.getDouble(k));
                case "potion" -> putPotion(ctx, v);
                case "amp" -> ctx.put(EffectKeys.AMP, sec.getInt(k));
                case "duration_ticks" -> ctx.put(EffectKeys.DURATION_TICKS, sec.getInt(k));
                case "slot" -> ctx.put(EffectKeys.SLOT, String.valueOf(v).toUpperCase(Locale.ROOT));
                case "items_by_level" -> ctx.put(EffectKeys.ITEMS_BY_LEVEL,
                        parseItemsByLevel(sec.getConfigurationSection(k)));
                default -> { }
            }
        }
        return ctx;
    }

    private static void parseGrantBlock(EffectContext auraCtx, ConfigurationSection gm) {
        if (gm == null) return;
        parseGrantBlock(auraCtx, gm.getValues(false));
    }

    private static void parseGrantBlock(EffectContext auraCtx, Map<?, ?> gm) {
        if (gm == null || gm.isEmpty()) return;
        Object effectRaw = gm.get("effect");
        if (effectRaw == null || String.valueOf(effectRaw).isBlank()) return;
        Effect grantEffect;
        try {
            grantEffect = Effect.valueOf(String.valueOf(effectRaw).trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return;
        }
        auraCtx.put(EffectKeys.GRANT_EFFECT, grantEffect);
        EffectContext grant = new EffectContext();
        for (var e : gm.entrySet()) {
            String k = String.valueOf(e.getKey());
            if ("effect".equals(k)) continue;
            Object v = e.getValue();
            switch (k) {
                case "attr" -> putAttr(grant, v);
                case "op" -> grant.put(EffectKeys.OP, String.valueOf(v).toUpperCase(Locale.ROOT));
                case "value" -> grant.put(EffectKeys.VALUE, v instanceof Number n ? n.doubleValue() : 0.0);
                case "amount" -> grant.put(EffectKeys.AMOUNT, v instanceof Number n ? n.doubleValue() : 0.0);
                case "amp" -> grant.put(EffectKeys.AMP, v instanceof Number n ? n.intValue() : 0);
                case "potion" -> putPotion(grant, v);
                case "potions" -> grant.put(EffectKeys.POTIONS, BuffPotionParser.parseList(v, false));
                default -> { }
            }
        }
        auraCtx.put(EffectKeys.GRANT_PARAMS, grant);
    }

    private static void putAttr(EffectContext ctx, Object v) {
        String name = String.valueOf(v);
        ctx.put(EffectKeys.ATTR_NAME, name);
        if (GameRegistries.isVirtualAttribute(name)) {
            return;
        }
        var a = GameRegistries.attribute(name);
        if (a != null) {
            ctx.put(EffectKeys.ATTR, a);
        }
    }

    private static void putPotion(EffectContext ctx, Object v) {
        String name = String.valueOf(v);
        ctx.put(EffectKeys.POTION_NAME, name);
        PotionEffectType type = GameRegistries.potionEffect(name);
        if (type != null) {
            ctx.put(EffectKeys.POTION, type);
        }
    }

    private static EffectContext parseFxContext(ConfigurationSection sec) {
        EffectContext fx = new EffectContext();
        if (sec == null) return fx;
        for (String k : sec.getKeys(false)) {
            MagicFxBuilder.putFxEntry(fx, k, sec.get(k));
        }
        return fx;
    }

    private static Map<String, Double> parseAttributesMap(ConfigurationSection sec) {
        if (sec == null) return Map.of();
        Map<String, Double> out = new HashMap<>();
        for (String k : sec.getKeys(false)) {
            out.put(k, sec.getDouble(k));
        }
        return Map.copyOf(out);
    }

    private static EffectContext parseProjectileContext(ConfigurationSection sec) {
        EffectContext proj = new EffectContext();
        if (sec == null) return proj;
        if (sec.contains("speed")) proj.put(EffectKeys.PROJECTILE_SPEED, sec.getDouble("speed"));
        if (sec.contains("toward")) proj.put(EffectKeys.PROJECTILE_TOWARD, sec.getString("toward", "look"));
        return proj;
    }

    private static Map<Integer, String> parseItemsByLevel(ConfigurationSection sec) {
        Map<Integer, String> out = new HashMap<>();
        if (sec == null) return Map.of();
        for (String k : sec.getKeys(false)) {
            try {
                out.put(Integer.parseInt(k), sec.getString(k));
            } catch (NumberFormatException ignored) { }
        }
        return Map.copyOf(out);
    }
}