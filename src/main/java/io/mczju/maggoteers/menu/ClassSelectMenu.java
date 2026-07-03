package io.mczju.maggoteers.menu;

import com.github.mczjuops.mczjugamecore.game.AbstractGame;
import com.github.mczjuops.mczjugamecore.menu.Menu;
import io.mczju.maggoteers.game.ClassSelectGate;
import io.mczju.maggoteers.game.MaggoteersGame;
import io.mczju.maggoteers.item.ItemKind;
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

/** 开局职业 3 选 1（复用 reward_pools.class）。 */
public class ClassSelectMenu extends Menu {
    private static final int[] SLOTS = {11, 13, 15};

    private final AbstractGame game;
    private final List<RewardOption> offers;

    public ClassSelectMenu(Player player, Object... args) {
        super(player, args);
        this.game = (AbstractGame) args[0];
        var pool = RewardService.pool("class");
        this.offers = pool == null ? List.of() : pool.options();
    }

    @Override protected String getTitle() { return "选择职业"; }
    @Override protected int getRows() { return 3; }
    @Override protected String getPermission() { return "maggoteers.play"; }

    @Override
    protected void setup() {
        int n = Math.min(SLOTS.length, offers.size());
        for (int i = 0; i < n; i++) {
            RewardOption opt = offers.get(i);
            Material mat = switch (opt.category()) {
                case WEAPON -> Material.IRON_SWORD;
                case STAT -> Material.GOLDEN_APPLE;
                case SUPPLY -> Material.CHEST;
            };
            setSlot(SLOTS[i], icon(opt, mat), (clicker, ev) -> {
                Player p = clicker.player();
                if (ClassSelectGate.hasChosen(game, p.getUniqueId())) return;
                if (!ItemService.spendOneKind(p, ItemKind.CLASS_TICKET)) {
                    p.sendMessage(Component.text("需要手持职业选择券才能确认！", NamedTextColor.RED));
                    return;
                }
                RewardService.apply(p, opt);
                ClassSelectGate.markChosen(game, p.getUniqueId());
                p.closeInventory();
                p.sendMessage(Component.text("职业已选定！", NamedTextColor.GREEN));
                if (ClassSelectGate.allChosen(game) && game instanceof MaggoteersGame mg) {
                    mg.onAllClassesChosen();
                }
            });
        }
    }

    private static ItemStack icon(RewardOption opt, Material mat) {
        ItemStack stack = ItemService.createItem(opt.item(), 1).orElse(new ItemStack(mat));
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(opt.displayPlain(), NamedTextColor.GOLD)
                    .decoration(TextDecoration.ITALIC, false));
            List<Component> lore = new ArrayList<>();
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
