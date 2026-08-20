package io.mczju.maggoteers.mob;

import io.mczju.maggoteers.effect.EffectContext;
import io.mczju.maggoteers.effect.EffectKey;
import io.mczju.maggoteers.effect.EffectKeys;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** 解析 mob_skills.yml 的单条技能（Map→MobSkillSpec）。纯逻辑，可单测。 */
public final class MobSkillSpecParser {

    private MobSkillSpecParser() {}

    public static Optional<MobSkillSpec> parse(Object raw) {
        if (!(raw instanceof Map<?, ?> m)) {
            return Optional.empty();
        }
        String id = m.get("id") == null ? "" : String.valueOf(m.get("id")).trim();
        if (id.isBlank()) {
            return Optional.empty();
        }
        MobTrigger trigger = enumOf(m.get("trigger"), MobTrigger.class);
        if (trigger == null) {
            return Optional.empty();
        }
        if (trigger == MobTrigger.COUNTER && m.get("counter") == null) {
            return Optional.empty();                       // counter 触发必须有 counter 块
        }
        int cooldownSec = m.get("cooldown_sec") instanceof Number n ? n.intValue() : 0;
        if (trigger == MobTrigger.TICK && cooldownSec <= 0) {
            return Optional.empty();                       // 光环类必须有冷却，否则每 tick 触发
        }
        boolean invisible = Boolean.parseBoolean(String.valueOf(m.get("invisible")));
        MobCounterSpec counter = parseCounter(m.get("counter"));
        MobCondition condition = parseCondition(m.get("condition"));
        List<MobEffectSpec> effects = parseEffects(m.get("effects"));
        return Optional.of(new MobSkillSpec(id, trigger, condition, invisible, counter, cooldownSec, effects));
    }

    private static MobCounterSpec parseCounter(Object raw) {
        if (!(raw instanceof Map<?, ?> m)) {
            return null;
        }
        String id = m.get("id") == null ? "" : String.valueOf(m.get("id")).trim();
        if (id.isBlank()) {
            return null;
        }
        MobTrigger count = enumOf(m.get("count"), MobTrigger.class);
        if (count == null) {
            return null;
        }
        int amount = m.get("amount") instanceof Number n ? n.intValue() : 1;
        MobTrigger resetOn = enumOf(m.get("reset"), MobTrigger.class);
        MobCondition condition = parseCondition(m.get("condition"));
        return new MobCounterSpec(id, count, amount, resetOn, condition);
    }

    private static List<MobEffectSpec> parseEffects(Object raw) {
        List<MobEffectSpec> out = new ArrayList<>();
        if (!(raw instanceof List<?> list)) {
            return out;
        }
        for (Object o : list) {
            if (!(o instanceof Map<?, ?> em)) {
                continue;
            }
            MobEffect effect = enumOf(em.get("effect"), MobEffect.class);
            if (effect == null) {
                continue;
            }
            out.add(new MobEffectSpec(effect, parseEffectParams(effect, em)));
        }
        return List.copyOf(out);
    }

    private static EffectContext parseEffectParams(MobEffect effect, Map<?, ?> em) {
        EffectContext ctx = new EffectContext();
        // target 是 effects 条目的顶层键：self / target / area（存 EffectKeys.TARGETS）
        Object targetRaw = em.get("target");
        if (targetRaw != null) {
            ctx.put(EffectKeys.TARGETS, String.valueOf(targetRaw).toLowerCase(Locale.ROOT));
        }
        // 参数字段：优先 params 子块，回落顶层（mob_skills.yml 两种写法皆可）
        Map<?, ?> pm = em.get("params") instanceof Map<?, ?> p ? p : em;
        for (var e : pm.entrySet()) {
            String k = String.valueOf(e.getKey());
            if ("effect".equals(k) || "target".equals(k)) {
                continue;
            }
            Object v = e.getValue();
            switch (k) {
                case "amount", "damage" -> putNum(ctx, k.equals("damage")
                        ? EffectKeys.DAMAGE : EffectKeys.AMOUNT, v);
                case "radius" -> putNum(ctx, EffectKeys.RADIUS, v);
                case "attr" -> putAttr(ctx, v);
                case "op" -> ctx.put(EffectKeys.OP, String.valueOf(v).toUpperCase(Locale.ROOT));
                case "value" -> putNum(ctx, EffectKeys.VALUE, v);
                case "potion" -> ctx.put(EffectKeys.POTION_NAME, String.valueOf(v));
                case "amp" -> putInt(ctx, EffectKeys.AMP, v);
                case "duration_ticks" -> putInt(ctx, EffectKeys.DURATION_TICKS, v);
                case "entity" -> ctx.put(EffectKeys.ENTITY, String.valueOf(v));
                case "count" -> putInt(ctx, EffectKeys.COUNT, v);
                case "homing_target" -> ctx.put(EffectKeys.HOMING_TARGET, String.valueOf(v));
                case "projectile_speed" -> putNum(ctx, EffectKeys.PROJECTILE_SPEED, v);
                default -> { }
            }
        }
        return ctx;
    }

    private static void putNum(EffectContext ctx, EffectKey<Double> key, Object v) {
        if (v instanceof Number n) {
            ctx.put(key, n.doubleValue());
        }
    }

    private static void putInt(EffectContext ctx, EffectKey<Integer> key, Object v) {
        if (v instanceof Number n) {
            ctx.put(key, n.intValue());
        }
    }

    private static void putAttr(EffectContext ctx, Object v) {
        String name = String.valueOf(v);
        ctx.put(EffectKeys.ATTR_NAME, name);
    }

    private static MobCondition parseCondition(Object raw) {
        if (!(raw instanceof Map<?, ?> m)) {
            return null;
        }
        if (m.containsKey("and")) {
            return new MobCondition.And(parseChildren(m.get("and")));
        }
        if (m.containsKey("or")) {
            return new MobCondition.Or(parseChildren(m.get("or")));
        }
        if (m.containsKey("not")) {
            MobCondition child = parseCondition(m.get("not"));
            return child == null ? null : new MobCondition.Not(child);
        }
        if (m.containsKey("hold_item_pdc")) {
            return new MobCondition.HoldItemPdc(String.valueOf(m.get("hold_item_pdc")));
        }
        if (m.containsKey("player_in_radius")) {
            double r = m.get("player_in_radius") instanceof Number n ? n.doubleValue() : 0.0;
            return new MobCondition.PlayerInRadius(r);
        }
        return null;
    }

    private static List<MobCondition> parseChildren(Object raw) {
        List<MobCondition> out = new ArrayList<>();
        if (!(raw instanceof List<?> list)) {
            return out;
        }
        for (Object o : list) {
            MobCondition c = parseCondition(o);
            if (c != null) {
                out.add(c);
            }
        }
        return out;
    }

    private static <T extends Enum<T>> T enumOf(Object raw, Class<T> type) {
        if (raw == null) {
            return null;
        }
        try {
            return Enum.valueOf(type, String.valueOf(raw).trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
