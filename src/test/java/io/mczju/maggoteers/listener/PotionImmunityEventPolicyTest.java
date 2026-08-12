package io.mczju.maggoteers.listener;

import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PotionImmunityEventPolicyTest {

    @Test
    void shouldCancelAddedAndChangedOnly() {
        assertTrue(PotionImmunityEventPolicy.shouldCancel(EntityPotionEffectEvent.Action.ADDED));
        assertTrue(PotionImmunityEventPolicy.shouldCancel(EntityPotionEffectEvent.Action.CHANGED));
        assertFalse(PotionImmunityEventPolicy.shouldCancel(EntityPotionEffectEvent.Action.REMOVED));
        assertFalse(PotionImmunityEventPolicy.shouldCancel(EntityPotionEffectEvent.Action.CLEARED));
    }
}