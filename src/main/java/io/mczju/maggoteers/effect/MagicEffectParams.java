package io.mczju.maggoteers.effect;

import java.util.Locale;
import java.util.Optional;

/** Defaults and load-time validation for configurable magic effect params. */
public final class MagicEffectParams {

    private MagicEffectParams() {}

    public static EffectContext withDefaults(Effect effect, EffectContext raw) {
        EffectContext p = raw == null ? new EffectContext() : raw.copy();
        if (effect == null) {
            return p;
        }
        switch (effect) {
            case DAMAGE_AREA, DAMAGE_BEAM -> {
                if (!p.has(EffectKeys.TARGETS)) {
                    p.put(EffectKeys.TARGETS, AuraParams.TARGET_ENEMIES);
                }
                if (!p.has(EffectKeys.ENEMY_SCOPE)) {
                    p.put(EffectKeys.ENEMY_SCOPE, AuraParams.ENEMY_TRACKED);
                }
            }
            case HEAL_AREA -> {
                if (!p.has(EffectKeys.TARGETS)) {
                    p.put(EffectKeys.TARGETS, AuraParams.TARGET_ALLIES);
                }
                if (!p.has(EffectKeys.INCLUDE_SELF)) {
                    p.put(EffectKeys.INCLUDE_SELF, true);
                }
            }
            default -> { }
        }
        return p;
    }

    /**
     * HEAL_AREA load validation (spec section 5.4).
     *
     * @return empty if acceptable; otherwise a short reason for warn+skip
     */
    public static Optional<String> validateHealAreaTargets(EffectContext params) {
        if (params == null) {
            return Optional.empty();
        }
        String targets = params.get(EffectKeys.TARGETS);
        if (targets == null || targets.isBlank()) {
            return Optional.empty();
        }
        String t = targets.trim().toLowerCase(Locale.ROOT);
        if (AuraParams.TARGET_ALLIES.equals(t)) {
            return Optional.empty();
        }
        if (AuraParams.TARGET_ENEMIES.equals(t)) {
            return Optional.of("targets=enemies cannot heal");
        }
        if (AuraParams.TARGET_BOTH.equals(t)) {
            if (Boolean.TRUE.equals(params.get(EffectKeys.INCLUDE_SELF))) {
                return Optional.empty();
            }
            return Optional.of("targets=both requires include_self: true");
        }
        return Optional.of("unknown targets: " + t);
    }
}