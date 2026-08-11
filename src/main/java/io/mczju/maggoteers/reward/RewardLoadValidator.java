package io.mczju.maggoteers.reward;

import io.mczju.maggoteers.effect.AuraParams;
import io.mczju.maggoteers.effect.BoundEquipParams;
import io.mczju.maggoteers.effect.BuffAreaTargets;
import io.mczju.maggoteers.effect.Effect;
import io.mczju.maggoteers.effect.EffectContext;
import io.mczju.maggoteers.effect.EffectKeys;
import io.mczju.maggoteers.effect.GrantItemParams;
import io.mczju.maggoteers.effect.MagicEffectParams;
import io.mczju.maggoteers.effect.Stack;
import io.mczju.maggoteers.effect.SummonParamsParser;
import io.mczju.maggoteers.effect.Trigger;
import io.mczju.maggoteers.effect.TriggeredGrantAttribute;
import io.mczju.maggoteers.effect.VirtualStats;
import io.mczju.maggoteers.item.ItemService;
import org.bukkit.attribute.Attribute;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;

/** Load-time validation for {@code rewards.yml} STAT options (config magic abilities P3). */
public final class RewardLoadValidator {

    private static final Logger LOG = Logger.getLogger("Maggoteers");

    private static final Set<Trigger> ALLOWED_FIRE = Set.of(
            Trigger.ON_KILL,
            Trigger.ON_DAMAGE_DEALT,
            Trigger.ON_DAMAGE_TAKEN,
            Trigger.ON_DEATH,
            Trigger.ON_WAVE_CLEAR,
            Trigger.ON_ACT_ENTER
    );

    private static final Set<Trigger> GRANT_SUMMON_TRIGGERS = Set.of(
            Trigger.ON_WAVE_CLEAR,
            Trigger.ON_ACT_ENTER,
            Trigger.ON_KILL,
            Trigger.ON_DAMAGE_DEALT,
            Trigger.ON_DAMAGE_TAKEN,
            Trigger.ON_DEATH
    );

    private RewardLoadValidator() {}

    public record ValidationResult(Optional<String> skipReason, EffectContext normalizedParams) {}

    /**
     * @return empty if the option may be loaded; otherwise a reason to warn+skip
     */
    public static Optional<String> validateStatOption(RewardOption opt) {
        if (opt == null || opt.category() != RewardOption.Category.STAT) {
            if (opt != null && opt.isBundle()) {
                return validateBundleOption(opt);
            }
            return Optional.empty();
        }
        Trigger fire = opt.trigger();
        if (opt.effect() == Effect.GRANT_ITEM || opt.effect() == Effect.SUMMON) {
            return validateGrantSummon(opt);
        }
        if (fire != null && !ALLOWED_FIRE.contains(fire)) {
            return Optional.of("forbidden fireTrigger: " + fire);
        }
        if (opt.expiryTrigger() == Trigger.ON_INTERACT) {
            return Optional.of("forbidden expiry.trigger: ON_INTERACT");
        }
        if (opt.effect() == Effect.ADD_ATTRIBUTE && opt.params() != null
                && TriggeredGrantAttribute.isRevoke(opt.params())) {
            return validateRevokeGrants(opt);
        }
        if (opt.effect() == Effect.AURA && (fire != null || opt.expiryTrigger() != null)) {
            return Optional.of("AURA reward must not have trigger or expiry");
        }
        if (opt.effect() == Effect.BOUND_EQUIP) {
            if (fire != null || opt.expiryTrigger() != null) {
                return Optional.of("BOUND_EQUIP reward must not have trigger or expiry");
            }
            if (opt.stack() != Stack.UPGRADE_LEVEL) {
                return Optional.of("BOUND_EQUIP requires stack UPGRADE_LEVEL");
            }
            int upgradeMax = opt.upgradeMax();
            if (upgradeMax <= 0) {
                return Optional.of("BOUND_EQUIP requires upgrade_max > 0");
            }
            Optional<String> paramsErr = BoundEquipParams.validate(opt.params(), upgradeMax)
                    .map(msg -> "BOUND_EQUIP " + msg);
            if (paramsErr.isPresent()) {
                return paramsErr;
            }
            if (io.mczju.maggoteers.MaggoteersPlugin.getInstance() != null && opt.params() != null) {
                java.util.Map<Integer, String> byLevel = opt.params().get(EffectKeys.ITEMS_BY_LEVEL);
                if (byLevel != null) {
                    for (String itemId : byLevel.values()) {
                        if (itemId != null && !itemId.isBlank() && !ItemService.hasItem(itemId)) {
                            return Optional.of("BOUND_EQUIP unknown item: " + itemId);
                        }
                    }
                }
            }
            return Optional.empty();
        }
        Optional<String> magic = validateMagicDamagePercent(opt);
        if (magic.isPresent()) {
            return magic;
        }
        if (opt.effect() == Effect.HEAL_AREA) {
            return MagicEffectParams.validateHealAreaTargets(opt.params())
                    .map(msg -> "HEAL_AREA " + msg);
        }
        if (opt.effect() == Effect.BUFF_AREA) {
            return validateAndNormalizeBuffArea(opt).skipReason();
        }
        if (opt.effect() == Effect.DISABLE_AI) {
            return validateAndNormalizeDisableAi(opt).skipReason();
        }
        return Optional.empty();
    }

    public static Optional<String> chestBoundEquipConflict(
            List<RewardOption> alreadyAccepted, RewardOption candidate) {
        if (candidate == null || candidate.effect() != Effect.BOUND_EQUIP) return Optional.empty();
        EffectContext params = candidate.params();
        String slot = params == null ? null : params.get(EffectKeys.SLOT);
        if (slot == null || !BoundEquipParams.SLOT_CHEST.equalsIgnoreCase(slot)) {
            return Optional.empty();
        }
        boolean exists = alreadyAccepted.stream().anyMatch(o ->
                o.effect() == Effect.BOUND_EQUIP
                        && o.params() != null
                        && BoundEquipParams.SLOT_CHEST.equalsIgnoreCase(
                                String.valueOf(o.params().get(EffectKeys.SLOT))));
        return exists
                ? Optional.of("duplicate BOUND_EQUIP slot CHEST: " + candidate.id())
                : Optional.empty();
    }

    private static Optional<String> validateGrantSummon(RewardOption opt) {
        Trigger fire = opt.trigger();
        if (fire == null) {
            return Optional.of("missing trigger");
        }
        if (!GRANT_SUMMON_TRIGGERS.contains(fire)) {
            return Optional.of("forbidden fireTrigger: " + fire);
        }
        EffectContext params = opt.params() == null ? new EffectContext() : opt.params();
        if (opt.effect() == Effect.GRANT_ITEM) {
            if (GrantItemParams.parse(params).isEmpty()) {
                return Optional.of("GRANT_ITEM missing item");
            }
            return Optional.empty();
        }
        Optional<String> err = SummonParamsParser.validate(params, fire, false);
        if (err.isPresent()) {
            return Optional.of("SUMMON " + err.get());
        }
        if (io.mczju.maggoteers.MaggoteersPlugin.getInstance() != null) {
            String entity = params.get(EffectKeys.ENTITY);
            if (SummonParamsParser.resolveEntity(entity).isEmpty()) {
                return Optional.of("SUMMON unknown entity: " + entity);
            }
        }
        return Optional.empty();
    }

    /** Validates BUFF_AREA and returns normalized params (mark_on coercion). */
    public static ValidationResult validateAndNormalizeBuffArea(RewardOption opt) {
        if (opt == null || opt.effect() != Effect.BUFF_AREA) {
            return new ValidationResult(Optional.empty(), opt == null ? null : opt.params());
        }
        EffectContext params = opt.params() == null ? new EffectContext() : opt.params().copy();
        params = MagicEffectParams.withDefaults(Effect.BUFF_AREA, params);

        Optional<String> general = MagicEffectParams.validateBuffArea(params, opt.trigger(), false);
        if (general.isPresent()) {
            return new ValidationResult(general, params);
        }

        Trigger fire = opt.trigger();
        String targets = params.get(EffectKeys.TARGETS);
        if (targets != null && BuffAreaTargets.TARGET_ATTACKER.equalsIgnoreCase(targets)) {
            if (fire == Trigger.ON_KILL || fire == Trigger.ON_DAMAGE_DEALT) {
                return new ValidationResult(
                        Optional.of("targets=attacker incompatible with " + fire), params);
            }
        }
        if (targets != null && BuffAreaTargets.TARGET_HIT_TARGET.equalsIgnoreCase(targets)) {
            if (fire == Trigger.ON_DAMAGE_TAKEN) {
                return new ValidationResult(
                        Optional.of("targets=hit_target incompatible with ON_DAMAGE_TAKEN"), params);
            }
            if (fire == Trigger.ON_KILL) {
                LOG.warning("rewards.yml 选项 " + opt.id()
                        + " ON_KILL + targets=hit_target：尸体通常无法上药，mark 仍可用");
            }
        }

        String markOn = params.get(EffectKeys.MARK_ON);
        if (markOn != null && !markOn.isBlank()) {
            String m = markOn.toLowerCase(Locale.ROOT);
            if ("attacker".equals(m) && (fire == Trigger.ON_KILL || fire == Trigger.ON_DAMAGE_DEALT)) {
                LOG.warning("rewards.yml 选项 " + opt.id()
                        + " mark_on=attacker 与 " + fire + " 不兼容，已强制为 none");
                params.put(EffectKeys.MARK_ON, "none");
            } else if ("hit_target".equals(m) && fire == Trigger.ON_DAMAGE_TAKEN) {
                LOG.warning("rewards.yml 选项 " + opt.id()
                        + " mark_on=hit_target 与 ON_DAMAGE_TAKEN 不兼容，已强制为 none");
                params.put(EffectKeys.MARK_ON, "none");
            } else if (!Set.of("none", "hit_target", "attacker").contains(m)) {
                LOG.warning("rewards.yml 选项 " + opt.id() + " 未知 mark_on=" + markOn + "，视为 none");
                params.put(EffectKeys.MARK_ON, "none");
            }
        }

        return new ValidationResult(Optional.empty(), params);
    }

    /** Validates DISABLE_AI and returns normalized params (mark_on coercion). */
    public static ValidationResult validateAndNormalizeDisableAi(RewardOption opt) {
        if (opt == null || opt.effect() != Effect.DISABLE_AI) {
            return new ValidationResult(Optional.empty(), opt == null ? null : opt.params());
        }
        EffectContext params = opt.params() == null ? new EffectContext() : opt.params().copy();
        params = MagicEffectParams.withDefaults(Effect.DISABLE_AI, params);

        Optional<String> general = MagicEffectParams.validateDisableAi(params, opt.trigger(), false);
        if (general.isPresent()) {
            return new ValidationResult(general, params);
        }

        Trigger fire = opt.trigger();
        String markOn = params.get(EffectKeys.MARK_ON);
        if (markOn != null && !markOn.isBlank()) {
            String m = markOn.trim().toLowerCase(Locale.ROOT);
            if ("attacker".equals(m) && fire != Trigger.ON_DAMAGE_TAKEN) {
                LOG.warning("rewards.yml 选项 " + opt.id()
                        + " mark_on=attacker 与 " + fire + " 不兼容，已强制为 none");
                params.put(EffectKeys.MARK_ON, "none");
            } else if ("hit_target".equals(m) && fire == Trigger.ON_DAMAGE_TAKEN) {
                LOG.warning("rewards.yml 选项 " + opt.id()
                        + " mark_on=hit_target 与 ON_DAMAGE_TAKEN 不兼容，已强制为 none");
                params.put(EffectKeys.MARK_ON, "none");
            } else if (!Set.of("none", "hit_target", "attacker").contains(m)) {
                LOG.warning("rewards.yml 选项 " + opt.id() + " 未知 mark_on=" + markOn + "，视为 none");
                params.put(EffectKeys.MARK_ON, "none");
            }
        }

        return new ValidationResult(Optional.empty(), params);
    }

    private static Optional<String> validateBundleOption(RewardOption opt) {
        if (opt.grants().isEmpty()) {
            return Optional.of("bundle grants empty");
        }
        for (RewardOption grant : opt.grants()) {
            if (grant.category() == RewardOption.Category.STAT) {
                Optional<String> err = validateStatOption(grant);
                if (err.isPresent()) return err;
            }
        }
        return validateBundleGrantReferences(opt);
    }

    private static Optional<String> validateRevokeGrants(RewardOption opt) {
        if (opt.trigger() == null) {
            return Optional.of("REVOKE_GRANTS requires trigger");
        }
        String sourceId = opt.params().get(EffectKeys.SOURCE_ID);
        if (sourceId == null || sourceId.isBlank()) {
            return Optional.of("REVOKE_GRANTS requires source_id");
        }
        if (opt.params().get(EffectKeys.ATTR) != null || opt.params().get(EffectKeys.ATTR_NAME) != null) {
            return Optional.of("REVOKE_GRANTS must not have attr");
        }
        if (opt.params().has(EffectKeys.VALUE)) {
            return Optional.of("REVOKE_GRANTS must not have value");
        }
        return Optional.empty();
    }

    private static Optional<String> validateBundleGrantReferences(RewardOption bundle) {
        java.util.Set<String> grantIds = new java.util.HashSet<>();
        for (RewardOption g : bundle.grants()) {
            grantIds.add(g.id());
        }
        for (RewardOption g : bundle.grants()) {
            if (g.effect() != Effect.ADD_ATTRIBUTE || g.params() == null) continue;
            if (!TriggeredGrantAttribute.isRevoke(g.params())) continue;
            String sid = g.params().get(EffectKeys.SOURCE_ID);
            if (sid != null && !sid.isBlank() && !grantIds.contains(sid)) {
                return Optional.of("REVOKE source_id not in bundle grants: " + sid);
            }
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
