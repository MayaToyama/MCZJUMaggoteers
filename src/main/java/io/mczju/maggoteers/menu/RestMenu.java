package io.mczju.maggoteers.menu;

import com.github.mczjuops.mczjugamecore.game.AbstractGame;
import com.github.mczjuops.mczjugamecore.menu.Menu;
import com.github.mczjuops.mczjugamecore.menu.MenuFacade;
import io.mczju.maggoteers.config.MessageService;
import io.mczju.maggoteers.item.ItemKind;
import io.mczju.maggoteers.item.ItemService;
import io.mczju.maggoteers.reward.RewardOption;
import io.mczju.maggoteers.reward.RewardPool;
import io.mczju.maggoteers.reward.RewardService;
import io.mczju.maggoteers.wave.WaveScheduler;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.Map;

/** 休整升级主菜单：普通/Boss 3 选 1 入口 + 跳过休整。 */
public class RestMenu extends Menu {
    private final AbstractGame game;

    public RestMenu(Player player, Object... args) {
        super(player, args);
        this.game = (AbstractGame) args[0];
    }

    @Override protected String getTitle() {
        return MessageService.raw("menu.rest.title", Map.of());
    }
    @Override protected int getRows() { return 3; }
    @Override protected String getPermission() { return "maggoteers.play"; }

    @Override
    protected void setup() {
        Player p = player.player();
        var snap = WaveScheduler.snapshot(game);
        // Act3：普通商店一律 act3_strong（见 2026-08-16-act3-reward-pool-shift）
        String normalTier = snap == null
                ? "weak"
                : RewardService.normalShopTier(snap.actIndex(), snap.lastTier());
        String poolNormal = snap == null ? "act1_weak" : RewardService.poolForWave(snap.actIndex(), normalTier);
        // Boss 池粘滞（§9.2）：用最近击杀 Boss 所在层，而非当前层；Act3 清 weak 后提前切到 act3_boss
        String poolBoss = snap == null ? "act1_boss" : RewardService.poolForWave(WaveScheduler.bossPoolActIndex(game), "boss");

        setSlot(11, btn(Material.GOLD_NUGGET,
                        MessageService.raw("menu.rest.normal_name", Map.of()),
                        MessageService.raw("menu.rest.normal_lore", Map.of()),
                        ItemService.countKind(p, ItemKind.CURRENCY_NORMAL)),
                (c, e) -> openPick(p, poolNormal, ItemKind.CURRENCY_NORMAL));
        setSlot(13, btn(Material.NETHER_STAR,
                        MessageService.raw("menu.rest.boss_name", Map.of()),
                        MessageService.raw("menu.rest.boss_lore", Map.of()),
                        ItemService.countKind(p, ItemKind.CURRENCY_BOSS)),
                (c, e) -> openPick(p, poolBoss, ItemKind.CURRENCY_BOSS));

        // 跳过休整：全员投票（§9.3），按钮显示投票进度
        int skipVoted = WaveScheduler.skipVoteCount(game);
        int skipTotal = WaveScheduler.skipVoteTotal(game);
        String skipLore = MessageService.raw("menu.rest.skip_lore", Map.of(
                "voted", String.valueOf(skipVoted),
                "total", String.valueOf(skipTotal)));
        setSlot(15, btn(Material.LIME_DYE,
                        MessageService.raw("menu.rest.skip_name", Map.of()),
                        skipLore, -1),
                (c, e) -> {
                    boolean skipped = WaveScheduler.voteSkip(game, p.getUniqueId());
                    p.closeInventory();
                    if (!skipped) {
                        // 未全员通过：重开菜单刷新投票进度
                        MenuFacade.open("maggoteers-rest", p, game);
                    }
                });
    }

    private void openPick(Player p, String poolId, ItemKind currencyKind) {
        RewardPool pool = RewardService.pool(poolId);
        if (pool == null) {
            p.sendMessage(MessageService.component("menu.rest.pool_missing", Map.of("pool", poolId)));
            return;
        }
        List<RewardOption> offers = RewardService.drawCached(p.getUniqueId(), poolId, 3, new java.util.Random(), p);
        if (offers.isEmpty()) {
            p.sendMessage(MessageService.component("menu.rest.no_offers", Map.of()));
            return;
        }
        if (ItemService.countKind(p, currencyKind) < pool.cost()) {
            p.sendMessage(MessageService.component("menu.rest.not_enough_currency", Map.of()));
            return;
        }
        p.closeInventory();
        MenuFacade.open("maggoteers-pick", p, game, offers, poolId, currencyKind.name(), pool.cost());
    }

    private static ItemStack btn(Material mat, String name, String loreLine, int countHint) {
        ItemStack s = new ItemStack(mat);
        ItemMeta m = s.getItemMeta();
        if (m != null) {
            m.displayName(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(name)
                    .decoration(TextDecoration.ITALIC, false));
            var lore = new java.util.ArrayList<Component>();
            lore.add(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(loreLine)
                    .decoration(TextDecoration.ITALIC, false));
            if (countHint >= 0) {
                lore.add(MessageService.component("menu.rest.held", Map.of("count", String.valueOf(countHint)))
                        .decoration(TextDecoration.ITALIC, false));
            }
            lore.add(MessageService.component("menu.rest.click", Map.of())
                    .decoration(TextDecoration.ITALIC, false));
            m.lore(lore);
            s.setItemMeta(m);
        }
        return s;
    }
}
