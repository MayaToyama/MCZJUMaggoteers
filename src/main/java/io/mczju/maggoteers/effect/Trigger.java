package io.mczju.maggoteers.effect;

/** 触发目录（§10.1，v1 全部）。监听器在各 fire-point 调 {@link EffectService#fireTrigger}。 */
public enum Trigger {
    ON_WAVE_CLEAR, ON_ACT_ENTER,                 // 生命周期
    ON_KILL, ON_DAMAGE_DEALT, ON_DAMAGE_TAKEN,     // 战斗
    ON_REVIVE, ON_DEATH, ON_TICK_1S,               // 玩家状态
    ON_COUNTER                                     // 计数器计满（唯一新增；计时器=count:tick 计数器）
}
