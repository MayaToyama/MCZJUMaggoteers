package io.mczju.maggoteers.reward;

import io.mczju.maggoteers.effect.Effect;
import io.mczju.maggoteers.effect.EffectContext;
import io.mczju.maggoteers.effect.EffectKey;
import io.mczju.maggoteers.effect.EffectKeys;
import io.mczju.maggoteers.effect.Stack;
import io.mczju.maggoteers.effect.Trigger;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.attribute.Attribute;
import org.bukkit.potion.PotionEffectType;

import java.util.Map;

/**
 * 奖励项（§12.1）。
 * <ul>
 *   <li>STAT：{@code effect/params/stack/trigger} 描述一条 {@link io.mczju.maggoteers.effect.PlayerEffect}（trigger 缺省=常驻）。</li>
 *   <li>WEAPON/SUPPLY：{@code item/amount} 发物品。</li>
 * </ul>
 */
public record RewardOption(
        String id, String display, String description, Category category,
        String item, int amount,
        Trigger trigger, Effect effect, EffectContext params, Stack stack,
        Trigger expiryTrigger, int expiryCharges,
        boolean requiresUnlock, int unlockCost, boolean unique
) {
    public enum Category { STAT, WEAPON, SUPPLY }

    public RewardOption {
        if (amount < 1) amount = 1;
        if (stack == null) stack = Stack.ADD;
    }

    public static RewardOption fromMap(Map<?, ?> m) {
        Category cat = Category.valueOf(str(m, "category", "SUPPLY").toUpperCase());
        Trigger trigger = optEnum(m, "trigger", Trigger.class);
        Effect effect = optEnum(m, "effect", Effect.class);
        EffectContext params = cat == Category.STAT ? parseParams(m) : null;
        Stack stack = optEnum(m, "stack", Stack.class);
        boolean reqUnlock = bool(m, "requires_unlock");
        int unlockCost = num(m, "unlock_cost", 0);
        boolean unique = bool(m, "unique");
        Trigger expiryTrigger = null;
        int expiryCharges = 0;
        Object expRaw = m.get("expiry");
        if (expRaw instanceof Map<?, ?> em) {
            expiryTrigger = optEnum(em, "trigger", Trigger.class);
            expiryCharges = num(em, "charges", 0);
        }
        return new RewardOption(
                str(m, "id"), str(m, "display"), str(m, "description"), cat,
                str(m, "item"), num(m, "amount", 1),
                trigger, effect, params, stack,
                expiryTrigger, expiryCharges,
                reqUnlock, unlockCost, unique);
    }

    /** STAT 选项是否完整到能建成 PlayerEffect（effect + 必要 params）。 */
    public boolean isStatEffect() {
        if (category != Category.STAT || effect == null) return false;
        if (effect == Effect.AURA) {
            return params != null && params.get(EffectKeys.GRANT_EFFECT) != null
                    && params.getOrDefault(EffectKeys.RADIUS, 0.0) > 0;
        }
        return true;
    }

    @SuppressWarnings("unchecked")
    private static EffectContext parseParams(Map<?, ?> m) {
        Object raw = m.get("params");
        if (!(raw instanceof Map<?, ?> pm)) return new EffectContext();
        EffectContext ctx = new EffectContext();
        for (var e : pm.entrySet()) {
            String k = String.valueOf(e.getKey());
            Object v = e.getValue();
            switch (k) {
                case "radius" -> ctx.put(EffectKeys.RADIUS, v instanceof Number n ? n.doubleValue() : 8.0);
                case "targets" -> ctx.put(EffectKeys.TARGETS, String.valueOf(v).toLowerCase());
                case "enemy_scope" -> ctx.put(EffectKeys.ENEMY_SCOPE, String.valueOf(v).toLowerCase());
                case "include_self" -> ctx.put(EffectKeys.INCLUDE_SELF, Boolean.parseBoolean(String.valueOf(v)));
                case "ray_length" -> ctx.put(EffectKeys.RAY_LENGTH, v instanceof Number n ? n.doubleValue() : 32.0);
                case "beam_radius" -> ctx.put(EffectKeys.BEAM_RADIUS, v instanceof Number n ? n.doubleValue() : 1.25);
                case "grant_pulse_sec" -> ctx.put(EffectKeys.GRANT_PULSE_SEC,
                        v instanceof Number n ? n.intValue() : 1);
                case "grant" -> parseGrantBlock(ctx, v);
                case "carrier_fx" -> ctx.put(EffectKeys.CARRIER_FX, parseFxContext(v));
                case "mark_fx" -> ctx.put(EffectKeys.MARK_FX, parseFxContext(v));
                case "mark_head" -> ctx.put(EffectKeys.MARK_HEAD, Boolean.parseBoolean(String.valueOf(v)));
                case "attr" -> putAttr(ctx, v);
                case "op" -> ctx.put(EffectKeys.OP, String.valueOf(v).toUpperCase());
                case "value", "amount", "damage" -> {
                    EffectKey<Double> key = switch (k) {
                        case "amount" -> EffectKeys.AMOUNT;
                        case "damage" -> EffectKeys.DAMAGE;
                        default -> EffectKeys.VALUE;
                    };
                    ctx.put(key, v instanceof Number n ? n.doubleValue() : 0.0);
                }
                case "amp", "count", "duration_ticks" -> {
                    int iv = v instanceof Number n ? n.intValue() : 0;
                    if (k.equals("amp")) ctx.put(EffectKeys.AMP, iv);
                    else if (k.equals("count")) ctx.put(EffectKeys.COUNT, iv);
                    else ctx.put(EffectKeys.DURATION_TICKS, iv);
                }
                case "potion" -> putPotion(ctx, v);
                default -> { /* 忽略未知键 */ }
            }
        }
        return ctx;
    }

    @SuppressWarnings("unchecked")
    private static void parseGrantBlock(EffectContext auraCtx, Object raw) {
        if (!(raw instanceof Map<?, ?> gm)) return;
        Effect grantEffect = safeEnum(Effect.class, gm.get("effect"));
        if (grantEffect == null) return;
        auraCtx.put(EffectKeys.GRANT_EFFECT, grantEffect);
        EffectContext grant = new EffectContext();
        for (var e : gm.entrySet()) {
            String k = String.valueOf(e.getKey());
            if ("effect".equals(k)) continue;
            Object v = e.getValue();
            switch (k) {
                case "attr" -> putAttr(grant, v);
                case "op" -> grant.put(EffectKeys.OP, String.valueOf(v).toUpperCase());
                case "value" -> grant.put(EffectKeys.VALUE, v instanceof Number n ? n.doubleValue() : 0.0);
                case "amount" -> grant.put(EffectKeys.AMOUNT, v instanceof Number n ? n.doubleValue() : 0.0);
                case "amp" -> grant.put(EffectKeys.AMP, v instanceof Number n ? n.intValue() : 0);
                case "potion" -> putPotion(grant, v);
                default -> { }
            }
        }
        auraCtx.put(EffectKeys.GRANT_PARAMS, grant);
    }

    @SuppressWarnings("unchecked")
    private static EffectContext parseFxContext(Object raw) {
        EffectContext fx = new EffectContext();
        if (!(raw instanceof Map<?, ?> m)) return fx;
        for (var e : m.entrySet()) {
            io.mczju.maggoteers.item.fx.MagicFxBuilder.putFxEntry(
                    fx, String.valueOf(e.getKey()), e.getValue());
        }
        return fx;
    }

    private static <T extends Enum<T>> T optEnum(Map<?, ?> m, String key, Class<T> type) {
        Object v = m.get(key);
        if (v == null) return null;
        return safeEnum(type, v);
    }
    private static <T extends Enum<T>> T safeEnum(Class<T> type, Object v) {
        try { return Enum.valueOf(type, String.valueOf(v).toUpperCase()); }
        catch (Exception e) { return null; }
    }
    private static void putPotion(EffectContext ctx, Object v) {
        String name = String.valueOf(v);
        ctx.put(EffectKeys.POTION_NAME, name);
        PotionEffectType p = io.mczju.maggoteers.util.GameRegistries.potionEffect(name);
        if (p != null) ctx.put(EffectKeys.POTION, p);
    }

    private static void putAttr(EffectContext ctx, Object v) {
        String name = String.valueOf(v);
        ctx.put(EffectKeys.ATTR_NAME, name);
        if (io.mczju.maggoteers.util.GameRegistries.isVirtualAttribute(name)) return;
        Attribute a = safeAttribute(v);
        if (a != null) ctx.put(EffectKeys.ATTR, a);
    }

    private static Attribute safeAttribute(Object v) {
        return io.mczju.maggoteers.util.GameRegistries.attribute(String.valueOf(v));
    }
    private static boolean bool(Map<?, ?> m, String key) {
        Object v = m.get(key); return v != null && Boolean.parseBoolean(String.valueOf(v));
    }
    private static int num(Map<?, ?> m, String key, int def) {
        Object v = m.get(key); return v instanceof Number n ? n.intValue() : def;
    }
    private static String str(Map<?, ?> m, String key) { return str(m, key, ""); }
    private static String str(Map<?, ?> m, String key, String def) {
        Object v = m.get(key); return v == null ? def : String.valueOf(v);
    }

    public String displayPlain() {
        return PlainTextComponentSerializer.plainText().serialize(
                net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(display));
    }
}
