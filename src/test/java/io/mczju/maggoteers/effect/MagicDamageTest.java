package io.mczju.maggoteers.effect;

import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class MagicDamageTest {

    @Test
    void independentMagicDamageClearsVanillaInvulnerabilityWindow() {
        AtomicInteger noDamageTicks = new AtomicInteger(7);
        AtomicReference<Double> lastDamage = new AtomicReference<>(80.0);
        AtomicReference<Double> appliedDamage = new AtomicReference<>();
        AtomicReference<Entity> appliedSource = new AtomicReference<>();

        LivingEntity target = proxy(LivingEntity.class, (proxy, method, args) -> switch (method.getName()) {
            case "getNoDamageTicks" -> noDamageTicks.get();
            case "setNoDamageTicks" -> {
                noDamageTicks.set((Integer) args[0]);
                yield null;
            }
            case "getLastDamage" -> lastDamage.get();
            case "setLastDamage" -> {
                lastDamage.set((Double) args[0]);
                yield null;
            }
            case "damage" -> {
                appliedDamage.set((Double) args[0]);
                if (args.length > 1) appliedSource.set((Entity) args[1]);
                yield null;
            }
            default -> objectDefault(proxy, method, args);
        });
        Entity source = proxy(Entity.class, MagicDamageTest::objectDefault);

        MagicDamage.applyIndependent(target, 50.0, source);

        assertEquals(0, noDamageTicks.get());
        assertEquals(0.0, lastDamage.get(), 0.001);
        assertEquals(50.0, appliedDamage.get(), 0.001);
        assertSame(source, appliedSource.get());
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, java.lang.reflect.InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler);
    }

    private static Object objectDefault(Object proxy, Method method, Object[] args) {
        return switch (method.getName()) {
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy == args[0];
            case "toString" -> "test-" + proxy.getClass().getInterfaces()[0].getSimpleName();
            default -> primitiveDefault(method.getReturnType());
        };
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