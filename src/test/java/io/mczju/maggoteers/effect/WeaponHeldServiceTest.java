package io.mczju.maggoteers.effect;

import io.mczju.maggoteers.state.PlayerState;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class WeaponHeldServiceTest {

    private static PlayerState stateWith(List<PlayerEffect> effects) {
        PlayerState st = new PlayerState(UUID.randomUUID(), 2);
        st.effects().addAll(effects);
        return st;
    }

    @Test
    void removeByPrefixDeletesGrantOrphans() {
        PlayerState st = stateWith(new ArrayList<>(List.of(
                new PlayerEffect("held:maggoteers:sword:0", Effect.ADD_ATTRIBUTE,
                        new EffectContext(), Trigger.ON_KILL, null, 0, 0, null, Stack.IGNORE, 0, 0),
                new PlayerEffect("held:maggoteers:sword:0:grant", Effect.ADD_ATTRIBUTE,
                        new EffectContext(), null, null, 0, 0, null, Stack.IGNORE, 0, 0),
                new PlayerEffect("held:maggoteers:other:0", Effect.ADD_POTION,
                        new EffectContext(), null, null, 0, 0, null, Stack.IGNORE, 0, 0)
        )));
        WeaponHeldService.removeHeldForItem(st, "maggoteers:sword");
        assertEquals(1, st.effects().size());
        assertEquals("held:maggoteers:other:0", st.effects().get(0).id());
    }

    @Test
    void parseHeldItemIdFromTemplateAndGrant() {
        assertEquals("maggoteers:sword",
                WeaponHeldService.parseHeldItemId("held:maggoteers:sword:0"));
        assertEquals("maggoteers:sword",
                WeaponHeldService.parseHeldItemId("held:maggoteers:sword:0:grant"));
    }

    @Test
    void findMountedItemIdReturnsFirstHeldWeapon() {
        List<PlayerEffect> fx = List.of(
                new PlayerEffect("reward:dmg10", Effect.ADD_ATTRIBUTE, new EffectContext(),
                        null, null, 0, 0, null, Stack.ADD, 0, 0),
                new PlayerEffect("held:maggoteers:blade:1", Effect.ADD_POTION, new EffectContext(),
                        null, null, 0, 0, null, Stack.IGNORE, 0, 0)
        );
        assertEquals("maggoteers:blade", WeaponHeldService.findMountedItemId(fx));
    }

    @Test
    void toPlayerEffectUsesHeldIdPrefix() {
        WeaponHeldEntry entry = new WeaponHeldEntry(
                Effect.ADD_ATTRIBUTE,
                new EffectContext().put(EffectKeys.ATTR_NAME, "ATTACK_DAMAGE"),
                null,
                Stack.ADD);
        PlayerEffect pe = WeaponHeldService.toPlayerEffect("maggoteers:test_blade", 2, entry);
        assertEquals("held:maggoteers:test_blade:2", pe.id());
        assertNull(pe.fireTrigger());
    }

    @Test
    void parseHeldItemIdNullForNonHeld() {
        assertNull(WeaponHeldService.parseHeldItemId("class_vanguard_token"));
        assertNull(WeaponHeldService.parseHeldItemId(null));
    }
}
