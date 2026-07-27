package io.mczju.maggoteers.reward;

import io.mczju.maggoteers.effect.AuraParams;
import io.mczju.maggoteers.effect.Effect;
import io.mczju.maggoteers.effect.EffectContext;
import io.mczju.maggoteers.effect.EffectKeys;
import io.mczju.maggoteers.effect.Stack;
import io.mczju.maggoteers.effect.Trigger;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RewardLoadValidationTest {

    @Test
    void rejectsOnInteractFireTrigger() {
        RewardOption opt = stat(Effect.DAMAGE_AREA, Trigger.ON_INTERACT, null, 0, null);
        assertTrue(RewardLoadValidator.validateStatOption(opt).orElse("").contains("ON_INTERACT"));
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

    private static EffectContext auraParams() {
        EffectContext params = new EffectContext();
        params.put(EffectKeys.RADIUS, 6.0);
        params.put(EffectKeys.GRANT_EFFECT, Effect.ADD_POTION);
        EffectContext grant = new EffectContext();
        grant.put(EffectKeys.POTION_NAME, "SPEED");
        params.put(EffectKeys.GRANT_PARAMS, grant);
        return params;
    }

    private static RewardOption stat(Effect effect, Trigger trigger, Trigger expiry, int charges,
                                       EffectContext params) {
        return new RewardOption(
                "test_opt", "<gray>test", "", RewardOption.Category.STAT,
                null, 1,
                trigger, effect, params, Stack.ADD,
                expiry, charges,
                false, 0, false);
    }
}