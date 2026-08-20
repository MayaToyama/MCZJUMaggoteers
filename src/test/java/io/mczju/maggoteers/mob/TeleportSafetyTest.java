package io.mczju.maggoteers.mob;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TeleportSafetyTest {

    @Test
    void outsideMapBoundary() {
        assertFalse(TeleportSafety.outsideMap(100, 0));
        assertFalse(TeleportSafety.outsideMap(0, 159));
        assertTrue(TeleportSafety.outsideMap(0, 200));
        assertTrue(TeleportSafety.outsideMap(-300, 0));
    }

    @Test
    void solidGroundKinds() {
        assertTrue(TeleportSafety.isSolidGround(Material.STONE));
        assertTrue(TeleportSafety.isSolidGround(Material.DIRT));
        assertFalse(TeleportSafety.isSolidGround(Material.AIR));
        assertFalse(TeleportSafety.isSolidGround(Material.BEDROCK));
        assertFalse(TeleportSafety.isSolidGround(Material.WATER));
    }
}
