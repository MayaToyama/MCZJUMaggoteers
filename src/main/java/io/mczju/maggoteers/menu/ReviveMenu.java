package io.mczju.maggoteers.menu;

import com.github.mczjuops.mczjugamecore.game.AbstractGame;
import com.github.mczjuops.mczjugamecore.menu.Menu;
import com.github.mczjuops.mczjugamecore.player.PlayerExt;
import io.mczju.maggoteers.item.ItemKind;
import io.mczju.maggoteers.item.ItemService;
import io.mczju.maggoteers.state.PlayerState;
import io.mczju.maggoteers.state.PlayerStateManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

/**
 * 复活币 GUI（§11）：本局玩家头像列表。点击头像：
 * <ul>
 *   <li>死者（观察者）→ {@link PlayerStateManager#revivePlayer}（仅复活，不加次数）</li>
 *   <li>活者（冒险）→ {@link PlayerStateManager#addReviveCount}(+1)</li>
 * </ul>
 * 成功后扣点击者 1 枚复活币。需在休整期/任意时（对局内）可用。
 */
public class ReviveMenu extends Menu {
    private final AbstractGame game;

    public ReviveMenu(Player player, Object... args) {
        super(player, args);
        this.game = (AbstractGame) args[0];
    }

    @Override protected String getTitle() { return "复活币 · 选择目标"; }
    @Override protected int getRows() { return 3; }
    @Override protected String getPermission() { return "maggoteers.play"; }

    @Override
    protected void setup() {
        int slot = 10;
        for (PlayerExt pe : game.getPlayers()) {
            Player target = pe.player();
            PlayerState st = PlayerStateManager.get(game, target.getUniqueId());
            boolean dead = st == null || !st.isAlive();
            setSlot(slot, headOf(target, dead), (clicker, ev) -> onPick(clicker.player(), target, dead));
            slot += 2;
            if (slot > 16) break;
        }
    }

    private void onPick(Player clicker, Player target, boolean targetDead) {
        // 扣 1 复活币
        if (!ItemService.spendOneKind(clicker, ItemKind.REVIVE_COIN)) {
            clicker.sendMessage(Component.text("没有复活币了！", NamedTextColor.RED));
            return;
        }
        if (targetDead) {
            boolean ok = PlayerStateManager.revivePlayer(game, target.getUniqueId());
            if (ok) {
                game.sender().info("<green>" + clicker.getName() + " 用复活币救回了 " + target.getName() + "！");
            } else {
                // 失败：退还（理论不发生，targetDead 已判定）
                ItemService.giveKind(clicker, ItemKind.REVIVE_COIN, 1);
                clicker.sendMessage(Component.text("该玩家无需复活。", NamedTextColor.GRAY));
                return;
            }
        } else {
            PlayerStateManager.addReviveCount(game, target.getUniqueId(), 1);
            game.sender().info("<green>" + clicker.getName() + " 用复活币给 " + target.getName() + " +1 复活次数。");
        }
        clicker.closeInventory();
    }

    private static ItemStack headOf(Player target, boolean dead) {
        ItemStack skull = new ItemStack(dead ? Material.SKELETON_SKULL : Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) skull.getItemMeta();
        if (meta != null) {
            meta.setPlayerProfile(target.getPlayerProfile());
            meta.displayName(Component.text(target.getName() + (dead ? "（倒下）" : "（存活）"),
                    dead ? NamedTextColor.RED : NamedTextColor.GREEN));
            skull.setItemMeta(meta);
        }
        return skull;
    }
}
