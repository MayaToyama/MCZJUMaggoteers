package io.mczju.maggoteers.mob;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MobCounterRegistryTest {

    private static MobCounterSpec counter(MobTrigger count, int amount) {
        return new MobCounterSpec("c", count, amount, null, null);
    }

    @Test
    void advanceCountIgnoresNonMatchingInput() {
        MobCounterSpec spec = counter(MobTrigger.ATTACK, 3);
        assertEquals(2, MobCounterRegistry.advanceCount(spec, MobTrigger.ATTACK, 1));
        assertEquals(1, MobCounterRegistry.advanceCount(spec, MobTrigger.DAMAGE_TAKEN, 1));
    }

    @Test
    void advanceCountWrapsToZeroAtFull() {
        MobCounterSpec spec = counter(MobTrigger.ATTACK, 3);
        assertEquals(0, MobCounterRegistry.advanceCount(spec, MobTrigger.ATTACK, 2));
    }

    @Test
    void fullAtDetectsTriggerMoment() {
        MobCounterSpec spec = counter(MobTrigger.TICK, 5);
        assertFalse(MobCounterRegistry.fullAt(spec, MobTrigger.TICK, 3));
        assertTrue(MobCounterRegistry.fullAt(spec, MobTrigger.TICK, 4));
        assertFalse(MobCounterRegistry.fullAt(spec, MobTrigger.DAMAGE_TAKEN, 4));
    }

    @Test
    void counterStateIncrementsAndReportsFull() {
        MobCounterSpec spec = counter(MobTrigger.TICK, 3);
        MobCounterRegistry.MobCounterState st =
                new MobCounterRegistry.MobCounterState(spec, 0);
        st = st.increment().increment();
        assertFalse(st.full());
        st = st.increment();
        assertTrue(st.full());
        assertEquals(3, st.current());
    }
}
