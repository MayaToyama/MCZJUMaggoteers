package io.mczju.maggoteers.menu;

import com.github.mczjuops.mczjugamecore.game.AbstractGame;
import com.github.mczjuops.mczjugamecore.menu.Menu;
import com.github.mczjuops.mczjugamecore.menu.MenuFacade;
import io.mczju.maggoteers.item.ItemService;
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

/** 3 选 1 奖励（休整菜单入口；货币已在 RestMenu 扣除）。 */
public class PickMenu extends Menu {
    private static final int[] SLOTS = {11, 13, 15};

    private final AbstractGame game;
    private final List<RewardOption> offers;

    @SuppressWarnings("unchecked")
    public PickMenu(Player player, Object... args) {
        super(player, args);
        this.game = (AbstractGame) args[0];
        this.offers = (List<RewardOption>) args[1];
    }

    @Override protected String getTitle() { return "3 选 1"; }
    @Override protected int getRows() { return 3; }
    @Override protected String getPermission() { return "maggoteers.play"; }

    @Override
    protected void setup() {
        for (int i = 0; i < Math.min(SLOTS.length, offers.size()); i++) {
            RewardOption opt = offers.get(i);
            setSlot(SLOTS[i], icon(opt), (clicker, ev) -> {
                Player p = clicker.player();
                boolean ok = RewardService.apply(p, opt, game);
                p.closeInventory();
                if (ok) {
                    MenuFacade.open("maggoteers-rest", p, game);
                } else {
                    p.sendMessage(Component.text("请重试或联系管理员。", NamedTextColor.GRAY));
                }
            });
        }
    }

    private static ItemStack icon(RewardOption opt) {
        Material mat = opt.icon() != null ? opt.icon() : switch (opt.category()) {
            case WEAPON -> Material.IRON_SWORD;
            case STAT -> Material.GOLDEN_APPLE;
            case SUPPLY -> Material.CHEST;
        };
        ItemStack stack = (opt.item() != null && !opt.item().isBlank())
                ? ItemService.createItem(opt.item(), 1).orElse(new ItemStack(mat))
                : new ItemStack(mat);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(opt.displayPlain(), NamedTextColor.AQUA)
                    .decoration(TextDecoration.ITALIC, false));
            var lore = new ArrayList<Component>();
            if (opt.description() != null && !opt.description().isBlank()) {
                lore.add(Component.text(opt.description(), NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false));
            }
            lore.add(Component.text("▶ 点击选择", NamedTextColor.YELLOW)
                    .decoration(TextDecoration.ITALIC, false));
            meta.lore(lore);
            stack.setItemMeta(meta);
        }
        return stack;
    }
}
