package io.mczju.maggoteers.config;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

public record InfernalCfg(int level, List<String> affixes) {
    public static final InfernalCfg NONE = new InfernalCfg(1, List.of());

    public InfernalCfg {
        if (level < 1 || level > 100) {
            throw new IllegalArgumentException("infernal.level must be 1..100, got " + level);
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        if (affixes != null) {
            for (String id : affixes) {
                if (id == null || id.isBlank()) continue;
                normalized.add(id.trim().toLowerCase(Locale.ROOT));
            }
        }
        affixes = List.copyOf(normalized);
    }

    public boolean enabled() {
        return !affixes.isEmpty();
    }
}