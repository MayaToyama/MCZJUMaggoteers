package io.mczju.maggoteers.effect;

import org.bukkit.potion.PotionEffectType;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PotionImmunityTest {

    private static PotionEffectType safeType(String field) {
        try {
            return (PotionEffectType) PotionEffectType.class.getField(field).get(null);
        } catch (Throwable t) {
            return null;
        }
    }

    private static PlayerEffect immunity(String id, PotionEffectType type) {
        EffectContext p = new EffectContext();
        p.put(EffectKeys.POTION, type);
        p.put(EffectKeys.IMMUNITY, true);
        return new PlayerEffect(id, Effect.ADD_POTION, p, null, null, 0, 0, null, Stack.IGNORE, 0, 0);
    }

    @Test
    void matchesWhenLegal() {
        PotionEffectType type = safeType("WITHER");
        Assumptions.assumeTrue(type != null && !type.isInstant());
        assertTrue(PotionImmunity.matchesImmunityEffect(immunity("a3w_imm_wither", type), type));
    }

    @Test
    void rejectsWhenExpiryPresent() {
        PotionEffectType type = safeType("WITHER");
        Assumptions.assumeTrue(type != null);
        EffectContext p = new EffectContext();
        p.put(EffectKeys.POTION, type);
        p.put(EffectKeys.IMMUNITY, true);
        PlayerEffect e = new PlayerEffect("x", Effect.ADD_POTION, p, null,
                Trigger.ON_WAVE_CLEAR, -1, 0, null, Stack.IGNORE, 0, 0);
        assertFalse(PotionImmunity.matchesImmunityEffect(e, type));
    }

    @Test
    void rejectsWrongType() {
        PotionEffectType wither = safeType("WITHER");
        PotionEffectType poison = safeType("POISON");
        Assumptions.assumeTrue(wither != null && poison != null);
        assertFalse(PotionImmunity.matchesImmunityEffect(immunity("a", wither), poison));
    }

    @Test
    void rejectsWhenFireTriggerPresent() {
        PotionEffectType type = safeType("WITHER");
        Assumptions.assumeTrue(type != null);
        EffectContext p = new EffectContext();
        p.put(EffectKeys.POTION, type);
        p.put(EffectKeys.IMMUNITY, true);
        PlayerEffect e = new PlayerEffect("x", Effect.ADD_POTION, p, Trigger.ON_KILL,
                null, 0, 0, null, Stack.IGNORE, 0, 0);
        assertFalse(PotionImmunity.matchesImmunityEffect(e, type));
    }

    @Test
    void rejectsWithoutImmunityFlag() {
        PotionEffectType type = safeType("WITHER");
        Assumptions.assumeTrue(type != null);
        EffectContext p = new EffectContext();
        p.put(EffectKeys.POTION, type);
        PlayerEffect e = new PlayerEffect("x", Effect.ADD_POTION, p, null, null, 0, 0, null, Stack.IGNORE, 0, 0);
        assertFalse(PotionImmunity.matchesImmunityEffect(e, type));
    }
}
