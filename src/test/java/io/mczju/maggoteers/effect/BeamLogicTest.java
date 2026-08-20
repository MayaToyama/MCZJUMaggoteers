package io.mczju.maggoteers.effect;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BeamLogicTest {

    @Test
    void shortensToTargetCenter() {
        assertEquals(10.0, BeamLogic.resolveLength(32.0, 10.0), 1e-9);
    }

    @Test
    void usesFullRangeWhenNoTarget() {
        assertEquals(32.0, BeamLogic.resolveLength(32.0, null), 1e-9);
    }

    @Test
    void doesNotExceedMaxRange() {
        assertEquals(32.0, BeamLogic.resolveLength(32.0, 100.0), 1e-9);
    }
}