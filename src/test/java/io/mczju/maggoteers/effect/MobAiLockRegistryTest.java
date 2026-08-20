package io.mczju.maggoteers.effect;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure bookkeeping tests (no Bukkit entity). Exercise noteLock / pollExpired /
 * takeForUnloadRestore; lock()/setAI covered at integration time.
 */
class MobAiLockRegistryTest {

    @AfterEach
    void tearDown() {
        MobAiLockRegistry.clearForTests();
    }

    @Test
    void firstLockStoresRestoreAndExpire() {
        UUID id = UUID.randomUUID();
        int now = 1000;
        MobAiLockRegistry.noteLock(id, now, 40, true);
        Optional<MobAiLockRegistry.Entry> e = MobAiLockRegistry.getForTests(id);
        assertTrue(e.isPresent());
        assertEquals(1040, e.get().expireAtTick());
        assertTrue(e.get().restoreAi());
    }

    @Test
    void longerWinsExtendsExpireKeepsRestore() {
        UUID id = UUID.randomUUID();
        MobAiLockRegistry.noteLock(id, 1000, 40, true);   // expire 1040
        MobAiLockRegistry.noteLock(id, 1010, 50, false);  // would expire 1060 — wins
        Optional<MobAiLockRegistry.Entry> e = MobAiLockRegistry.getForTests(id);
        assertEquals(1060, e.get().expireAtTick());
        assertTrue(e.get().restoreAi()); // first restore kept
    }

    @Test
    void shorterReapplyIgnored() {
        UUID id = UUID.randomUUID();
        MobAiLockRegistry.noteLock(id, 1000, 100, true); // expire 1100
        MobAiLockRegistry.noteLock(id, 1050, 10, false); // expire 1060 < 1100
        assertEquals(1100, MobAiLockRegistry.getForTests(id).get().expireAtTick());
    }

    @Test
    void pollExpiredReturnsEntryAndRemoves() {
        UUID id = UUID.randomUUID();
        MobAiLockRegistry.noteLock(id, 1000, 40, true);
        assertTrue(MobAiLockRegistry.pollExpired(id, 1039).isEmpty());
        Optional<MobAiLockRegistry.Entry> expired = MobAiLockRegistry.pollExpired(id, 1040);
        assertTrue(expired.isPresent());
        assertTrue(expired.get().restoreAi());
        assertTrue(MobAiLockRegistry.getForTests(id).isEmpty());
    }

    @Test
    void unloadValidTakesRestoreValue() {
        UUID id = UUID.randomUUID();
        MobAiLockRegistry.noteLock(id, 1000, 40, false);
        Optional<Boolean> restore = MobAiLockRegistry.takeForUnloadRestore(id, true);
        assertTrue(restore.isPresent());
        assertFalse(restore.get());
        assertTrue(MobAiLockRegistry.getForTests(id).isEmpty());
    }

    @Test
    void unloadInvalidDropsWithoutRestoreValue() {
        UUID id = UUID.randomUUID();
        MobAiLockRegistry.noteLock(id, 1000, 40, true);
        Optional<Boolean> restore = MobAiLockRegistry.takeForUnloadRestore(id, false);
        assertTrue(restore.isEmpty()); // no restore signal — entity already gone
        assertTrue(MobAiLockRegistry.getForTests(id).isEmpty());
    }
}
