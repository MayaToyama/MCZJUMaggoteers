package io.mczju.maggoteers.wave;



import org.bukkit.entity.EntityType;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;



class WaveSpecTest {

    private static SpawnStep step(int delay) {

        return new SpawnStep(new Vec3(0, 64, 0), EntityType.ZOMBIE, 5,

                1.0, 1.0, 1.0, 1.0, delay, List.of(), List.of());

    }



    @Test

    void expandRepeatsStepsInOrder() {

        WaveSpec w = new WaveSpec(List.of(step(0), step(100)), 2, List.of());

        List<SpawnStep> exp = w.expand();

        assertEquals(4, exp.size());

        assertEquals(0, exp.get(0).delayTicks());

        assertEquals(100, exp.get(1).delayTicks());

        // 第二轮首步 delay 0：紧接上一轮末时刻

        assertEquals(100, exp.get(2).delayTicks());

        assertEquals(200, exp.get(3).delayTicks());

    }



    @Test

    void expandChainsRelativeDelaysAcrossRepeats() {

        // s1_crp_many 语义：步前 delay 0/6s × repeat 3 → 0,6,6,12,12,18（秒×20 在 RunPlanner 里换算）

        WaveSpec w = new WaveSpec(List.of(step(0), step(120)), 3, List.of());

        List<SpawnStep> exp = w.expand();

        assertEquals(6, exp.size());

        assertEquals(0, exp.get(0).delayTicks());

        assertEquals(120, exp.get(1).delayTicks());

        assertEquals(120, exp.get(2).delayTicks());

        assertEquals(240, exp.get(3).delayTicks());

        assertEquals(240, exp.get(4).delayTicks());

        assertEquals(360, exp.get(5).delayTicks());

    }



    @Test
    void repeatClampedToOne() {
        WaveSpec w = new WaveSpec(List.of(step(0)), 0, List.of());
        assertEquals(1, w.expand().size());
    }

    @Test
    void expandPreservesPassengers() {
        PassengerSpawn child = new PassengerSpawn(
                EntityType.SKELETON, 1.0, 1.0, 1.0, 1.0, List.of(), List.of());
        SpawnStep mount = new SpawnStep(
                new Vec3(0, 64, 0), EntityType.STRIDER, 1,
                1.0, 1.0, 1.0, 1.0, 0, List.of(), List.of(), List.of(child));
        WaveSpec w = new WaveSpec(List.of(mount), 1, List.of());
        List<SpawnStep> exp = w.expand();
        assertEquals(1, exp.size());
        assertEquals(1, exp.get(0).passengers().size());
        assertEquals(EntityType.SKELETON, exp.get(0).passengers().get(0).type());
    }

    @Test
    void expandPreservesPassengersAcrossRepeat() {
        PassengerSpawn child = new PassengerSpawn(
                EntityType.ZOMBIE, 1.0, 1.0, 1.0, 1.0, List.of(), List.of());
        SpawnStep mount = new SpawnStep(
                new Vec3(0, 64, 0), EntityType.ZOMBIE_HORSE, 1,
                1.0, 1.0, 1.0, 1.0, 0, List.of(), List.of(), List.of(child));
        WaveSpec w = new WaveSpec(List.of(mount), 2, List.of());
        List<SpawnStep> exp = w.expand();
        assertEquals(2, exp.size());
        assertFalse(exp.get(0).passengers().isEmpty());
        assertFalse(exp.get(1).passengers().isEmpty());
    }

}