package io.mczju.maggoteers.effect;

import io.mczju.maggoteers.state.PlayerState;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TriggeredGrantAttributeTest {

    @Test
    void grantIdAppendsSuffix() {
        assertEquals("stack_hit:grant", TriggeredGrantAttribute.grantId("stack_hit"));
    }

    @Test
    void isRevokeDetectsOp() {
        EffectContext ctx = new EffectContext();
        ctx.put(EffectKeys.OP, "REVOKE_GRANTS");
        ctx.put(EffectKeys.SOURCE_ID, "stack_hit");
        assertTrue(TriggeredGrantAttribute.isRevoke(ctx));
    }

    @Test
    void magicGrantDoesNotNeedBukkitResync() {
        PlayerState st = new PlayerState(UUID.randomUUID(), 2);
        st.effects().add(magicGrant("stack_hit:grant", 0.05));
        assertFalse(TriggeredGrantAttribute.revokeGrants(st, "stack_hit"));
        assertTrue(st.effects().isEmpty());
    }

    @Test
    void bukkitAttrNameNeedsResync() {
        EffectContext ctx = new EffectContext();
        ctx.put(EffectKeys.ATTR_NAME, "ATTACK_DAMAGE");
        ctx.put(EffectKeys.OP, "PERCENT");
        ctx.put(EffectKeys.VALUE, 0.05);
        PlayerEffect grant = new PlayerEffect(
                "stack_hit:grant", Effect.ADD_ATTRIBUTE, ctx, null, null, 0, 0, null, Stack.ADD, 0, 0);
        assertTrue(TriggeredGrantAttribute.grantUsesBukkitAttribute(grant));
    }

    @Test
    void revokeOnlyMatchingSource() {
        PlayerState st = new PlayerState(UUID.randomUUID(), 2);
        st.effects().add(magicGrant("a:grant", 0.05));
        st.effects().add(magicGrant("b:grant", 0.10));
        TriggeredGrantAttribute.revokeGrants(st, "a");
        assertEquals(1, st.effects().size());
        assertEquals("b:grant", st.effects().getFirst().id());
    }

    private static PlayerEffect magicGrant(String id, double pct) {
        EffectContext ctx = new EffectContext();
        ctx.put(EffectKeys.ATTR_NAME, "MAGIC_DAMAGE");
        ctx.put(EffectKeys.OP, "PERCENT");
        ctx.put(EffectKeys.VALUE, pct);
        return new PlayerEffect(id, Effect.ADD_ATTRIBUTE, ctx, null, null, 0, 0, null, Stack.ADD, 0, 0);
    }
}