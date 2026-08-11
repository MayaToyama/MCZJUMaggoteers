package io.mczju.maggoteers.effect;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BoundEquipServiceLogicTest {
    @Test
    void conflictDestroyRules() {
        assertTrue(BoundEquipService.isConflictToDestroy(true, false));
        assertFalse(BoundEquipService.isConflictToDestroy(true, true));
        assertFalse(BoundEquipService.isConflictToDestroy(false, false));
    }
}