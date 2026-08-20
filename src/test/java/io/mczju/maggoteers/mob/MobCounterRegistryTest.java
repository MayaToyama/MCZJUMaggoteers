package io.mczju.maggoteers.mob;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MobCounterRegistryTest {

    private static MobCounterSpec counter(MobTrigger count, int amount) {
        return new MobCounterSpec("c", count, amount, null, null);
    }

    private static MobCounterSpec counter(String id, MobTrigger count, int amount) {
        return new MobCounterSpec(id, count, amount, null, null);
    }

    /** 构造 {id → 状态} 的可变 Map（resolveChain 会原地推进）。 */
    private static java.util.LinkedHashMap<String, MobCounterRegistry.MobCounterState> counters(
            MobCounterSpec... specs) {
        java.util.LinkedHashMap<String, MobCounterRegistry.MobCounterState> m =
                new java.util.LinkedHashMap<>();
        for (MobCounterSpec s : specs) {
            m.put(s.id(), new MobCounterRegistry.MobCounterState(s, 0));
        }
        return m;
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

    @Test
    void resolveChainHandlesTwoCounterPingPongWithoutDeadlock() {
        // 两个 count==COUNTER、amount=1 的计数器互 ping-pong：visited 集合保证各触发一次、不卡死
        var map = counters(
                counter("a", MobTrigger.COUNTER, 1),
                counter("b", MobTrigger.COUNTER, 1));
        java.util.Deque<String> initial = new java.util.ArrayDeque<>();
        initial.add("a");
        var fired = MobCounterRegistry.resolveChain(map, initial, null);
        assertEquals(java.util.List.of("a", "b"), fired);
    }

    @Test
    void resolveChainBreaksCounterCycleViaEnqueuedGuard() {
        // a→b→c→a 闭环：enqueued 防止 a 被二次驱动，链在 [a,b,c] 终止
        var map = counters(
                counter("a", MobTrigger.COUNTER, 1),
                counter("b", MobTrigger.COUNTER, 1),
                counter("c", MobTrigger.COUNTER, 1));
        java.util.Deque<String> initial = new java.util.ArrayDeque<>();
        initial.add("a");
        var fired = MobCounterRegistry.resolveChain(map, initial, null);
        assertEquals(3, fired.size(), "闭环必须终止且每个计数器只触发一次");
        assertEquals(3, fired.stream().distinct().count(), "同一条链内不允许重复触发");
        assertTrue(fired.contains("a") && fired.contains("b") && fired.contains("c"));
    }

    @Test
    void resolveChainLeavesAmountAboveOneCounterUnfiredUntilFull() {
        // amount=2 的计数器被单次 COUNTER 信号推进但未计满：不入队、不触发
        var map = counters(
                counter("a", MobTrigger.COUNTER, 1),
                counter("b", MobTrigger.COUNTER, 2));
        java.util.Deque<String> initial = new java.util.ArrayDeque<>();
        initial.add("a");
        var fired = MobCounterRegistry.resolveChain(map, initial, null);
        assertEquals(java.util.List.of("a"), fired);
        assertEquals(1, map.get("b").current(), "b 推进 1 次但未计满");
    }
}
