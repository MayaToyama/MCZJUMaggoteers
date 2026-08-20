package io.mczju.maggoteers.effect;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SummonParamsParserTest {

    @Test
    void rejectsHitTargetWithDamageTaken() {
        EffectContext ctx = baseSummonCtx();
        ctx.put(EffectKeys.ANCHOR, "hit_target");
        assertTrue(SummonParamsParser.validate(ctx, Trigger.ON_DAMAGE_TAKEN, false).isPresent());
    }

    @Test
    void rejectsAttackerWithDamageDealt() {
        EffectContext ctx = baseSummonCtx();
        ctx.put(EffectKeys.ANCHOR, "attacker");
        assertTrue(SummonParamsParser.validate(ctx, Trigger.ON_DAMAGE_DEALT, false).isPresent());
    }

    @Test
    void rejectsHitTargetOnWeaponPath() {
        EffectContext ctx = baseSummonCtx();
        ctx.put(EffectKeys.ANCHOR, "hit_target");
        assertTrue(SummonParamsParser.validate(ctx, null, true).isPresent());
    }

    @Test
    void validatesDurationCleanup() {
        EffectContext ctx = baseSummonCtx();
        ctx.put(EffectKeys.CLEANUP, "duration");
        ctx.put(EffectKeys.DURATION_SEC, 3);
        assertTrue(SummonParamsParser.validate(ctx, Trigger.ON_WAVE_CLEAR, false).isEmpty());
    }

    @Test
    void parseProjectilePreservesSpeed() {
        EffectContext proj = new EffectContext();
        proj.put(EffectKeys.PROJECTILE_SPEED, 1.2);
        SummonParams.ProjectileParams p = SummonParamsParser.parseProjectile(proj);
        assertEquals(1.2, p.speed(), 1e-6);
    }

    @Test
    void weaponPathValidatePassesWithSelfAnchor() {
        EffectContext ctx = baseSummonCtx();
        assertTrue(SummonParamsParser.validate(ctx, null, true).isEmpty());
    }

    @Test
    void parsesProjectileHomingTarget() {
        EffectContext ctx = baseSummonCtx();
        EffectContext proj = new EffectContext();
        proj.put(EffectKeys.PROJECTILE_SPEED, 1.2);
        ctx.put(EffectKeys.PROJECTILE, proj);
        ctx.put(EffectKeys.HOMING_TARGET, "nearest_enemy");
        SummonParams sp = SummonParamsParser.parse(ctx, true).orElseThrow();
        assertEquals("nearest_enemy", sp.homingTarget());
        assertEquals(1.2, sp.projectile().speed(), 1e-6);
    }

    @Test
    void homingTargetOptionalWithoutProjectile() {
        SummonParams sp = SummonParamsParser.parse(baseSummonCtx(), true).orElseThrow();
        assertNull(sp.homingTarget());
    }

    private static EffectContext baseSummonCtx() {
        EffectContext ctx = new EffectContext();
        ctx.put(EffectKeys.ENTITY, "WOLF");
        ctx.put(EffectKeys.CLEANUP, "wave_clear");
        return ctx;
    }
}