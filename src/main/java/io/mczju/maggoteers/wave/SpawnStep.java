package io.mczju.maggoteers.wave;

import org.bukkit.entity.EntityType;
import java.util.List;

/**
 * 一条刷怪指令，数值已"完全解析"（coeff×affix×scaling）。主线程生成直接套用，零随机/零查询。
 */
public record SpawnStep(Vec3 point, EntityType type, int count,
                        double hpMult, double dmgMult, double speedMult, double dropMult,
                        int delayTicks, List<String> affixes, List<PotionSpec> potions) {
    public SpawnStep {
        affixes = affixes == null ? List.of() : List.copyOf(affixes);
        potions = potions == null ? List.of() : List.copyOf(potions);
    }
}
