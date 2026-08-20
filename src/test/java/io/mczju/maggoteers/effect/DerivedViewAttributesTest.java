package io.mczju.maggoteers.effect;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DerivedViewAttributesTest {

    @Test
    void playerStripIncludesEntityInteractionRange() {
        assertTrue(DerivedViewAttributes.PLAYER_STRIP_KEYS.contains("entity_interaction_range"),
                "ENTITY_INTERACTION_RANGE must be stripped on resync or reach modifiers stack unbound");
    }

    @Test
    void playerStripIncludesKnockbackAndArmor() {
        assertTrue(DerivedViewAttributes.PLAYER_STRIP_KEYS.contains("knockback_resistance"));
        assertTrue(DerivedViewAttributes.PLAYER_STRIP_KEYS.contains("armor"));
        assertTrue(DerivedViewAttributes.PLAYER_STRIP_KEYS.contains("armor_toughness"));
    }
}
