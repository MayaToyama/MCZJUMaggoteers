package io.mczju.maggoteers.menu;

import com.github.mczjuops.mczjugamecore.menu.Menu;
import com.github.mczjuops.mczjugamecore.menu.MenuFacade;
import com.github.mczjuops.mczjugamecore.player.PlayerExt;
import io.mczju.maggoteers.persist.MaggoteersPlayerData;
import io.mczju.maggoteers.reward.RewardOption;
import io.mczju.maggoteers.reward.RewardService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * 局外解锁商店（§13.3）：商品 = 所有池里 requires_unlock:true 的选项。
 * 点击：balance >= unlock_cost → 扣 balance、unlocks.add(id)。
 */
public class UnlockShopMenu extends Menu {

    public UnlockShopMenu(Player player, Object... args) {
        super(player, args);
    }

    @Override protected String getTitle() { return "<gold>卫戍商店 · 解锁"; }
    @Override protected int getRows() { return 6; }
    @Override protected String getPermission() { return "maggoteers.play"; }

    @Override
    protected void setup() {
        Player p = player.player();
        var pe = new PlayerExt(p);
        var data = pe.getData(MaggoteersPlayerData.class);
        List<RewardOption> goods = new ArrayList<>();
        for (String poolId : RewardService.allPoolIds()) {
            var pool = RewardService.pool(poolId);
            if (pool == null) continue;
            for (RewardOption o : pool.options()) {
                if (o.requiresUnlock()) goods.add(o);
            }
        }
        int slot = 10;
        for (RewardOption o : goods) {
            if (slot > 43) break;
            if ((slot + 1) % 9 == 0) slot += 2;   // 跳过边列
            boolean owned = data != null && data.unlocks.contains(o.id());
            setSlot(slot++, icon(o, o.unlockCost(), owned), (clicker, ev) -> {
                if (data == null) return;
                if (owned || data.unlocks.contains(o.id())) {
                    p.sendMessage(Component.text("已拥有：" + o.displayPlain(), NamedTextColor.GRAY));
                    return;
                }
                if (!data.spend(o.unlockCost())) {
                    p.sendMessage(Component.text("卫戍币不足（需 " + o.unlockCost() + "）", NamedTextColor.RED));
                    return;
                }
                data.unlock(o.id());
                p.sendMessage(Component.text("已解锁：" + o.displayPlain(), NamedTextColor.GREEN));
                p.closeInventory();
                MenuFacade.open("maggoteers-shop", p);   // 刷新
            });
        }
    }

    private static ItemStack icon(RewardOption o, int cost, boolean owned) {
        ItemStack s = new ItemStack(owned ? Material.ENCHANTED_BOOK : (o.icon() != null ? o.icon() : Material.PAPER));
        ItemMeta m = s.getItemMeta();
        if (m != null) {
            m.displayName(Component.text(o.displayPlain(), owned ? NamedTextColor.GREEN : NamedTextColor.AQUA)
                    .decoration(TextDecoration.ITALIC, false));
            var lore = new ArrayList<Component>();
            lore.add(Component.text(owned ? "已解锁" : ("花费 " + cost + " 卫戍币"), NamedTextColor.YELLOW)
                    .decoration(TextDecoration.ITALIC, false));
            if (o.description() != null && !o.description().isBlank()) {
                lore.add(Component.text(o.description(), NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
            }
            m.lore(lore);
            s.setItemMeta(m);
        }
        return s;
    }
}
