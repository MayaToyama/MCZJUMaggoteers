package io.mczju.maggoteers.config;

import io.mczju.maggoteers.wave.MobEquipment;
import org.bukkit.entity.EntityType;

import java.util.List;

/** waves.yml 一条 step（未解析）。affixes 为词缀 id 列表（Plan 3 起 RunPlanner 解析）。 */
public record StepCfg(String point, EntityType type, int count, CoeffCfg coeff,
                      int delaySec, List<String> affixes, InfernalCfg infernal,
                      List<MobEquipment> equipment, List<DeathSpawnCfg> onDeath,
                      List<PassengerCfg> passengers) {
    public StepCfg(String point, EntityType type, int count, CoeffCfg coeff,
                   int delaySec, List<String> affixes) {
        this(point, type, count, coeff, delaySec, affixes, InfernalCfg.NONE,
                List.of(), List.of(), List.of());
    }

    public StepCfg {
        affixes = affixes == null ? List.of() : List.copyOf(affixes);
        infernal = infernal == null ? InfernalCfg.NONE : infernal;
        equipment = equipment == null ? List.of() : List.copyOf(equipment);
        onDeath = onDeath == null ? List.of() : List.copyOf(onDeath);
        passengers = passengers == null ? List.of() : List.copyOf(passengers);
    }
}
