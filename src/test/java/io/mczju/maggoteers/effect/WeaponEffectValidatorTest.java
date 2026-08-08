package io.mczju.maggoteers.effect;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WeaponEffectValidatorTest {

    @Test
    void heldRejectsExpiryBlock() {
        assertTrue(WeaponEffectValidator.validateHeld(
                Effect.ADD_ATTRIBUTE, null, new EffectContext(), Stack.ADD, 0, true).isPresent());
    }

    @Test
    void heldRejectsPermanentHeal() {
        assertTrue(WeaponEffectValidator.validateHeld(
                Effect.HEAL, null, new EffectContext(), Stack.IGNORE, 0, false).isPresent());
    }

    @Test
    void heldRejectsUpgradeMax() {
        assertTrue(WeaponEffectValidator.validateHeld(
                Effect.ADD_ATTRIBUTE, null, new EffectContext(), Stack.ADD, 4, false).isPresent());
    }

    @Test
    void heldRejectsTriggeredPotionZeroDuration() {
        EffectContext ctx = new EffectContext()
                .put(EffectKeys.DURATION_TICKS, 0);
        assertTrue(WeaponEffectValidator.validateHeld(
                Effect.ADD_POTION, Trigger.ON_DAMAGE_DEALT, ctx, Stack.IGNORE, 0, false).isPresent());
    }

    @Test
    void heldAuraRequiresGrant() {
        EffectContext ctx = new EffectContext().put(EffectKeys.RADIUS, 12.0);
        assertTrue(WeaponEffectValidator.validateHeld(
                Effect.AURA, null, ctx, Stack.IGNORE, 0, false).isPresent());
    }

    @Test
    void useAbilityPotionInstantOk() {
        assertTrue(WeaponEffectValidator.validateUseAbilityPotion(false, 100).isEmpty());
    }

    @Test
    void useAbilityPotionRejectExpiryAndDuration() {
        assertTrue(WeaponEffectValidator.validateUseAbilityPotion(true, 100).isPresent());
    }

    @Test
    void useAbilityPotionRejectNoExpiryZeroDuration() {
        assertTrue(WeaponEffectValidator.validateUseAbilityPotion(false, 0).isPresent());
    }
}
