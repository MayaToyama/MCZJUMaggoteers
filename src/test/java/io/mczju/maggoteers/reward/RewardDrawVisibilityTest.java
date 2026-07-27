package io.mczju.maggoteers.reward;

import io.mczju.maggoteers.effect.Effect;
import io.mczju.maggoteers.effect.EffectContext;
import io.mczju.maggoteers.effect.PlayerEffect;
import io.mczju.maggoteers.effect.Stack;
import io.mczju.maggoteers.state.PlayerState;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class RewardDrawVisibilityTest {

    private static PlayerState emptyState() {
        return new PlayerState(UUID.randomUUID(), 2);
    }

    private static RewardOption supply(String id) {
        return new RewardOption(id, "", "", RewardOption.Category.SUPPLY, "maggoteers:x", 1,
                null, null, null, Stack.ADD, null, 0, false, 0, false);
    }

    private static RewardOption weapon(String id) {
        return new RewardOption(id, "", "", RewardOption.Category.WEAPON, "maggoteers:w", 1,
                null, null, null, Stack.ADD, null, 0, false, 0, false);
    }

    private static RewardOption statAdd(String id) {
        return new RewardOption(id, "", "", RewardOption.Category.STAT, null, 1,
                null, Effect.ADD_ATTRIBUTE, new EffectContext(), Stack.ADD,
                null, 0, false, 0, true);
    }

    private static RewardOption statUpgrade(String id) {
        return new RewardOption(id, "", "", RewardOption.Category.STAT, null, 1,
                null, Effect.ADD_POTION, new EffectContext(), Stack.UPGRADE_LEVEL,
                null, 0, false, 0, true);
    }

    private static PlayerState stateWithEffect(String id, Stack stack, int level, int upgradeMax) {
        PlayerState ps = emptyState();
        PlayerEffect pe = new PlayerEffect(id, Effect.ADD_POTION, new EffectContext(),
                null, null, 0, 0, null, stack, upgradeMax, 0);
        pe.setLevel(level);
        ps.effects().add(pe);
        return ps;
    }

    @Test
    void supplyAlwaysVisible() {
        assertTrue(RewardDrawVisibility.isVisible(supply("s1"), emptyState(), Set.of("s1")));
    }

    @Test
    void weaponHiddenIfAcquired() {
        assertFalse(RewardDrawVisibility.isVisible(weapon("w1"), emptyState(), Set.of("w1")));
    }

    @Test
    void statHiddenIfOwned() {
        PlayerState ps = stateWithEffect("a1s_magic_dmg", Stack.ADD, 1, 0);
        assertFalse(RewardDrawVisibility.isVisible(statAdd("a1s_magic_dmg"), ps, Set.of()));
    }

    @Test
    void upgradeVisibleUntilMax() {
        RewardOption up = statUpgrade("a1s_resist");
        PlayerState ps = stateWithEffect("a1s_resist", Stack.UPGRADE_LEVEL, 2, 4);
        assertTrue(RewardDrawVisibility.isVisible(up, ps, Set.of()));
        ps = stateWithEffect("a1s_resist", Stack.UPGRADE_LEVEL, 4, 4);
        assertFalse(RewardDrawVisibility.isVisible(up, ps, Set.of()));
    }
}
