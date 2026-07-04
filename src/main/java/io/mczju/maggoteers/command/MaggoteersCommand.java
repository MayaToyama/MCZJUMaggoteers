package io.mczju.maggoteers.command;

import com.github.mczjuops.mczjugamecore.menu.MenuFacade;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/** /maggoteers shop —— 打开局外解锁商店（Plan 8 扩展更多子命令）。 */
public class MaggoteersCommand implements CommandExecutor {
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player p)) { sender.sendMessage("仅玩家可用。"); return true; }
        if (args.length == 1 && "shop".equalsIgnoreCase(args[0])) {
            MenuFacade.open("maggoteers-shop", p);
            return true;
        }
        p.sendMessage("用法：/maggoteers shop");
        return true;
    }
}
