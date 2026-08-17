package io.mczju.maggoteers.config;

import io.mczju.maggoteers.wave.Vec3;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MapPointsTest {

    @Test
    void resolveFallsBackToBossWhenSparseMap() {
        MapPoints pts = new MapPoints(new Vec3(0.5, 1, 0.5), Map.of(
                "1", new Vec3(1, 1, 1),
                "boss", new Vec3(0, 1, 0)));
        assertEquals(new Vec3(0, 1, 0), pts.resolvePointOrBossFallback("9"));
        assertEquals(1, pts.nonBossSpawnCount());
    }

    @Test
    void resolveDoesNotFallbackWhenNineNonBoss() {
        Map<String, Vec3> m = new HashMap<>();
        for (int i = 1; i <= 9; i++) m.put(String.valueOf(i), new Vec3(i, 1, i));
        m.put("boss", new Vec3(0, 1, 0));
        MapPoints pts = new MapPoints(new Vec3(0.5, 1, 0.5), m);
        assertNull(pts.resolvePointOrBossFallback("missing"));
        assertEquals(9, pts.nonBossSpawnCount());
    }
}
