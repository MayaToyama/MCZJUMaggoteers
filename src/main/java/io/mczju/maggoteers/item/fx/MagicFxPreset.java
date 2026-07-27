package io.mczju.maggoteers.item.fx;

/** 魔法物品释放时的粒子预设（config / items YAML 引用 id）。 */
public enum MagicFxPreset {
    /** 默认：加粗水平圆环（替代旧 playAreaRing）。 */
    THICK_RING,
    /** 自玩家前方起绕玩家快速旋转的半径粒子，前方缺口、后方递减。 */
    SPIRAL_RADIUS,
    /** 波浪式半径递增的同心环，保留前环残留。 */
    RIPPLE_RINGS,
    /** 沿准星方向的加粗射线。 */
    AIM_RAY,
    /** 平滑单环半径递增（同一时刻仅一环）。 */
    SMOOTH_EXPAND_RING,
    /** 实体正上方一格的圆点。 */
    DOT_ABOVE
}
