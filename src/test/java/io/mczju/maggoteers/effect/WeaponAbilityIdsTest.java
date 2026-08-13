package io.mczju.maggoteers.effect;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class WeaponAbilityIdsTest {
    @Test
    void packMemberExactAndStepIndex() {
        assertEquals("ability:maggoteers:foo", WeaponAbilityIds.effectId("maggoteers:foo"));
        assertEquals("ability:maggoteers:foo:0", WeaponAbilityIds.effectId("maggoteers:foo", 0));
        assertTrue(WeaponAbilityIds.isPackMember("ability:maggoteers:foo", "maggoteers:foo"));
        assertTrue(WeaponAbilityIds.isPackMember("ability:maggoteers:foo:0", "maggoteers:foo"));
        assertTrue(WeaponAbilityIds.isPackMember("ability:maggoteers:foo:1", "maggoteers:foo"));
    }

    @Test
    void packMemberRejectsPrefixCollision() {
        assertFalse(WeaponAbilityIds.isPackMember("ability:maggoteers:foo_bar:0", "maggoteers:foo"));
        assertFalse(WeaponAbilityIds.isPackMember("ability:maggoteers:foobar", "maggoteers:foo"));
        assertFalse(WeaponAbilityIds.isPackMember("ability:maggoteers:fooX", "maggoteers:foo"));
    }

    @Test
    void itemIdOfRoundTrip() {
        assertEquals(Optional.of("maggoteers:foo"), WeaponAbilityIds.itemIdOf("ability:maggoteers:foo"));
        assertEquals(Optional.of("maggoteers:foo"), WeaponAbilityIds.itemIdOf("ability:maggoteers:foo:2"));
        assertTrue(WeaponAbilityIds.itemIdOf("held:x").isEmpty());
    }
}
