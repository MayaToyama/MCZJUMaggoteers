package io.mczju.maggoteers.integration;

import io.mczju.maggoteers.config.InfernalCfg;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public final class InfernalMobsBridge {
    static final Set<String> UNSUPPORTED = Set.of(
            "morph", "mama", "mounted", "vexsummoner", "ghost");
    private static final Set<UUID> MANAGED = ConcurrentHashMap.newKeySet();
    private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();
    private static volatile Backend backend;
    private static Logger logger = Logger.getLogger("Maggoteers");

    interface Backend {
        boolean hasSkill(String id) throws ReflectiveOperationException;

        void mechanize(LivingEntity entity, int level, List<String> ids)
                throws ReflectiveOperationException;

        boolean isRegistered(UUID uuid) throws ReflectiveOperationException;

        void unregister(UUID uuid) throws ReflectiveOperationException;
    }

    public static void initialize(JavaPlugin plugin) {
        logger = plugin.getLogger();
        Plugin im = Bukkit.getPluginManager().getPlugin("InfernalMobs");
        if (im == null || !im.isEnabled()) {
            warnOnce("missing", "InfernalMobs not installed or disabled; infernal blocks will be skipped");
            return;
        }
        try {
            Field factoryField = im.getClass().getDeclaredField("mobFactory");
            factoryField.setAccessible(true);
            Object factory = factoryField.get(im);
            Method mechanize = factory.getClass().getMethod(
                    "mechanizeWithAffixes", LivingEntity.class, Location.class, int.class, List.class);

            Object combat = im.getClass().getMethod("getCombatService").invoke(im);
            Method getState = combat.getClass().getMethod("getMobState", UUID.class);
            Method unregister = combat.getClass().getMethod("unregisterMob", UUID.class);

            ClassLoader loader = im.getClass().getClassLoader();
            Class<?> registry = Class.forName(
                    "com.infernalmobs.registry.SkillRegistry", true, loader);
            Method has = registry.getMethod("has", String.class);

            backend = new ReflectiveBackend(factory, mechanize, combat, getState, unregister, has);
        } catch (ReflectiveOperationException | RuntimeException e) {
            disable("InfernalMobs reflection contract incompatible; bridge disabled for session", e);
        }
    }

    public static boolean mechanize(LivingEntity entity, InfernalCfg cfg) {
        Backend current = backend;
        if (entity == null || cfg == null || !cfg.enabled() || current == null) return false;
        List<String> accepted = new ArrayList<>();
        for (String id : cfg.affixes()) {
            if (UNSUPPORTED.contains(id)) {
                warnOnce("unsupported:" + id, "Skipping incompatible IM skill: " + id);
                continue;
            }
            try {
                if (!current.hasSkill(id)) {
                    warnOnce("unknown:" + id, "Skipping unknown IM skill: " + id);
                    continue;
                }
            } catch (ReflectiveOperationException e) {
                disable("IM skill registry unavailable", e);
                return false;
            }
            accepted.add(id);
        }
        if (accepted.isEmpty()) return false;
        try {
            current.mechanize(entity, cfg.level(), List.copyOf(accepted));
            if (!current.isRegistered(entity.getUniqueId())) {
                warnOnce("not-registered", "IM did not register entity; keeping vanilla mob");
                return false;
            }
            MANAGED.add(entity.getUniqueId());
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            Throwable cause = e instanceof ReflectiveOperationException roe && roe.getCause() != null
                    ? roe.getCause() : e;
            warnOnce("invoke:" + cause.getClass().getName(), "IM mechanize failed: " + cause.getMessage());
            return false;
        }
    }

    public static boolean isManaged(UUID uuid) {
        return uuid != null && MANAGED.contains(uuid);
    }

    public static boolean unregisterManaged(UUID uuid) {
        if (uuid == null || !MANAGED.remove(uuid)) return false;
        Backend current = backend;
        if (current != null) {
            try {
                current.unregister(uuid);
            } catch (ReflectiveOperationException | RuntimeException e) {
                Throwable cause = e instanceof ReflectiveOperationException roe && roe.getCause() != null
                        ? roe.getCause() : e;
                warnOnce("unregister:" + cause.getClass().getName(), "IM unregister failed: " + cause.getMessage());
            }
        }
        return true;
    }

    static void installForTesting(Backend value, Logger testLogger) {
        shutdown();
        backend = value;
        logger = testLogger;
    }

    public static void shutdown() {
        Backend current = backend;
        if (current != null) {
            for (UUID uuid : List.copyOf(MANAGED)) unregisterManaged(uuid);
        }
        MANAGED.clear();
        WARNED.clear();
        backend = null;
    }

    private static void warnOnce(String key, String message) {
        if (WARNED.add(key)) logger.warning(message);
    }

    private static void disable(String message, Exception cause) {
        backend = null;
        warnOnce("disabled", message + ": " + cause.getMessage());
    }

    private record ReflectiveBackend(
            Object factory, Method mechanize,
            Object combat, Method getState, Method unregister,
            Method hasSkill) implements Backend {
        @Override
        public boolean hasSkill(String id) throws ReflectiveOperationException {
            return Boolean.TRUE.equals(hasSkill.invoke(null, id));
        }

        @Override
        public void mechanize(LivingEntity entity, int level, List<String> ids)
                throws ReflectiveOperationException {
            mechanize.invoke(factory, entity, entity.getLocation(), level, ids);
        }

        @Override
        public boolean isRegistered(UUID uuid) throws ReflectiveOperationException {
            return getState.invoke(combat, uuid) != null;
        }

        @Override
        public void unregister(UUID uuid) throws ReflectiveOperationException {
            unregister.invoke(combat, uuid);
        }
    }

    private InfernalMobsBridge() {}
}