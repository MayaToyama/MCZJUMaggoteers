package io.mczju.maggoteers.wave;

import org.bukkit.entity.EntityType;

import java.util.List;

/**
 * 一条刷怪指令，数值已"完全解析"（coeff×scaling）。主线程生成直接套用，零随机/零查询。
 * <p>{@code skills} 为技能 id 列表（mob_skills.yml，Task 4/6 解析执行）。
 * <p>{@code delayTicks} 在 {@link WaveSpec} 未展开前为<strong>步前等待</strong>（秒×20）；经 {@link WaveSpec#expand()} 后为距本波开始的绝对 tick。
 * <p>{@code repeat} 为该 step 自身重复次数（未展开前）；{@link WaveSpec#expand()} 后恒为 1。
 */
public record SpawnStep(Vec3 point, EntityType type, int count,
                        double hpMult, double dmgMult, double speedMult,
                        double scaleMult, double followRangeMult,
                        int delayTicks, List<String> skills,
                        List<MobEquipment> equipment, List<DeathSpawn> onDeath,
                        List<PassengerSpawn> passengers, int repeat,
                        String name, boolean bossBar) {
    /** full 之外最常用：无 name/bossBar（repeat 1）。 */
    public SpawnStep(Vec3 point, EntityType type, int count,
                     double hpMult, double dmgMult, double speedMult,
                     double scaleMult, double followRangeMult,
                     int delayTicks, List<String> skills,
                     List<MobEquipment> equipment, List<DeathSpawn> onDeath,
                     List<PassengerSpawn> passengers) {
        this(point, type, count, hpMult, dmgMult, speedMult, scaleMult, followRangeMult,
                delayTicks, skills, equipment, onDeath, passengers, 1, null, false);
    }

    public SpawnStep(Vec3 point, EntityType type, int count,
                     double hpMult, double dmgMult, double speedMult,
                     double scaleMult, double followRangeMult,
                     int delayTicks, List<String> skills,
                     List<MobEquipment> equipment, List<DeathSpawn> onDeath,
                     List<PassengerSpawn> passengers, int repeat) {
        this(point, type, count, hpMult, dmgMult, speedMult, scaleMult, followRangeMult,
                delayTicks, skills, equipment, onDeath, passengers, repeat, null, false);
    }

    public SpawnStep(Vec3 point, EntityType type, int count,
                     double hpMult, double dmgMult, double speedMult,
                     int delayTicks, List<String> skills,
                     List<PassengerSpawn> passengers) {
        this(point, type, count, hpMult, dmgMult, speedMult, 1.0, 1.0,
                delayTicks, skills, List.of(), List.of(), passengers, 1, null, false);
    }

    public SpawnStep(Vec3 point, EntityType type, int count,
                     double hpMult, double dmgMult, double speedMult,
                     int delayTicks, List<String> skills) {
        this(point, type, count, hpMult, dmgMult, speedMult, delayTicks, skills, List.of());
    }

    public SpawnStep {
        skills = skills == null ? List.of() : List.copyOf(skills);
        equipment = equipment == null ? List.of() : List.copyOf(equipment);
        onDeath = onDeath == null ? List.of() : List.copyOf(onDeath);
        passengers = passengers == null ? List.of() : List.copyOf(passengers);
        repeat = Math.max(1, repeat);
        if (name != null) {
            name = name.trim();
            if (name.isEmpty()) name = null;
        }
    }
}
