package io.mczju.maggoteers.reward;

import io.mczju.maggoteers.effect.Effect;
import io.mczju.maggoteers.effect.EffectContext;
import io.mczju.maggoteers.effect.PlayerEffect;
import io.mczju.maggoteers.effect.Stack;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CollectiblePreviewLevelTest {

    @Test
    void previewLevelForUpgradeShowsNext() {
        PlayerEffect pe = new PlayerEffect("a1s_resist", Effect.ADD_POTION, new EffectContext(),
                null, null, 0, 0, null, Stack.UPGRADE_LEVEL, 4, 0);
        pe.setLevel(2);
        assertEquals(3, CollectibleService.previewLevel(pe, true));
    }

    @Test
    void previewLevelCapsAtMax() {
        PlayerEffect pe = new PlayerEffect("a1s_resist", Effect.ADD_POTION, new EffectContext(),
                null, null, 0, 0, null, Stack.UPGRADE_LEVEL, 4, 0);
        pe.setLevel(4);
        assertEquals(4, CollectibleService.previewLevel(pe, true));
    }

    @Test
    void previewLevelDefaultOne() {
        assertEquals(1, CollectibleService.previewLevel(null, true));
        assertEquals(1, CollectibleService.previewLevel(null, false));
    }
}
