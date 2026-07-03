package io.mczju.maggoteers.effect;

/** Effect 目录（§10.2，5 种）。 */
public enum Effect {
    ADD_ATTRIBUTE,   // params: attr, op(PERCENT|FLAT), value
    ADD_POTION,      // params: potion, amp, duration_ticks(0=常驻)
    HEAL,            // params: amount
    DAMAGE_AREA,     // params: radius, damage
    GRANT_REVIVE     // params: count
}
