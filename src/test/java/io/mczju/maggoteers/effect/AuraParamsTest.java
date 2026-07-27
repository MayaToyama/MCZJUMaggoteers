package io.mczju.maggoteers.effect;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AuraParamsTest {

    @Test
    void defaultsAlliesIncludeSelf() {
        EffectContext ctx = new EffectContext();
        ctx.put(EffectKeys.RADIUS, 10.0);
        ctx.put(EffectKeys.GRANT_EFFECT, Effect.HEAL);
        assertTrue(AuraParams.targetsAllies(ctx));
        assertFalse(AuraParams.targetsEnemies(ctx));
        assertTrue(AuraParams.includeSelf(ctx));
        assertEquals(10.0, AuraParams.radius(ctx));
    }

    @Test
    void bothTargets() {
        EffectContext ctx = new EffectContext();
        ctx.put(EffectKeys.TARGETS, AuraParams.TARGET_BOTH);
        assertTrue(AuraParams.targetsAllies(ctx));
        assertTrue(AuraParams.targetsEnemies(ctx));
    }
}
