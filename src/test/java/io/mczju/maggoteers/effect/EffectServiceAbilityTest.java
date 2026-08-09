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

    @Test
    void preserveHealthAcrossResyncKeepsHealAfterAttributeRefresh() {
        assertEquals(25.0, EffectService.preserveHealthAcrossResync(25.0, 30.0, 30.0), 0.001);
        assertEquals(18.0, EffectService.preserveHealthAcrossResync(18.0, 20.0, 20.0), 0.001);
    }

    @Test
    void preserveHealthAcrossResyncScalesWhenMaxChanges() {
        assertEquals(22.5, EffectService.preserveHealthAcrossResync(15.0, 20.0, 30.0), 0.001);
        assertEquals(16.666, EffectService.preserveHealthAcrossResync(25.0, 30.0, 20.0), 0.01);
    }

    @Test
    void finalizeHealthAddsCurrentHpWhenMaxIncreases() {
        assertEquals(44.0, EffectService.finalizeHealthAfterResync(20.0, 20.0, 44.0), 0.001);
        assertEquals(39.0, EffectService.finalizeHealthAfterResync(15.0, 44.0, 68.0), 0.001);
    }

    @Test
    void finalizeHealthPreservesRatioWhenMaxUnchanged() {
        assertEquals(25.0, EffectService.finalizeHealthAfterResync(25.0, 30.0, 30.0), 0.001);
    }
}
