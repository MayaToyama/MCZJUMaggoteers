package io.mczju.maggoteers.reward;

import io.mczju.maggoteers.effect.Effect;
import io.mczju.maggoteers.effect.EffectContext;
import io.mczju.maggoteers.effect.PlayerEffect;
import io.mczju.maggoteers.effect.Stack;
import io.mczju.maggoteers.state.PlayerState;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class RewardDrawVisibilityTest {

    private static PlayerState emptyState() {
        return new PlayerState(UUID.randomUUID(), 2);
    }

    private static RewardOption supply(String id) {
        return new RewardOption(id, "", "", RewardOption.Category.SUPPLY, "maggoteers:x", 1,
                null, null, null, Stack.ADD, null, 0, false, 0, false, 0, List.of());
    }

    private static RewardOption weapon(String id) {
        return new RewardOption(id, "", "", RewardOption.Category.WEAPON, "maggoteers:w", 1,
                null, null, null, Stack.ADD, null, 0, false, 0, false, 0, List.of());
    }

    private static RewardOption statAdd(String id) {
        return new RewardOption(id, "", "", RewardOption.Category.STAT, null, 1,
                null, Effect.ADD_ATTRIBUTE, new EffectContext(), Stack.ADD,
                null, 0, false, 0, true, 0, List.of());
    }

    private static RewardOption statUpgrade(String id) {
        return new RewardOption(id, "", "", RewardOption.Category.STAT, null, 1,
                null, Effect.ADD_POTION, new EffectContext(), Stack.UPGRADE_LEVEL,
                null, 0, false, 0, true, 0, List.of());
    }

    private static PlayerState stateWithEffect(String id, Stack stack, int level, int upgradeMax) {
        PlayerState ps = emptyState();
        PlayerEffect pe = new PlayerEffect(id, Effect.ADD_POTION, new EffectContext(),
                null, null, 0, 0, null, stack, upgradeMax, 0);
        pe.setLevel(level);
        ps.effects().add(pe);
        return ps;
    }

    private static RewardPool poolWithCap(int cap) {
        return new RewardPool("act1_weak", 1, "normal", cap, List.of());
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
    void upgradeVisibleUntilPoolCap() {
        RewardOption up = statUpgrade("a1s_resist");
        RewardPool weakPool = poolWithCap(2);
        PlayerState ps = stateWithEffect("a1s_resist", Stack.UPGRADE_LEVEL, 1, 4);
        assertTrue(RewardDrawVisibility.isVisible(up, ps, Set.of(), weakPool));
        ps = stateWithEffect("a1s_resist", Stack.UPGRADE_LEVEL, 2, 4);
        assertFalse(RewardDrawVisibility.isVisible(up, ps, Set.of(), weakPool));

        RewardPool strongPool = poolWithCap(3);
        ps = stateWithEffect("a1s_resist", Stack.UPGRADE_LEVEL, 2, 4);
        assertTrue(RewardDrawVisibility.isVisible(up, ps, Set.of(), strongPool));
    }

    @Test
    void sameIdHiddenAcrossPoolsOnceOwnedNonUpgrade() {
        PlayerState ps = stateWithEffect("a1w_atk25", Stack.ADD, 1, 0);
        assertFalse(RewardDrawVisibility.isVisible(statAdd("a1w_atk25"), ps, Set.of(), poolWithCap(0)));
    }

    @Test
    void upgradeSameIdVisibleInHigherCapPoolOnly() {
        RewardOption up = statUpgrade("a1w_str1");
        PlayerState ps = stateWithEffect("a1w_str1", Stack.UPGRADE_LEVEL, 1, 1);
        assertFalse(RewardDrawVisibility.isVisible(up, ps, Set.of(), poolWithCap(1)));
        assertTrue(RewardDrawVisibility.isVisible(up, ps, Set.of(), poolWithCap(2)));
    }
}