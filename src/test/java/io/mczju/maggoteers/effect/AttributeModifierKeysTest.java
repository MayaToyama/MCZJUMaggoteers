package io.mczju.maggoteers.effect;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class AttributeModifierKeysTest {

    @Test
    void sanitizeReplacesColonsInAbilityIds() {
        String raw = "ability:maggoteers:class_vanguard_axe_1_23";
        String path = AttributeModifierKeys.sanitizeKeyPath(raw);
        assertFalse(path.contains(":"));
        assertEquals("ability_maggoteers_class_vanguard_axe_1_23", path);
    }

    @Test
    void sanitizeHeldPrefix() {
        assertEquals("held_maggoteers_wedge_0",
                AttributeModifierKeys.sanitizeKeyPath("held:maggoteers:wedge:0"));
    }
}