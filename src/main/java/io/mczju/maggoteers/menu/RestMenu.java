package io.mczju.maggoteers.menu;

import com.github.mczjuops.mczjugamecore.game.AbstractGame;
import com.github.mczjuops.mczjugamecore.menu.Menu;
import com.github.mczjuops.mczjugamecore.menu.MenuFacade;
import io.mczju.maggoteers.item.ItemKind;
import io.mczju.maggoteers.item.ItemService;
import io.mczju.maggoteers.reward.RewardOption;
import io.mczju.maggoteers.reward.RewardPool;
import io.mczju.maggoteers.reward.RewardService;
import io.mczju.maggoteers.wave.WaveScheduler;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/** 休整升级主菜单：普通/Boss 3 选 1 入口 + 跳过休整。 */
public class RestMenu extends Menu {
    private final AbstractGame game;

    public RestMenu(Player player, Object... args) {
        super(player, args);
        this.game = (AbstractGame) args[0];
    }

    @Override protected String getTitle() { return "休整 · 升级"; }
    @Override protected int getRows() { return 3; }
    @Override protected String getPermission() { return "maggoteers.play"; }

    @Override
    protected void setup() {
        Player p = player.player();
        var snap = WaveScheduler.snapshot(game);
        String normalTier = snap == null ? "weak" : ("weak".equals(snap.lastTier()) ? "weak" : "strong");
        String poolNormal = snap == null ? "act1_weak" : RewardService.poolForWave(snap.actIndex(), normalTier);
        String poolBoss = snap == null ? "act1_boss" : RewardService.poolForWave(snap.actIndex(), "boss");

        setSlot(11, btn(Material.GOLD_NUGGET, "<yellow>普通奖励",
                        "消耗 1 普通卫戍币 · 3 选 1", ItemService.countKind(p, ItemKind.CURRENCY_NORMAL)),
                (c, e) -> openPick(p, poolNormal, ItemKind.CURRENCY_NORMAL));
        setSlot(13, btn(Material.NETHER_STAR, "<light_purple>Boss 奖励",
                        "消耗 1 Boss 币 · 3 选 1", ItemService.countKind(p, ItemKind.CURRENCY_BOSS)),
                (c, e) -> openPick(p, poolBoss, ItemKind.CURRENCY_BOSS));
        setSlot(15, btn(Material.LIME_DYE, "<green>跳过休整", "立即进入下一波", -1),
                (c, e) -> {
                    p.closeInventory();
                    WaveScheduler.skipRest(game);
                });
    }

    private void openPick(Player p, String poolId, ItemKind currencyKind) {
        RewardPool pool = RewardService.pool(poolId);
        if (pool == null) {
            p.sendMessage(Component.text("奖励池未配置：" + poolId, NamedTextColor.RED));
            return;
        }
        List<RewardOption> offers = RewardService.draw(poolId, 3, new java.util.Random(), p);
        if (offers.isEmpty()) {
            p.sendMessage(Component.text("当前没有可选奖励。", NamedTextColor.RED));
            return;
        }
        if (ItemService.countKind(p, currencyKind) < pool.cost()) {
            p.sendMessage(Component.text("货币不足！", NamedTextColor.RED));
            return;
        }
        if (!ItemService.spendOneKind(p, currencyKind)) {
            p.sendMessage(Component.text("扣费失败。", NamedTextColor.RED));
            return;
        }
        p.closeInventory();
        MenuFacade.open("maggoteers-pick", p, game, offers);
    }

    private static ItemStack btn(Material mat, String name, String loreLine, int countHint) {
        ItemStack s = new ItemStack(mat);
        ItemMeta m = s.getItemMeta();
        if (m != null) {
            m.displayName(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(name)
                    .decoration(TextDecoration.ITALIC, false));
            var lore = new java.util.ArrayList<Component>();
            lore.add(Component.text(loreLine, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
            if (countHint >= 0) {
                lore.add(Component.text("持有：" + countHint, NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, false));
            }
            lore.add(Component.text("▶ 点击", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
            m.lore(lore);
            s.setItemMeta(m);
        }
        return s;
    }
}
