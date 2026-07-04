package io.mczju.maggoteers.persist;

import io.mczju.maggoteers.game.GameOutcome;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SettlementTest {
    @Test
    void winIsFlat() {
        assertEquals(100, Settlement.grant(new Settlement.Input(GameOutcome.WIN, 3, 6), 100, 25, 5));
    }

    @Test
    void failScalesWithProgress() {
        // 2 层过 + 当前层 3 波过 → 25*2 + 5*3 = 65
        assertEquals(65, Settlement.grant(new Settlement.Input(GameOutcome.FAIL, 2, 3), 100, 25, 5));
    }

    @Test
    void inProgressIsZero() {
        assertEquals(0, Settlement.grant(new Settlement.Input(GameOutcome.IN_PROGRESS, 0, 0), 100, 25, 5));
    }

    @Test
    void negativeProgressClampedToZero() {
        assertEquals(0, Settlement.grant(new Settlement.Input(GameOutcome.FAIL, -1, -2), 100, 25, 5));
    }
}
