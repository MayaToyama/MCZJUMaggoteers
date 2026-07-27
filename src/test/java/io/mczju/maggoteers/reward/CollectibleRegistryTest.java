package io.mczju.maggoteers.reward;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CollectibleRegistryTest {

    @BeforeEach
    void reset() {
        CollectibleRegistry.parseYamlForTest("collectibles: {}");
    }

    @Test
    void resolvesSingleItem() {
        CollectibleRegistry.parseYamlForTest("""
            collectibles:
              a1s_magic_dmg:
                item: maggoteers:charm_magic_10
            """);
        assertEquals("maggoteers:charm_magic_10",
                CollectibleRegistry.resolveItemId("a1s_magic_dmg", 1).orElseThrow());
    }

    @Test
    void resolvesLevelItem() {
        CollectibleRegistry.parseYamlForTest("""
            collectibles:
              a1s_resist:
                items_by_level:
                  1: maggoteers:charm_resist_1
                  2: maggoteers:charm_resist_2
            """);
        assertEquals("maggoteers:charm_resist_2",
                CollectibleRegistry.resolveItemId("a1s_resist", 2).orElseThrow());
    }

    @Test
    void hasMapping() {
        CollectibleRegistry.parseYamlForTest("""
            collectibles:
              x:
                item: maggoteers:charm_magic_10
            """);
        assertTrue(CollectibleRegistry.hasMapping("x"));
        assertFalse(CollectibleRegistry.hasMapping("missing"));
    }
}
