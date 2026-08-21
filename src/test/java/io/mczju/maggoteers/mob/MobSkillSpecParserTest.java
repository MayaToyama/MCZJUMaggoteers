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
    void parsesNewSixSkillsFromMigration() {
        // waves.yml 迁移后引用的 6 个补齐 id 必须可解析（对照 mob_skills.yml 定义形态）
        MobSkillSpec confusing = MobSkillSpecParser.parse(Map.of(
                "id", "confusing", "trigger", "attack", "cooldown_sec", 3,
                "effects", List.of(Map.of(
                        "effect", "potion", "target", "target",
                        "potion", "nausea", "amp", 0, "duration_ticks", 100)))).orElseThrow();
        assertEquals(MobTrigger.ATTACK, confusing.trigger());

        MobSkillSpec swap = MobSkillSpecParser.parse(Map.of(
                "id", "swap", "trigger", "damage_taken", "cooldown_sec", 8,
                "effects", List.of(Map.of(
                        "effect", "teleport", "target", "target", "offset_y", 0)))).orElseThrow();
        assertEquals(MobEffect.TELEPORT, swap.effects().get(0).effect());
        assertEquals(MobTrigger.DAMAGE_TAKEN, swap.trigger());   // 被打时传送到攻击者身边

        MobSkillSpec wardenwrath = MobSkillSpecParser.parse(Map.of(
                "id", "wardenwrath", "trigger", "tick", "cooldown_sec", 6,
                "condition", Map.of("player_in_radius", 16),
                "effects", List.of(Map.of(
                        "effect", "health", "target", "area", "radius", 6, "damage", 6)))).orElseThrow();
        assertTrue(wardenwrath.condition() instanceof MobCondition.PlayerInRadius);

        MobSkillSpec revive = MobSkillSpecParser.parse(Map.of(
                "id", "1up", "trigger", "killed",
                "effects", List.of(Map.of(
                        "effect", "health", "target", "self", "amount", 99999)))).orElseThrow();
        assertEquals(MobTrigger.KILLED, revive.trigger());
        assertEquals(MobEffect.HEALTH, revive.effects().get(0).effect());
        assertEquals(99999.0, revive.effects().get(0).params().getOrDefault(EffectKeys.AMOUNT, 0.0));
    }

    @Test
    void parsesDisplaceTosser() {
        MobSkillSpec s = MobSkillSpecParser.parse(Map.of(
                "id", "tosser",
                "trigger", "tick",
                "cooldown_sec", 7,
                "condition", Map.of("player_in_radius", 24),
                "effects", List.of(Map.of(
                        "effect", "displace", "target", "player",
                        "radius", 24, "force", 1.2, "upward", 0.2)))).orElseThrow();
        assertEquals(MobTrigger.TICK, s.trigger());
        MobEffectSpec es = s.effects().get(0);
        assertEquals(MobEffect.DISPLACE, es.effect());
        assertEquals("player", es.params().get(EffectKeys.TARGETS));
        assertEquals(24.0, es.params().getOrDefault(EffectKeys.RADIUS, 0.0));
        assertEquals(1.2, es.params().getOrDefault(EffectKeys.FORCE, 0.0));
        assertEquals(0.2, es.params().getOrDefault(EffectKeys.UPWARD, 0.0));
    }

    @Test
    void parsesNecromancerSkullAndStormLightning() {
        // necromancer：向玩家发射蓝色凋零头（wither_skull homing）
        MobSkillSpec necro = MobSkillSpecParser.parse(Map.of(
                "id", "necromancer", "trigger", "tick", "cooldown_sec", 10,
                "condition", Map.of("player_in_radius", 20),
                "effects", List.of(Map.of(
                        "effect", "summon", "target", "self", "entity", "wither_skull",
                        "count", 1, "homing_target", "nearest_player", "projectile_speed", 1.2))))
                .orElseThrow();
        MobEffectSpec ne = necro.effects().get(0);
        assertEquals(MobEffect.SUMMON, ne.effect());
        assertEquals("wither_skull", ne.params().get(EffectKeys.ENTITY));
        assertEquals("nearest_player", ne.params().get(EffectKeys.HOMING_TARGET));

        // storm：在玩家处落雷（target: player + lightning_bolt）
        MobSkillSpec storm = MobSkillSpecParser.parse(Map.of(
                "id", "storm", "trigger", "tick", "cooldown_sec", 10,
                "condition", Map.of("player_in_radius", 18),
                "effects", List.of(Map.of(
                        "effect", "summon", "target", "player", "entity", "lightning_bolt",
                        "radius", 18)))).orElseThrow();
        MobEffectSpec se = storm.effects().get(0);
        assertEquals("player", se.params().get(EffectKeys.TARGETS));
        assertEquals("lightning_bolt", se.params().get(EffectKeys.ENTITY));
        assertEquals(18.0, se.params().getOrDefault(EffectKeys.RADIUS, 0.0));
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
