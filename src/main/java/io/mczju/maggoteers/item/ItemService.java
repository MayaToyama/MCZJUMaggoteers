package io.mczju.maggoteers.item;

import io.mczju.maggoteers.MaggoteersPlugin;
import io.mczju.mczjuitemcreator.api.ItemCreatorApi;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.util.*;
import java.util.logging.Logger;

/** ItemCreator 接入 + config/items 可配置券种 + items/*.yml 缓存（G2：发放走 giveItem(ItemStack)）。 */
public final class ItemService {
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final Logger LOG = Logger.getLogger("Maggoteers");
    private static final Map<String, ItemStack> CACHE = new HashMap<>();
    private static ItemCreatorApi api;

    /** config items.*.item_id */
    private static String idCurrencyNormal = "maggoteers:currency_normal";
    private static String idCurrencyBoss = "maggoteers:currency_boss";
    private static String idClassTicket = "maggoteers:class_ticket";

    private ItemService() {}

    private static NamespacedKey kindKey() {
        return new NamespacedKey(MaggoteersPlugin.getInstance(), "kind");
    }

    public static void init(MaggoteersPlugin plugin) {
        CACHE.clear();
        api = Bukkit.getServicesManager().load(ItemCreatorApi.class);
        if (api == null) {
            LOG.warning("MCZJUItemCreator 未安装，物品功能不可用。");
        }
        loadConfigurableItems(plugin);
        File dir = new File(plugin.getDataFolder(), "items");
        if (!dir.exists()) dir.mkdirs();
        copyDefaultItems(plugin, dir);
        int total = 0;
        File[] files = dir.listFiles((d, n) -> n.endsWith(".yml"));
        if (files != null && api != null) {
            for (File f : files) {
                YamlConfiguration yaml = YamlConfiguration.loadConfiguration(f);
                Map<String, ItemStack> parsed = api.parseYamlToItems(yaml);
                for (var e : parsed.entrySet()) {
                    if (!CACHE.containsKey(e.getKey())) CACHE.put(e.getKey(), e.getValue());
                }
                total += parsed.size();
            }
        }
        LOG.info("ItemService 已缓存 " + CACHE.size() + " 个物品（含 config 可配置券种）。");
    }

    /** 从 config.yml {@code items} 段构建货币/职业券（管理员可自定义 name/lore/material/glint）。 */
    private static void loadConfigurableItems(MaggoteersPlugin plugin) {
        ConfigurationSection root = plugin.getConfig().getConfigurationSection("items");
        if (root == null) return;
        for (String key : root.getKeys(false)) {
            ConfigurationSection sec = root.getConfigurationSection(key);
            if (sec == null) continue;
            String itemId = sec.getString("item_id", "maggoteers:" + key);
            ItemKind kind = ItemKind.fromPdcLoose(sec.getString("kind", key));
            if (kind == null) {
                LOG.warning("items." + key + " 缺少有效 kind，已跳过。");
                continue;
            }
            CACHE.put(itemId, buildConfigured(sec, kind));
            switch (kind) {
                case CURRENCY_NORMAL -> idCurrencyNormal = itemId;
                case CURRENCY_BOSS -> idCurrencyBoss = itemId;
                case CLASS_TICKET -> idClassTicket = itemId;
            }
        }
    }

    private static ItemStack buildConfigured(ConfigurationSection sec, ItemKind kind) {
        Material mat = Material.matchMaterial(sec.getString("material", "PAPER"));
        if (mat == null || !mat.isItem()) mat = Material.PAPER;
        ItemStack stack = new ItemStack(mat);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return stack;

        String name = sec.getString("name", "");
        if (!name.isBlank()) {
            meta.displayName(MM.deserialize(name).decoration(TextDecoration.ITALIC, false));
        }
        List<String> loreLines = sec.getStringList("lore");
        if (!loreLines.isEmpty()) {
            List<Component> lore = new ArrayList<>();
            for (String line : loreLines) {
                lore.add(MM.deserialize(line).decoration(TextDecoration.ITALIC, false));
            }
            meta.lore(lore);
        }
        if (sec.getBoolean("glint", false)) {
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        meta.getPersistentDataContainer().set(kindKey(), PersistentDataType.STRING, kind.pdcValue());
        stack.setItemMeta(meta);
        if (sec.getBoolean("glint", false)) {
            stack.addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.UNBREAKING, 1);
        }
        return stack;
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

    public static String itemId(ItemKind kind) {
        return switch (kind) {
            case CURRENCY_NORMAL -> idCurrencyNormal;
            case CURRENCY_BOSS -> idCurrencyBoss;
            case CLASS_TICKET -> idClassTicket;
        };
    }

    public static ItemKind readKind(ItemStack stack) {
        if (stack == null || stack.getType().isAir() || !stack.hasItemMeta()) return null;
        String raw = stack.getItemMeta().getPersistentDataContainer().get(kindKey(), PersistentDataType.STRING);
        return ItemKind.fromPdcLoose(raw);
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

    public static void giveKind(Player player, ItemKind kind, int amount) {
        give(player, itemId(kind), amount);
    }

    public static void giveInitialEquipment(Player player) {
        for (String id : MaggoteersPlugin.getInstance().getConfig().getStringList("initial_equipment")) {
            give(player, id, 1);
        }
    }

    public static int countKind(Player player, ItemKind kind) {
        return countCurrency(player, kind.pdcValue());
    }

    /** 统计背包中某类 PDC kind 数量（按件数）。 */
    public static int countCurrency(Player player, String kindPdc) {
        int n = 0;
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack == null || stack.getType().isAir()) continue;
            ItemKind k = readKind(stack);
            if (k != null && k.pdcValue().equals(kindPdc)) n += stack.getAmount();
        }
        return n;
    }

    /** 扣除 1 枚指定 kind；成功返回 true。 */
    public static boolean spendOneKind(Player player, ItemKind kind) {
        return spendOne(player, kind.pdcValue());
    }

    public static boolean spendOne(Player player, String kindPdc) {
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack == null || stack.getType().isAir()) continue;
            ItemKind k = readKind(stack);
            if (k == null || !k.pdcValue().equals(kindPdc)) continue;
            if (stack.getAmount() <= 1) player.getInventory().setItem(i, null);
            else stack.setAmount(stack.getAmount() - 1);
            return true;
        }
        return false;
    }

    /** @deprecated 使用 {@link #countKind(Player, ItemKind)} */
    @Deprecated
    public static final String CURRENCY_NORMAL = ItemKind.CURRENCY_NORMAL.pdcValue();
    @Deprecated
    public static final String CURRENCY_BOSS = ItemKind.CURRENCY_BOSS.pdcValue();

    public static Map<String, ItemStack> cacheView() { return Collections.unmodifiableMap(CACHE); }
}
