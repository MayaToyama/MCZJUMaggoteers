package io.mczju.maggoteers.menu;

import com.github.mczjuops.mczjugamecore.game.AbstractGame;
import com.github.mczjuops.mczjugamecore.menu.Menu;
import io.mczju.maggoteers.game.ClassSelectGate;
import io.mczju.maggoteers.game.MaggoteersGame;
import io.mczju.maggoteers.item.ItemKind;
import io.mczju.maggoteers.item.ItemService;
import io.mczju.maggoteers.reward.RewardOption;
import io.mczju.maggoteers.reward.RewardOptionIcons;
import io.mczju.maggoteers.reward.RewardService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** 开局职业 3 选 1（复用 reward_pools.class）。 */
public class ClassSelectMenu extends Menu {
    private static final int[] SLOTS = {11, 13, 15};

    private final AbstractGame game;
    private final List<RewardOption> offers;

    public ClassSelectMenu(Player player, Object... args) {
        super(player, args);
        this.game = (AbstractGame) args[0];
        // 与休整 3 选 1 相同：可见性过滤 + shuffle，勿写死池内前 3 个
        this.offers = new ArrayList<>(RewardService.draw("class", SLOTS.length, new Random(), player));
    }

    @Override protected String getTitle() { return "选择职业"; }
    @Override protected int getRows() { return 3; }
    @Override protected String getPermission() { return "maggoteers.play"; }

    @Override
    protected void setup() {
        Player viewer = player.player();
        MaggoteersGame mg = game instanceof MaggoteersGame g ? g : null;
        int n = Math.min(SLOTS.length, offers.size());
        for (int i = 0; i < n; i++) {
            RewardOption opt = offers.get(i);
            setSlot(SLOTS[i], RewardOptionIcons.preview(opt, viewer, mg), (clicker, ev) -> {
                Player p = clicker.player();
                if (ClassSelectGate.hasChosen(game, p.getUniqueId())) return;
                if (!ItemService.spendOneKind(p, ItemKind.CLASS_TICKET)) {
                    p.sendMessage(Component.text("需要手持职业选择券才能确认！", NamedTextColor.RED));
                    return;
                }
                RewardService.apply(p, opt, game, "class");
                ClassSelectGate.markChosen(game, p.getUniqueId());
                p.closeInventory();
                p.sendMessage(Component.text("职业已选定！", NamedTextColor.GREEN));
                if (mg != null) {
                    org.bukkit.Bukkit.getScheduler().runTask(
                            io.mczju.maggoteers.MaggoteersPlugin.getInstance(),
                            () -> io.mczju.maggoteers.effect.WeaponHeldService.sync(p, mg));
                }
                if (ClassSelectGate.allChosen(game) && game instanceof MaggoteersGame chosenGame) {
                    chosenGame.onAllClassesChosen();
                }
            });
        }
    }
}
