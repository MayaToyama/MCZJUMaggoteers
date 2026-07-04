package io.mczju.maggoteers.menu;

import com.github.mczjuops.mczjugamecore.game.AbstractGame;
import com.github.mczjuops.mczjugamecore.menu.Menu;
import io.mczju.maggoteers.config.ShopConfig;
import io.mczju.maggoteers.item.ItemKind;
import io.mczju.maggoteers.item.ItemService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 局内商店 GUI（QA #4）：持有绿宝石右键打开。
 * 每件商品一格，显示图标 + 价格；点击扣对应货币后发放物品。
 * 绿宝石不消耗——是可复用的入口物品。
 */
public class ShopMenu extends Menu {
    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final AbstractGame game;
    private final List<ShopConfig.Good> goods;

    public ShopMenu(Player player, Object... args) {
        super(player, args);
        this.game = (AbstractGame) args[0];
        this.goods = ShopConfig.getInstance().goods();
    }

    @Override protected String getTitle() { return ShopConfig.getInstance().title(); }
    @Override protected int getRows() { return ShopConfig.getInstance().rows(); }
    @Override protected String getPermission() { return "maggoteers.play"; }

    @Override
    protected void setup() {
        for (ShopConfig.Good g : goods) {
            setSlot(g.slot(), icon(g), (clicker, ev) -> onBuy(clicker.player(), g));
        }
    }

    private void onBuy(Player p, ShopConfig.Good g) {
        ItemKind cur = "boss".equals(g.currency()) ? ItemKind.CURRENCY_BOSS : ItemKind.CURRENCY_NORMAL;
        int price = g.price();
        if (price <= 0) return;

        // 先检查持有量是否足够
        if (ItemService.countKind(p, cur) < price) {
            p.sendMessage(Component.text("货币不足！", NamedTextColor.RED));
            return;
        }

        // 逐个扣费，若中途失败（理论上不会，已检查）则退还已扣部分
        int spent = 0;
        for (int i = 0; i < price; i++) {
            if (!ItemService.spendOneKind(p, cur)) {
                // 退还已扣部分
                if (spent > 0) ItemService.giveKind(p, cur, spent);
                p.sendMessage(Component.text("扣费异常，已退款。", NamedTextColor.RED));
                return;
            }
            spent++;
        }

        // 发放物品
        ItemService.give(p, g.itemId(), 1);
        p.sendMessage(Component.text("已购买！", NamedTextColor.GREEN));
        p.updateInventory();
    }

    /** 构造商品展示图标：用 ItemService 缓存物品 + 价格 lore。 */
    private static ItemStack icon(ShopConfig.Good g) {
        Optional<ItemStack> base = ItemService.createItem(g.itemId(), 1);
        ItemStack stack = base.orElseGet(() -> {
            ItemStack fallback = new ItemStack(org.bukkit.Material.BARRIER);
            ItemMeta m = fallback.getItemMeta();
            if (m != null) m.displayName(Component.text("未知物品: " + g.itemId()));
            fallback.setItemMeta(m);
            return fallback;
        });
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return stack;

        List<Component> lore = meta.lore() != null ? new ArrayList<>(meta.lore()) : new ArrayList<>();
        String curLabel = "boss".equals(g.currency()) ? "星石" : "金币";
        NamedTextColor curColor = "boss".equals(g.currency()) ? NamedTextColor.LIGHT_PURPLE : NamedTextColor.YELLOW;
        String priceText = g.price() <= 1
                ? "花费 1 " + curLabel
                : "花费 " + g.price() + " " + curLabel;
        lore.add(Component.text(priceText, curColor).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("▶ 点击购买", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        stack.setItemMeta(meta);
        return stack;
    }
}
