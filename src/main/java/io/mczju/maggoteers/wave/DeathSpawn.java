package io.mczju.maggoteers.wave;

import io.mczju.maggoteers.config.InfernalCfg;
import org.bukkit.entity.EntityType;

import java.util.List;

public record DeathSpawn(EntityType type,
                         double hpMult, double dmgMult, double speedMult,
                         double scaleMult, double followRangeMult,
                         List<String> affixes, List<PotionSpec> potions,
                         InfernalCfg infernal,
                         List<MobEquipment> equipment, List<PassengerSpawn> passengers,
                         List<DeathSpawn> onDeath,
                         String name, boolean bossBar) {
    public DeathSpawn {
        affixes = affixes == null ? List.of() : List.copyOf(affixes);
        potions = potions == null ? List.of() : List.copyOf(potions);
        infernal = infernal == null ? InfernalCfg.NONE : infernal;
        equipment = equipment == null ? List.of() : List.copyOf(equipment);
        passengers = passengers == null ? List.of() : List.copyOf(passengers);
        onDeath = onDeath == null ? List.of() : List.copyOf(onDeath);
        if (name != null) {
            name = name.trim();
            if (name.isEmpty()) name = null;
        }
    }

    public DeathSpawn(EntityType type,
                      double hpMult, double dmgMult, double speedMult,
                      double scaleMult, double followRangeMult,
                      List<String> affixes, List<PotionSpec> potions,
                      InfernalCfg infernal,
                      List<MobEquipment> equipment, List<PassengerSpawn> passengers,
                      List<DeathSpawn> onDeath) {
        this(type, hpMult, dmgMult, speedMult, scaleMult, followRangeMult,
                affixes, potions, infernal, equipment, passengers, onDeath, null, false);
    }
}
