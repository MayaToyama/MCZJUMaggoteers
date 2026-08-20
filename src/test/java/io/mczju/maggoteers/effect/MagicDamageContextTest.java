package io.mczju.maggoteers.effect;

import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MagicDamageContextTest {

    @Test
    void runWithNullGameStillExecutes() {
        AtomicBoolean ran = new AtomicBoolean();
        MagicDamageContext.run(null, UUID.randomUUID(), () -> ran.set(true));
        assertTrue(ran.get());
    }

    @Test
    void runWithNullSourceStillExecutes() {
        AtomicBoolean ran = new AtomicBoolean();
        MagicDamageContext.run(null, null, () -> ran.set(true));
        assertTrue(ran.get());
    }

    @Test
    void suppressFalseWhenGameNull() {
        assertFalse(MagicDamageContext.shouldSuppressOnDamageDealt(null, UUID.randomUUID()));
        assertFalse(MagicDamageContext.shouldSuppressOnDamageTaken(null, UUID.randomUUID()));
        assertFalse(MagicDamageContext.shouldSuppressCombatTriggers(null, UUID.randomUUID()));
    }

    /** Same-tick mark must block both DEALT and TAKEN re-entry (Guardian spike + thorns loop). */
    @Test
    void markBlocksCombatTriggersSameTick() {
        Object gameToken = new Object();
        UUID source = UUID.randomUUID();
        int tick = 99;
        AtomicBoolean sawActive = new AtomicBoolean();
        MagicDamageContext.runMarked(gameToken, source, tick, () -> {
            assertTrue(MagicDamageContext.isMarked(gameToken, source, tick));
            sawActive.set(true);
        });
        assertTrue(sawActive.get());
        assertFalse(MagicDamageContext.isMarked(gameToken, source, tick));
    }

    /** Nested runMarked must not clear the mark prematurely (refcount, not binary set). */
    @Test
    void nestedRunPreservesOuterMark() {
        Object gameToken = new Object();
        UUID source = UUID.randomUUID();
        int tick = 42;
        AtomicBoolean innerSawMarked = new AtomicBoolean();
        AtomicBoolean outerSawMarkedAfterInner = new AtomicBoolean();
        MagicDamageContext.runMarked(gameToken, source, tick, () -> {
            // inner AOE / ON_KILL
            MagicDamageContext.runMarked(gameToken, source, tick, () -> {
                assertTrue(MagicDamageContext.isMarked(gameToken, source, tick));
                innerSawMarked.set(true);
            });
            // outer scope should still be marked after inner finishes
            assertTrue(MagicDamageContext.isMarked(gameToken, source, tick));
            outerSawMarkedAfterInner.set(true);
        });
        assertTrue(innerSawMarked.get());
        assertTrue(outerSawMarkedAfterInner.get());
        assertFalse(MagicDamageContext.isMarked(gameToken, source, tick));
    }
}
