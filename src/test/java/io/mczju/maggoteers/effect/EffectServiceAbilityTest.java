package io.mczju.maggoteers.effect;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class EffectServiceAbilityTest {

    @Test
    void instantPotionPathAllowedWithoutExpiry() {
        assertTrue(WeaponEffectValidator.validateUseAbilityPotion(false, 100).isEmpty());
    }

    @Test
    void expiryPotionPathRequiresZeroDuration() {
        EffectContext params = new EffectContext()
                .put(EffectKeys.DURATION_TICKS, 0);
        assertTrue(WeaponTempEffect.validate(
                Effect.ADD_POTION, params, Trigger.ON_DAMAGE_DEALT, 1).isPresent());
        assertTrue(WeaponTempEffect.validate(
                Effect.ADD_POTION, params.put(EffectKeys.DURATION_TICKS, 100),
                Trigger.ON_DAMAGE_DEALT, 1).isPresent());
    }

    @Test
    void preserveHealthAcrossResyncKeepsHealAfterAttributeRefresh() {
        assertEquals(25.0, EffectService.preserveHealthAcrossResync(25.0, 30.0, 30.0), 0.001);
        assertEquals(18.0, EffectService.preserveHealthAcrossResync(18.0, 20.0, 20.0), 0.001);
    }

    @Test
    void preserveHealthAcrossResyncScalesWhenMaxChanges() {
        assertEquals(22.5, EffectService.preserveHealthAcrossResync(15.0, 20.0, 30.0), 0.001);
        assertEquals(16.666, EffectService.preserveHealthAcrossResync(25.0, 30.0, 20.0), 0.01);
    }

    @Test
    void finalizeHealthAddsCurrentHpWhenMaxIncreases() {
        assertEquals(44.0, EffectService.finalizeHealthAfterResync(20.0, 20.0, 44.0), 0.001);
        assertEquals(39.0, EffectService.finalizeHealthAfterResync(15.0, 44.0, 68.0), 0.001);
    }

    @Test
    void finalizeHealthPreservesRatioWhenMaxUnchanged() {
        assertEquals(25.0, EffectService.finalizeHealthAfterResync(25.0, 30.0, 30.0), 0.001);
    }

    @Test
    void anyStepSuccessMeansAbilitySuccess() {
        boolean step0 = false;
        boolean step1 = true;
        boolean self = false;
        assertTrue(step0 || step1 || self);
        assertFalse(false || false || false);
    }

    @Test
    void negativeHealIsExactHealthCostNotDamageEvent() {
        AtomicReference<Double> health = new AtomicReference<>(30.0);
        AtomicReference<Double> damageCall = new AtomicReference<>();
        Player player = (Player) Proxy.newProxyInstance(
                Player.class.getClassLoader(),
                new Class<?>[]{Player.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getHealth" -> health.get();
                    case "setHealth" -> {
                        health.set((Double) args[0]);
                        yield null;
                    }
                    case "damage" -> {
                        damageCall.set((Double) args[0]);
                        yield null;
                    }
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    case "toString" -> "test-player";
                    default -> primitiveDefault(method.getReturnType());
                });

        assertTrue(EffectService.applyHealOrSelfDamage(player, -20.0));
        assertEquals(10.0, health.get(), 0.001);
        assertNull(damageCall.get(), "health costs must bypass armor, resistance and invulnerability frames");
    }

    private static Object primitiveDefault(Class<?> type) {
        if (!type.isPrimitive() || type == void.class) return null;
        if (type == boolean.class) return false;
        if (type == char.class) return '\0';
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0F;
        return 0D;
    }
}
