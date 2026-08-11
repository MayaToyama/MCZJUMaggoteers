package io.mczju.maggoteers.effect;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class BoundEquipParamsTest {

    private static EffectContext full8() {
        EffectContext p = new EffectContext();
        p.put(EffectKeys.SLOT, "CHEST");
        Map<Integer, String> m = new HashMap<>();
        for (int i = 1; i <= 8; i++) {
            m.put(i, "maggoteers:prot_chest_l" + i);
        }
        p.put(EffectKeys.ITEMS_BY_LEVEL, Map.copyOf(m));
        return p;
    }

    @Test
    void acceptsCompleteMap() {
        assertTrue(BoundEquipParams.validate(full8(), 8).isEmpty());
    }

    @Test
    void rejectsMissingLevel() {
        EffectContext p = full8();
        var map = new HashMap<>(p.get(EffectKeys.ITEMS_BY_LEVEL));
        map.remove(3);
        p.put(EffectKeys.ITEMS_BY_LEVEL, map);
        Optional<String> err = BoundEquipParams.validate(p, 8);
        assertTrue(err.isPresent());
        assertTrue(err.get().contains("3") || err.get().toLowerCase().contains("level"));
    }

    @Test
    void rejectsNonChestSlot() {
        EffectContext p = full8();
        p.put(EffectKeys.SLOT, "HEAD");
        assertTrue(BoundEquipParams.validate(p, 8).isPresent());
    }

    @Test
    void resolveLevel() {
        assertEquals("maggoteers:prot_chest_l4",
                BoundEquipParams.itemIdForLevel(full8(), 4).orElseThrow());
    }

    @Test
    void previewLevelRules() {
        assertEquals(1, BoundEquipParams.previewLevel(null, 8));
        assertEquals(2, BoundEquipParams.previewLevel(1, 8));
        assertEquals(8, BoundEquipParams.previewLevel(7, 8));
        assertEquals(8, BoundEquipParams.previewLevel(8, 8));
    }
}