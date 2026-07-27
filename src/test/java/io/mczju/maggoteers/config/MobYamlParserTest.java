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
}