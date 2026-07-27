package io.mczju.maggoteers.integration;

import io.mczju.maggoteers.config.InfernalCfg;
import org.bukkit.entity.LivingEntity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class InfernalMobsBridgeTest {
    @AfterEach
    void reset() {
        InfernalMobsBridge.shutdown();
    }

    @Test
    void forwardsOnlyKnownAndSupportedIdsExactly() {
        UUID uuid = UUID.randomUUID();
        FakeBackend backend = new FakeBackend(Set.of("poisonous", "sprint", "ghost"));
        InfernalMobsBridge.installForTesting(backend, Logger.getAnonymousLogger());

        assertTrue(InfernalMobsBridge.mechanize(entity(uuid),
                new InfernalCfg(3, List.of("poisonous", "ghost", "missing", "sprint"))));

        assertEquals(3, backend.level);
        assertEquals(List.of("poisonous", "sprint"), backend.ids);
        assertTrue(InfernalMobsBridge.isManaged(uuid));
    }

    @Test
    void emptyFilteredListKeepsEntityOrdinary() {
        FakeBackend backend = new FakeBackend(Set.of("ghost"));
        InfernalMobsBridge.installForTesting(backend, Logger.getAnonymousLogger());
        assertFalse(InfernalMobsBridge.mechanize(entity(UUID.randomUUID()),
                new InfernalCfg(1, List.of("ghost", "missing"))));
        assertEquals(0, backend.calls);
    }

    @Test
    void unregisterIsIdempotent() {
        UUID uuid = UUID.randomUUID();
        FakeBackend backend = new FakeBackend(Set.of("sprint"));
        InfernalMobsBridge.installForTesting(backend, Logger.getAnonymousLogger());
        InfernalMobsBridge.mechanize(entity(uuid), new InfernalCfg(1, List.of("sprint")));
        assertTrue(InfernalMobsBridge.unregisterManaged(uuid));
        assertFalse(InfernalMobsBridge.unregisterManaged(uuid));
        assertEquals(List.of(uuid), backend.unregistered);
    }

    @Test
    void failedMechanizeIsNeverManagedOrUnregistered() {
        FakeBackend backend = new FakeBackend(Set.of("sprint"));
        backend.registerOnMechanize = false;
        InfernalMobsBridge.installForTesting(backend, Logger.getAnonymousLogger());
        UUID uuid = UUID.randomUUID();
        assertFalse(InfernalMobsBridge.mechanize(
                entity(uuid), new InfernalCfg(1, List.of("sprint"))));
        assertFalse(InfernalMobsBridge.isManaged(uuid));
        assertFalse(InfernalMobsBridge.unregisterManaged(uuid));
    }

    private static LivingEntity entity(UUID uuid) {
        return (LivingEntity) Proxy.newProxyInstance(
                LivingEntity.class.getClassLoader(), new Class<?>[]{LivingEntity.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getUniqueId" -> uuid;
                    case "getLocation" -> null;
                    case "isValid" -> true;
                    case "isDead" -> false;
                    default -> method.getReturnType().isPrimitive() ? primitiveDefault(method.getReturnType()) : null;
                });
    }

    private static Object primitiveDefault(Class<?> type) {
        if (type == boolean.class) return false;
        if (type == char.class) return '\0';
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0F;
        return 0D;
    }

    private static final class FakeBackend implements InfernalMobsBridge.Backend {
        final Set<String> known;
        final List<UUID> unregistered = new ArrayList<>();
        int calls;
        int level;
        List<String> ids = List.of();
        UUID registered;
        boolean registerOnMechanize = true;

        FakeBackend(Set<String> known) {
            this.known = known;
        }

        @Override
        public boolean hasSkill(String id) {
            return known.contains(id);
        }

        @Override
        public void mechanize(LivingEntity entity, int value, List<String> skillIds) {
            calls++;
            level = value;
            ids = List.copyOf(skillIds);
            if (registerOnMechanize) registered = entity.getUniqueId();
        }

        @Override
        public boolean isRegistered(UUID uuid) {
            return registerOnMechanize && uuid.equals(registered);
        }

        @Override
        public void unregister(UUID uuid) {
            unregistered.add(uuid);
        }
    }
}