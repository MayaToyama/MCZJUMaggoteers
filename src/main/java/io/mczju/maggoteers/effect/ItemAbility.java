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

    public AbilityStep primaryStep() {
        return steps.isEmpty() ? null : steps.get(0);
    }

    public Effect effect() {
        AbilityStep s = primaryStep();
        return s == null ? null : s.effect();
    }

    public EffectContext params() {
        AbilityStep s = primaryStep();
        return s == null ? null : s.params();
    }

    public Trigger expiryTrigger() {
        AbilityStep s = primaryStep();
        return s == null ? null : s.expiryTrigger();
    }

    public int expiryCharges() {
        AbilityStep s = primaryStep();
        return s == null ? 0 : s.expiryCharges();
    }

    public Stack stack() {
        AbilityStep s = primaryStep();
        return s == null ? null : s.stack();
    }

    public boolean isLegacySingleStep() {
        return steps.size() == 1;
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

    /** Compat factory for single-step abilities. */
    public static ItemAbility single(
            String itemId, int cooldownSec, Effect effect, EffectContext params,
            MagicUseFx fx, boolean consume, Trigger expiryTrigger, int expiryCharges,
            Stack stack, List<BuffPotionSpec> selfPotions,
            List<PotionEffectType> selfClear, MagicUseFx empowerFx) {
        AbilityStep step = new AbilityStep(effect, params, expiryTrigger, expiryCharges, stack);
        return new ItemAbility(itemId, cooldownSec, consume, fx, empowerFx,
                selfPotions, selfClear, List.of(step));
    }
}
