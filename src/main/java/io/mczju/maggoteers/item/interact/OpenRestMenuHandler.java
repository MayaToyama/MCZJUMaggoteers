package io.mczju.maggoteers.item.interact;

import com.github.mczjuops.mczjugamecore.game.AbstractGame;
import com.github.mczjuops.mczjugamecore.menu.MenuFacade;
import io.mczju.maggoteers.wave.WaveScheduler;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** 普通/Boss 货币右键 → 打开休整升级菜单（仅 REST 相位）。 */
public final class OpenRestMenuHandler implements ItemUseHandler {
    @Override
    public void onUse(Player player, AbstractGame game, ItemStack stack) {
        if (!WaveScheduler.isRestPhase(game)) {
            player.sendMessage(Component.text("仅在波次休整期可打开升级菜单。", NamedTextColor.GRAY));
            return;
        }
        MenuFacade.open("maggoteers-rest", player, game);
    }
}
