package io.mczju.maggoteers.effect;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pure param defaults / validation (no Bukkit API). */
class TargetResolverTest {

    @Test
    void damageAreaDefaultsEnemiesTracked() {
        EffectContext ctx = MagicEffectParams.withDefaults(Effect.DAMAGE_AREA, new EffectContext());
        assertEquals(AuraParams.TARGET_ENEMIES, ctx.get(EffectKeys.TARGETS));
        assertEquals(AuraParams.ENEMY_TRACKED, ctx.get(EffectKeys.ENEMY_SCOPE));
    }

    @Test
    void damageBeamDefaultsEnemiesTracked() {
        EffectContext ctx = MagicEffectParams.withDefaults(Effect.DAMAGE_BEAM, new EffectContext());
        assertEquals(AuraParams.TARGET_ENEMIES, ctx.get(EffectKeys.TARGETS));
        assertEquals(AuraParams.ENEMY_TRACKED, ctx.get(EffectKeys.ENEMY_SCOPE));
    }

    @Test
    void healAreaDefaultsAlliesIncludeSelf() {
        EffectContext ctx = MagicEffectParams.withDefaults(Effect.HEAL_AREA, new EffectContext());
        assertEquals(AuraParams.TARGET_ALLIES, ctx.get(EffectKeys.TARGETS));
        assertTrue(ctx.get(EffectKeys.INCLUDE_SELF));
    }

    @Test
    void healValidationAcceptsAllies() {
        EffectContext ctx = new EffectContext();
        ctx.put(EffectKeys.TARGETS, AuraParams.TARGET_ALLIES);
        assertTrue(MagicEffectParams.validateHealAreaTargets(ctx).isEmpty());
    }

    @Test
    void healValidationAcceptsBothWithIncludeSelfTrue() {
        EffectContext ctx = new EffectContext();
        ctx.put(EffectKeys.TARGETS, AuraParams.TARGET_BOTH);
        ctx.put(EffectKeys.INCLUDE_SELF, true);
        assertTrue(MagicEffectParams.validateHealAreaTargets(ctx).isEmpty());
    }

    @Test
    void healValidationRejectsEnemies() {
        EffectContext ctx = new EffectContext();
        ctx.put(EffectKeys.TARGETS, AuraParams.TARGET_ENEMIES);
        Optional<String> err = MagicEffectParams.validateHealAreaTargets(ctx);
        assertTrue(err.isPresent());
        assertFalse(err.get().isBlank());
    }

    @Test
    void healValidationRejectsBothWithoutIncludeSelfTrue() {
        EffectContext ctx = new EffectContext();
        ctx.put(EffectKeys.TARGETS, AuraParams.TARGET_BOTH);
        assertTrue(MagicEffectParams.validateHealAreaTargets(ctx).isPresent());
        ctx.put(EffectKeys.INCLUDE_SELF, false);
        assertTrue(MagicEffectParams.validateHealAreaTargets(ctx).isPresent());
    }
}