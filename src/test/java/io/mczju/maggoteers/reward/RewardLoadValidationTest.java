package io.mczju.maggoteers.reward;

import io.mczju.maggoteers.effect.AuraParams;
import io.mczju.maggoteers.effect.BuffAreaTargets;
import io.mczju.maggoteers.effect.BuffPotionSpec;
import io.mczju.maggoteers.effect.Effect;
import io.mczju.maggoteers.effect.EffectContext;
import io.mczju.maggoteers.effect.EffectKeys;
import io.mczju.maggoteers.effect.Stack;
import io.mczju.maggoteers.effect.Trigger;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RewardLoadValidationTest {

    @Test
    void rejectsForbiddenFireTrigger() {
        RewardOption opt = stat(Effect.DAMAGE_AREA, Trigger.ON_TICK_1S, null, 0, null);
        assertTrue(RewardLoadValidator.validateStatOption(opt).orElse("").contains("forbidden fireTrigger"));
    }

    @Test
    void rejectsAuraWithTrigger() {
        EffectContext params = auraParams();
        RewardOption opt = stat(Effect.AURA, Trigger.ON_KILL, null, 0, params);
        assertTrue(RewardLoadValidator.validateStatOption(opt).isPresent());
    }

    @Test
    void rejectsMagicDamageFlat() {
        EffectContext params = new EffectContext();
        params.put(EffectKeys.ATTR_NAME, "MAGIC_DAMAGE");
        params.put(EffectKeys.OP, "FLAT");
        params.put(EffectKeys.VALUE, 0.10);
        RewardOption opt = stat(Effect.ADD_ATTRIBUTE, null, null, 0, params);
        assertTrue(RewardLoadValidator.validateStatOption(opt).orElse("").contains("PERCENT"));
    }

    @Test
    void rejectsUnknownAttributeOp() {
        EffectContext params = new EffectContext();
        params.put(EffectKeys.ATTR_NAME, "ATTACK_DAMAGE");
        params.put(EffectKeys.OP, "BOGUS");
        params.put(EffectKeys.VALUE, 0.10);
        RewardOption opt = stat(Effect.ADD_ATTRIBUTE, null, null, 0, params);
        assertTrue(RewardLoadValidator.validateStatOption(opt).orElse("").contains("unknown op"));
    }

    @Test
    void acceptsMultiplyOp() {
        EffectContext params = new EffectContext();
        params.put(EffectKeys.ATTR_NAME, "ATTACK_DAMAGE");
        params.put(EffectKeys.OP, "MULTIPLY");
        params.put(EffectKeys.VALUE, 0.10);
        RewardOption opt = stat(Effect.ADD_ATTRIBUTE, null, null, 0, params);
        assertTrue(RewardLoadValidator.validateStatOption(opt).isEmpty());
    }

    @Test
    void rejectsHealAreaEnemies() {
        EffectContext params = new EffectContext();
        params.put(EffectKeys.TARGETS, AuraParams.TARGET_ENEMIES);
        RewardOption opt = stat(Effect.HEAL_AREA, null, null, 0, params);
        Optional<String> err = RewardLoadValidator.validateStatOption(opt);
        assertTrue(err.isPresent());
        assertTrue(err.get().contains("HEAL_AREA"));
    }

    @Test
    void acceptsAllowedFireTrigger() {
        RewardOption opt = stat(Effect.DAMAGE_AREA, Trigger.ON_KILL, null, 0, new EffectContext());
        assertTrue(RewardLoadValidator.validateStatOption(opt).isEmpty());
    }

    @Test
    void rejectsHitTargetOnDamageTaken() {
        EffectContext params = buffAreaParams("hit_target");
        RewardOption opt = stat(Effect.BUFF_AREA, Trigger.ON_DAMAGE_TAKEN, params);
        assertTrue(RewardLoadValidator.validateStatOption(opt).isPresent());
    }

    @Test
    void coercesMarkAttackerOnKill() {
        EffectContext params = buffAreaParams("allies");
        params.put(EffectKeys.MARK_ON, "attacker");
        RewardOption opt = stat(Effect.BUFF_AREA, Trigger.ON_KILL, params);
        var norm = RewardLoadValidator.validateAndNormalizeBuffArea(opt);
        assertTrue(norm.skipReason().isEmpty());
        assertEquals("none", norm.normalizedParams().get(EffectKeys.MARK_ON));
    }

    @Test
    void requiresBuffAreaFireTrigger() {
        RewardOption opt = stat(Effect.BUFF_AREA, null, buffAreaParams("allies"));
        assertTrue(RewardLoadValidator.validateStatOption(opt).orElse("").contains("fireTrigger"));
    }

    @Test
    void disableAiOnKillHitTargetSkipped() {
        EffectContext params = disableAiParams(BuffAreaTargets.TARGET_HIT_TARGET);
        RewardOption opt = stat(Effect.DISABLE_AI, Trigger.ON_KILL, params);
        assertTrue(RewardLoadValidator.validateStatOption(opt).isPresent());
    }

    @Test
    void disableAiAttackerOnDamageDealtSkipped() {
        EffectContext params = disableAiParams(BuffAreaTargets.TARGET_ATTACKER);
        RewardOption opt = stat(Effect.DISABLE_AI, Trigger.ON_DAMAGE_DEALT, params);
        assertTrue(RewardLoadValidator.validateStatOption(opt).isPresent());
    }

    @Test
    void disableAiHitTargetOnDamageDealtAllowed() {
        EffectContext params = disableAiParams(BuffAreaTargets.TARGET_HIT_TARGET);
        RewardOption opt = stat(Effect.DISABLE_AI, Trigger.ON_DAMAGE_DEALT, params);
        assertTrue(RewardLoadValidator.validateStatOption(opt).isEmpty());
    }

    private static EffectContext disableAiParams(String targets) {
        EffectContext p = new EffectContext();
        p.put(EffectKeys.DURATION_TICKS, 40);
        p.put(EffectKeys.TARGETS, targets);
        return p;
    }

    private static EffectContext buffAreaParams(String targets) {
        EffectContext p = new EffectContext();
        p.put(EffectKeys.TARGETS, targets);
        p.put(EffectKeys.POTIONS, List.of(new BuffPotionSpec(null, 0, 60)));
        return p;
    }

    private static EffectContext auraParams() {
        EffectContext params = new EffectContext();
        params.put(EffectKeys.RADIUS, 6.0);
        params.put(EffectKeys.GRANT_EFFECT, Effect.ADD_POTION);
        EffectContext grant = new EffectContext();
        grant.put(EffectKeys.POTION_NAME, "SPEED");
        params.put(EffectKeys.GRANT_PARAMS, grant);
        return params;
    }

    @Test
    void acceptsOnWaveClearForMagicStack() {
        EffectContext params = new EffectContext();
        params.put(EffectKeys.ATTR_NAME, "MAGIC_DAMAGE");
        params.put(EffectKeys.OP, "PERCENT");
        params.put(EffectKeys.VALUE, 0.05);
        RewardOption opt = stat(Effect.ADD_ATTRIBUTE, Trigger.ON_WAVE_CLEAR, null, 0, params);
        assertTrue(RewardLoadValidator.validateStatOption(opt).isEmpty());
    }

    @Test
    void rejectsRevokeWithoutTrigger() {
        EffectContext params = revokeParams("stack_hit");
        RewardOption opt = stat(Effect.ADD_ATTRIBUTE, null, null, 0, params);
        assertTrue(RewardLoadValidator.validateStatOption(opt).orElse("").contains("requires trigger"));
    }

    @Test
    void rejectsRevokeWithoutSourceId() {
        EffectContext params = new EffectContext();
        params.put(EffectKeys.OP, "REVOKE_GRANTS");
        RewardOption opt = stat(Effect.ADD_ATTRIBUTE, Trigger.ON_WAVE_CLEAR, null, 0, params);
        assertTrue(RewardLoadValidator.validateStatOption(opt).orElse("").contains("source_id"));
    }

    @Test
    void rejectsBundleRevokeMissingSibling() {
        EffectContext stack = new EffectContext();
        stack.put(EffectKeys.ATTR_NAME, "MAGIC_DAMAGE");
        stack.put(EffectKeys.OP, "PERCENT");
        stack.put(EffectKeys.VALUE, 0.05);
        RewardOption stackGrant = statWithId("stack_hit", Effect.ADD_ATTRIBUTE, Trigger.ON_DAMAGE_TAKEN, stack);

        EffectContext revoke = revokeParams("missing_id");
        RewardOption revokeGrant = statWithId("clear", Effect.ADD_ATTRIBUTE, Trigger.ON_WAVE_CLEAR, revoke);

        RewardOption bundle = bundleOf(stackGrant, revokeGrant);
        assertTrue(RewardLoadValidator.validateStatOption(bundle).orElse("").contains("not in bundle"));
    }

    @Test
    void rejectsBoundEquipWithTrigger() {
        RewardOption opt = boundEquipStat("prot_a", Trigger.ON_WAVE_CLEAR, Stack.UPGRADE_LEVEL, 8);
        assertTrue(RewardLoadValidator.validateStatOption(opt).isPresent());
    }

    @Test
    void rejectsBoundEquipWithoutUpgradeStack() {
        RewardOption opt = boundEquipStat("prot_a", null, Stack.IGNORE, 8);
        assertTrue(RewardLoadValidator.validateStatOption(opt).isPresent());
    }

    @Test
    void acceptsBoundEquipOk() {
        RewardOption opt = boundEquipStat("prot_a", null, Stack.UPGRADE_LEVEL, 8);
        assertTrue(RewardLoadValidator.validateStatOption(opt).isEmpty());
    }

    @Test
    void secondChestBoundEquipRejectedByUniquenessHelper() {
        RewardOption a = boundEquipStat("prot_a", null, Stack.UPGRADE_LEVEL, 8);
        RewardOption b = boundEquipStat("prot_b", null, Stack.UPGRADE_LEVEL, 8);
        assertTrue(RewardLoadValidator.chestBoundEquipConflict(List.of(a), b).isPresent());
        assertTrue(RewardLoadValidator.chestBoundEquipConflict(List.of(), a).isEmpty());
    }

    @Test
    void sameIdChestBoundEquipAllowedAcrossPools() {
        RewardOption a1 = boundEquipStat("prot_chest", null, Stack.UPGRADE_LEVEL, 8);
        RewardOption a2 = boundEquipStat("prot_chest", null, Stack.UPGRADE_LEVEL, 8);
        assertTrue(RewardLoadValidator.chestBoundEquipConflict(List.of(a1), a2).isEmpty());
    }

    @Test
    void acceptsBundleRevokeSibling() {
        EffectContext stack = new EffectContext();
        stack.put(EffectKeys.ATTR_NAME, "MAGIC_DAMAGE");
        stack.put(EffectKeys.OP, "PERCENT");
        stack.put(EffectKeys.VALUE, 0.05);
        RewardOption stackGrant = statWithId("stack_hit", Effect.ADD_ATTRIBUTE, Trigger.ON_DAMAGE_TAKEN, stack);
        RewardOption revokeGrant = statWithId("clear", Effect.ADD_ATTRIBUTE, Trigger.ON_WAVE_CLEAR, revokeParams("stack_hit"));
        RewardOption bundle = bundleOf(stackGrant, revokeGrant);
        assertTrue(RewardLoadValidator.validateStatOption(bundle).isEmpty());
    }

    private static EffectContext revokeParams(String sourceId) {
        EffectContext params = new EffectContext();
        params.put(EffectKeys.OP, "REVOKE_GRANTS");
        params.put(EffectKeys.SOURCE_ID, sourceId);
        return params;
    }

    private static RewardOption statWithId(String id, Effect effect, Trigger trigger, EffectContext params) {
        return new RewardOption(
                id, "<gray>test", "", RewardOption.Category.STAT,
                null, 1,
                trigger, effect, params, Stack.ADD,
                null, 0,
                false, 0, false, 0, java.util.List.of());
    }

    private static RewardOption bundleOf(RewardOption... grants) {
        return new RewardOption(
                "bundle", "<gray>bundle", "", RewardOption.Category.BUNDLE,
                null, 1,
                null, null, null, Stack.ADD,
                null, 0,
                false, 0, false, 0, java.util.List.of(grants));
    }

    private static RewardOption stat(Effect effect, Trigger trigger, EffectContext params) {
        return stat(effect, trigger, null, 0, params);
    }

    private static RewardOption stat(Effect effect, Trigger trigger, Trigger expiry, int charges,
                                       EffectContext params) {
        return new RewardOption(
                "test_opt", "<gray>test", "", RewardOption.Category.STAT,
                null, 1,
                trigger, effect, params, Stack.ADD,
                expiry, charges,
                false, 0, false, 0, java.util.List.of());
    }

    @Test
    void parsesBoundEquipSlotAndItemsByLevelFromMap() {
        Map<String, Object> levels = new HashMap<>();
        for (int i = 1; i <= 8; i++) {
            levels.put(String.valueOf(i), "maggoteers:prot_chest_l" + i);
        }
        Map<String, Object> params = new HashMap<>();
        params.put("slot", "CHEST");
        params.put("items_by_level", levels);
        Map<String, Object> m = new HashMap<>();
        m.put("id", "prot_chest");
        m.put("display", "<aqua>保护胸甲");
        m.put("category", "STAT");
        m.put("effect", "BOUND_EQUIP");
        m.put("stack", "UPGRADE_LEVEL");
        m.put("upgrade_max", 8);
        m.put("unique", true);
        m.put("params", params);

        RewardOption opt = RewardOption.fromMap(m);
        assertEquals(Effect.BOUND_EQUIP, opt.effect());
        assertEquals("CHEST", opt.params().get(EffectKeys.SLOT));
        assertEquals("maggoteers:prot_chest_l1",
                opt.params().get(EffectKeys.ITEMS_BY_LEVEL).get(1));
        assertTrue(RewardLoadValidator.validateStatOption(opt).isEmpty());
    }

    @Test
    void parsesSummonHomingTargetFromMap() {
        Map<String, Object> params = new HashMap<>();
        params.put("entity", "SMALL_FIREBALL");
        params.put("projectile", Map.of("speed", 1.2));
        params.put("homing_target", "nearest_enemy");
        Map<String, Object> m = new HashMap<>();
        m.put("id", "fire_orb_summon");
        m.put("category", "STAT");
        m.put("effect", "SUMMON");
        m.put("params", params);

        RewardOption opt = RewardOption.fromMap(m);
        assertEquals(Effect.SUMMON, opt.effect());
        assertEquals("nearest_enemy", opt.params().get(EffectKeys.HOMING_TARGET));
        assertEquals(1.2, opt.params().get(EffectKeys.PROJECTILE).get(EffectKeys.PROJECTILE_SPEED), 1e-6);
    }

    private static EffectContext boundEquipParams(int max) {
        EffectContext p = new EffectContext();
        p.put(EffectKeys.SLOT, "CHEST");
        Map<Integer, String> m = new HashMap<>();
        for (int i = 1; i <= max; i++) {
            m.put(i, "maggoteers:prot_chest_l" + i);
        }
        p.put(EffectKeys.ITEMS_BY_LEVEL, m);
        return p;
    }

    private static RewardOption boundEquipStat(String id, Trigger trigger, Stack stack, int upgradeMax) {
        return new RewardOption(
                id, "<gray>test", "", RewardOption.Category.STAT,
                null, 1,
                trigger, Effect.BOUND_EQUIP, boundEquipParams(upgradeMax), stack,
                null, 0,
                false, 0, false, upgradeMax, List.of());
    }
}