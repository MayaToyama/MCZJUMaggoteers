package io.mczju.maggoteers.effect;

/** Effect 目录（§10.2，5 种）。 */
public enum Effect {
    ADD_ATTRIBUTE,   // params: attr, op(PERCENT|FLAT|MULTIPLY), value
    ADD_POTION,      // params: potion, amp, duration_ticks(0=常驻)
    HEAL,            // params: amount
    DAMAGE_AREA,     // params: radius, damage, targets, enemy_scope
    DAMAGE_BEAM,     // params: damage, ray_length, beam_radius, targets, enemy_scope
    HEAL_AREA,       // params: amount, radius, targets, include_self
    GRANT_REVIVE,    // params: count
    BUFF_AREA,       // params: potions[], targets, radius, fx, mark_fx, mark_on
    /** params: duration_ticks, targets(enemies|hit_target|attacker), radius, enemy_scope, fx, mark_fx, mark_on */
    DISABLE_AI,
    /** 范围光环：params 见 {@link AuraParams}；grant 嵌套在 params 内。 */
    AURA,
    /** params: item (IC id), amount */
    GRANT_ITEM,
    /** params: entity, anchor, cleanup, attributes, tamed, projectile, friendly_fire */
    SUMMON,
    /** Bound equipment view; params: slot, items_by_level. Permanent only. */
    BOUND_EQUIP
}
