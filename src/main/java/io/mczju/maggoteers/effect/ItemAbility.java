package io.mczju.maggoteers.effect;

import io.mczju.maggoteers.item.fx.MagicUseFx;
import org.bukkit.potion.PotionEffectType;

import java.util.List;

/** Parsed {@code use_ability} block for one weapon item id (one or more steps). */
public record ItemAbility(
        String itemId,
        int cooldownSec,
        boolean consume,
        MagicUseFx fx,
        MagicUseFx empowerFx,
        List<BuffPotionSpec> selfPotions,
        List<PotionEffectType> selfClearPotions,
        List<AbilityStep> steps
) {
    public ItemAbility {
        selfPotions = selfPotions == null ? List.of() : List.copyOf(selfPotions);
        selfClearPotions = selfClearPotions == null ? List.of() : List.copyOf(selfClearPotions);
        steps = steps == null ? List.of() : List.copyOf(steps);
    }

    /** All steps have expiry (next-hit / deferred pack). */
    public boolean isDeferredOnly() {
        if (steps.isEmpty()) {
            return false;
        }
        for (AbilityStep step : steps) {
            if (!step.isDeferred()) {
                return false;
            }
        }
        return true;
    }
}
