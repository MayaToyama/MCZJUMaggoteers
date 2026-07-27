package io.mczju.maggoteers.effect;

import io.mczju.maggoteers.item.fx.MagicUseFx;

/** Parsed {@code use_ability} block for one weapon item id. */
public record ItemAbility(
        String itemId,
        int cooldownSec,
        Effect effect,
        EffectContext params,
        MagicUseFx fx
) {
}