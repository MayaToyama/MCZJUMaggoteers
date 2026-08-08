package io.mczju.maggoteers.effect;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuffAreaTargetsTest {

    @Test
    void selfReturnsCasterOnly() {
        Player caster = playerProxy(UUID.randomUUID());
        List<LivingEntity> out = BuffAreaTargets.resolveTargetKey(
                BuffAreaTargets.TARGET_SELF, null, caster, new EffectContext(), TriggerContext.empty());
        assertEquals(1, out.size());
        assertSame(caster, out.getFirst());
    }

    @Test
    void hitTargetUsesContextOnly() {
        LivingEntity victim = livingProxy(UUID.randomUUID());
        TriggerContext ctx = new TriggerContext(Trigger.ON_DAMAGE_DEALT, victim, null);
        EffectContext params = new EffectContext();
        params.put(EffectKeys.TARGETS, BuffAreaTargets.TARGET_HIT_TARGET);
        List<LivingEntity> out = BuffAreaTargets.resolveTargetKey(
                BuffAreaTargets.TARGET_HIT_TARGET, null, null, params, ctx);
        assertEquals(1, out.size());
        assertSame(victim, out.getFirst());
    }

    @Test
    void hitTargetNullReturnsEmpty() {
        EffectContext params = new EffectContext();
        params.put(EffectKeys.TARGETS, BuffAreaTargets.TARGET_HIT_TARGET);
        List<LivingEntity> out = BuffAreaTargets.resolveTargetKey(
                BuffAreaTargets.TARGET_HIT_TARGET, null, null, params, TriggerContext.empty());
        assertTrue(out.isEmpty());
    }

    @Test
    void unknownTargetKeyReturnsEmpty() {
        assertTrue(BuffAreaTargets.resolveTargetKey(
                "nobody", null, null, new EffectContext(), TriggerContext.empty()).isEmpty());
    }

    private static LivingEntity livingProxy(UUID uuid) {
        return (LivingEntity) Proxy.newProxyInstance(
                LivingEntity.class.getClassLoader(), new Class<?>[]{LivingEntity.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getUniqueId" -> uuid;
                    default -> defaultValue(method.getReturnType());
                });
    }

    private static Player playerProxy(UUID uuid) {
        return (Player) Proxy.newProxyInstance(
                Player.class.getClassLoader(), new Class<?>[]{Player.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getUniqueId" -> uuid;
                    default -> defaultValue(method.getReturnType());
                });
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == char.class) return '\0';
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0f;
        return 0d;
    }
}
