package io.mczju.maggoteers.effect;

import java.util.Locale;
import java.util.Optional;

public enum VirtualStats {
    MAGIC_DAMAGE;

    public static Optional<VirtualStats> parse(String name) {
        if (name == null || name.isBlank()) return Optional.empty();
        String u = name.trim().toUpperCase(Locale.ROOT);
        for (VirtualStats vs : values()) {
            if (vs.name().equals(u)) return Optional.of(vs);
        }
        return Optional.empty();
    }

    public static boolean isMagicDamage(String name) {
        return parse(name).filter(v -> v == MAGIC_DAMAGE).isPresent();
    }
}
