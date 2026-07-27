package io.mczju.maggoteers.item;

import io.mczju.maggoteers.MaggoteersPlugin;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Optional;

/** PDC helpers for run-bound items (collectibles, initial gear, plugin items). */
public final class RunItemTags {
    private RunItemTags() {}

    public static NamespacedKey rewardIdKey() {
        return new NamespacedKey(MaggoteersPlugin.getInstance(), "reward_id");
    }

    public static NamespacedKey rewardLevelKey() {
        return new NamespacedKey(MaggoteersPlugin.getInstance(), "reward_level");
    }

    public static boolean isRunItem(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) return false;
        if (ItemService.readKind(stack) != null) return true;
        return ItemService.itemIdOf(stack) != null;
    }

    public static Optional<String> readRewardId(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) return Optional.empty();
        String raw = stack.getItemMeta().getPersistentDataContainer()
                .get(rewardIdKey(), PersistentDataType.STRING);
        return raw == null || raw.isBlank() ? Optional.empty() : Optional.of(raw);
    }

    public static int readRewardLevel(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) return 0;
        Integer lv = stack.getItemMeta().getPersistentDataContainer()
                .get(rewardLevelKey(), PersistentDataType.INTEGER);
        return lv == null ? 0 : lv;
    }

    public static void setCollectiblePdc(ItemMeta meta, String rewardId, int level) {
        if (meta == null) return;
        var pdc = meta.getPersistentDataContainer();
        pdc.set(new NamespacedKey(MaggoteersPlugin.getInstance(), "kind"),
                PersistentDataType.STRING, ItemKind.COLLECTIBLE.pdcValue());
        pdc.set(rewardIdKey(), PersistentDataType.STRING, rewardId);
        pdc.set(rewardLevelKey(), PersistentDataType.INTEGER, level);
    }
}
