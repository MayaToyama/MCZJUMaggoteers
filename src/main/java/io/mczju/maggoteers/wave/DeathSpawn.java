package io.mczju.maggoteers.wave;

import io.mczju.maggoteers.config.InfernalCfg;
import org.bukkit.entity.EntityType;

import java.util.List;

public record DeathSpawn(EntityType type, int count,
                         double hpMult, double dmgMult, double speedMult, double dropMult,
                         double scaleMult, double followRangeMult,
                         List<String> affixes, List<PotionSpec> potions,
                         InfernalCfg infernal,
                         List<MobEquipment> equipment, List<DeathSpawn> onDeath) {
    public DeathSpawn {
        affixes = affixes == null ? List.of() : List.copyOf(affixes);
        potions = potions == null ? List.of() : List.copyOf(potions);
        infernal = infernal == null ? InfernalCfg.NONE : infernal;
        equipment = equipment == null ? List.of() : List.copyOf(equipment);
        onDeath = onDeath == null ? List.of() : List.copyOf(onDeath);
        if (count < 1) count = 1;
    }
}
