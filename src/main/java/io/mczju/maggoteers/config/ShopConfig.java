package io.mczju.maggoteers.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/** 加载 config.yml {@code shop:} 段。onEnable 调一次。 */
public final class ShopConfig {
    private static final Logger LOG = Logger.getLogger("Maggoteers");
    private static ShopConfig INSTANCE;

    private String title = "<gold>卫戍商店";
    private int rows = 3;
    private final List<Good> goods = new ArrayList<>();

    /** 一个商品条目 */
    public record Good(int slot, String itemId, String currency, int price) {}

    public static void load(JavaPlugin plugin) {
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("shop");
        INSTANCE = new ShopConfig();
        if (sec == null) {
            LOG.warning("config.yml 缺少 shop 段，商店不可用。");
            return;
        }
        INSTANCE.title = sec.getString("title", INSTANCE.title);
        INSTANCE.rows = Math.max(1, Math.min(6, sec.getInt("rows", INSTANCE.rows)));

        for (var m : sec.getMapList("goods")) {
            int slot = ((Number) m.get("slot")).intValue();
            Object itemIdRaw = m.get("item");
            String itemId = itemIdRaw != null ? itemIdRaw.toString() : "";
            Object currencyRaw = m.get("currency");
            String currency = currencyRaw != null ? currencyRaw.toString() : "normal";
            int price = m.get("price") instanceof Number n ? n.intValue() : 1;
            if (itemId == null || itemId.isBlank()) {
                LOG.warning("shop goods 条目缺少 item，已跳过。");
                continue;
            }
            INSTANCE.goods.add(new Good(slot, itemId, currency, price));
        }
        LOG.info("ShopConfig 已加载：" + INSTANCE.goods.size() + " 件商品。");
    }

    public static ShopConfig getInstance() {
        if (INSTANCE == null) throw new IllegalStateException("ShopConfig 未加载！");
        return INSTANCE;
    }

    public String title() { return title; }
    public int rows() { return rows; }
    public List<Good> goods() { return goods; }

    private ShopConfig() {}
}
