package io.mczju.maggoteers.game;

import com.github.mczjuops.mczjugamecore.game.AbstractGame;
import io.mczju.maggoteers.state.PlayerStateManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** 局内友军选取（以 PlayerState 为准，不依赖 MGC {@code getPlayers()}）。 */
public final class AllyTargeting {

    private AllyTargeting() {}

    public static MaggoteersGame resolveForPlayer(Player player, AbstractGame hint) {
        if (player == null) return null;
        MaggoteersGame bound = PlayerStateManager.gameOf(player);
        if (bound != null) return bound;
        if (hint instanceof MaggoteersGame mg) return mg;
        for (MaggoteersGame mg : ActiveMaggoteersGames.snapshot()) {
            if (PlayerStateManager.get(mg, player.getUniqueId()) != null) return mg;
        }
        return null;
    }

    /** 半径内存活的局内玩家；{@code includeSelf=true} 时包含施法者（单人有效）。 */
    public static List<Player> alliesInRadius(MaggoteersGame mg, Player origin, double radius, boolean includeSelf) {
        List<Player> out = new ArrayList<>();
        if (origin == null) return out;
        if (mg == null) mg = PlayerStateManager.gameForPlayer(origin.getUniqueId());
        if (mg == null) return out;

        Set<UUID> seen = new HashSet<>();
        for (UUID uuid : PlayerStateManager.uuidsInGame(mg)) {
            tryAdd(out, seen, mg, origin, Bukkit.getPlayer(uuid), radius, includeSelf);
        }
        if (includeSelf) {
            tryAdd(out, seen, mg, origin, origin, radius, true);
        }
        return out;
    }

    /** Allies within {@code radius} of {@code center} (for death-site area effects). */
    public static List<Player> alliesNear(MaggoteersGame mg, org.bukkit.Location center,
                                          double radius, Player caster, boolean includeSelf) {
        List<Player> out = new ArrayList<>();
        if (center == null || center.getWorld() == null) return out;
        if (mg == null && caster != null) {
            mg = PlayerStateManager.gameForPlayer(caster.getUniqueId());
        }
        if (mg == null) return out;

        Set<UUID> seen = new HashSet<>();
        for (UUID uuid : PlayerStateManager.uuidsInGame(mg)) {
            Player candidate = org.bukkit.Bukkit.getPlayer(uuid);
            if (candidate == null || seen.contains(uuid)) continue;
            if (!includeSelf && caster != null && candidate.getUniqueId().equals(caster.getUniqueId())) {
                continue;
            }
            if (candidate.getWorld() != center.getWorld()) continue;
            if (candidate.getLocation().distance(center) > radius) continue;
            if (!PlayerStateManager.isRunParticipant(mg, candidate)) continue;
            seen.add(uuid);
            out.add(candidate);
        }
        if (includeSelf && caster != null && !seen.contains(caster.getUniqueId())
                && PlayerStateManager.isRunParticipant(mg, caster)
                && caster.getWorld() == center.getWorld()
                && caster.getLocation().distance(center) <= radius) {
            out.add(caster);
        }
        return out;
    }

    private static void tryAdd(List<Player> out, Set<UUID> seen, MaggoteersGame mg, Player origin,
                               Player candidate, double radius, boolean includeSelf) {
        if (candidate == null || seen.contains(candidate.getUniqueId())) return;
        if (!includeSelf && candidate.getUniqueId().equals(origin.getUniqueId())) return;
        if (candidate.getWorld() != origin.getWorld()) return;
        if (candidate.getLocation().distance(origin.getLocation()) > radius) return;
            if (!PlayerStateManager.isRunParticipant(mg, candidate)) return;
        seen.add(candidate.getUniqueId());
        out.add(candidate);
    }
}
