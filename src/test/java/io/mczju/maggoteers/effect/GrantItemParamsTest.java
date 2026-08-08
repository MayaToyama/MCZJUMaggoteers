package io.mczju.maggoteers.effect;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GrantItemParamsTest {

    @Test
    void parsesItemAndAmount() {
        EffectContext ctx = new EffectContext();
        ctx.put(EffectKeys.ITEM_ID, "maggoteers:revive_coin");
        ctx.put(EffectKeys.COUNT, 2);
        GrantItemParams p = GrantItemParams.parse(ctx).orElseThrow();
        assertEquals("maggoteers:revive_coin", p.itemId());
        assertEquals(2, p.amount());
    }

    @Test
    void missingItemEmpty() {
        assertTrue(GrantItemParams.parse(new EffectContext()).isEmpty());
    }
}
