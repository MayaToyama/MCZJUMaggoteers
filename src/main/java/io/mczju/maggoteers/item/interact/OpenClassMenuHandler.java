package io.mczju.maggoteers.item.interact;

import com.github.mczjuops.mczjugamecore.game.AbstractGame;
import com.github.mczjuops.mczjugamecore.menu.MenuFacade;
import io.mczju.maggoteers.game.ClassSelectGate;
import io.mczju.maggoteers.game.MaggoteersGame;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** 职业选择券右键 → 打开职业菜单（仅开局选职业阶段、且未选过）。 */
public final class OpenClassMenuHandler implements ItemUseHandler {
    @Override
    public void onUse(Player player, AbstractGame game, ItemStack stack) {
        if (!(game instanceof MaggoteersGame mg) || !mg.isInClassSelectPhase()) {
            player.sendMessage(Component.text("当前无法使用职业选择券。", NamedTextColor.GRAY));
            return;
        }
        if (ClassSelectGate.hasChosen(game, player.getUniqueId())) {
            player.sendMessage(Component.text("你已选定职业。", NamedTextColor.YELLOW));
            return;
        }
        MenuFacade.open("maggoteers-class", player, game);
    }
}
