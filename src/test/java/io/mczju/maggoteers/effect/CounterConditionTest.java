package io.mczju.maggoteers.effect;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CounterConditionTest {

    // 叶子在 (game,player)=(null,null) 恒 false；用 Not 构造 TRUE 测纯逻辑组合真值表
    private static final CounterCondition FALSE = new CounterCondition.HoldItemPdc("x");
    private static final CounterCondition TRUE = new CounterCondition.Not(FALSE);

    @Test
    void andRequiresAllTrue() {
        assertTrue(new CounterCondition.And(List.of(TRUE, TRUE)).evaluate(null, null));
        assertFalse(new CounterCondition.And(List.of(TRUE, FALSE)).evaluate(null, null));
    }

    @Test
    void orRequiresAnyTrue() {
        assertTrue(new CounterCondition.Or(List.of(FALSE, TRUE)).evaluate(null, null));
        assertFalse(new CounterCondition.Or(List.of(FALSE, FALSE)).evaluate(null, null));
    }

    @Test
    void notInverts() {
        assertTrue(TRUE.evaluate(null, null));
        assertFalse(FALSE.evaluate(null, null));
        assertFalse(new CounterCondition.Not(TRUE).evaluate(null, null));
    }

    @Test
    void parserRoundTripsNestedCondition() {
        CounterCondition c = CounterSpecParser.parse(Map.of(
                "id", "c", "count", "tick", "amount", 1,
                "condition", Map.of("or", List.of(
                        Map.of("not", Map.of("hold_item_pdc", "a")),
                        Map.of("player_in_radius", 3.0))))).orElseThrow().condition();
        assertTrue(c instanceof CounterCondition.Or);
        assertTrue(((CounterCondition.Or) c).children().get(0) instanceof CounterCondition.Not);
    }
}
