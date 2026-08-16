package io.mczju.maggoteers.config;

import io.mczju.maggoteers.wave.MobEquipment;
import org.bukkit.entity.EntityType;

import java.util.List;

/** waves.yml step.passengers[] 条目（可嵌套 passengers / on_death）。 */
public record PassengerCfg(EntityType type, int count, CoeffCfg coeff, List<String> affixes,
                           InfernalCfg infernal,
                           List<MobEquipment> equipment, List<DeathSpawnCfg> onDeath,
                           List<PassengerCfg> passengers,
                           String name, boolean bossBar) {
    public PassengerCfg {
        affixes = affixes == null ? List.of() : List.copyOf(affixes);
        infernal = infernal == null ? InfernalCfg.NONE : infernal;
        equipment = equipment == null ? List.of() : List.copyOf(equipment);
        onDeath = onDeath == null ? List.of() : List.copyOf(onDeath);
        passengers = passengers == null ? List.of() : List.copyOf(passengers);
        if (count < 1) count = 1;
        if (name != null) {
            name = name.trim();
            if (name.isEmpty()) name = null;
        }
    }

    /** 扁平条目（无嵌套）。 */
    public PassengerCfg(EntityType type, int count, CoeffCfg coeff, List<String> affixes) {
        this(type, count, coeff, affixes, InfernalCfg.NONE,
                List.of(), List.of(), List.of(), null, false);
    }

    /** 兼容旧 8 参构造。 */
    public PassengerCfg(EntityType type, int count, CoeffCfg coeff, List<String> affixes,
                        InfernalCfg infernal,
                        List<MobEquipment> equipment, List<DeathSpawnCfg> onDeath,
                        List<PassengerCfg> passengers) {
        this(type, count, coeff, affixes, infernal, equipment, onDeath, passengers, null, false);
    }
}
