package io.mczju.maggoteers.effect;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DisableAiTargetsTest {

    @Test
    void emptyHitTargetWithoutContext() {
        EffectContext p = new EffectContext();
        p.put(EffectKeys.DURATION_TICKS, 40);
        p.put(EffectKeys.TARGETS, BuffAreaTargets.TARGET_HIT_TARGET);
        List<?> out = DisableAiTargets.resolve(null, null, p, null);
        assertTrue(out.isEmpty());
    }

    @Test
    void emptyAttackerWithoutContext() {
        EffectContext p = new EffectContext();
        p.put(EffectKeys.DURATION_TICKS, 40);
        p.put(EffectKeys.TARGETS, BuffAreaTargets.TARGET_ATTACKER);
        assertTrue(DisableAiTargets.resolve(null, null, p, null).isEmpty());
    }

    @Test
    void unknownKeyEmpty() {
        EffectContext p = new EffectContext();
        p.put(EffectKeys.DURATION_TICKS, 40);
        p.put(EffectKeys.TARGETS, "self");
        assertTrue(DisableAiTargets.resolveTargetKey("self", null, null, p, null).isEmpty());
    }

    @Test
    void passesHostileFilterNullIsFalse() {
        assertFalse(DisableAiTargets.passesHostileFilter(null, null));
    }
}
