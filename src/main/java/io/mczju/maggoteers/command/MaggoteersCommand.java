package io.mczju.maggoteers.command;

import com.github.mczjuops.mczjugamecore.menu.MenuFacade;
import com.github.mczjuops.mczjugamecore.player.PlayerExt;
import io.mczju.maggoteers.game.MaggoteersGame;
import io.mczju.maggoteers.item.ItemKind;
import io.mczju.maggoteers.item.ItemService;
import io.mczju.maggoteers.state.PlayerState;
import io.mczju.maggoteers.state.PlayerStateManager;
import io.mczju.maggoteers.wave.WaveEngine;
import io.mczju.maggoteers.wave.WaveScheduler;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * /maggoteers 命令。
 * <ul>
 *   <li>{@code shop} —— 局外解锁商店（所有人）。</li>
 *   <li>{@code debug state|plan|wave|act|give|coin} —— 运维/调试（需 maggoteers.admin）。</li>
 * </ul>
 */
public class MaggoteersCommand implements CommandExecutor {

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player p)) { sender.sendMessage("仅玩家可用。"); return true; }
        if (args.length == 0) { usage(p); return true; }

        switch (args[0].toLowerCase()) {
            case "shop" -> { MenuFacade.open("maggoteers-shop", p); return true; }
            case "debug" -> { return debug(p, args); }
            default -> { usage(p); return true; }
        }
    }

    private boolean debug(Player p, String[] args) {
        if (!p.hasPermission("maggoteers.admin")) {
            p.sendMessage(Component.text("无权限（需 maggoteers.admin）。", NamedTextColor.RED));
            return true;
        }
        if (args.length < 2) {
            p.sendMessage(Component.text("用法：/maggoteers debug <state|plan|wave|act|give|coin> [...]", NamedTextColor.GRAY));
            return true;
        }
        var pe = new PlayerExt(p);
        MaggoteersGame game = pe.isInGame() ? (MaggoteersGame) pe.getGame() : null;
        switch (args[1].toLowerCase()) {
            case "state" -> {
                if (game == null) { msg(p, "不在局内。"); return true; }
                PlayerState st = PlayerStateManager.get(game, p.getUniqueId());
                if (st == null) { msg(p, "无 PlayerState。"); return true; }
                msg(p, "reviveCount=" + st.getReviveCount() + " alive=" + st.isAlive()
                        + " effects=" + st.effects().size() + " acquiredUnique=" + st.acquiredUnique().size());
            }
            case "plan" -> {
                if (game == null) { msg(p, "不在局内。"); return true; }
                int[] prog = WaveScheduler.progress(game);
                msg(p, "actIndex=" + prog[0] + " waveIndex=" + prog[1]);
            }
            case "wave" -> {
                if (game == null) { msg(p, "不在局内。"); return true; }
                var snap = WaveScheduler.snapshot(game);
                int living = WaveEngine.livingCount(game);
                msg(p, "act=" + (snap == null ? "?" : snap.actIndex()) + " tier=" + (snap == null ? "?" : snap.lastTier())
                        + " rest=" + WaveScheduler.isRestPhase(game) + " livingMobs=" + living);
            }
            case "act" -> {
                if (game == null) { msg(p, "不在局内。"); return true; }
                int[] prog = WaveScheduler.progress(game);
                msg(p, "当前层=" + (prog[0] + 1) + "/3  当前层波=" + (prog[1] + 1));
            }
            case "give" -> {
                // /maggoteers debug give <item_id> [amount]
                if (args.length < 3) { msg(p, "用法：/maggoteers debug give <item_id> [amount]"); return true; }
                String id = args[2];
                int amt = args.length > 3 ? safeInt(args[3], 1) : 1;
                ItemService.give(p, id, amt);
                msg(p, "已给 " + id + " ×" + amt);
            }
            case "coin" -> {
                // /maggoteers debug coin [amount]
                int amt = args.length > 2 ? safeInt(args[2], 1) : 5;
                ItemService.giveKind(p, ItemKind.CURRENCY_NORMAL, amt);
                msg(p, "已给普通卫戍币 ×" + amt);
            }
            default -> msg(p, "未知 debug 子命令：" + args[1]);
        }
        return true;
    }

    private static void usage(Player p) {
        p.sendMessage(Component.text("/maggoteers <shop|debug ...>", NamedTextColor.YELLOW));
    }

    private static void msg(Player p, String text) {
        p.sendMessage(Component.text(text, NamedTextColor.AQUA));
    }

    private static int safeInt(String s, int def) {
        try { return Integer.parseInt(s); } catch (Exception e) { return def; }
    }
}
