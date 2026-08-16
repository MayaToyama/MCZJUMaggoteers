package io.mczju.maggoteers.effect;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ItemAbilityParseTest {
    @Test
    void legacySingleNormalizesToOneStep() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("cooldown_sec", 10);
        y.set("effect", "ADD_ATTRIBUTE");
        y.set("params.attr", "ATTACK_DAMAGE");
        y.set("params.op", "PERCENT");
        y.set("params.value", 1.0);
        y.set("expiry.trigger", "ON_DAMAGE_DEALT");
        y.set("expiry.charges", 1);
        List<AbilityStep> steps = ItemAbilityRegistry.parseStepsForTest("maggoteers:power_strike", y);
        assertEquals(1, steps.size());
        assertEquals(Effect.ADD_ATTRIBUTE, steps.get(0).effect());
        assertEquals(Trigger.ON_DAMAGE_DEALT, steps.get(0).expiryTrigger());
        assertEquals(Stack.REPLACE, steps.get(0).stack());
    }

    @Test
    void effectsAndTopLevelEffectMutuallyExclusive() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("cooldown_sec", 10);
        y.set("effect", "DISABLE_AI");
        y.set("effects.0.effect", "BUFF_AREA");
        assertTrue(ItemAbilityRegistry.parseStepsForTest("maggoteers:bad", y).isEmpty());
    }

    @Test
    void rejectsAuraStep() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("cooldown_sec", 5);
        y.set("effects.0.effect", "AURA");
        y.set("effects.0.params.radius", 8.0);
        assertTrue(ItemAbilityRegistry.parseStepsForTest("maggoteers:aura_bad", y).isEmpty());
    }

    @Test
    void perStepInstantAddPotionAllowed() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("cooldown_sec", 5);
        y.set("effects.0.effect", "ADD_POTION");
        y.set("effects.0.params.potion", "SPEED");
        y.set("effects.0.params.amp", 0);
        y.set("effects.0.params.duration_ticks", 60);
        List<AbilityStep> steps = ItemAbilityRegistry.parseStepsForTest("maggoteers:potion", y);
        assertEquals(1, steps.size());
        assertNull(steps.get(0).expiryTrigger());
    }

    @Test
    void eightyHammerLegacyShapeIsDeferredDisableAi() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("cooldown_sec", 10);
        y.set("effect", "DISABLE_AI");
        y.set("params.duration_ticks", 100);
        y.set("params.targets", "hit_target");
        y.set("expiry.trigger", "ON_DAMAGE_DEALT");
        y.set("expiry.charges", 1);
        List<AbilityStep> steps = ItemAbilityRegistry.parseStepsForTest("maggoteers:eighty_hammer", y);
        assertEquals(1, steps.size());
        assertEquals(Effect.DISABLE_AI, steps.get(0).effect());
        assertEquals(Trigger.ON_DAMAGE_DEALT, steps.get(0).expiryTrigger());
    }

    /** Regression: getMapList nests params/expiry as Map, not ConfigurationSection. */
    @Test
    void effectsListNestedMapsKeepParamsAndExpiry() throws InvalidConfigurationException {
        YamlConfiguration y = new YamlConfiguration();
        y.loadFromString("""
                cooldown_sec: 10
                effects:
                  -
                    effect: DISABLE_AI
                    params:
                      duration_ticks: 60
                      radius: 3.0
                      targets: enemies
                  -
                    effect: ADD_ATTRIBUTE
                    params:
                      attr: ATTACK_DAMAGE
                      op: PERCENT
                      value: 2.0
                    expiry:
                      trigger: ON_DAMAGE_DEALT
                      charges: 1
                    stack: REPLACE
                  -
                    effect: ADD_POTION
                    params:
                      potion: SPEED
                      amp: 0
                      duration_ticks: 100
                """);
        List<AbilityStep> steps = ItemAbilityRegistry.parseStepsForTest("maggoteers:nested_maps", y);
        assertEquals(3, steps.size(), "all steps should parse; nested Map params must not be dropped");
        assertEquals(Effect.DISABLE_AI, steps.get(0).effect());
        assertEquals(60, steps.get(0).params().get(EffectKeys.DURATION_TICKS));
        assertEquals(Effect.ADD_ATTRIBUTE, steps.get(1).effect());
        assertEquals(Trigger.ON_DAMAGE_DEALT, steps.get(1).expiryTrigger());
        assertEquals(Effect.ADD_POTION, steps.get(2).effect());
        assertEquals(100, steps.get(2).params().get(EffectKeys.DURATION_TICKS));
    }
}
