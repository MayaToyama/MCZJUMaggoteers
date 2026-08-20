package io.mczju.maggoteers.effect;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class WeaponAbilityIdsTest {
    @Test
    void packMemberExactAndStepIndex() {
        assertEquals("ability:maggoteers:foo", WeaponAbilityIds.effectId("maggoteers:foo"));
        assertEquals("ability:maggoteers:foo:0", WeaponAbilityIds.effectId("maggoteers:foo", 0));
    }

    @Test
    void itemIdOfRoundTrip() {
        assertEquals(Optional.of("maggoteers:foo"), WeaponAbilityIds.itemIdOf("ability:maggoteers:foo"));
        assertEquals(Optional.of("maggoteers:foo"), WeaponAbilityIds.itemIdOf("ability:maggoteers:foo:2"));
        assertTrue(WeaponAbilityIds.itemIdOf("held:x").isEmpty());
    }
}
