package io.mczju.maggoteers.reward;

import io.mczju.maggoteers.effect.Effect;
import io.mczju.maggoteers.effect.EffectContext;
import io.mczju.maggoteers.effect.EffectKeys;
import io.mczju.maggoteers.effect.MagicEffectParams;
import io.mczju.maggoteers.effect.Trigger;
import io.mczju.maggoteers.effect.VirtualStats;
import org.bukkit.attribute.Attribute;

import java.util.Optional;
import java.util.Set;

/** Load-time validation for {@code rewards.yml} STAT options (config magic abilities P3). */
public final class RewardLoadValidator {

    private static final Set<Trigger> ALLOWED_FIRE = Set.of(
            Trigger.ON_KILL,
            Trigger.ON_DAMAGE_DEALT,
            Trigger.ON_DAMAGE_TAKEN
    );

    private RewardLoadValidator() {}

    /**
     * @return empty if the option may be loaded; otherwise a reason to warn+skip
     */
    public static Optional<String> validateStatOption(RewardOption opt) {
        if (opt == null || opt.category() != RewardOption.Category.STAT) {
            return Optional.empty();
        }
        Trigger fire = opt.trigger();
        if (fire != null && !ALLOWED_FIRE.contains(fire)) {
            return Optional.of("forbidden fireTrigger: " + fire);
        }
        if (opt.expiryTrigger() == Trigger.ON_INTERACT) {
            return Optional.of("forbidden expiry.trigger: ON_INTERACT");
        }
        if (opt.effect() == Effect.AURA && (fire != null || opt.expiryTrigger() != null)) {
            return Optional.of("AURA reward must not have trigger or expiry");
        }
        Optional<String> magic = validateMagicDamagePercent(opt);
        if (magic.isPresent()) {
            return magic;
        }
        if (opt.effect() == Effect.HEAL_AREA) {
            return MagicEffectParams.validateHealAreaTargets(opt.params())
                    .map(msg -> "HEAL_AREA " + msg);
        }
        return Optional.empty();
    }

    private static Optional<String> validateMagicDamagePercent(RewardOption opt) {
        if (opt.effect() != Effect.ADD_ATTRIBUTE || opt.params() == null) {
            return Optional.empty();
        }
        EffectContext params = opt.params();
        String attrName = params.get(EffectKeys.ATTR_NAME);
        if (attrName == null) {
            Attribute attr = params.get(EffectKeys.ATTR);
            if (attr != null) {
                attrName = attr.getKey().getKey().toUpperCase();
            }
        }
        if (!VirtualStats.isMagicDamage(attrName)) {
            return Optional.empty();
        }
        String op = params.getOrDefault(EffectKeys.OP, "FLAT");
        if (!"PERCENT".equalsIgnoreCase(op)) {
            return Optional.of("MAGIC_DAMAGE requires op: PERCENT");
        }
        return Optional.empty();
    }
}