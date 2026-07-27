package io.mczju.maggoteers.config;

import io.mczju.maggoteers.wave.MobEquipment;
import org.bukkit.entity.EntityType;

import java.util.List;

public record DeathSpawnCfg(EntityType type, int count, CoeffCfg coeff, List<String> affixes,
                            InfernalCfg infernal,
                            List<MobEquipment> equipment, List<DeathSpawnCfg> onDeath) {
    public DeathSpawnCfg {
        affixes = affixes == null ? List.of() : List.copyOf(affixes);
        infernal = infernal == null ? InfernalCfg.NONE : infernal;
        equipment = equipment == null ? List.of() : List.copyOf(equipment);
        onDeath = onDeath == null ? List.of() : List.copyOf(onDeath);
        if (count < 1) count = 1;
    }
}
