package io.mczju.maggoteers.effect;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TriggerContextWithersTest {
    @Test
    void withHitTargetPreservesOtherFields() {
        TriggerContext base = new TriggerContext(Trigger.ON_KILL, null, null, null);
        TriggerContext fired = base.withFired(Trigger.ON_DAMAGE_DEALT);
        assertEquals(Trigger.ON_DAMAGE_DEALT, fired.fired());
        assertNull(fired.hitTarget());
        TriggerContext cleared = fired.withHitTarget(null);
        assertEquals(Trigger.ON_DAMAGE_DEALT, cleared.fired());
    }

}
