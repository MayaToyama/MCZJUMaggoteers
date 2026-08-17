package io.mczju.maggoteers.effect;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PotionMergeTest {

    @Test
    void higherNewAmpReplaces() {
        assertEquals(PotionMerge.Decision.REPLACE,
                PotionMerge.decide(0, 100, 1, 60));
    }

    @Test
    void equalAmpLongerDurationExtends() {
        assertEquals(PotionMerge.Decision.EXTEND,
                PotionMerge.decide(1, 40, 1, 100));
    }

    @Test
    void equalAmpShorterDurationIgnored() {
        assertEquals(PotionMerge.Decision.IGNORE,
                PotionMerge.decide(1, 100, 1, 40));
    }

    @Test
    void lowerNewAmpIgnored() {
        assertEquals(PotionMerge.Decision.IGNORE,
                PotionMerge.decide(2, 40, 1, 200));
    }

    @Test
    void noExistingReplaces() {
        assertEquals(PotionMerge.Decision.REPLACE,
                PotionMerge.decide(-1, 0, 0, 80));
    }
}
