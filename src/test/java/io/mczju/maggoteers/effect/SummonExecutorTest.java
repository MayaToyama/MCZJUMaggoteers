package io.mczju.maggoteers.effect;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;

class SummonExecutorTest {

    @Test
    void hitTargetWithoutContextReturnsNull() {
        assertNull(SummonExecutor.resolveAnchorLocation(null, BuffAreaTargets.TARGET_HIT_TARGET, TriggerContext.empty()));
    }

    @Test
    void attackerWithoutContextReturnsNull() {
        assertNull(SummonExecutor.resolveAnchorLocation(null, "attacker", TriggerContext.empty()));
    }
}