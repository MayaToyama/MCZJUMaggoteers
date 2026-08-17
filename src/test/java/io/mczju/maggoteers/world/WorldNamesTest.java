package io.mczju.maggoteers.world;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class WorldNamesTest {
    @Test
    void forSeedProducesHexWithMaggoteersPrefix() {
        assertEquals("maggoteers_0", WorldNames.forSeed(0L));
        assertEquals("maggoteers_deadbeef", WorldNames.forSeed(0xdeadbeefL));
    }
}
