package io.mczju.maggoteers.reward;

import io.mczju.maggoteers.effect.Effect;
import io.mczju.maggoteers.effect.EffectContext;
import io.mczju.maggoteers.effect.Stack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UpgradeLevelCapsTest {

    @BeforeEach
    void resetDefault() {
        UpgradeLevelCaps.resetDefaultCap(4);
    }

    private static RewardOption upgradeOpt(int optionMax) {
        return new RewardOption(
                "resist", "", "", RewardOption.Category.STAT, null, 1,
                null, Effect.ADD_POTION, new EffectContext(), Stack.UPGRADE_LEVEL,
                null, 0, false, 0, true, optionMax, List.of());
    }

    @Test
    void optionOverrideBeatsPool() {
        RewardPool pool = new RewardPool("act1_weak", 1, "normal", 2, List.of());
        RewardOption opt = upgradeOpt(3);
        assertEquals(3, UpgradeLevelCaps.resolve(pool, opt));
    }

    @Test
    void poolCapWhenOptionUnset() {
        RewardPool pool = new RewardPool("act1_weak", 1, "normal", 2, List.of());
        RewardOption opt = upgradeOpt(0);
        assertEquals(2, UpgradeLevelCaps.resolve(pool, opt));
    }

    @Test
    void defaultWhenNeitherSet() {
        RewardOption opt = upgradeOpt(0);
        assertEquals(4, UpgradeLevelCaps.resolve(null, opt));
    }
}