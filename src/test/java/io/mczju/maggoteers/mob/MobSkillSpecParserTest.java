package io.mczju.maggoteers.mob;

import io.mczju.maggoteers.effect.EffectKeys;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MobSkillSpecParserTest {

    @Test
    void parsesSpawnPotionSkill() {
        MobSkillSpec s = MobSkillSpecParser.parse(Map.of(
                "id", "plastic",
                "trigger", "spawn",
                "effects", List.of(Map.of(
                        "effect", "potion",
                        "target", "self",
                        "potion", "resistance",
                        "amp", 4,
                        "duration_ticks", 600)))).orElseThrow();
        assertEquals(MobTrigger.SPAWN, s.trigger());
        assertFalse(s.invisible());
        assertNull(s.counter());
        assertEquals(1, s.effects().size());
        MobEffectSpec es = s.effects().get(0);
        assertEquals(MobEffect.POTION, es.effect());
        assertEquals(4, es.params().getOrDefault(EffectKeys.AMP, 0));
    }

    @Test
    void parsesInvisibleFlag() {
        MobSkillSpec s = MobSkillSpecParser.parse(Map.of(
                "id", "invisible",
                "trigger", "spawn",
                "invisible", true)).orElseThrow();
        assertTrue(s.invisible());
        assertTrue(s.effects().isEmpty());   // 纯装饰允许空 effects
    }

    @Test
    void counterTriggerRequiresCounter() {
        assertTrue(MobSkillSpecParser.parse(Map.of(
                "id", "x",
                "trigger", "counter",
                "effects", List.of(Map.of("effect", "health", "target", "self", "amount", 2)))).isEmpty());
        assertTrue(MobSkillSpecParser.parse(Map.of(
                "id", "x",
                "trigger", "counter",
                "counter", Map.of("id", "c1", "count", "attack", "amount", 5),
                "effects", List.of(Map.of("effect", "health", "target", "self", "amount", 2)))).isPresent());
    }

    @Test
    void tickTriggerRequiresCooldown() {
        assertTrue(MobSkillSpecParser.parse(Map.of(
                "id", "aura",
                "trigger", "tick",
                "cooldown_sec", 2,
                "effects", List.of(Map.of(
                        "effect", "potion", "target", "self",
                        "potion", "regeneration", "amp", 0, "duration_ticks", 40)))).isPresent());
        assertTrue(MobSkillSpecParser.parse(Map.of(
                "id", "aura",
                "trigger", "tick",
                "effects", List.of(Map.of("effect", "health", "target", "self", "amount", 1)))).isEmpty());
    }

    @Test
    void parsesCounterSpec() {
        MobSkillSpec s = MobSkillSpecParser.parse(Map.of(
                "id", "x",
                "trigger", "counter",
                "counter", Map.of("id", "c1", "count", "damage_taken", "amount", 3,
                        "condition", Map.of("not", Map.of("player_in_radius", 4.0))),
                "cooldown_sec", 1,
                "effects", List.of(Map.of("effect", "teleport", "target", "self")))).orElseThrow();
        MobCounterSpec c = s.counter();
        assertNotNull(c);
        assertEquals(MobTrigger.DAMAGE_TAKEN, c.count());
        assertEquals(3, c.amount());
        assertTrue(c.condition() instanceof MobCondition.Not);
    }

    @Test
    void unknownTriggerRejected() {
        assertTrue(MobSkillSpecParser.parse(Map.of(
                "id", "x", "trigger", "bogus")).isEmpty());
    }

    @Test
    void mobConditionCombinators() {
        MobCondition t = MobCondition.TRUE;   // 见实现
        MobCondition f = MobCondition.FALSE;
        assertTrue(new MobCondition.And(List.of(t, t)).evaluate(null, null, null, null));
        assertFalse(new MobCondition.And(List.of(t, f)).evaluate(null, null, null, null));
        assertTrue(new MobCondition.Or(List.of(f, t)).evaluate(null, null, null, null));
        assertFalse(new MobCondition.Not(t).evaluate(null, null, null, null));
    }
}
