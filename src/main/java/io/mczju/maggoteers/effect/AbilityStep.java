package io.mczju.maggoteers.effect;

/** One parsed step inside use_ability (immediate or deferred). */
public record AbilityStep(
        Effect effect,
        EffectContext params,
        Trigger expiryTrigger,
        int expiryCharges,
        Stack stack
) {
    public boolean isDeferred() {
        return expiryTrigger != null;
    }
}
