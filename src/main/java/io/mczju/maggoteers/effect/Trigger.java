package io.mczju.maggoteers.effect;

/** 触发目录（§10.1，v1 全部）。监听器在各 fire-point 调 {@link EffectService#fireTrigger}。 */
public enum Trigger {
    ON_WAVE_CLEAR, ON_ACT_ENTER, ON_GAME_END,     // 生命周期
    ON_KILL, ON_DAMAGE_DEALT, ON_DAMAGE_TAKEN,     // 战斗
    ON_REVIVE, ON_DEATH, ON_TICK_1S,               // 玩家状态
    ON_INTERACT                                     // 物品交互（Plan 6 接）
}
