package io.mczju.maggoteers.wave;

import io.mczju.maggoteers.config.InfernalCfg;
import org.bukkit.entity.EntityType;

import java.util.List;

/**
 * 一条刷怪指令，数值已"完全解析"（coeff×affix×scaling）。主线程生成直接套用，零随机/零查询。
 * <p>{@code delayTicks} 在 {@link WaveSpec} 未展开前为<strong>步前等待</strong>（秒×20）；经 {@link WaveSpec#expand()} 后为距本波开始的绝对 tick。
 * <p>{@code repeat} 为该 step 自身重复次数（未展开前）；{@link WaveSpec#expand()} 后恒为 1。
 */
public record SpawnStep(Vec3 point, EntityType type, int count,
                        double hpMult, double dmgMult, double speedMult, double dropMult,
                        double scaleMult, double followRangeMult,
                        int delayTicks, List<String> affixes, List<PotionSpec> potions,
                        InfernalCfg infernal,
                        List<MobEquipment> equipment, List<DeathSpawn> onDeath,
                        List<PassengerSpawn> passengers, int repeat) {
    public SpawnStep(Vec3 point, EntityType type, int count,
                     double hpMult, double dmgMult, double speedMult, double dropMult,
                     double scaleMult, double followRangeMult,
                     int delayTicks, List<String> affixes, List<PotionSpec> potions,
                     InfernalCfg infernal,
                     List<MobEquipment> equipment, List<DeathSpawn> onDeath,
                     List<PassengerSpawn> passengers) {
        this(point, type, count, hpMult, dmgMult, speedMult, dropMult, scaleMult, followRangeMult,
                delayTicks, affixes, potions, infernal, equipment, onDeath, passengers, 1);
    }

    public SpawnStep(Vec3 point, EntityType type, int count,
                     double hpMult, double dmgMult, double speedMult, double dropMult,
                     int delayTicks, List<String> affixes, List<PotionSpec> potions,
                     List<PassengerSpawn> passengers) {
        this(point, type, count, hpMult, dmgMult, speedMult, dropMult, 1.0, 1.0,
                delayTicks, affixes, potions, InfernalCfg.NONE,
                List.of(), List.of(), passengers, 1);
    }

    public SpawnStep(Vec3 point, EntityType type, int count,
                     double hpMult, double dmgMult, double speedMult, double dropMult,
                     int delayTicks, List<String> affixes, List<PotionSpec> potions) {
        this(point, type, count, hpMult, dmgMult, speedMult, dropMult, delayTicks, affixes, potions, List.of());
    }

    public SpawnStep {
        affixes = affixes == null ? List.of() : List.copyOf(affixes);
        potions = potions == null ? List.of() : List.copyOf(potions);
        infernal = infernal == null ? InfernalCfg.NONE : infernal;
        equipment = equipment == null ? List.of() : List.copyOf(equipment);
        onDeath = onDeath == null ? List.of() : List.copyOf(onDeath);
        passengers = passengers == null ? List.of() : List.copyOf(passengers);
        repeat = Math.max(1, repeat);
    }
}
