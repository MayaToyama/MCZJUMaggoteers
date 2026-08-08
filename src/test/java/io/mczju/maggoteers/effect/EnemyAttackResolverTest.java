package io.mczju.maggoteers.effect;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnemyAttackResolverTest {

    @Test
    void mobIsEnemyParticipant() {
        LivingEntity mob = livingProxy(UUID.randomUUID());
        assertTrue(EnemyAttackResolver.isEnemyParticipant(null, mob));
    }

    @Test
    void offlineAllyPlayerWithoutGameStateIsNotTreatedAsAlly() {
        Player outsider = playerProxy(UUID.randomUUID());
        assertTrue(EnemyAttackResolver.isEnemyParticipant(null, outsider));
    }

    @Test
    void nullEntityIsNotEnemy() {
        assertFalse(EnemyAttackResolver.isEnemyParticipant(null, null));
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
