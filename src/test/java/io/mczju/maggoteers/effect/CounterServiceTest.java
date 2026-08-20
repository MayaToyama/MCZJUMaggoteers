package io.mczju.maggoteers.effect;

import io.mczju.maggoteers.state.PlayerState;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

class CounterServiceTest {

    private static final Predicate<CounterSpec> ALL = spec -> true;

    private static CounterState counter(String id, CounterCountTrigger t, int amount) {
        return new CounterState(new CounterSpec(id, t, amount, null, null));
    }

    private static CounterState counter(String id, CounterCountTrigger t, int amount, Trigger resetOn) {
        return new CounterState(new CounterSpec(id, t, amount, resetOn, null));
    }

    private static PlayerState stateWith(CounterState... states) {
        PlayerState ps = new PlayerState(UUID.randomUUID(), 2);
        for (CounterState cs : states) {
            ps.counters().put(cs.id(), cs);
        }
        return ps;
    }

    @Test
    void firesAtAmountAndResets() {
        PlayerState ps = stateWith(counter("c", CounterCountTrigger.ATTACK, 3));
        assertEquals(List.of(), CounterService.advance(ps, CounterCountTrigger.ATTACK, null, ALL));
        assertEquals(List.of(), CounterService.advance(ps, CounterCountTrigger.ATTACK, null, ALL));
        assertEquals(List.of("c"), CounterService.advance(ps, CounterCountTrigger.ATTACK, null, ALL));
        assertEquals(0, ps.counters().get("c").progress());   // 计满清零（秒表循环语义）
    }

    @Test
    void timerIsTickCounter() {
        // 计时器 = count:tick：每秒 +1，amount=2 → 第 2、4、6…秒触发
        PlayerState ps = stateWith(counter("t", CounterCountTrigger.TICK, 2));
        assertEquals(List.of(), CounterService.advance(ps, CounterCountTrigger.TICK, null, ALL));
        assertEquals(List.of("t"), CounterService.advance(ps, CounterCountTrigger.TICK, null, ALL));
        assertEquals(List.of(), CounterService.advance(ps, CounterCountTrigger.TICK, null, ALL));
        assertEquals(List.of("t"), CounterService.advance(ps, CounterCountTrigger.TICK, null, ALL));
    }

    @Test
    void relayChainDrivesDownstream() {
        PlayerState ps = stateWith(
                counter("a", CounterCountTrigger.ATTACK, 1),
                counter("b", CounterCountTrigger.ON_COUNTER, 2),
                counter("c", CounterCountTrigger.ON_COUNTER, 1));
        // a 计满 → ON_COUNTER 信号 → b+1、c 计满
        List<String> fired = CounterService.advance(ps, CounterCountTrigger.ATTACK, null, ALL);
        assertTrue(fired.contains("a"));
        assertTrue(fired.contains("c"));
        assertFalse(fired.contains("b"));   // b 只 +1 未满
        // 下一次：a 又满、b 满
        fired = CounterService.advance(ps, CounterCountTrigger.ATTACK, null, ALL);
        assertTrue(fired.contains("a"));
        assertTrue(fired.contains("b"));
    }

    @Test
    void selfLoopSkipped() {
        PlayerState ps = stateWith(counter("a", CounterCountTrigger.ON_COUNTER, 1));
        // ON_COUNTER 信号来自 a 自身 → 排除 sourceId，不触发
        assertEquals(List.of(), CounterService.advance(ps, CounterCountTrigger.ON_COUNTER, "a", ALL));
    }

    @Test
    void gateFilters() {
        PlayerState ps = stateWith(counter("c", CounterCountTrigger.ATTACK, 1));
        assertEquals(List.of(), CounterService.advance(ps, CounterCountTrigger.ATTACK, null,
                spec -> false));
        assertEquals(0, ps.counters().get("c").progress());
    }

    @Test
    void resetMatchingZeroesOnMatchingResetOn() {
        PlayerState ps = stateWith(counter("c", CounterCountTrigger.TICK, 5, Trigger.ON_WAVE_CLEAR));
        ps.counters().get("c").increment();
        CounterService.resetMatching(ps, Trigger.ON_WAVE_CLEAR);
        assertEquals(0, ps.counters().get("c").progress());
        // resetOn 不匹配 → 不重置
        ps.counters().get("c").increment();
        CounterService.resetMatching(ps, Trigger.ON_ACT_ENTER);
        assertEquals(1, ps.counters().get("c").progress());
    }

    @Test
    void registerAndUnregister() {
        PlayerState ps = new PlayerState(UUID.randomUUID(), 2);
        CounterService.register(ps, new CounterSpec("c", CounterCountTrigger.KILL, 2, null, null));
        assertTrue(ps.counters().containsKey("c"));
        CounterService.unregister(ps, "c");
        assertFalse(ps.counters().containsKey("c"));
    }
}
