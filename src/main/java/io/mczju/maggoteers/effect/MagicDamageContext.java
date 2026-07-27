package io.mczju.maggoteers.effect;

import io.mczju.maggoteers.game.MaggoteersGame;
import org.bukkit.Bukkit;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class MagicDamageContext {
    private static final Set<String> ACTIVE = new HashSet<>();

    private MagicDamageContext() {}

    private static String key(MaggoteersGame game, UUID source, int tick) {
        return System.identityHashCode(game) + ":" + source + ":" + tick;
    }

    public static void run(MaggoteersGame game, UUID source, Runnable action) {
        if (game == null || source == null) {
            action.run();
            return;
        }
        int tick = Bukkit.getCurrentTick();
        String k = key(game, source, tick);
        ACTIVE.add(k);
        try {
            action.run();
        } finally {
            ACTIVE.remove(k);
        }
    }

    public static boolean shouldSuppressOnDamageDealt(MaggoteersGame game, UUID source) {
        if (game == null || source == null) return false;
        return ACTIVE.contains(key(game, source, Bukkit.getCurrentTick()));
    }
}