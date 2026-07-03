package io.mczju.maggoteers.item;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;

/**
 * 物品 CD 门禁（§10.3 D2）：按 **PDC id（maggoteers:id）** 的时间戳表。
 * **不用** {@code setCooldown(Material)}（同材质不同 PDC 会串 CD）。
 * <p>主线程同步访问，无需加锁。时钟可注入（{@link #useTestClock(LongSupplier)}）便于单测。
 * <p>未来魔法武器 handler 在其 {@code onUse} 内调用 {@link #tryUse} 做门禁。
 */
public final class CooldownService {
    private static final Map<UUID, Map<String, Long>> EXPIRE = new HashMap<>();   // player -> pdcId -> expireMillis
    private static LongSupplier clock = System::currentTimeMillis;

    /** 放行且计 CD -> true；在 CD 内 -> false。cooldownSec<=0 视为无 CD（恒放行）。 */
    public static boolean tryUse(UUID player, String pdcId, int cooldownSec) {
        if (cooldownSec <= 0) return true;
        long now = clock.getAsLong();
        long exp = EXPIRE.getOrDefault(player, Map.of()).getOrDefault(pdcId, 0L);
        if (now < exp) return false;
        EXPIRE.computeIfAbsent(player, k -> new HashMap<>()).put(pdcId, now + cooldownSec * 1000L);
        return true;
    }

    public static boolean onCooldown(UUID player, String pdcId) {
        return clock.getAsLong() < EXPIRE.getOrDefault(player, Map.of()).getOrDefault(pdcId, 0L);
    }

    /** 玩家退出/对局结束清理。 */
    public static void clear(UUID player) { EXPIRE.remove(player); }

    /** 单测注入时钟。 */
    static void useTestClock(LongSupplier c) { clock = c; }
    static void resetClock() { clock = System::currentTimeMillis; }

    private CooldownService() {}
}
