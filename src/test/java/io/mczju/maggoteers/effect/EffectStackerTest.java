package io.mczju.maggoteers.effect;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class EffectStackerTest {

    private static PlayerEffect perm(String id, Stack s) {
        return new PlayerEffect(id, Effect.ADD_ATTRIBUTE, new EffectContext().put(EffectKeys.OP, "FLAT"),
                null, null, 0, 0, null, s, 4, 0);
    }
    private static PlayerEffect timed(String id, int charges, Trigger expiry) {
        return new PlayerEffect(id, Effect.HEAL, new EffectContext().put(EffectKeys.AMOUNT, 2.0),
                Trigger.ON_KILL, expiry, charges, 0, null, Stack.REFRESH, 0, 0);
    }
    private static PlayerEffect timedIgnored(String id, int charges, Trigger expiry) {
        return new PlayerEffect(id, Effect.HEAL, new EffectContext().put(EffectKeys.AMOUNT, 2.0),
                Trigger.ON_KILL, expiry, charges, 0, null, Stack.IGNORE, 0, 0);
    }

    @Test
    void addNewAppends() {
        List<PlayerEffect> out = EffectStacker.merge(List.of(), perm("a", Stack.ADD));
        assertEquals(1, out.size());
    }

    @Test
    void ignoreKeepsExisting() {
        List<PlayerEffect> out = EffectStacker.merge(List.of(perm("a", Stack.IGNORE)), perm("a", Stack.IGNORE));
        assertEquals(1, out.size());
    }

    @Test
    void replaceSwaps() {
        PlayerEffect old = perm("a", Stack.ADD);
        List<PlayerEffect> out = EffectStacker.merge(List.of(old), perm("a", Stack.REPLACE));
        assertEquals(1, out.size());
        assertNotSame(old, out.get(0));
    }

    @Test
    void addStacksMultiple() {
        List<PlayerEffect> out = EffectStacker.merge(List.of(perm("a", Stack.ADD)), perm("a", Stack.ADD));
        assertEquals(2, out.size());
    }

    @Test
    void upgradeLevelIncrementsToMax() {
        PlayerEffect base = perm("a", Stack.UPGRADE_LEVEL);   // upgradeMax=4
        List<PlayerEffect> out = List.of(base);
        out = EffectStacker.merge(out, perm("a", Stack.UPGRADE_LEVEL));
        assertEquals(2, out.get(0).level());
        out = EffectStacker.merge(out, perm("a", Stack.UPGRADE_LEVEL));
        out = EffectStacker.merge(out, perm("a", Stack.UPGRADE_LEVEL));
        assertEquals(4, out.get(0).level());                  // 封顶
        out = EffectStacker.merge(out, perm("a", Stack.UPGRADE_LEVEL));
        assertEquals(4, out.get(0).level());                  // 满级 IGNORE
    }

    @Test
    void upgradeLevelRaisesCapFromHigherPool() {
        PlayerEffect base = new PlayerEffect("str", Effect.ADD_POTION,
                new EffectContext().put(EffectKeys.AMP, 0),
                null, null, 0, 0, null, Stack.UPGRADE_LEVEL, 1, 0);
        base.setLevel(1);
        PlayerEffect higher = new PlayerEffect("str", Effect.ADD_POTION,
                new EffectContext().put(EffectKeys.AMP, 0),
                null, null, 0, 0, null, Stack.UPGRADE_LEVEL, 2, 0);
        List<PlayerEffect> out = EffectStacker.merge(List.of(base), higher);
        assertEquals(2, out.get(0).upgradeMax());
        assertEquals(2, out.get(0).level());
        assertEquals(1, out.get(0).params().get(EffectKeys.AMP));
    }

    @Test
    void refreshResetsCharges() {
        PlayerEffect t = timed("ls", 3, Trigger.ON_WAVE_CLEAR);
        t.setExpiryCharges(1);
        PlayerEffect refresh = timed("ls", 3, Trigger.ON_WAVE_CLEAR);
        List<PlayerEffect> out = EffectStacker.merge(List.of(t), refresh);
        assertEquals(3, out.get(0).expiryCharges());
    }

    @Test
    void sweepRemovesOnFirstFireWhenChargesMinusOne() {
        PlayerEffect t = timedIgnored("burst", -1, Trigger.ON_DAMAGE_DEALT);
        List<PlayerEffect> list = new java.util.ArrayList<>(List.of(t));
        assertEquals(1, EffectStacker.sweepExpiry(list, Trigger.ON_DAMAGE_DEALT).size());
        assertTrue(list.isEmpty());
    }

    @Test
    void sweepCountsDownPositiveCharges() {
        PlayerEffect t = timedIgnored("berry", 2, Trigger.ON_KILL);
        List<PlayerEffect> list = new java.util.ArrayList<>(List.of(t));
        assertTrue(EffectStacker.sweepExpiry(list, Trigger.ON_KILL).isEmpty());  // 2→1，未移除
        assertEquals(1, list.get(0).expiryCharges());
        assertEquals(1, EffectStacker.sweepExpiry(list, Trigger.ON_KILL).size());  // 1→0，移除
        assertTrue(list.isEmpty());
    }

    @Test
    void sweepOnlyMatchesFiredTrigger() {
        PlayerEffect t = timedIgnored("x", -1, Trigger.ON_WAVE_CLEAR);
        List<PlayerEffect> list = new java.util.ArrayList<>(List.of(t));
        assertTrue(EffectStacker.sweepExpiry(list, Trigger.ON_KILL).isEmpty());  // 不同 trigger，不动
        assertEquals(1, list.size());
    }
}
