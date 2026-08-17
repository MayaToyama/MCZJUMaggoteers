package io.mczju.maggoteers.config;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MobYamlParserTest {
    @Test
    void parsesInfernalBlock() {
        InfernalCfg cfg = MobYamlParser.parseInfernal(Map.of(
                "infernal", Map.of("level", 4, "affixes", List.of("poisonous", "sprint"))));
        assertEquals(4, cfg.level());
        assertEquals(List.of("poisonous", "sprint"), cfg.affixes());
    }

    @Test
    void missingBlockReturnsNone() {
        assertSame(InfernalCfg.NONE, MobYamlParser.parseInfernal(Map.of()));
    }

    @Test
    void invalidLevelFailsWithContext() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> MobYamlParser.parseInfernal(Map.of(
                        "infernal", Map.of("level", 0, "affixes", List.of("sprint")))));
        assertTrue(ex.getMessage().contains("infernal.level"));
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
}