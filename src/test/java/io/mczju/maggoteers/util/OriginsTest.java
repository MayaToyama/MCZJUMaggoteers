package io.mczju.maggoteers.util;

import io.mczju.maggoteers.wave.Vec3;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;

class OriginsTest {
    @Test
    void parseFullList() {
        assertEquals(new Vec3(1024, 64, 0), Origins.parse(List.of(1024, 64, 0), 64));
    }

    @Test
    void parseEmptyUsesDefaults() {
        assertEquals(new Vec3(0, 64, 0), Origins.parse(List.of(), 64));
    }

    @Test
    void parsePartial() {
        assertEquals(new Vec3(2048, 70, 0), Origins.parse(List.of(2048, 70), 64));
    }
}
