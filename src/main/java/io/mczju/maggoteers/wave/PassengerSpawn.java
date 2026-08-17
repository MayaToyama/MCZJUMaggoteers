package io.mczju.maggoteers.wave;

import io.mczju.maggoteers.config.InfernalCfg;
import org.bukkit.entity.EntityType;

import java.util.List;

/** 已解析的乘客怪树节点（coeff×affix×scaling）。 */
public record PassengerSpawn(EntityType type,
                             double hpMult, double dmgMult, double speedMult,
                             double scaleMult, double followRangeMult,
                             List<String> affixes, List<PotionSpec> potions,
                             InfernalCfg infernal,
                             List<MobEquipment> equipment, List<DeathSpawn> onDeath,
                             List<PassengerSpawn> passengers,
                             String name, boolean bossBar, boolean controller) {
    public PassengerSpawn {
        affixes = affixes == null ? List.of() : List.copyOf(affixes);
        potions = potions == null ? List.of() : List.copyOf(potions);
        infernal = infernal == null ? InfernalCfg.NONE : infernal;
        equipment = equipment == null ? List.of() : List.copyOf(equipment);
        onDeath = onDeath == null ? List.of() : List.copyOf(onDeath);
        passengers = passengers == null ? List.of() : List.copyOf(passengers);
        if (name != null) {
            name = name.trim();
            if (name.isEmpty()) name = null;
        }
    }

    /** 无嵌套乘客（兼容旧构造）。 */
    public PassengerSpawn(EntityType type, double hpMult, double dmgMult, double speedMult,
                          List<String> affixes, List<PotionSpec> potions) {
        this(type, hpMult, dmgMult, speedMult, 1.0, 1.0,
                affixes, potions, InfernalCfg.NONE, List.of(), List.of(), List.of(), null, false, false);
    }

    public PassengerSpawn(EntityType type,
                          double hpMult, double dmgMult, double speedMult,
                          double scaleMult, double followRangeMult,
                          List<String> affixes, List<PotionSpec> potions,
                          InfernalCfg infernal,
                          List<MobEquipment> equipment, List<DeathSpawn> onDeath,
                          List<PassengerSpawn> passengers) {
        this(type, hpMult, dmgMult, speedMult, scaleMult, followRangeMult,
                affixes, potions, infernal, equipment, onDeath, passengers, null, false, false);
    }
}
