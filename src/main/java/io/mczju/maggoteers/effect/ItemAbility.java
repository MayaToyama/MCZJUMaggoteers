package io.mczju.maggoteers.effect;

import io.mczju.maggoteers.item.fx.MagicUseFx;

import java.util.List;

/** Parsed {@code use_ability} block for one weapon item id. */
public record ItemAbility(
        String itemId,
        int cooldownSec,
        Effect effect,
        EffectContext params,
        MagicUseFx fx,
        boolean consume,
        Trigger expiryTrigger,
        int expiryCharges,
        Stack stack,
        List<BuffPotionSpec> selfPotions
) {
    public ItemAbility(String itemId, int cooldownSec, Effect effect, EffectContext params,
                       MagicUseFx fx, boolean consume) {
        this(itemId, cooldownSec, effect, params, fx, consume, null, 0, null, List.of());
    }

    public ItemAbility(String itemId, int cooldownSec, Effect effect, EffectContext params,
                       MagicUseFx fx, boolean consume, Trigger expiryTrigger, int expiryCharges,
                       Stack stack) {
        this(itemId, cooldownSec, effect, params, fx, consume, expiryTrigger, expiryCharges, stack, List.of());
    }
}
