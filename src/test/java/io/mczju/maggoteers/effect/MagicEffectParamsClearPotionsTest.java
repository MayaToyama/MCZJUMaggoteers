package io.mczju.maggoteers.effect;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MagicEffectParamsClearPotionsTest {

    @Test
    void clearOnlyBuffAreaAccepted() {
        EffectContext p = new EffectContext();
        p.put(EffectKeys.TARGETS, BuffAreaTargets.TARGET_SELF);
        List<org.bukkit.potion.PotionEffectType> clears = new ArrayList<>();
        clears.add(null); // non-empty sentinel; registry not required for emptiness check
        p.put(EffectKeys.CLEAR_POTIONS, clears);
        assertTrue(MagicEffectParams.validateBuffArea(p, Trigger.ON_DAMAGE_DEALT, true).isEmpty());
    }

    @Test
    void bothEmptyRejected() {
        EffectContext p = new EffectContext();
        p.put(EffectKeys.TARGETS, BuffAreaTargets.TARGET_SELF);
        assertTrue(MagicEffectParams.validateBuffArea(p, Trigger.ON_DAMAGE_DEALT, true)
                .orElse("").contains("potions"));
    }
}