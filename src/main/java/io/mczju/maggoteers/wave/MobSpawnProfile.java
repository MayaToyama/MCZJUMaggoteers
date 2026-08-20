package io.mczju.maggoteers.wave;

import java.util.List;

/** 运行时怪物档案：技能 id 列表 + 死亡召唤列表 + 是否挂 Boss 血条。 */
public record MobSpawnProfile(List<String> skills, List<DeathSpawn> onDeath, boolean bossBar) {
    public MobSpawnProfile {
        skills = skills == null ? List.of() : List.copyOf(skills);
        onDeath = onDeath == null ? List.of() : List.copyOf(onDeath);
    }

    public MobSpawnProfile(List<DeathSpawn> onDeath) {
        this(List.of(), onDeath, false);
    }

    public static MobSpawnProfile fromStep(SpawnStep step) {
        return new MobSpawnProfile(step.skills(), step.onDeath(), step.bossBar());
    }

    public static MobSpawnProfile fromPassenger(PassengerSpawn spawn) {
        return new MobSpawnProfile(spawn.skills(), spawn.onDeath(), spawn.bossBar());
    }

    public static MobSpawnProfile fromDeathSpawn(DeathSpawn spawn) {
        return new MobSpawnProfile(spawn.skills(), spawn.onDeath(), spawn.bossBar());
    }
}
