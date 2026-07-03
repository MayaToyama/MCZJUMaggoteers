package io.mczju.maggoteers.wave;

import org.bukkit.entity.EntityType;
import java.util.List;

/**
 * 一条刷怪指令，数值已"完全解析"：{@code hpMult/dmgMult/speedMult} 是最终倍率，
 * 主线程生成时直接套用，不再做任何池查询/随机/乘法。
 * <p>Plan 2：hpMult=coeff.hp（affixes/potions 空）。Plan 3：RunPlanner 填 coeff×affix×scaling + 词缀药水。
 *
 * @param point      刷怪点绝对坐标（已叠层原点）
 * @param type       原版实体类型
 * @param count      一次刷几只（Plan 2 固定；Plan 3 由 RunPlanner 做 count-roll）
 * @param hpMult     最终血量倍率（× 原版基础血量）
 * @param dmgMult    最终攻击倍率
 * @param speedMult  最终速度倍率
 * @param delayTicks 距本波开始的延迟（tick）；0=与首步同刷
 * @param affixes    词缀 id（Plan 3 起填，用于头顶显示）
 * @param potions    词缀药水（Plan 3 起填）
 */
public record SpawnStep(Vec3 point, EntityType type, int count,
                        double hpMult, double dmgMult, double speedMult,
                        int delayTicks, List<String> affixes, List<PotionSpec> potions) {
    public SpawnStep {
        affixes = affixes == null ? List.of() : List.copyOf(affixes);
        potions = potions == null ? List.of() : List.copyOf(potions);
    }
}
