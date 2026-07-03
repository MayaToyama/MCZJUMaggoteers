package io.mczju.maggoteers.wave;

import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class WaveSpecTest {
    private static SpawnStep step(int delay) {
        return new SpawnStep(new Vec3(0, 64, 0), EntityType.ZOMBIE, 5,
                1.0, 1.0, 1.0, delay, List.of(), List.of());
    }

    @Test
    void expandRepeatsStepsInOrder() {
        WaveSpec w = new WaveSpec(List.of(step(0), step(100)), 2, List.of());
        List<SpawnStep> exp = w.expand();
        assertEquals(4, exp.size());
        assertEquals(0, exp.get(0).delayTicks());
        assertEquals(100, exp.get(1).delayTicks());
        assertEquals(0, exp.get(2).delayTicks());
    }

    @Test
    void repeatClampedToOne() {
        WaveSpec w = new WaveSpec(List.of(step(0)), 0, List.of());
        assertEquals(1, w.expand().size());
    }
}
