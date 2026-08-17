package io.mczju.maggoteers.mob;

import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MountPassengerLimitsTest {

    @Test
    void camelFamilySeatLimitIsTwo() {
        assertEquals(2, MountPassengerLimits.directPassengerLimit(EntityType.CAMEL));
        assertEquals(2, MountPassengerLimits.directPassengerLimit(EntityType.CAMEL_HUSK));
    }

    @Test
    void horsesAndOthersSeatLimitIsOne() {
        assertEquals(1, MountPassengerLimits.directPassengerLimit(EntityType.HORSE));
        assertEquals(1, MountPassengerLimits.directPassengerLimit(EntityType.ZOMBIE_HORSE));
        assertEquals(1, MountPassengerLimits.directPassengerLimit(EntityType.STRIDER));
        assertEquals(1, MountPassengerLimits.directPassengerLimit(EntityType.ZOMBIE));
    }

    @Test
    void nullCarrierSeatLimitIsZero() {
        assertEquals(0, MountPassengerLimits.directPassengerLimit((LivingEntity) null));
    }
}
