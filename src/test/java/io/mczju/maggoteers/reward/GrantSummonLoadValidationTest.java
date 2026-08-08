package io.mczju.maggoteers.reward;

import io.mczju.maggoteers.effect.Effect;
import io.mczju.maggoteers.effect.EffectContext;
import io.mczju.maggoteers.effect.EffectKeys;
import io.mczju.maggoteers.effect.Stack;
import io.mczju.maggoteers.effect.Trigger;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GrantSummonLoadValidationTest {

    @Test
    void grantItemRequiresTrigger() {
        EffectContext params = new EffectContext();
        params.put(EffectKeys.ITEM_ID, "maggoteers:revive_coin");
        RewardOption opt = stat(Effect.GRANT_ITEM, null, params);
        assertTrue(RewardLoadValidator.validateStatOption(opt).orElse("").contains("missing trigger"));
    }

    @Test
    void summonAllowsWaveClear() {
        RewardOption opt = stat(Effect.SUMMON, Trigger.ON_WAVE_CLEAR, summonParams());
        assertTrue(RewardLoadValidator.validateStatOption(opt).isEmpty());
    }

    @Test
    void summonRejectsOnTick1s() {
        RewardOption opt = stat(Effect.SUMMON, Trigger.ON_TICK_1S, summonParams());
        assertTrue(RewardLoadValidator.validateStatOption(opt).orElse("").contains("forbidden"));
    }

    @Test
    void summonAllowsOnDeathWithDeathSite() {
        EffectContext ctx = summonParams();
        ctx.put(EffectKeys.ANCHOR, "death_site");
        RewardOption opt = stat(Effect.SUMMON, Trigger.ON_DEATH, ctx);
        assertTrue(RewardLoadValidator.validateStatOption(opt).isEmpty());
    }

    @Test
    void onDeathRejectsHitTargetAnchor() {
        EffectContext ctx = summonParams();
        ctx.put(EffectKeys.ANCHOR, "hit_target");
        RewardOption opt = stat(Effect.SUMMON, Trigger.ON_DEATH, ctx);
        assertTrue(RewardLoadValidator.validateStatOption(opt).orElse("").contains("incompatible"));
    }

    @Test
    void parseParamsMapsItemKey() {
        RewardOption opt = RewardOption.fromMap(Map.of(
                "id", "wave_coin",
                "category", "STAT",
                "effect", "GRANT_ITEM",
                "trigger", "ON_WAVE_CLEAR",
                "params", Map.of("item", "maggoteers:revive_coin", "count", 1)
        ));
        assertEquals("maggoteers:revive_coin", opt.params().get(EffectKeys.ITEM_ID));
    }

    @Test
    void parseParamsMapsSourceId() {
        RewardOption opt = RewardOption.fromMap(Map.of(
                "id", "clear",
                "category", "STAT",
                "effect", "ADD_ATTRIBUTE",
                "trigger", "ON_WAVE_CLEAR",
                "params", Map.of("op", "REVOKE_GRANTS", "source_id", "stack_hit")
        ));
        assertEquals("stack_hit", opt.params().get(EffectKeys.SOURCE_ID));
    }

    private static EffectContext summonParams() {
        EffectContext ctx = new EffectContext();
        ctx.put(EffectKeys.ENTITY, "WOLF");
        ctx.put(EffectKeys.CLEANUP, "wave_clear");
        return ctx;
    }

    private static RewardOption stat(Effect effect, Trigger trigger, EffectContext params) {
        return new RewardOption(
                "test_opt", "<gray>test", "", RewardOption.Category.STAT,
                null, 1,
                trigger, effect, params, Stack.ADD,
                null, 0,
                false, 0, false, 0, java.util.List.of());
    }
}