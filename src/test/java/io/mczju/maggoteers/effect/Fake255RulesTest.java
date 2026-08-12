package io.mczju.maggoteers.effect;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Fake255RulesTest {

    @Test
    void detectsZeroDuration() {
        assertTrue(Fake255Rules.isFakeClear(255, 0, false));
    }

    @Test
    void detectsOneTick() {
        assertTrue(Fake255Rules.isFakeClear(255, 1, false));
    }

    @Test
    void immunityExempt() {
        assertFalse(Fake255Rules.isFakeClear(255, 0, true));
    }

    @Test
    void realLong255NotFake() {
        assertFalse(Fake255Rules.isFakeClear(255, 200, false));
    }

    @Test
    void normalPotionNotFake() {
        assertFalse(Fake255Rules.isFakeClear(0, 1, false));
    }
}
