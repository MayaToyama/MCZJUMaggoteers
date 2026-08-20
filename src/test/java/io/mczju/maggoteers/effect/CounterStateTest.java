package io.mczju.maggoteers.effect;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CounterStateTest {

    @Test
    void incrementsAndFiresAtAmount() {
        CounterState st = new CounterState(new CounterSpec("c", CounterCountTrigger.ATTACK, 3, null, null));
        assertFalse(st.increment());
        assertFalse(st.increment());
        assertTrue(st.increment());   // 3/3 → fire
        assertEquals(0, st.progress());   // 计满后清零（秒表/循环语义）
    }

    @Test
    void resetZeroes() {
        CounterState st = new CounterState(new CounterSpec("c", CounterCountTrigger.ATTACK, 3, null, null));
        st.increment();
        st.reset();
        assertEquals(0, st.progress());
    }

    @Test
    void triggeredByMatchesTrigger() {
        CounterSpec spec = new CounterSpec("c", CounterCountTrigger.ON_COUNTER, 2, null, null);
        assertTrue(spec.triggeredBy(CounterCountTrigger.ON_COUNTER));
        assertFalse(spec.triggeredBy(CounterCountTrigger.ATTACK));
    }
}
