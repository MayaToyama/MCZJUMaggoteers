package io.mczju.maggoteers.menu;

import com.github.mczjuops.mczjugamecore.menu.AlertMenu;
import com.github.mczjuops.mczjugamecore.menu.Menu;
import com.github.mczjuops.mczjugamecore.menu.MenuFacade;
import com.github.mczjuops.mczjugamecore.player.PlayerExt;
import io.mczju.maggoteers.config.MessageService;
import io.mczju.maggoteers.persist.MaggoteersPlayerData;
import io.mczju.maggoteers.reward.RewardOption;
import io.mczju.maggoteers.reward.RewardOptionIcons;
import io.mczju.maggoteers.reward.RewardService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 局外解锁商店（§13.3）：商品 = 所有池里 requires_unlock:true 的选项（按 id 去重）。
 * 点击：balance >= unlock_cost → 扣 balance、unlocks.add(id)。
 */
public class UnlockShopMenu extends Menu {

    public UnlockShopMenu(Player player, Object... args) {
        super(player, args);
    }

    @Override protected String getTitle() {
        return MessageService.raw("menu.shop.title", Map.of());
    }
    @Override protected int getRows() { return 6; }
    @Override protected String getPermission() { return "maggoteers.play"; }

    @Override
    protected void setup() {
        Player p = player.player();
        var pe = new PlayerExt(p);
        var data = pe.getData(MaggoteersPlayerData.class);
        Map<String, RewardOption> byId = new LinkedHashMap<>();
        for (String poolId : RewardService.allPoolIds()) {
            var pool = RewardService.pool(poolId);
            if (pool == null) continue;
            for (RewardOption o : pool.options()) {
                if (!o.requiresUnlock()) continue;
                byId.putIfAbsent(o.id(), o);
            }
        }
        List<RewardOption> goods = new ArrayList<>(byId.values());
        int slot = 10;
        for (RewardOption o : goods) {
            if (slot > 43) break;
            if ((slot + 1) % 9 == 0) slot += 2;   // 跳过边列
            boolean owned = data != null && data.unlocks.contains(o.id());
            setSlot(slot++, icon(o, p, o.unlockCost(), owned), (clicker, ev) -> {
                if (data == null) return;
                if (owned || data.unlocks.contains(o.id())) {
                    p.sendMessage(MessageService.component("menu.shop.already_owned", Map.of(
                            "display", o.displayPlain())));
                    return;
                }
                if (data.balance < o.unlockCost()) {
                    p.sendMessage(MessageService.component("menu.shop.not_enough", Map.of(
                            "cost", String.valueOf(o.unlockCost()))));
                    return;
                }
                // 二次确认（§13.3，R14）：确认后才扣费解锁
                new AlertMenu(p, () -> {
                    if (data.unlocks.contains(o.id())) return;
                    if (!data.spend(o.unlockCost())) {
                        p.sendMessage(MessageService.component("menu.shop.not_enough", Map.of(
                                "cost", String.valueOf(o.unlockCost()))));
                        return;
                    }
                    data.unlock(o.id());
                    p.sendMessage(MessageService.component("menu.shop.unlocked", Map.of(
                            "display", o.displayPlain())));
                    p.closeInventory();
                    MenuFacade.open("maggoteers-shop", p);   // 刷新
                }).open();
            });
        }
    }

    private static ItemStack icon(RewardOption o, Player viewer, int cost, boolean owned) {
        ItemStack base = owned
                ? new ItemStack(Material.ENCHANTED_BOOK)
                : RewardOptionIcons.preview(o, viewer, null);
        ItemStack s = base.clone();
        ItemMeta m = s.getItemMeta();
        if (m != null) {
            if (s.getType() == Material.PAPER || owned) {
                m.displayName(Component.text(o.displayPlain(), owned ? NamedTextColor.GREEN : NamedTextColor.AQUA)
                        .decoration(TextDecoration.ITALIC, false));
            }
            var lore = m.lore() != null ? new ArrayList<>(m.lore()) : new ArrayList<Component>();
            lore.add((owned
                    ? MessageService.component("menu.shop.lore_owned", Map.of())
                    : MessageService.component("menu.shop.lore_cost", Map.of("cost", String.valueOf(cost))))
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
