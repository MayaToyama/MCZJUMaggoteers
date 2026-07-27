package io.mczju.maggoteers.config;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class InfernalCfgTest {
    @Test
    void normalizesIdsAndCopiesInput() {
        var ids = new java.util.ArrayList<>(List.of(" Poisonous ", "SPRINT", "poisonous"));
        InfernalCfg cfg = new InfernalCfg(3, ids);
        ids.clear();
        assertEquals(3, cfg.level());
        assertEquals(List.of("poisonous", "sprint"), cfg.affixes());
        assertTrue(cfg.enabled());
    }

    @Test
    void noneIsDisabled() {
        assertFalse(InfernalCfg.NONE.enabled());
        assertEquals(1, InfernalCfg.NONE.level());
    }

    @Test
    void rejectsOutOfRangeLevels() {
        assertThrows(IllegalArgumentException.class, () -> new InfernalCfg(0, List.of("sprint")));
        assertThrows(IllegalArgumentException.class, () -> new InfernalCfg(101, List.of("sprint")));
    }
}