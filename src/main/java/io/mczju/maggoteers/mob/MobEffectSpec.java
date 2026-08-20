package io.mczju.maggoteers.mob;

import io.mczju.maggoteers.effect.EffectContext;
import io.mczju.maggoteers.effect.EffectKeys;

/**
 * 一条怪物效果。参数复用玩家侧 {@link EffectContext}/{@link EffectKeys}：
 * <ul>
 *   <li>HEALTH: {@code amount}（正=治疗 负=伤害；单体）或 {@code radius}+{@code damage}（范围）</li>
 *   <li>ATTRIBUTE: {@code attr} + {@code op} + {@code value} + {@code duration_ticks}（>0 为临时）</li>
 *   <li>POTION: {@code potion} + {@code amp} + {@code duration_ticks}</li>
 *   <li>SUMMON: {@code entity} + {@code count} + {@code projectile}（含 homing）</li>
 *   <li>TELEPORT: {@code radius}（目标随机落点范围）；锚点由触发上下文决定</li>
 * </ul>
 */
public record MobEffectSpec(MobEffect effect, EffectContext params) {
    public MobEffectSpec {
        params = params == null ? new EffectContext() : params;
    }
}
