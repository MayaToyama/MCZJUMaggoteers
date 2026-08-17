package io.mczju.maggoteers.config;

import io.mczju.maggoteers.wave.MobEquipment;
import org.bukkit.entity.EntityType;

import java.util.List;

public record DeathSpawnCfg(EntityType type, int count, CoeffCfg coeff, List<String> affixes,
                            InfernalCfg infernal,
                            List<MobEquipment> equipment, List<PassengerCfg> passengers,
                            List<DeathSpawnCfg> onDeath,
                            String name, boolean bossBar) {
    public DeathSpawnCfg {
        affixes = affixes == null ? List.of() : List.copyOf(affixes);
        infernal = infernal == null ? InfernalCfg.NONE : infernal;
        equipment = equipment == null ? List.of() : List.copyOf(equipment);
        passengers = passengers == null ? List.of() : List.copyOf(passengers);
        onDeath = onDeath == null ? List.of() : List.copyOf(onDeath);
        if (count < 1) count = 1;
        if (name != null) {
            name = name.trim();
            if (name.isEmpty()) name = null;
        }
    }

    /** 兼容旧 8 参构造。 */
    public DeathSpawnCfg(EntityType type, int count, CoeffCfg coeff, List<String> affixes,
                         InfernalCfg infernal,
                         List<MobEquipment> equipment, List<PassengerCfg> passengers,
                         List<DeathSpawnCfg> onDeath) {
        this(type, count, coeff, affixes, infernal, equipment, passengers, onDeath, null, false);
    }
}
