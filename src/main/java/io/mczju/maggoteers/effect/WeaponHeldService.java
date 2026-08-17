package io.mczju.maggoteers.effect;

import io.mczju.maggoteers.game.MaggoteersGame;
import io.mczju.maggoteers.item.ItemService;
import io.mczju.maggoteers.state.PlayerState;
import io.mczju.maggoteers.state.PlayerStateManager;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

/** Mount/unmount main-hand weapon passives ({@code held:<itemId>:<n>}). */
public final class WeaponHeldService {

    public static final String HELD_PREFIX = "held:";

    private WeaponHeldService() {}

    public static void sync(Player p, MaggoteersGame game) {
        if (p == null || game == null) return;
        PlayerState st = PlayerStateManager.get(game, p.getUniqueId());
        if (st == null) return;

        String itemId = ItemService.itemIdOf(p.getInventory().getItemInMainHand());
        String mountedItemId = findMountedItemId(st.effects());
        if (Objects.equals(mountedItemId, itemId)) {
            return;
        }

        if (mountedItemId != null) {
            removeHeldForItem(st, mountedItemId);
        } else if (hasAnyHeld(st)) {
            removeAllHeld(st);
        }
        if (itemId != null) {
            mountHeld(st, itemId);
        }
        EffectService.resyncDerived(p, st); // 内部会 AuraService.refresh，恢复被 strip 的光环药水
    }

    static boolean removeHeldForItem(PlayerState st, String itemId) {
        String prefix = HELD_PREFIX + itemId + ":";
        return removeByPrefix(st, prefix);
    }

    static boolean removeAllHeld(PlayerState st) {
        return removeByPrefix(st, HELD_PREFIX);
    }

    private static boolean removeByPrefix(PlayerState st, String prefix) {
        boolean aura = false;
        Iterator<PlayerEffect> it = st.effects().iterator();
        while (it.hasNext()) {
            PlayerEffect e = it.next();
            if (!e.id().startsWith(prefix)) {
                continue;
            }
            if (e.effect() == Effect.AURA) {
                aura = true;
            }
            it.remove();
        }
        return aura;
    }

    private static boolean mountHeld(PlayerState st, String itemId) {
        List<WeaponHeldEntry> entries = WeaponHeldRegistry.get(itemId);
        if (entries.isEmpty()) {
            return false;
        }
        boolean aura = false;
        List<PlayerEffect> merged = new ArrayList<>(st.effects());
        for (int i = 0; i < entries.size(); i++) {
            WeaponHeldEntry entry = entries.get(i);
            if (entry.effect() == Effect.AURA) {
                aura = true;
            }
            PlayerEffect pe = toPlayerEffect(itemId, i, entry);
            merged = EffectStacker.merge(merged, pe);
        }
        st.effects().clear();
        st.effects().addAll(merged);
        return aura;
    }

    static PlayerEffect toPlayerEffect(String itemId, int index, WeaponHeldEntry entry) {
        Stack stack = entry.stack() != null ? entry.stack() : Stack.IGNORE;
        EffectContext params = entry.params() != null ? entry.params().copyDeep() : new EffectContext();
        return new PlayerEffect(
                HELD_PREFIX + itemId + ":" + index,
                entry.effect(),
                params,
                entry.fireTrigger(),
                null,
                0,
                0,
                null,
                stack,
                0,
                0);
    }

    static String findMountedItemId(List<PlayerEffect> effects) {
        for (PlayerEffect e : effects) {
            String parsed = parseHeldItemId(e.id());
            if (parsed != null) {
                return parsed;
            }
        }
        return null;
    }

    static String parseHeldItemId(String id) {
        if (id == null || !id.startsWith(HELD_PREFIX)) {
            return null;
        }
        String withoutGrant = id.endsWith(TriggeredGrantAttribute.GRANT_SUFFIX)
                ? id.substring(0, id.length() - TriggeredGrantAttribute.GRANT_SUFFIX.length())
                : id;
        String rest = withoutGrant.substring(HELD_PREFIX.length());
        int lastColon = rest.lastIndexOf(':');
        if (lastColon <= 0) {
            return null;
        }
        String indexPart = rest.substring(lastColon + 1);
        if (!indexPart.chars().allMatch(Character::isDigit)) {
            return null;
        }
        return rest.substring(0, lastColon);
    }

    private static boolean hasAnyHeld(PlayerState st) {
        for (PlayerEffect e : st.effects()) {
            if (e.id().startsWith(HELD_PREFIX)) {
                return true;
            }
        }
        return false;
    }
}
