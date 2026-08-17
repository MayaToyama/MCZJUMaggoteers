package io.mczju.maggoteers.effect;

import java.util.Locale;
import java.util.Optional;

/** Validation for weapon use_ability temporary / deferred steps (PlayerState + expiry). */
public final class WeaponTempEffect {

    private WeaponTempEffect() {}

    /**
     * @return empty if valid; otherwise a short reason for load-time skip
     */
    public static Optional<String> validate(Effect effect, EffectContext params,
                                            Trigger expiryTrigger, int expiryCharges) {
        if (expiryTrigger == null) {
            return Optional.of("requires expiry");
        }
        if (expiryCharges != -1 && expiryCharges < 1) {
            return Optional.of("invalid expiry.charges");
        }
        if (params == null) {
            return Optional.of("missing params");
        }
        if (effect == Effect.ADD_ATTRIBUTE) {
            return validateAddAttribute(params);
        }
        if (effect == Effect.ADD_POTION) {
            return validateExpiryPotion(params);
        }
        if (effect == Effect.AURA || effect == Effect.BOUND_EQUIP) {
            return Optional.of("unsupported effect for weapon ability");
        }
        // Deferred magic (BUFF_AREA, DISABLE_AI, DAMAGE_*, HEAL_AREA, ...):
        // combat expiry whitelist enforced in ItemAbilityRegistry.
        return Optional.empty();
    }

    private static Optional<String> validateAddAttribute(EffectContext params) {
        String attrName = params.get(EffectKeys.ATTR_NAME);
        if ((attrName == null || attrName.isBlank()) && params.get(EffectKeys.ATTR) == null) {
            return Optional.of("missing attr");
        }
        if (!params.has(EffectKeys.VALUE)) {
            return Optional.of("missing value");
        }
        String op = params.getOrDefault(EffectKeys.OP, "FLAT");
        if (op == null) {
            return Optional.of("invalid op");
        }
        String opU = op.toUpperCase(Locale.ROOT);
        if (!"FLAT".equals(opU) && !"PERCENT".equals(opU)) {
            return Optional.of("invalid op");
        }
        return Optional.empty();
    }

    private static Optional<String> validateExpiryPotion(EffectContext params) {
        int dur = params.getOrDefault(EffectKeys.DURATION_TICKS, 0);
        if (dur > 0) {
            return Optional.of("expiry ADD_POTION must use duration_ticks 0 or omit");
        }
        if (params.get(EffectKeys.POTION) == null) {
            return Optional.of("missing potion");
        }
        return Optional.empty();
    }
}
