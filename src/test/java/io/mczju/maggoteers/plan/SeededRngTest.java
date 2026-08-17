package io.mczju.maggoteers.plan;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SeededRngTest {
    @Test
    void sameSeedSameSequence() {
        SeededRng a = new SeededRng(12345L);
        SeededRng b = new SeededRng(12345L);
        for (int i = 0; i < 100; i++) assertEquals(a.nextInt(1000), b.nextInt(1000));
        assertEquals(a.nextDouble(), b.nextDouble());
    }

    @Test
    void differentSeedDifferentSequence() {
        SeededRng a = new SeededRng(1L);
        SeededRng b = new SeededRng(2L);
        boolean diff = false;
        for (int i = 0; i < 20; i++) if (a.nextInt(1000) != b.nextInt(1000)) diff = true;
        assertTrue(diff);
    }

    @Test
    void weightedIndexRespectsWeights() {
        SeededRng rng = new SeededRng(42L);
        int[] counts = new int[3];
        for (int i = 0; i < 3000; i++) counts[rng.weightedIndex(new double[]{1, 3, 1})]++;
        assertTrue(counts[1] > counts[0] && counts[1] > counts[2]);
        assertEquals(counts[0], counts[2], 120);
    }

    @Test
    void weightedIndexAllZeroReturnsZero() {
        assertEquals(0, new SeededRng(1L).weightedIndex(new double[]{0, 0, 0}));
    }

    @Test
    void deriveIsReproducibleAndIndependent() {
        SeededRng root = new SeededRng(99L);
        SeededRng c1 = root.derive(1L);
        SeededRng c1again = new SeededRng(99L).derive(1L);
        assertEquals(c1.nextInt(500), c1again.nextInt(500));
        assertNotEquals(root.derive(1L).nextInt(500), root.derive(2L).nextInt(500));
    }
}
