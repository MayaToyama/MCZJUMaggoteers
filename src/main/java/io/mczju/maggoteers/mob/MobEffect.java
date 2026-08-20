package io.mczju.maggoteers.mob;

/** 怪物技能效果（自身/目标/范围）。 */
public enum MobEffect {
    HEALTH,      // 对目标/自身增减血量（单体或半径范围）
    ATTRIBUTE,   // 对目标/自身属性修改（临时，可虚拟属性）
    POTION,      // 对目标/自身施加药水
    SUMMON,      // 召唤（含追踪投射物 homing）
    TELEPORT     // 传送（以自身或目标为锚点，防虚空/出图）
}
