package io.mczju.maggoteers.reward;

import io.mczju.maggoteers.effect.Effect;
import io.mczju.maggoteers.effect.EffectContext;
import io.mczju.maggoteers.effect.EffectKey;
import io.mczju.maggoteers.effect.EffectKeys;
import io.mczju.maggoteers.effect.Stack;
import io.mczju.maggoteers.effect.Trigger;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
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
        Material icon, boolean requiresUnlock, int unlockCost, boolean unique
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
        Material icon = optEnum(m, "icon", Material.class);
        boolean reqUnlock = bool(m, "requires_unlock");
        int unlockCost = num(m, "unlock_cost", 0);
        boolean unique = bool(m, "unique");
        return new RewardOption(
                str(m, "id"), str(m, "display"), str(m, "description"), cat,
                str(m, "item"), num(m, "amount", 1),
                trigger, effect, params, stack, icon, reqUnlock, unlockCost, unique);
    }

    /** STAT 选项是否完整到能建成 PlayerEffect（effect + 必要 params）。 */
    public boolean isStatEffect() {
        return category == Category.STAT && effect != null;
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
                case "attr" -> { Attribute a = safeAttribute(v); if (a != null) ctx.put(EffectKeys.ATTR, a); }
                case "op" -> ctx.put(EffectKeys.OP, String.valueOf(v).toUpperCase());
                case "value", "amount", "radius", "damage" -> {
                    EffectKey<Double> key = switch (k) {
                        case "amount" -> EffectKeys.AMOUNT;
                        case "radius" -> EffectKeys.RADIUS;
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
                case "potion" -> {
                    PotionEffectType p = PotionEffectType.getByName(String.valueOf(v).toUpperCase());
                    if (p == null) {
                        io.mczju.maggoteers.MaggoteersPlugin.getInstance()
                                .getLogger().warning("未知药水类型: " + v);
                    } else {
                        ctx.put(EffectKeys.POTION, p);
                    }
                }
                default -> { /* 忽略未知键 */ }
            }
        }
        return ctx;
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
    private static Attribute safeAttribute(Object v) {
        try { return Attribute.valueOf(String.valueOf(v).toUpperCase()); }
        catch (Exception e) { return null; }
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
