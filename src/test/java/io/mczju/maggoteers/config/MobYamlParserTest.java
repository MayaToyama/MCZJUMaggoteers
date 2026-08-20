package io.mczju.maggoteers.config;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MobYamlParserTest {
    @Test
    void parsesSkillsList() {
        assertEquals(List.of("plastic", "berserk"),
                MobYamlParser.parseSkills(Map.of("skills", List.of("plastic", "berserk")), "t"));
    }

    @Test
    void fallsBackToAffixes() {
        assertEquals(List.of("armored"),
                MobYamlParser.parseSkills(Map.of("affixes", List.of("armored")), "t"));
    }

    @Test
    void fallsBackToInfernalAffixes() {
        assertEquals(List.of("poisonous", "sprint"),
                MobYamlParser.parseSkills(Map.of("infernal",
                        Map.of("level", 4, "affixes", List.of("poisonous", "sprint"))), "t"));
    }

    @Test
    void skillsWinsOverAffixes() {
        assertEquals(List.of("plastic"),
                MobYamlParser.parseSkills(Map.of("skills", List.of("plastic"),
                        "affixes", List.of("armored")), "t"));
    }

    @Test
    void parsesNameAndBossBar() {
        Map<String, Object> m = Map.of(
                "type", "ZOMBIE",
                "name", "  <gold>Boss</gold>  ",
                "boss_bar", true);
        assertEquals("<gold>Boss</gold>", MobYamlParser.parseName(m, "t"));
        assertTrue(MobYamlParser.parseBossBar(m, "t"));
    }

    @Test
    void blankNameIsNull() {
        assertNull(MobYamlParser.parseName(Map.of("name", "  "), "t"));
        assertNull(MobYamlParser.parseName(Map.of(), "t"));
    }

    @Test
    void bossBarStringFails() {
        assertThrows(IllegalStateException.class,
                () -> MobYamlParser.parseBossBar(Map.of("boss_bar", "true"), "t"));
    }

    @Test
    void nameNonStringFails() {
        assertThrows(IllegalStateException.class,
                () -> MobYamlParser.parseName(Map.of("name", 1), "t"));
    }

    @Test
    void parsesControllerBoolean() {
        assertTrue(MobYamlParser.parseController(Map.of("controller", true), "t"));
        assertFalse(MobYamlParser.parseController(Map.of("controller", false), "t"));
        assertFalse(MobYamlParser.parseController(Map.of(), "t"));
    }

    @Test
    void controllerStringFails() {
        assertThrows(IllegalStateException.class,
                () -> MobYamlParser.parseController(Map.of("controller", "true"), "t"));
    }
}