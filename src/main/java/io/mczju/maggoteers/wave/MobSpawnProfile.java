package io.mczju.maggoteers.wave;

import io.mczju.maggoteers.config.InfernalCfg;

import java.util.List;

public record MobSpawnProfile(double dropMult,
                              List<String> affixes,
                              InfernalCfg infernal,
                              List<DeathSpawn> onDeath,
                              String name,
                              boolean bossBar) {
    public MobSpawnProfile {
        affixes = affixes == null ? List.of() : List.copyOf(affixes);
        infernal = infernal == null ? InfernalCfg.NONE : infernal;
        onDeath = onDeath == null ? List.of() : List.copyOf(onDeath);
        if (name != null) {
            name = name.trim();
            if (name.isEmpty()) name = null;
        }
    }

    public MobSpawnProfile(double dropMult, List<String> affixes, InfernalCfg infernal, List<DeathSpawn> onDeath) {
        this(dropMult, affixes, infernal, onDeath, null, false);
    }

    public static MobSpawnProfile fromStep(SpawnStep step) {
        return new MobSpawnProfile(step.dropMult(), step.affixes(), step.infernal(), step.onDeath(),
                step.name(), step.bossBar());
    }

    public static MobSpawnProfile fromPassenger(PassengerSpawn spawn) {
        return new MobSpawnProfile(spawn.dropMult(), spawn.affixes(), spawn.infernal(), spawn.onDeath(),
                spawn.name(), spawn.bossBar());
    }

    public static MobSpawnProfile fromDeathSpawn(DeathSpawn spawn) {
        return new MobSpawnProfile(spawn.dropMult(), spawn.affixes(), spawn.infernal(), spawn.onDeath(),
                spawn.name(), spawn.bossBar());
    }
}
