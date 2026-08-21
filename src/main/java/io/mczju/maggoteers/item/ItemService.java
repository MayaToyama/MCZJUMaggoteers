package io.mczju.maggoteers.item;

import io.mczju.maggoteers.MaggoteersPlugin;
import io.mczju.maggoteers.util.ItemYaml;
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

    static NamespacedKey kindKey() {
        return new NamespacedKey(MaggoteersPlugin.getInstance(), "kind");
    }

    private static NamespacedKey idKey() {
        return new NamespacedKey(MaggoteersPlugin.getInstance(), "id");
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
        int[] total = {0};
        ItemYaml.forEachYaml(dir, yaml -> {
            if (api == null) {
                return;
            }
            Map<String, ItemStack> parsed = api.parseYamlToItems(yaml);
            for (var e : parsed.entrySet()) {
                if (!CACHE.containsKey(e.getKey())) CACHE.put(e.getKey(), e.getValue());
            }
            total[0] += parsed.size();
        });
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
            CACHE.put(itemId, buildConfigured(sec, kind, itemId));
            switch (kind) {
                case CURRENCY_NORMAL -> idCurrencyNormal = itemId;
                case CURRENCY_BOSS -> idCurrencyBoss = itemId;
                case CLASS_TICKET -> idClassTicket = itemId;
                default -> { }
            }
        }
    }

    private static ItemStack buildConfigured(ConfigurationSection sec, ItemKind kind, String itemId) {
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
        meta.getPersistentDataContainer().set(idKey(), PersistentDataType.STRING, itemId);
        stack.setItemMeta(meta);
        if (sec.getBoolean("glint", false)) {
            stack.addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.UNBREAKING, 1);
        }
        return stack;
    }

    private static void copyDefaultItems(MaggoteersPlugin plugin, File dir) {
        for (String name : new String[]{"maggoteers.yml", "collectibles.yml"}) {
            File out = new File(dir, name);
            if (out.exists()) continue;
            var stream = plugin.getResource("items/" + name);
            if (stream == null) continue;
            try (stream) {
                java.nio.file.Files.copy(stream, out.toPath());
            } catch (Exception e) {
                LOG.warning("释放默认物品 " + name + " 失败：" + e.getMessage());
            }
        }
    }

    private static String itemId(ItemKind kind) {
        return switch (kind) {
            case CURRENCY_NORMAL -> idCurrencyNormal;
            case CURRENCY_BOSS -> idCurrencyBoss;
            case CLASS_TICKET -> idClassTicket;
            case REVIVE_COIN -> "maggoteers:revive_coin";
            case SUPPLY_HEALING -> "maggoteers:supply_healing";
            case SHOP_EMERALD -> "maggoteers:shop_emerald";
            case COLLECTIBLE, RUN_GEAR, BOUND_EQUIP -> null;
        };
    }

    public static ItemKind readKind(ItemStack stack) {
        if (stack == null || stack.getType().isAir() || !stack.hasItemMeta()) return null;
        String raw = stack.getItemMeta().getPersistentDataContainer().get(kindKey(), PersistentDataType.STRING);
        return ItemKind.fromPdcLoose(raw);
    }

    /** 读取 PDC {@code maggoteers:id}，返回物品 id 字符串（如 {@code "maggoteers:fire_sword"}），无则 null。 */
    public static String itemIdOf(ItemStack stack) {
        if (stack == null || stack.getType().isAir() || !stack.hasItemMeta()) return null;
        return stack.getItemMeta().getPersistentDataContainer().get(idKey(), PersistentDataType.STRING);
    }

    public static boolean hasItem(String id) {
        return CACHE.containsKey(id) || (api != null && api.hasItem(id));
    }

    public static Optional<ItemStack> createItem(String id, int amount) {
        if (id == null || id.isBlank()) return Optional.empty();
        ItemStack base = CACHE.get(id);
        if (base == null && api != null) base = api.createItem(id).orElse(null);
        if (base == null && !id.contains(":")) {
            String prefixed = "maggoteers:" + id;
            base = CACHE.get(prefixed);
            if (base == null && api != null) base = api.createItem(prefixed).orElse(null);
            if (base != null) id = prefixed;
        }
        if (base == null) return Optional.empty();
        ItemStack stack = base.clone();
        stack.setAmount(Math.max(1, amount));
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(idKey(), PersistentDataType.STRING, id);
            stack.setItemMeta(meta);
        }
        return Optional.of(stack);
    }

    /**
     * 发放物品到背包；满则掉脚下。未知 id 返回 false（不抛异常）。
     * 无冒号前缀时会尝试 {@code maggoteers:<id>}。
     */
    public static boolean give(Player player, String id, int amount) {
        Optional<ItemStack> created = createItem(id, amount);
        if (created.isEmpty()) {
            LOG.warning("物品不存在，无法发放：" + id);
            return false;
        }
        deliver(player, created.get());
        return true;
    }

    /** 背包满则掉落脚下（与 CLAUDE G2 / Collectible 一致）。 */
    public static void deliver(Player player, ItemStack stack) {
        if (player == null || stack == null || stack.getType().isAir()) return;
        var leftover = player.getInventory().addItem(stack);
        for (ItemStack left : leftover.values()) {
            if (left == null || left.getType().isAir()) continue;
            player.getWorld().dropItemNaturally(player.getLocation(), left);
        }
        syncWeaponHeldSoon(player);
    }

    /** 发放后（延迟 1 tick）同步主手武器 held_effects。
     *  代码直接 addItem 塞进背包不产生 PlayerItemHeldEvent，WeaponHeldListener 收不到；
     *  否则开局/3选1/职业武器「持有时不生效、首次切武器才生效」（bug #52）。延迟与事件路径一致，防效果链内重入。 */
    private static void syncWeaponHeldSoon(Player player) {
        if (player == null || !player.isOnline()) return;
        Bukkit.getScheduler().runTask(MaggoteersPlugin.getInstance(), () -> {
            var game = io.mczju.maggoteers.state.PlayerStateManager.gameOf(player);
            if (game == null) return;
            io.mczju.maggoteers.effect.WeaponHeldService.sync(player, game);
        });
    }

    public static boolean giveKind(Player player, ItemKind kind, int amount) {
        return give(player, itemId(kind), amount);
    }

    public static void giveInitialEquipment(Player player) {
        for (String id : MaggoteersPlugin.getInstance().getConfig().getStringList("initial_equipment")) {
            createItem(id, 1).ifPresent(stack -> {
                ItemMeta meta = stack.getItemMeta();
                if (meta != null) {
                    meta.getPersistentDataContainer().set(kindKey(), PersistentDataType.STRING,
                            ItemKind.RUN_GEAR.pdcValue());
                    stack.setItemMeta(meta);
                }
                deliver(player, stack);
            });
        }
    }

    public static int countKind(Player player, ItemKind kind) {
        return countCurrency(player, kind.pdcValue());
    }

    /** 统计背包中某类 PDC kind 数量（按件数，含副手，与 {@link #spendOne} 槽位范围一致，R13）。 */
    private static int countCurrency(Player player, String kindPdc) {
        int n = 0;
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
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

    /** 消耗主手 1 个（amount<=1 清槽，否则减 1）；魔法武器消耗/补给共用。 */
    public static void spendOneFromMainHand(Player player) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand == null || hand.getType().isAir()) return;
        if (hand.getAmount() <= 1) {
            player.getInventory().setItemInMainHand(null);
        } else {
            hand.setAmount(hand.getAmount() - 1);
        }
        syncWeaponHeldSoon(player);   // 主手清空/变更，卸载 held_effects（不产生事件）
    }

    private static boolean spendOne(Player player, String kindPdc) {
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

    public static Map<String, ItemStack> cacheView() { return Collections.unmodifiableMap(CACHE); }
}
