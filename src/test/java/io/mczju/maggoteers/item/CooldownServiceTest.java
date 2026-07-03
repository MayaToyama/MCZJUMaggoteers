package io.mczju.maggoteers.item;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;

class CooldownServiceTest {
    private final AtomicLong now = new AtomicLong(1_000_000L);

    CooldownServiceTest() { CooldownService.useTestClock(now::get); }

    @AfterEach void reset() { CooldownService.resetClock(); }

    @Test
    void firstUseAllowedThenBlockedUntilExpire() {
        UUID p = UUID.randomUUID();
        assertTrue(CooldownService.tryUse(p, "maggoteers:fire_sword", 3));   // 计 CD 3s
        assertFalse(CooldownService.tryUse(p, "maggoteers:fire_sword", 3));   // 在 CD
        now.addAndGet(2_999L);
        assertFalse(CooldownService.tryUse(p, "maggoteers:fire_sword", 3));   // 仍差 1ms
        now.addAndGet(1L);
        assertTrue(CooldownService.tryUse(p, "maggoteers:fire_sword", 3));    // 到点放行
    }

    @Test
    void differentPdcIdsDontShareCd() {
        UUID p = UUID.randomUUID();
        assertTrue(CooldownService.tryUse(p, "maggoteers:a", 5));
        assertTrue(CooldownService.tryUse(p, "maggoteers:b", 5));   // 不同 id 不串 CD
    }

    @Test
    void zeroOrNegativeCooldownAlwaysAllowed() {
        UUID p = UUID.randomUUID();
        assertTrue(CooldownService.tryUse(p, "maggoteers:x", 0));
        assertTrue(CooldownService.tryUse(p, "maggoteers:x", 0));
        assertTrue(CooldownService.tryUse(p, "maggoteers:x", -1));
    }

    @Test
    void clearWipesPlayer() {
        UUID p = UUID.randomUUID();
        CooldownService.tryUse(p, "maggoteers:y", 10);
        assertTrue(CooldownService.onCooldown(p, "maggoteers:y"));
        CooldownService.clear(p);
        assertFalse(CooldownService.onCooldown(p, "maggoteers:y"));
    }
}
