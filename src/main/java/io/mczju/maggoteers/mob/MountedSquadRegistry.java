package io.mczju.maggoteers.mob;

import org.bukkit.entity.LivingEntity;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class MountedSquadRegistry {

    public record Squad(UUID rootUuid, UUID controllerUuid) {}

    private static final Map<UUID, Squad> BY_ROOT = new ConcurrentHashMap<>();

    private MountedSquadRegistry() {}

    public static void register(LivingEntity root, LivingEntity controller) {
        if (root == null || controller == null) return;
        BY_ROOT.put(root.getUniqueId(), new Squad(root.getUniqueId(), controller.getUniqueId()));
    }

    public static Squad removeByRoot(UUID rootUuid) {
        return rootUuid == null ? null : BY_ROOT.remove(rootUuid);
    }

    public static void clearAll() {
        BY_ROOT.clear();
    }

    public static Map<UUID, Squad> snapshot() {
        return Map.copyOf(BY_ROOT);
    }
}
