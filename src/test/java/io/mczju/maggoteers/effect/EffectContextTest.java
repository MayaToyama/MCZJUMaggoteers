package io.mczju.maggoteers.effect;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EffectContextTest {
    static final EffectKey<String> NAME = new EffectKey<>("name");
    static final EffectKey<Integer> LVL = new EffectKey<>("lvl");

    @Test
    void putAndGetIsTyped() {
        EffectContext ctx = new EffectContext().put(NAME, "lifesteal").put(LVL, 2);
        assertEquals("lifesteal", ctx.get(NAME));
        assertEquals(2, ctx.get(LVL));
    }

    @Test
    void missingKeyReturnsNullOrDefault() {
        EffectContext ctx = new EffectContext();
        assertNull(ctx.get(NAME));
        assertEquals("x", ctx.getOrDefault(NAME, "x"));
    }

    @Test
    void hasReportsPresence() {
        EffectContext ctx = new EffectContext().put(LVL, 1);
        assertTrue(ctx.has(LVL));
        assertFalse(ctx.has(NAME));
    }
}
