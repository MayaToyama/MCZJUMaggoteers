package io.mczju.maggoteers.effect;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WeaponTempEffectTest {

    private static EffectContext dmgPercent(double value) {
        return new EffectContext()
                .put(EffectKeys.ATTR_NAME, "ATTACK_DAMAGE")
                .put(EffectKeys.OP, "PERCENT")
                .put(EffectKeys.VALUE, value);
    }

    @Test
    void effectIdPrefixed() {
        assertEquals("ability:maggoteers:power_strike",
                WeaponTempEffect.effectId("maggoteers:power_strike"));
    }

    @Test
    void validateOkForNextHit() {
        assertTrue(WeaponTempEffect.validate(
                Effect.ADD_ATTRIBUTE, dmgPercent(0.5), Trigger.ON_DAMAGE_DEALT, 1).isEmpty());
    }

    @Test
    void validateRequiresExpiry() {
        assertTrue(WeaponTempEffect.validate(Effect.ADD_ATTRIBUTE, dmgPercent(0.5), null, 1)
                .orElse("").contains("expiry"));
    }

    @Test
    void validateRequiresAttr() {
        EffectContext params = new EffectContext()
                .put(EffectKeys.OP, "PERCENT")
                .put(EffectKeys.VALUE, 0.5);
        assertTrue(WeaponTempEffect.validate(Effect.ADD_ATTRIBUTE, params, Trigger.ON_DAMAGE_DEALT, 1)
                .orElse("").contains("attr"));
    }

    @Test
    void validateRequiresValue() {
        EffectContext params = new EffectContext()
                .put(EffectKeys.ATTR_NAME, "ATTACK_DAMAGE")
                .put(EffectKeys.OP, "PERCENT");
        assertTrue(WeaponTempEffect.validate(Effect.ADD_ATTRIBUTE, params, Trigger.ON_DAMAGE_DEALT, 1)
                .orElse("").contains("value"));
    }

    @Test
    void validateRejectsBadCharges() {
        assertTrue(WeaponTempEffect.validate(
                Effect.ADD_ATTRIBUTE, dmgPercent(0.5), Trigger.ON_DAMAGE_DEALT, 0).isPresent());
    }

    @Test
    void validateRejectsBadOp() {
        EffectContext params = dmgPercent(0.5).put(EffectKeys.OP, "MUL");
        assertTrue(WeaponTempEffect.validate(Effect.ADD_ATTRIBUTE, params, Trigger.ON_DAMAGE_DEALT, 1)
                .orElse("").contains("op"));
    }

    @Test
    void expiryPotionRejectsPositiveDuration() {
        EffectContext params = new EffectContext()
                .put(EffectKeys.DURATION_TICKS, 100);
        assertTrue(WeaponTempEffect.validate(Effect.ADD_POTION, params, Trigger.ON_DAMAGE_DEALT, 1)
                .orElse("").contains("duration_ticks"));
    }

    @Test
    void nextHitExpiryChargesOneRemovedOnFirstDealt() {
        PlayerEffect buff = new PlayerEffect(
                WeaponTempEffect.effectId("maggoteers:power_strike"),
                Effect.ADD_ATTRIBUTE,
                dmgPercent(0.5),
                null,
                Trigger.ON_DAMAGE_DEALT,
                1,
                0,
                null,
                Stack.REPLACE,
                0,
                0);
        List<PlayerEffect> list = new ArrayList<>(List.of(buff));
        List<String> removed = EffectStacker.sweepExpiry(list, Trigger.ON_DAMAGE_DEALT);
        assertEquals(List.of(WeaponTempEffect.effectId("maggoteers:power_strike")), removed);
        assertTrue(list.isEmpty());
    }

    @Test
    void replaceKeepsSingleTempBuff() {
        PlayerEffect a = new PlayerEffect(
                "ability:x", Effect.ADD_ATTRIBUTE, dmgPercent(0.3),
                null, Trigger.ON_DAMAGE_DEALT, 1, 0, null, Stack.REPLACE, 0, 0);
        PlayerEffect b = new PlayerEffect(
                "ability:x", Effect.ADD_ATTRIBUTE, dmgPercent(0.5),
                null, Trigger.ON_DAMAGE_DEALT, 1, 0, null, Stack.REPLACE, 0, 0);
        List<PlayerEffect> out = EffectStacker.merge(List.of(a), b);
        assertEquals(1, out.size());
        assertEquals(0.5, out.get(0).params().get(EffectKeys.VALUE));
    }
}
