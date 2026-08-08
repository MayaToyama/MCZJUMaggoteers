package io.mczju.maggoteers.effect;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EffectServiceAbilityTest {

    @Test
    void instantPotionPathAllowedWithoutExpiry() {
        assertTrue(WeaponEffectValidator.validateUseAbilityPotion(false, 100).isEmpty());
    }

    @Test
    void expiryPotionPathRequiresZeroDuration() {
        EffectContext params = new EffectContext()
                .put(EffectKeys.DURATION_TICKS, 0);
        assertTrue(WeaponTempEffect.validate(
                Effect.ADD_POTION, params, Trigger.ON_DAMAGE_DEALT, 1).isPresent());
        assertTrue(WeaponTempEffect.validate(
                Effect.ADD_POTION, params.put(EffectKeys.DURATION_TICKS, 100),
                Trigger.ON_DAMAGE_DEALT, 1).isPresent());
    }
}
