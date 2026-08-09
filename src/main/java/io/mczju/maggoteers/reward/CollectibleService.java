package io.mczju.maggoteers.reward;

import io.mczju.maggoteers.effect.PlayerEffect;
import io.mczju.maggoteers.effect.Stack;
import io.mczju.maggoteers.game.MaggoteersGame;
import io.mczju.maggoteers.item.ItemService;
import io.mczju.maggoteers.item.RunItemTags;
import io.mczju.maggoteers.state.PlayerState;
import io.mczju.maggoteers.state.PlayerStateManager;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;

public final class CollectibleService {
    private static final Logger LOG = Logger.getLogger("Maggoteers");

    private CollectibleService() {}

    /** GUI / shop preview for a fixed level. */
    public static Optional<ItemStack> preview(String rewardId, int level) {
        return CollectibleRegistry.resolveItemId(rewardId, level)
                .flatMap(id -> ItemService.createItem(id, 1))
                .map(CollectibleService::cloneForGui);
    }

    /** Pick-menu preview: next upgrade level when owned, else level 1. */
    public static Optional<ItemStack> preview(String rewardId, Player player, MaggoteersGame game) {
        if (player == null || game == null) return preview(rewardId, 1);
        PlayerState ps = PlayerStateManager.get(game, player.getUniqueId());
        PlayerEffect owned = ps == null ? null : ps.effects().stream()
                .filter(e -> e.id().equals(rewardId)).findFirst().orElse(null);
        boolean upgrade = CollectibleRegistry.get(rewardId).map(CollectibleMapping::isLeveled).orElse(false)
                || (owned != null && owned.stack() == Stack.UPGRADE_LEVEL);
        int level = previewLevel(owned, upgrade);
        return preview(rewardId, level);
    }

    static int previewLevel(PlayerEffect owned, boolean upgradePath) {
        if (owned == null || !upgradePath) return 1;
        if (owned.stack() == Stack.UPGRADE_LEVEL) {
            return Math.min(owned.level() + 1, owned.upgradeMax());
        }
        return owned.level();
    }

    public static boolean grant(Player player, String rewardId, int level) {
        if (player == null || rewardId == null) return false;
        Optional<String> itemId = CollectibleRegistry.resolveItemId(rewardId, level);
        if (itemId.isEmpty()) {
            LOG.warning("CollectibleService.grant: no item for " + rewardId + " level " + level);
            return false;
        }
        remove(player, rewardId);
        Optional<ItemStack> created = ItemService.createItem(itemId.get(), 1);
        if (created.isEmpty()) {
            LOG.warning("CollectibleService.grant: ItemCreator missing " + itemId.get());
            return false;
        }
        ItemStack stack = created.get();
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            RunItemTags.setCollectiblePdc(meta, rewardId, level);
            stack.setItemMeta(meta);
        }
        var leftover = player.getInventory().addItem(stack);
        if (!leftover.isEmpty()) {
            LOG.warning("CollectibleService.grant: inventory full for " + player.getName() + " " + rewardId);
        }
        return true;
    }

    public static int remove(Player player, String rewardId) {
        if (player == null || rewardId == null) return 0;
        int removed = 0;
        var inv = player.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (!isCollectibleFor(stack, rewardId)) continue;
            inv.setItem(i, null);
            removed++;
        }
        ItemStack off = inv.getItemInOffHand();
        if (isCollectibleFor(off, rewardId)) {
            inv.setItemInOffHand(null);
            removed++;
        }
        return removed;
    }

    public static void resync(Player player, MaggoteersGame game) {
        if (player == null || game == null) return;
        PlayerState ps = PlayerStateManager.get(game, player.getUniqueId());
        if (ps == null) return;

        Set<String> keepCharmIds = new HashSet<>();
        for (PlayerEffect pe : ps.effects()) {
            if (!CollectibleRegistry.hasMapping(pe.id())) continue;
            keepCharmIds.add(pe.id());
            if (!hasCharm(player, pe.id(), pe.level())) {
                grant(player, pe.id(), pe.level());
            }
        }
        for (String acquiredId : ps.acquiredUnique()) {
            if (!CollectibleRegistry.hasMapping(acquiredId)) continue;
            keepCharmIds.add(acquiredId);
            if (!hasCharm(player, acquiredId, 1)) {
                grant(player, acquiredId, 1);
            }
        }

        for (ItemStack stack : allInventoryStacks(player)) {
            RunItemTags.readRewardId(stack).ifPresent(rid -> {
                if (!keepCharmIds.contains(rid)) {
                    remove(player, rid);
                }
            });
        }
    }

    private static boolean hasCharm(Player player, String rewardId, int level) {
        for (ItemStack stack : allInventoryStacks(player)) {
            if (isCollectibleFor(stack, rewardId) && RunItemTags.readRewardLevel(stack) == level) {
                return true;
            }
        }
        return false;
    }

    private static boolean isCollectibleFor(ItemStack stack, String rewardId) {
        return RunItemTags.readRewardId(stack).filter(rewardId::equals).isPresent();
    }

    private static Iterable<ItemStack> allInventoryStacks(Player player) {
        java.util.List<ItemStack> out = new java.util.ArrayList<>();
        for (ItemStack s : player.getInventory().getContents()) {
            if (s != null && !s.getType().isAir()) out.add(s);
        }
        ItemStack off = player.getInventory().getItemInOffHand();
        if (off != null && !off.getType().isAir()) out.add(off);
        return out;
    }

    private static ItemStack cloneForGui(ItemStack stack) {
        return stack.clone();
    }
}
