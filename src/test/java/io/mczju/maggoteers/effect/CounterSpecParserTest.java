package io.mczju.maggoteers.effect;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CounterSpecParserTest {

    @Test
    void parsesPlainCounter() {
        CounterSpec spec = CounterSpecParser.parse(Map.of(
                "id", "c1",
                "count", "attack",
                "amount", 5)).orElseThrow();
        assertEquals("c1", spec.id());
        assertEquals(CounterCountTrigger.ATTACK, spec.countTrigger());
        assertEquals(5, spec.amount());
        assertNull(spec.condition());
        assertNull(spec.resetOn());
    }

    @Test
    void timerIsTickCounter() {
        // 计时器 = count:tick 计数器：每 1 秒计一次，amount=30 → 30 秒触发
        CounterSpec spec = CounterSpecParser.parse(Map.of(
                "id", "t1",
                "count", "tick",
                "amount", 30)).orElseThrow();
        assertEquals(CounterCountTrigger.TICK, spec.countTrigger());
        assertEquals(30, spec.amount());
    }

    @Test
    void parsesCountOnCounterForRelay() {
        CounterSpec spec = CounterSpecParser.parse(Map.of(
                "id", "relay",
                "count", "on_counter",
                "amount", 2)).orElseThrow();
        assertEquals(CounterCountTrigger.ON_COUNTER, spec.countTrigger());
    }

    @Test
    void parsesResetTrigger() {
        CounterSpec spec = CounterSpecParser.parse(Map.of(
                "id", "c2",
                "count", "wave_clear",
                "amount", 1,
                "reset", "on_wave_clear")).orElseThrow();
        assertEquals(Trigger.ON_WAVE_CLEAR, spec.resetOn());
    }

    @Test
    void parsesConditionHoldItem() {
        CounterSpec spec = CounterSpecParser.parse(Map.of(
                "id", "c3",
                "count", "tick",
                "amount", 3,
                "condition", Map.of("hold_item_pdc", "maggoteers:wrench"))).orElseThrow();
        assertTrue(spec.condition() instanceof CounterCondition.HoldItemPdc h
                && h.pdcValue().equals("maggoteers:wrench"));
    }

    @Test
    void parsesCompoundCondition() {
        CounterSpec spec = CounterSpecParser.parse(Map.of(
                "id", "c4",
                "count", "kill",
                "amount", 2,
                "condition", Map.of(
                        "and", List.of(
                                Map.of("hold_item_pdc", "a"),
                                Map.of("not", Map.of("player_in_radius", 5.0)))))).orElseThrow();
        assertTrue(spec.condition() instanceof CounterCondition.And and
                && and.children().size() == 2
                && and.children().get(1) instanceof CounterCondition.Not);
    }

    @Test
    void missingIdRejected() {
        assertTrue(CounterSpecParser.parse(Map.of("count", "tick", "amount", 1)).isEmpty());
    }

    @Test
    void unknownCountTriggerRejected() {
        assertTrue(CounterSpecParser.parse(Map.of(
                "id", "x", "count", "bogus", "amount", 1)).isEmpty());
    }
}
