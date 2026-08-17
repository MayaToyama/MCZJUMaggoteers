package io.mczju.maggoteers.config;

import io.mczju.maggoteers.plan.SeededRng;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ScalingConfigTest {
    @Test
    void rollCountFloorNoFraction() {
        SeededRng rng = new SeededRng(1L);
        assertEquals(5, ScalingConfig.rollCount(5, 1.0, rng));
        assertEquals(5, ScalingConfig.rollCount(5, 1.0, new SeededRng(2L)));
    }

    @Test
    void rollCountFloorAlwaysWhenFracZeroFromIntegerMult() {
        assertEquals(10, ScalingConfig.rollCount(5, 2.0, new SeededRng(7L)));
    }

    @Test
    void rollCountFractionalSupplementIsSeeded() {
        assertEquals(6, ScalingConfig.rollCount(5, 1.2, new SeededRng(7L)));
        int sevens = 0;
        SeededRng rng = new SeededRng(777L);
        for (int i = 0; i < 2000; i++) {
            if (ScalingConfig.rollCount(5, 1.3, rng) == 7) sevens++;
        }
        assertTrue(sevens > 850 && sevens < 1150, "sevens=" + sevens);
    }

    @Test
    void rollCountDeterministic() {
        assertEquals(ScalingConfig.rollCount(5, 1.3, new SeededRng(555L)),
                     ScalingConfig.rollCount(5, 1.3, new SeededRng(555L)));
    }

    @Test
    void rollCountNeverNegative() {
        assertEquals(0, ScalingConfig.rollCount(0, 1.5, new SeededRng(1L)));
    }
}
