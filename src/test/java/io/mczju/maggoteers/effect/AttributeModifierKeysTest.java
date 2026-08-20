package io.mczju.maggoteers.effect;

import org.bukkit.attribute.AttributeModifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void managedEffectPathExcludesItemAndAuraModifiers() {
        assertTrue(AttributeModifierKeys.isManagedEffectPath("effect/ability_maggoteers_herafinger_0_1_23"));
        assertFalse(AttributeModifierKeys.isManagedEffectPath("herafinger_attack_damage"));
        assertFalse(AttributeModifierKeys.isManagedEffectPath("aura/source/effect/attribute"));
    }

    @Test
    void percentMapsToAdditiveScalarNotMultiplicative() {
        assertEquals(AttributeModifier.Operation.ADD_SCALAR,
                AttributeModifierKeys.attributeOperation("PERCENT"));
        assertEquals(AttributeModifier.Operation.ADD_SCALAR,
                AttributeModifierKeys.attributeOperation("percent"));
        assertEquals(AttributeModifier.Operation.ADD_NUMBER,
                AttributeModifierKeys.attributeOperation("FLAT"));
        assertEquals(AttributeModifier.Operation.ADD_NUMBER,
                AttributeModifierKeys.attributeOperation(null));
    }

    @Test
    void multiplyMapsToMultiplicativeScalar() {
        assertEquals(AttributeModifier.Operation.MULTIPLY_SCALAR_1,
                AttributeModifierKeys.attributeOperation("MULTIPLY"));
        assertEquals(AttributeModifier.Operation.MULTIPLY_SCALAR_1,
                AttributeModifierKeys.attributeOperation("multiply"));
    }

    @Test
    void isValidAttributeOpWhitelist() {
        assertTrue(AttributeModifierKeys.isValidAttributeOp("FLAT"));
        assertTrue(AttributeModifierKeys.isValidAttributeOp("PERCENT"));
        assertTrue(AttributeModifierKeys.isValidAttributeOp("MULTIPLY"));
        assertTrue(AttributeModifierKeys.isValidAttributeOp("percent"));
        assertFalse(AttributeModifierKeys.isValidAttributeOp("REVOKE_GRANTS"));
        assertFalse(AttributeModifierKeys.isValidAttributeOp("BOGUS"));
        assertFalse(AttributeModifierKeys.isValidAttributeOp(null));
    }
}