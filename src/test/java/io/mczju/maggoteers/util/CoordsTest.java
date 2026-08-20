package io.mczju.maggoteers.util;

import io.mczju.maggoteers.wave.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class CoordsTest {
    @Test
    void resolveAddsOriginAndRelative() {
        Vec3 act2Origin = new Vec3(1024, 64, 0);
        assertEquals(new Vec3(1036, 65, 8), Coords.resolve(act2Origin, new Vec3(12, 1, 8)));
    }

    @Test
    void resolveAtOriginIsRelative() {
        assertEquals(new Vec3(4, 65, 4), Coords.resolve(new Vec3(0, 0, 0), new Vec3(4, 65, 4)));
    }
}
