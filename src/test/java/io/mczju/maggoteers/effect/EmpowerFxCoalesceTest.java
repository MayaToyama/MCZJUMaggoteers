package io.mczju.maggoteers.effect;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class EmpowerFxCoalesceTest {
    @Test
    void coalescePackMembersToOneItemId() {
        Set<String> touched = Set.of(
                "ability:maggoteers:foo",
                "ability:maggoteers:foo:0",
                "ability:maggoteers:foo:1",
                "ability:maggoteers:foo_bar:0");
        Set<String> itemIds = EmpowerFxCoalesce.itemIdsTouched(touched);
        assertEquals(Set.of("maggoteers:foo", "maggoteers:foo_bar"), itemIds);
    }

    @Test
    void deferredOnlyWithoutVisibleCastUsesStrike() {
        assertEquals(EmpowerFxCoalesce.Choice.STRIKE,
                EmpowerFxCoalesce.choose(false, true, false));
    }

    @Test
    void deferredOnlyWithVisibleCastUsesCast() {
        assertEquals(EmpowerFxCoalesce.Choice.CAST,
                EmpowerFxCoalesce.choose(false, true, true));
    }

    @Test
    void empowerFxWins() {
        assertEquals(EmpowerFxCoalesce.Choice.EMPOWER,
                EmpowerFxCoalesce.choose(true, true, true));
    }

    @Test
    void immediateWithoutFxIsNone() {
        assertEquals(EmpowerFxCoalesce.Choice.NONE,
                EmpowerFxCoalesce.choose(false, false, false));
    }
}
