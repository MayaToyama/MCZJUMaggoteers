package io.mczju.maggoteers.effect;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MagicEffectParamsBuffAreaTest {

    @Test
    void buffAreaDefaults() {
        EffectContext p = MagicEffectParams.withDefaults(Effect.BUFF_AREA, new EffectContext());
        assertEquals(AuraParams.TARGET_ALLIES, p.get(EffectKeys.TARGETS));
        assertEquals(5.0, p.get(EffectKeys.RADIUS));
        assertTrue(p.get(EffectKeys.INCLUDE_SELF));
        assertEquals(AuraParams.ENEMY_TRACKED, p.get(EffectKeys.ENEMY_SCOPE));
    }

    @Test
    void rejectsEmptyPotions() {
        EffectContext p = new EffectContext();
        p.put(EffectKeys.TARGETS, AuraParams.TARGET_ALLIES);
        assertTrue(MagicEffectParams.validateBuffArea(p, Trigger.ON_KILL, false)
                .orElse("").contains("potions"));
    }

    @Test
    void acceptsValidPotions() {
        EffectContext p = new EffectContext();
        p.put(EffectKeys.TARGETS, AuraParams.TARGET_ALLIES);
        p.put(EffectKeys.POTIONS, List.of(new BuffPotionSpec(null, 0, 60)));
        assertTrue(MagicEffectParams.validateBuffArea(p, Trigger.ON_KILL, false).isEmpty());
    }
}
