package io.mczju.maggoteers.item;

import io.mczju.maggoteers.MaggoteersPlugin;
import io.mczju.mczjuitemcreator.api.ItemCreatorApi;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

/** ItemCreator 接入 + 本插件 items/*.yml 缓存（G2：发放走 giveItem(ItemStack)）。 */
public final class ItemService {
    private static NamespacedKey kindKey() {
        return new NamespacedKey(MaggoteersPlugin.getInstance(), "kind");
    }
    public static final String CURRENCY_NORMAL = "currency_normal";
    public static final String CURRENCY_BOSS = "currency_boss";

    private static final Logger LOG = Logger.getLogger("Maggoteers");
    private static final Map<String, ItemStack> CACHE = new HashMap<>();
    private static ItemCreatorApi api;

    private ItemService() {}

    public static void init(MaggoteersPlugin plugin) {
        CACHE.clear();
        api = Bukkit.getServicesManager().load(ItemCreatorApi.class);
        if (api == null) {
            LOG.warning("MCZJUItemCreator 未安装，物品功能不可用。");
            return;
        }
        File dir = new File(plugin.getDataFolder(), "items");
        if (!dir.exists()) dir.mkdirs();
        copyDefaultItems(plugin, dir);
        int total = 0;
        File[] files = dir.listFiles((d, n) -> n.endsWith(".yml"));
        if (files == null) return;
        for (File f : files) {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(f);
            Map<String, ItemStack> parsed = api.parseYamlToItems(yaml);
            CACHE.putAll(parsed);
            total += parsed.size();
        }
        LOG.info("ItemService 已缓存 " + total + " 个物品。");
    }

    private static void copyDefaultItems(MaggoteersPlugin plugin, File dir) {
        String name = "maggoteers.yml";
        File out = new File(dir, name);
        if (out.exists()) return;
        var stream = plugin.getResource("items/" + name);
        if (stream == null) return;
        try (stream) {
            java.nio.file.Files.copy(stream, out.toPath());
        } catch (Exception e) {
            LOG.warning("释放默认物品 " + name + " 失败：" + e.getMessage());
        }
    }

    public static boolean hasItem(String id) {
        return CACHE.containsKey(id) || (api != null && api.hasItem(id));
    }

    public static Optional<ItemStack> createItem(String id, int amount) {
        ItemStack base = CACHE.get(id);
        if (base == null && api != null) base = api.createItem(id).orElse(null);
        if (base == null) return Optional.empty();
        ItemStack stack = base.clone();
        stack.setAmount(Math.max(1, amount));
        return Optional.of(stack);
    }

    public static void give(Player player, String id, int amount) {
        createItem(id, amount).ifPresentOrElse(
                stack -> player.getInventory().addItem(stack),
                () -> LOG.warning("物品不存在，无法发放：" + id));
    }

    public static void giveInitialEquipment(Player player) {
        for (String id : MaggoteersPlugin.getInstance().getConfig().getStringList("initial_equipment")) {
            give(player, id, 1);
        }
    }

    /** 统计背包中某类 PDC 货币数量（按件数，非堆叠数）。 */
    public static int countCurrency(Player player, String kind) {
        int n = 0;
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack == null || stack.getType().isAir()) continue;
            String k = stack.getItemMeta() != null
                    ? stack.getItemMeta().getPersistentDataContainer().get(kindKey(), PersistentDataType.STRING) : null;
            if (kind.equals(k)) n += stack.getAmount();
        }
        return n;
    }

    /** 扣除 1 枚指定货币；成功返回 true。 */
    public static boolean spendOne(Player player, String kind) {
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack == null || stack.getType().isAir()) continue;
            var meta = stack.getItemMeta();
            if (meta == null) continue;
            String k = meta.getPersistentDataContainer().get(kindKey(), PersistentDataType.STRING);
            if (!kind.equals(k)) continue;
            if (stack.getAmount() <= 1) player.getInventory().setItem(i, null);
            else stack.setAmount(stack.getAmount() - 1);
            return true;
        }
        return false;
    }

    public static Map<String, ItemStack> cacheView() { return Collections.unmodifiableMap(CACHE); }
}
