package io.mczju.maggoteers.item;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.UseCooldown;
import net.kyori.adventure.key.Key;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;

/**
 * 物品 CD 门禁（§10.3 D2）：按 **PDC id（maggoteers:id）** 的时间戳表。
 * 视觉 CD 走 ItemCreator {@code useCooldown} 组件 + Paper {@link Player#setCooldown(ItemStack, int)}。
 * <p>主线程同步访问，无需加锁。时钟可注入（{@link #useTestClock(LongSupplier)}）便于单测。
 */
public final class CooldownService {
    private static final Map<UUID, Map<String, Long>> EXPIRE = new HashMap<>();
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

    /** 逻辑 CD + ItemCreator 使用冷却条（若物品带 useCooldown 组件）。 */
    public static boolean tryUse(Player player, ItemStack stack, String pdcId, int cooldownSec) {
        if (!tryUse(player.getUniqueId(), pdcId, cooldownSec)) return false;
        applyVisualUseCooldown(player, stack, cooldownSec);
        return true;
    }

    /** 触发原版/ItemCreator 使用冷却 UI（按 cooldownGroup，不与 Material 串 CD）。 */
    public static void applyVisualUseCooldown(Player player, ItemStack stack, int fallbackSec) {
        if (player == null || stack == null || stack.getType().isAir()) return;
        UseCooldown uc = stack.getData(DataComponentTypes.USE_COOLDOWN);
        int ticks;
        if (uc != null) {
            ticks = Math.max(1, Math.round(uc.seconds() * 20f));
            Key group = uc.cooldownGroup();
            if (group != null) {
                player.setCooldown(group, ticks);
                return;
            }
        } else if (fallbackSec > 0) {
            ticks = fallbackSec * 20;
        } else {
            return;
        }
        player.setCooldown(stack, ticks);
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
