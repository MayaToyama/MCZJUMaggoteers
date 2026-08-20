package io.mczju.maggoteers.effect;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MagicEffectParamsDisableAiTest {

    private static EffectContext base() {
        EffectContext p = new EffectContext();
        p.put(EffectKeys.DURATION_TICKS, 40);
        return p;
    }

    @Test
    void defaults() {
        EffectContext p = MagicEffectParams.withDefaults(Effect.DISABLE_AI, new EffectContext());
        assertEquals(AuraParams.TARGET_ENEMIES, p.get(EffectKeys.TARGETS));
        assertEquals(AuraParams.ENEMY_TRACKED, p.get(EffectKeys.ENEMY_SCOPE));
        assertEquals(5.0, p.get(EffectKeys.RADIUS));
    }

    @Test
    void missingDurationSkipped() {
        EffectContext p = new EffectContext();
        p.put(EffectKeys.TARGETS, AuraParams.TARGET_ENEMIES);
        assertTrue(MagicEffectParams.validateDisableAi(p, Trigger.ON_DAMAGE_DEALT, false)
                .orElse("").toLowerCase().contains("duration"));
    }

    @Test
    void zeroDurationSkipped() {
        EffectContext p = base();
        p.put(EffectKeys.DURATION_TICKS, 0);
        assertTrue(MagicEffectParams.validateDisableAi(p, Trigger.ON_DAMAGE_DEALT, false).isPresent());
    }

    @Test
    void hitTargetPlusOnDamageTakenSkipped() {
        EffectContext p = base();
        p.put(EffectKeys.TARGETS, BuffAreaTargets.TARGET_HIT_TARGET);
        assertTrue(MagicEffectParams.validateDisableAi(p, Trigger.ON_DAMAGE_TAKEN, false).isPresent());
    }

    @Test
    void hitTargetPlusOnKillSkipped() {
        EffectContext p = base();
        p.put(EffectKeys.TARGETS, BuffAreaTargets.TARGET_HIT_TARGET);
        assertTrue(MagicEffectParams.validateDisableAi(p, Trigger.ON_KILL, false).isPresent());
    }

    @Test
    void attackerPlusOnDamageDealtSkipped() {
        EffectContext p = base();
        p.put(EffectKeys.TARGETS, BuffAreaTargets.TARGET_ATTACKER);
        assertTrue(MagicEffectParams.validateDisableAi(p, Trigger.ON_DAMAGE_DEALT, false).isPresent());
    }

    @Test
    void attackerPlusOnDamageTakenAllowed() {
        EffectContext p = base();
        p.put(EffectKeys.TARGETS, BuffAreaTargets.TARGET_ATTACKER);
        assertTrue(MagicEffectParams.validateDisableAi(p, Trigger.ON_DAMAGE_TAKEN, false).isEmpty());
    }

    @Test
    void hitTargetPlusOnDamageDealtAllowed() {
        EffectContext p = base();
        p.put(EffectKeys.TARGETS, BuffAreaTargets.TARGET_HIT_TARGET);
        assertTrue(MagicEffectParams.validateDisableAi(p, Trigger.ON_DAMAGE_DEALT, false).isEmpty());
    }

    @Test
    void statRequiresFireTrigger() {
        EffectContext p = base();
        p.put(EffectKeys.TARGETS, AuraParams.TARGET_ENEMIES);
        assertTrue(MagicEffectParams.validateDisableAi(p, null, false)
                .orElse("").toLowerCase().contains("trigger"));
    }

    @Test
    void weaponPathAllowsNullTrigger() {
        EffectContext p = base();
        p.put(EffectKeys.TARGETS, AuraParams.TARGET_ENEMIES);
        assertTrue(MagicEffectParams.validateDisableAi(p, null, true).isEmpty());
    }

    @Test
    void rejectsSelfTargets() {
        EffectContext p = base();
        p.put(EffectKeys.TARGETS, BuffAreaTargets.TARGET_SELF);
        assertTrue(MagicEffectParams.validateDisableAi(p, Trigger.ON_DAMAGE_DEALT, false).isPresent());
    }

    @Test
    void rejectsAlliesTargets() {
        EffectContext p = base();
        p.put(EffectKeys.TARGETS, AuraParams.TARGET_ALLIES);
        assertTrue(MagicEffectParams.validateDisableAi(p, Trigger.ON_DAMAGE_DEALT, false).isPresent());
    }

    @Test
    void enemiesWithNonPositiveRadiusSkipped() {
        EffectContext p = base();
        p.put(EffectKeys.TARGETS, AuraParams.TARGET_ENEMIES);
        p.put(EffectKeys.RADIUS, 0.0);
        assertTrue(MagicEffectParams.validateDisableAi(p, Trigger.ON_DAMAGE_DEALT, false)
                .orElse("").toLowerCase().contains("radius"));
    }

    @Test
    void enemiesDefaultRadiusOkWhenOmitted() {
        EffectContext p = base();
        p.put(EffectKeys.TARGETS, AuraParams.TARGET_ENEMIES);
        assertTrue(MagicEffectParams.validateDisableAi(p, Trigger.ON_DAMAGE_DEALT, false).isEmpty());
    }
}
