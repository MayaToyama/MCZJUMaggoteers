package io.mczju.maggoteers.mob;

import java.util.List;

/**
 * 一条怪物技能（trigger + 可选条件 + 可选计数器 + 效果列表）。
 * <p>{@code invisible: true} 由 {@code MobSkillService} 在 SPAWN 时处理
 * （setInvisible + 玻璃瓶头盔，不用 INVISIBILITY 药水）。{@code effects} 允许为空（纯装饰）。
 */
public record MobSkillSpec(
        String id,
        MobTrigger trigger,
        MobCondition condition,
        boolean invisible,
        MobCounterSpec counter,
        int cooldownSec,
        List<MobEffectSpec> effects
) {
    public MobSkillSpec {
        effects = effects == null ? List.of() : List.copyOf(effects);
        cooldownSec = Math.max(0, cooldownSec);
    }
}
