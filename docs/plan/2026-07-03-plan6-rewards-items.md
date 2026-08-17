# The Maggoteers — Plan 6: 奖励接效果引擎 + 物品/菜单收尾 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把 Plan 5 的效果引擎接通到奖励系统（`RewardOption` 加 §12 schema 的 `trigger/effect/params/stack/icon/requires_unlock/unlock_cost/unique`、`rewards.yml` 加 STAT 选项、`RewardService.apply` 改走 `EffectService`），并修掉两个已知 bug（**复活币不可用**、**右键空气打不开菜单**），再补 D2 物品 CD、`ON_INTERACT` 触发、D1 净化。

**Architecture:** Task 1 先修两个用户 flag 的 bug（最高优先）：`ItemKind` 加 `REVIVE_COIN` + `menu/ReviveMenu`（玩家头像 GUI：点死者=复活、点活者=+1 次数，扣 1 复活币）+ 修 `ItemInteractRouter`（`ignoreCancelled=false` + 健壮处理 RIGHT_CLICK_AIR）。Task 2–3 重塑 `RewardOption`（§12.1 全字段）+ `rewards.yml` 加 STAT 选项 + `RewardService.apply` 把 STAT 建成 `PlayerEffect` 喂 `EffectService.apply` + `PickMenu` 加 `unique` 可见性过滤。Task 4–6 补 `ON_INTERACT`、D2（按 PDC-id 时间戳 CD）、D1（`ADD_POTION` 0s-255 抵消 + damage-cancel fallback）。

**Tech Stack:** Paper 1.21.7 API，Java 21，Maven，MCZJUGameCore 1.0.5（`Menu`/`MenuFacade`/`PlayerExt`），JUnit 5（纯逻辑单测）。

## Global Constraints

- **前置条件**：Plan 1–5 DoD 已满足（HEAD `f1dfaf7`，已推 origin）。Plan 5 的 `effect/`（`Trigger`/`Effect`/`Stack`/`EffectKey`/`EffectContext`/`EffectKeys`/`PlayerEffect`/`EffectStacker`/`EffectService`/`EffectListener`）就位。`ItemService`（`createItem`/`give`/`giveKind`/`countKind`/`spendOneKind`/`readKind`/`itemId`）、`ItemKind`（CURRENCY_NORMAL/Boss/CLASS_TICKET）、`menu/Menu` 子类模式（`(Player,Object...)` 构造 + `getTitle/getRows/getPermission/setup` + `setSlot`）、`RewardService.draw/apply/grantClearRewards/poolForWave`、`PlayerStateManager.revivePlayer/addReviveCount` 均已存在。
- **§12.1 schema**：`RewardOption{id, display, description, category(STAT/WEAPON/SUPPLY), item, amount, trigger?, effect?, params?, stack?, icon?, requires_unlock?, unlock_cost?, unique?}`。
- **§12.2 可见性**：`可选 = (!requires_unlock || unlocks.contains(id)) && (!unique || !acquiredUnique.contains(id))`。`unique`（本局）→ `PlayerState.acquiredUnique`（本 Plan 加）；`requires_unlock`（持久）→ Plan 7（本 Plan 留过滤钩子，恒返回"未解锁"直到 Plan 7 接 unlocks）。
- **D2（物品 CD 不走 `setCooldown(Material)`）**：按 PDC id 的时间戳表 `Map<UUID,Map<pdcId,expireMillis>>`（同材质不同 PDC 不串 CD）。
- **D1（净化技巧待实测）**：`ADD_POTION` 0s 255 级抵消不一定成立；fallback = `EntityDamageEvent` 按 cause cancel。
- Attribute 命名 Paper 1.21.7 用**无前缀**（`Attribute.MAX_HEALTH`）。
- **JDK 21 + Maven 在 PATH**；Bukkit/MGC 运行时手测。

---

## 本 Plan 范围

- **做**：复活币 GUI + 右键空气修复；`RewardOption` reshape + STAT 选项 + 接 `EffectService`；`unique` 过滤；`ON_INTERACT`；D2 物品 CD；D1 净化。
- **不做（Plan 7）**：`requires_unlock` 的持久 `unlocks`（账户货币/商店/解锁/排行榜/结算）；通关时间榜。

---

## Task 1: 复活币可用 + 右键空气修复（两个用户 flag 的 bug）

**Files:**
- Modify: `src/main/java/io/mczju/maggoteers/item/ItemKind.java`（加 `REVIVE_COIN`）
- Modify: `src/main/java/io/mczju/maggoteers/item/ItemService.java`（`itemId(REVIVE_COIN)` 映射 + `reviveCoinItemId()`）
- Modify: `src/main/java/io/mczju/maggoteers/item/ItemInteractRouter.java`（修 `ignoreCancelled` + 注册 REVIVE_COIN 路由）
- Create: `src/main/java/io/mczju/maggoteers/menu/ReviveMenu.java`
- Modify: `src/main/java/io/mczju/maggoteers/MaggoteersPlugin.java`（`registerMenu("maggoteers-revive", ReviveMenu.class)`）

**Interfaces:**
- Consumes: `ItemKind`、`ItemService.spendOneKind/countKind`、`PlayerStateManager.revivePlayer/addReviveCount/isAnyAlive`、MGC `Menu`/`MenuFacade`。
- Produces: `ItemKind.REVIVE_COIN`（pdc `revive_coin`）；右键复活币 → `ReviveMenu`（玩家头像：死者头像点击→复活、活者头像点击→+1 次数，扣 1 复活币）。

- [ ] **Step 1: `ItemKind` 加 `REVIVE_COIN`**

在 `ItemKind` 枚举里加一个常量（在 `CLASS_TICKET` 之后）：
```java
    CLASS_TICKET("class_ticket"),
    REVIVE_COIN("revive_coin");
```
（`fromPdc`/`fromPdcLoose` 循环自动覆盖新值，无需改。）

- [ ] **Step 2: `ItemService.itemId` 映射 REVIVE_COIN**

打开 `item/ItemService.java`，找到 `itemId(ItemKind kind)` 方法（约 131 行），确保 `REVIVE_COIN` 映射到 `"maggoteers:revive_coin"`（与 `items/maggoteers.yml` 的 key 一致）。若该方法是 `switch`，加：
```java
        case REVIVE_COIN -> "maggoteers:revive_coin";
```
（若已是查表/已有默认，确认 `revive_coin` PDC 物品能被 `createItem("maggoteers:revive_coin")` 取到——`items/maggoteers.yml` 已定义该物品，`ItemService` 启动时解析缓存，应可用。）

- [ ] **Step 3: 写 `menu/ReviveMenu.java`（玩家头像 GUI）**

```java
package io.mczju.maggoteers.menu;

import com.github.mczjuops.mczjugamecore.game.AbstractGame;
import com.github.mczjuops.mczjugamecore.menu.Menu;
import com.github.mczjuops.mczjugamecore.player.PlayerExt;
import io.mczju.maggoteers.game.MaggoteersGame;
import io.mczju.maggoteers.item.ItemKind;
import io.mczju.maggoteers.item.ItemService;
import io.mczju.maggoteers.state.PlayerState;
import io.mczju.maggoteers.state.PlayerStateManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * 复活币 GUI（§11）：本局玩家头像列表。点击头像：
 * <ul>
 *   <li>死者（观察者）→ {@link PlayerStateManager#revivePlayer}（仅复活，不加次数）</li>
 *   <li>活者（冒险）→ {@link PlayerStateManager#addReviveCount}(+1)</li>
 * </ul>
 * 成功后扣点击者 1 枚复活币。需在休整期/任意时（对局内）可用。
 */
public class ReviveMenu extends Menu {
    private final AbstractGame game;
    private final List<java.util.UUID> heads = new ArrayList<>();   // slot → 玩家 uuid

    public ReviveMenu(Player player, Object... args) {
        super(player, args);
        this.game = (AbstractGame) args[0];
    }

    @Override protected String getTitle() { return "复活币 · 选择目标"; }
    @Override protected int getRows() { return 3; }
    @Override protected String getPermission() { return "maggoteers.play"; }

    @Override
    protected void setup() {
        int slot = 10;
        for (PlayerExt pe : game.getPlayers()) {
            Player target = pe.player();
            PlayerState st = PlayerStateManager.get(game, target.getUniqueId());
            boolean dead = st == null || !st.isAlive();
            setSlot(slot, headOf(target, dead), (clicker, ev) -> onPick(clicker.player(), target, dead));
            heads.add(target.getUniqueId());
            slot += 2;
            if (slot > 16) break;
        }
    }

    private void onPick(Player clicker, Player target, boolean targetDead) {
        // 扣 1 复活币
        if (!ItemService.spendOneKind(clicker, ItemKind.REVIVE_COIN)) {
            clicker.sendMessage(Component.text("没有复活币了！", NamedTextColor.RED));
            return;
        }
        if (targetDead) {
            boolean ok = PlayerStateManager.revivePlayer(game, target.getUniqueId());
            if (ok) {
                game.sender().info("<green>" + clicker.getName() + " 用复活币救回了 " + target.getName() + "！");
            } else {
                // 失败：退还（理论不发生，targetDead 已判定）
                ItemService.giveKind(clicker, ItemKind.REVIVE_COIN, 1);
                clicker.sendMessage(Component.text("该玩家无需复活。", NamedTextColor.GRAY));
                return;
            }
        } else {
            PlayerStateManager.addReviveCount(game, target.getUniqueId(), 1);
            game.sender().info("<green>" + clicker.getName() + " 用复活币给 " + target.getName() + " +1 复活次数。");
        }
        clicker.closeInventory();
    }

    private static ItemStack headOf(Player target, boolean dead) {
        ItemStack skull = new ItemStack(dead ? Material.SKELETON_SKULL : Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) skull.getItemMeta();
        if (meta != null) {
            meta.setPlayerProfile(target.getPlayerProfile());
            meta.displayName(Component.text(target.getName() + (dead ? "（倒下）" : "（存活）"),
                    dead ? NamedTextColor.RED : NamedTextColor.GREEN));
            skull.setItemMeta(meta);
        }
        return skull;
    }
}
```

> `meta.setPlayerProfile(target.getPlayerProfile())` 是 Paper API（`SkullMeta.setPlayerProfile`）。若编译报错，改用 `meta.setOwningPlayer((OfflinePlayer) target)`（旧 API，仍可用）。`Material.SKELETON_SKULL` 代表"倒下"（视觉区分）。

- [ ] **Step 4: 修 `ItemInteractRouter`（右键空气 + 注册复活币路由）**

把 `ItemInteractRouter` 的 `onInteract` 注解从 `ignoreCancelled = true` 改为 `false`（**这是右键空气打不开的主嫌疑**：某些情形下空气事件被标 cancelled，HIGH+ignoreCancelled 会跳过），并注册 REVIVE_COIN → 开 `ReviveMenu`。整文件替换为：

```java
package io.mczju.maggoteers.item;

import com.github.mczjuops.mczjugamecore.game.AbstractGame;
import com.github.mczjuops.mczjugamecore.player.PlayerExt;
import com.github.mczjuops.mczjugamecore.menu.MenuFacade;
import io.mczju.maggoteers.item.interact.ItemUseHandler;
import io.mczju.maggoteers.item.interact.OpenClassMenuHandler;
import io.mczju.maggoteers.item.interact.OpenRestMenuHandler;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.EnumMap;
import java.util.Map;

/**
 * 物品交互统一路由（§10.4）：读 PDC {@code maggoteers:kind} → {@link ItemKind} → {@link ItemUseHandler}。
 * <p>右键空气/方块均触发（{@code ignoreCancelled=false}，避免空气事件被标 cancelled 而漏触发）。
 */
public final class ItemInteractRouter implements Listener {

    private static final Map<ItemKind, ItemUseHandler> HANDLERS = new EnumMap<>(ItemKind.class);

    static {
        OpenRestMenuHandler rest = new OpenRestMenuHandler();
        HANDLERS.put(ItemKind.CURRENCY_NORMAL, rest);
        HANDLERS.put(ItemKind.CURRENCY_BOSS, rest);
        HANDLERS.put(ItemKind.CLASS_TICKET, new OpenClassMenuHandler());
        HANDLERS.put(ItemKind.REVIVE_COIN, (player, game, stack) ->
                MenuFacade.open("maggoteers-revive", player, game));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;

        ItemStack stack = event.getItem();
        ItemKind kind = ItemService.readKind(stack);
        if (kind == null) return;

        ItemUseHandler handler = HANDLERS.get(kind);
        if (handler == null) return;

        event.setCancelled(true);
        PlayerExt pe = new PlayerExt(event.getPlayer());
        if (!pe.isInGame()) return;
        AbstractGame game = pe.getGame();
        if (game == null) return;

        handler.onUse(event.getPlayer(), game, stack);
    }
}
```

- [ ] **Step 5: `MaggoteersPlugin` 注册 `maggoteers-revive` 菜单**

在 `onEnable` 已有的 `MenuFacade.registerMenu(...)` 三行之后加：
```java
        MenuFacade.registerMenu("maggoteers-revive", io.mczju.maggoteers.menu.ReviveMenu.class);
```

- [ ] **Step 6: 构建 + 部署 + 手测**

Run: `mvn -q -DskipTests package`（先 `mvn -q test` 确认全绿）。部署 jar。
手测（真服）：
1. 右键**空气**手持普通币 → 休整菜单打开（修复验证）。右键方块也仍可。
2. Boss 波后获复活币（`act1_boss` 池或 `/mgc` 给），右键 → `ReviveMenu` 打开，列出本局玩家头像。
3. 让一玩家耗尽复活转观察者，另一玩家右键复活币点死者头像 → 死者复活（变回冒险）；点击者扣 1 复活币。
4. 点活者头像 → 该玩家 `+1` 复活次数（下次死亡多一次自动复活）；扣 1 币。

- [ ] **Step 7: 提交**

```bash
git add src/main/java/io/mczju/maggoteers/item/ItemKind.java \
        src/main/java/io/mczju/maggoteers/item/ItemService.java \
        src/main/java/io/mczju/maggoteers/item/ItemInteractRouter.java \
        src/main/java/io/mczju/maggoteers/menu/ReviveMenu.java \
        src/main/java/io/mczju/maggoteers/MaggoteersPlugin.java
git commit -m "fix(plan6): 复活币 GUI(ItemKind.REVIVE_COIN/ReviveMenu) + 右键空气修复(ignoreCancelled=false)"
```

---

## Task 2: RewardOption reshape（§12 schema）+ rewards.yml STAT 选项 + PlayerState.acquiredUnique

**Files:**
- Modify: `src/main/java/io/mczju/maggoteers/reward/RewardOption.java`
- Modify: `src/main/java/io/mczju/maggoteers/state/PlayerState.java`（加 `acquiredUnique` 集）
- Modify: `src/main/resources/rewards.yml`（加 STAT 选项）

**Interfaces:**
- Produces: `RewardOption` 加 `Trigger trigger`(null=常驻)、`Effect effect`、`EffectContext params`、`Stack stack`、`Material icon`、`boolean requiresUnlock`、`int unlockCost`、`boolean unique`；`fromMap` 解析这些字段。`PlayerState.acquiredUnique()`/`addAcquiredUnique(id)`。

- [ ] **Step 1: `PlayerState` 加 `acquiredUnique`（本局唯一选项集）**

在 `PlayerState` 的 `effects` 列表字段后加：
```java
    private final java.util.Set<String> acquiredUnique = new java.util.HashSet<>();
```
访问方法（在 `clearEffects()` 后）：
```java
    public java.util.Set<String> acquiredUnique() { return acquiredUnique; }
```
并在 `clearEffects()` 末尾追加 `acquiredUnique.clear();`（对局结束一并清）。

- [ ] **Step 2: `RewardOption` reshape**

整文件替换 `reward/RewardOption.java`：
```java
package io.mczju.maggoteers.reward;

import io.mczju.maggoteers.effect.Effect;
import io.mczju.maggoteers.effect.EffectContext;
import io.mczju.maggoteers.effect.EffectKey;
import io.mczju.maggoteers.effect.EffectKeys;
import io.mczju.maggoteers.effect.Stack;
import io.mczju.maggoteers.effect.Trigger;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.potion.PotionEffectType;

import java.util.Map;

/**
 * 奖励项（§12.1）。
 * <ul>
 *   <li>STAT：{@code effect/params/stack/trigger} 描述一条 {@link io.mczju.maggoteers.effect.PlayerEffect}（trigger 缺省=常驻）。</li>
 *   <li>WEAPON/SUPPLY：{@code item/amount} 发物品。</li>
 * </ul>
 */
public record RewardOption(
        String id, String display, String description, Category category,
        String item, int amount,
        Trigger trigger, Effect effect, EffectContext params, Stack stack,
        Material icon, boolean requiresUnlock, int unlockCost, boolean unique
) {
    public enum Category { STAT, WEAPON, SUPPLY }

    public RewardOption {
        if (amount < 1) amount = 1;
        if (stack == null) stack = Stack.ADD;
    }

    public static RewardOption fromMap(Map<?, ?> m) {
        Category cat = Category.valueOf(str(m, "category", "SUPPLY").toUpperCase());
        Trigger trigger = optEnum(m, "trigger", Trigger.class);
        Effect effect = optEnum(m, "effect", Effect.class);
        EffectContext params = cat == Category.STAT ? parseParams(m) : null;
        Stack stack = optEnum(m, "stack", Stack.class);
        Material icon = optEnum(m, "icon", Material.class);
        boolean reqUnlock = bool(m, "requires_unlock");
        int unlockCost = num(m, "unlock_cost", 0);
        boolean unique = bool(m, "unique");
        return new RewardOption(
                str(m, "id"), str(m, "display"), str(m, "description"), cat,
                str(m, "item"), num(m, "amount", 1),
                trigger, effect, params, stack, icon, reqUnlock, unlockCost, unique);
    }

    /** STAT 选项是否完整到能建成 PlayerEffect（effect + 必要 params）。 */
    public boolean isStatEffect() {
        return category == Category.STAT && effect != null;
    }

    @SuppressWarnings("unchecked")
    private static EffectContext parseParams(Map<?, ?> m) {
        Object raw = m.get("params");
        if (!(raw instanceof Map<?, ?> pm)) return new EffectContext();
        EffectContext ctx = new EffectContext();
        for (var e : pm.entrySet()) {
            String k = String.valueOf(e.getKey());
            Object v = e.getValue();
            switch (k) {
                case "attr" -> { Attribute a = safeEnum(Attribute.class, v); if (a != null) ctx.put(EffectKeys.ATTR, a); }
                case "op" -> ctx.put(EffectKeys.OP, String.valueOf(v).toUpperCase());
                case "value", "amount", "radius", "damage" -> {
                    EffectKey<Double> key = switch (k) {
                        case "amount" -> EffectKeys.AMOUNT;
                        case "radius" -> EffectKeys.RADIUS;
                        case "damage" -> EffectKeys.DAMAGE;
                        default -> EffectKeys.VALUE;
                    };
                    ctx.put(key, v instanceof Number n ? n.doubleValue() : 0.0);
                }
                case "amp", "count", "duration_ticks" -> {
                    int iv = v instanceof Number n ? n.intValue() : 0;
                    if (k.equals("amp")) ctx.put(EffectKeys.AMP, iv);
                    else if (k.equals("count")) ctx.put(EffectKeys.COUNT, iv);
                    else ctx.put(EffectKeys.DURATION_TICKS, iv);
                }
                case "potion" -> { PotionEffectType p = PotionEffectType.getByName(String.valueOf(v).toUpperCase()); if (p != null) ctx.put(EffectKeys.POTION, p); }
                default -> { /* 忽略未知键 */ }
            }
        }
        return ctx;
    }

    private static <T extends Enum<T>> T optEnum(Map<?, ?> m, String key, Class<T> type) {
        Object v = m.get(key);
        if (v == null) return null;
        return safeEnum(type, v);
    }
    private static <T extends Enum<T>> T safeEnum(Class<T> type, Object v) {
        try { return Enum.valueOf(type, String.valueOf(v).toUpperCase()); }
        catch (Exception e) { return null; }
    }
    private static boolean bool(Map<?, ?> m, String key) {
        Object v = m.get(key); return v != null && Boolean.parseBoolean(String.valueOf(v));
    }
    private static int num(Map<?, ?> m, String key, int def) {
        Object v = m.get(key); return v instanceof Number n ? n.intValue() : def;
    }
    private static String str(Map<?, ?> m, String key) { return str(m, key, ""); }
    private static String str(Map<?, ?> m, String key, String def) {
        Object v = m.get(key); return v == null ? def : String.valueOf(v);
    }

    public String displayPlain() {
        return PlainTextComponentSerializer.plainText().serialize(
                net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(display));
    }
}
```

> `PotionEffectType.getByName` 在 Paper 1.21.7 仍可用（旧静态法）；若编译告警/失败，改用 `Registry.EFFECT.get(NamespacedKey.minecraft(...))`。先按 `getByName` 编译。

- [ ] **Step 3: `rewards.yml` 加 STAT 选项（喂效果引擎）**

在每个 `act1_weak/strong/boss`（act2/act3 同理）的 `options` 里**追加** STAT 选项示例。例如在 `act1_weak.options` 末尾加：
```yaml
      - id: a1w_dmg10
        display: "<red>+10% 攻击伤害"
        description: "常驻 +10% ATTACK_DAMAGE"
        category: STAT
        effect: ADD_ATTRIBUTE
        params: { attr: ATTACK_DAMAGE, op: PERCENT, value: 0.10 }
        stack: ADD
        icon: IRON_SWORD
      - id: a1w_lifesteal
        display: "<dark_red>嗜血"
        description: "击杀回 2 心"
        category: STAT
        trigger: ON_KILL
        effect: HEAL
        params: { amount: 4.0 }
        stack: IGNORE
        icon: GOLDEN_APPLE
```
在 `act1_strong.options` 末尾加（可升级示例）：
```yaml
      - id: a1s_resist
        display: "<aqua>抗性(可升级)"
        description: "抗性 I，重复抽到升级（最多 4 级）"
        category: STAT
        effect: ADD_POTION
        params: { potion: RESISTANCE, amp: 0, duration_ticks: 0 }
        stack: UPGRADE_LEVEL
        icon: SHIELD
```
（act2/act3 各池同理追加 1–2 个 STAT 选项即可，让引擎有真实输入。`amp` 随 UPGRADE_LEVEL level 升——见 Task 3 Step 2 的 level 注入。）

- [ ] **Step 4: 构建 + 测试**

Run: `mvn -q test` → 全绿（新增字段不破旧测试；若 `RewardOptionTest` 之类不存在则无碍）。`mvn -q -DskipTests compile` 确认 `PotionEffectType.getByName`/`SkullMeta` 等编译过。

- [ ] **Step 5: 提交**

```bash
git add src/main/java/io/mczju/maggoteers/reward/RewardOption.java \
        src/main/java/io/mczju/maggoteers/state/PlayerState.java \
        src/main/resources/rewards.yml
git commit -m "feat(plan6): RewardOption §12 reshape(trigger/effect/params/stack/icon/unlock/unique) + STAT 选项 + acquiredUnique"
```

---

## Task 3: RewardService.apply 改走 EffectService + unique 可见性过滤

**Files:**
- Modify: `src/main/java/io/mczju/maggoteers/reward/RewardService.java`（`apply` STAT 分支 + `draw` 加 unique 过滤）
- Modify: `src/main/java/io/mczju/maggoteers/menu/PickMenu.java`（按可见性抽）

**Interfaces:**
- Consumes: `RewardOption.isStatEffect()`、`EffectService.apply`、`PlayerStateManager.get(...).acquiredUnique()`。
- Produces: STAT 选项建成 `PlayerEffect` 喂引擎；`unique` 选后记入 `acquiredUnique`，`draw` 过滤掉已选 unique。

- [ ] **Step 1: `RewardService.apply` STAT 走 EffectService**

把 `RewardService.apply` 整方法替换为：
```java
    public static void apply(Player player, RewardOption opt) {
        if (opt == null) return;
        switch (opt.category()) {
            case SUPPLY, WEAPON -> {
                ItemService.give(player, opt.item(), opt.amount());
                if ("maggoteers:supply_healing".equals(opt.item())) {
                    var attr = player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
                    double max = attr != null ? attr.getValue() : 20.0;
                    player.setHealth(Math.min(max, player.getHealth() + 12.0));
                }
            }
            case STAT -> applyStat(player, opt);
        }
        // unique：记入本局已选，下次抽不再出现
        if (opt.unique()) {
            var pe = new com.github.mczjuops.mczjugamecore.player.PlayerExt(player);
            if (pe.isInGame()) {
                var ps = io.mczju.maggoteers.state.PlayerStateManager.get(pe.getGame(), player.getUniqueId());
                if (ps != null) ps.acquiredUnique().add(opt.id());
            }
        }
        player.sendMessage(net.kyori.adventure.text.Component.text(
                "获得：" + opt.displayPlain(), net.kyori.adventure.text.format.NamedTextColor.GREEN));
    }

    /** STAT 选项 → 建成 PlayerEffect → EffectService.apply。 */
    private static void applyStat(Player player, RewardOption opt) {
        if (!opt.isStatEffect()) return;   // 配置不全则不发
        int level = 1;
        io.mczju.maggoteers.effect.EffectContext params = opt.params();
        // UPGRADE_LEVEL：按玩家已 acquired 次数定 level（amp/value 随 level 缩放）
        if (opt.stack() == io.mczju.maggoteers.effect.Stack.UPGRADE_LEVEL) {
            var pe = new com.github.mczjuops.mczjugamecore.player.PlayerExt(player);
            var ps = pe.isInGame() ? io.mczju.maggoteers.state.PlayerStateManager.get(pe.getGame(), player.getUniqueId()) : null;
            int acquired = ps == null ? 0 : (int) ps.acquiredUnique().stream().filter(opt.id()::equals).count();
            level = Math.min(acquired + 1, 4);
            // 药水 amp 随 level：amp = baseAmp + (level-1)
            Integer baseAmp = params.get(io.mczju.maggoteers.effect.EffectKeys.AMP);
            if (baseAmp != null) params.put(io.mczju.maggoteers.effect.EffectKeys.AMP, baseAmp + (level - 1));
        }
        io.mczju.maggoteers.effect.PlayerEffect pe = new io.mczju.maggoteers.effect.PlayerEffect(
                opt.id(), opt.effect(), params, opt.trigger(),
                null, 0, 0, null, opt.stack(), 4, 0);
        pe.setLevel(level);
        io.mczju.maggoteers.effect.EffectService.apply(player, pe);
    }
```

> 注意：`UPGRADE_LEVEL` 的 `acquiredUnique` 复用做"已抽次数"计数——故 `a1s_resist` 这类可升级 STAT 选项**不要**再标 `unique: true`（否则会被 unique 过滤移出池）；Task 2 的 `a1s_resist` 未标 unique，正确。UPGRADE_LEVEL 靠 `acquiredUnique` 计数升 level，但不过滤（过滤仅看 `unique` 字段）。

- [ ] **Step 2: `RewardService.draw` 加可见性过滤**

把 `draw` 方法替换为（按 `unique` 过滤；`requires_unlock` 留 Plan 7 钩子）：
```java
    /** 从池中随机抽 n 个**对玩家可见**的不同选项（不足则全给）。unique 已选过的排除；requires_unlock 暂恒可见（Plan 7 接 unlocks）。 */
    public static List<RewardOption> draw(String poolId, int n, Random rng, org.bukkit.entity.Player viewer) {
        RewardPool pool = POOLS.get(poolId);
        if (pool == null || pool.options().isEmpty()) return List.of();
        var pe = new com.github.mczjuops.mczjugamecore.player.PlayerExt(viewer);
        var ps = pe.isInGame() ? io.mczju.maggoteers.state.PlayerStateManager.get(pe.getGame(), viewer.getUniqueId()) : null;
        java.util.Set<String> acquired = ps == null ? java.util.Set.of() : ps.acquiredUnique();
        List<RewardOption> visible = new ArrayList<>();
        for (RewardOption o : pool.options()) {
            if (o.unique() && acquired.contains(o.id())) continue;     // §12.2 unique 过滤
            // requires_unlock 过滤留 Plan 7（unlocks 持久化前恒可见）
            visible.add(o);
        }
        Collections.shuffle(visible, rng);
        return visible.subList(0, Math.min(n, visible.size()));
    }
```
保留旧 `draw(poolId, n, rng)` 重载（委托新签名、传 `null` viewer → 不过滤）以免破坏其它调用，或在 PickMenu 改用新签名后删除旧重载（见 Step 3）。

- [ ] **Step 3: `PickMenu` 用带 viewer 的 draw**

`PickMenu` 构造器里把 `RewardService.draw(poolId, 3, new Random())` 改为：
```java
        this.offers = RewardService.draw(poolId, 3, new Random(), player);
```
（`player` 是 `Menu` 基类提供的 `Player`。若 `draw` 旧重载已删，这是唯一调用点。）

- [ ] **Step 4: 构建 + 测试 + 手测**

`mvn -q test`（全绿）；`mvn -q -DskipTests package` 部署。
手测：休整期开 3 选 1，抽 `+10% 攻击伤害` → 攻击力属性涨（`/minecraft:data get` 验证 ATTACK_DAMAGE 增）；抽 `嗜血` → 击杀回血；重复抽 `抗性` → 抗性等级递增（I→II…）。`unique` 选项抽过后下次不再出现。

- [ ] **Step 5: 提交**

```bash
git add src/main/java/io/mczju/maggoteers/reward/RewardService.java \
        src/main/java/io/mczju/maggoteers/menu/PickMenu.java
git commit -m "feat(plan6): RewardService STAT→EffectService + unique/UPGRADE_LEVEL 可见性与升级"
```

---

## Task 4: ON_INTERACT 触发（未来魔法武器入口）

**Files:**
- Modify: `src/main/java/io/mczju/maggoteers/item/ItemInteractRouter.java`

**Interfaces:**
- Produces: 持有带 `maggoteers:id`（无特殊 kind）的物品右键 → `EffectService.fireTrigger(game, ON_INTERACT)`（供未来魔法武器配 `fireTrigger=ON_INTERACT` 的 PlayerEffect）。

- [ ] **Step 1: 路由末尾加 ON_INTERACT 兜底**

在 `ItemInteractRouter.onInteract` 的 `if (handler == null) return;` 处，改成：已知 kind 无 handler 时**不直接 return**，而是对局内右键 → fire `ON_INTERACT`（让未来魔法武器能用）。把：
```java
        ItemUseHandler handler = HANDLERS.get(kind);
        if (handler == null) return;
```
改为：
```java
        ItemUseHandler handler = HANDLERS.get(kind);
        event.setCancelled(true);
        PlayerExt pe = new PlayerExt(event.getPlayer());
        if (!pe.isInGame()) return;
        AbstractGame game = pe.getGame();
        if (game == null) return;
        if (handler != null) {
            handler.onUse(event.getPlayer(), game, stack);
        } else {
            // 无专用 handler：作为通用 ON_INTERACT 触发（未来魔法武器 / CD 门禁在 Task 5 加）
            io.mczju.maggoteers.effect.EffectService.fireTrigger(
                    (io.mczju.maggoteers.game.MaggoteersGame) game,
                    io.mczju.maggoteers.effect.Trigger.ON_INTERACT);
        }
```
（删掉后面重复的 `event.setCancelled(true)` + `PlayerExt pe = ...` + `game` 取值块——已上移。）

- [ ] **Step 2: 构建 + 提交**

`mvn -q -DskipTests compile` → BUILD SUCCESS。
```bash
git add src/main/java/io/mczju/maggoteers/item/ItemInteractRouter.java
git commit -m "feat(plan6): ItemInteractRouter 兜底 fire ON_INTERACT(魔法武器入口)"
```

---

## Task 5: D2 物品 CD（按 PDC-id 时间戳表）

**Files:**
- Create: `src/main/java/io/mczju/maggoteers/item/CooldownService.java`
- Modify: `src/main/java/io/mczju/maggoteers/item/ItemInteractRouter.java`（分发前查 CD → 在 CD 则拦、否则执行并计 CD）
- Modify: `src/main/resources/items/maggoteers.yml`（可选：给某武器加 `cooldown_sec`，通过 PDC 或配置——本 Plan 用物品 id 查一张 `item_cooldowns.yml` 或硬编码示例）

**Interfaces:**
- Produces: `CooldownService.tryUse(UUID player, String pdcId, int cooldownSec): boolean`（true=放行并计 CD；false=在 CD）。路由对 `ON_INTERACT` 兜底武器调此门禁。

- [ ] **Step 1: 写 `CooldownService`**

```java
package io.mczju.maggoteers.item;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 物品 CD 门禁（D2）：按 **PDC id** 的时间戳表，**不用** {@code setCooldown(Material)}（同材质不同 PDC 会串 CD）。
 * 主线程同步访问，无需加锁。
 */
public final class CooldownService {
    private static final Map<UUID, Map<String, Long>> EXPIRE = new HashMap<>();   // player → pdcId → expireMillis

    /** 放行且计 CD 返回 true；在 CD 返回 false。 */
    public static boolean tryUse(UUID player, String pdcId, int cooldownSec) {
        if (cooldownSec <= 0) return true;
        long now = System.currentTimeMillis();
        long exp = EXPIRE.getOrDefault(player, Map.of()).getOrDefault(pdcId, 0L);
        if (now < exp) return false;
        EXPIRE.computeIfAbsent(player, k -> new HashMap<>()).put(pdcId, now + cooldownSec * 1000L);
        return true;
    }

    public static boolean onCooldown(UUID player, String pdcId) {
        return System.currentTimeMillis() < EXPIRE.getOrDefault(player, Map.of()).getOrDefault(pdcId, 0L);
    }

    /** 对局/退出清理（可选）。 */
    public static void clear(UUID player) { EXPIRE.remove(player); }

    private CooldownService() {}
}
```

- [ ] **Step 2: 路由兜底 ON_INTERACT 加 CD 门禁**

在 Task 4 的 `else`（无专用 handler）分支，fire ON_INTERACT 前加 CD 检查（用物品 PDC id `maggoteers:id`；CD 秒数暂取配置或固定 1s 示例）：
```java
        } else {
            String pdcId = io.mczju.maggoteers.item.ItemService.itemIdOf(stack);   // 见下注
            int cd = 1;   // 默认 1s；Plan 8 接 item_cooldowns.yml 按物品配
            if (!io.mczju.maggoteers.item.CooldownService.tryUse(event.getPlayer().getUniqueId(), pdcId, cd)) {
                event.getPlayer().sendMessage(net.kyori.adventure.text.Component.text(
                        "技能冷却中…", net.kyori.adventure.text.format.NamedTextColor.GRAY));
                return;
            }
            io.mczju.maggoteers.effect.EffectService.fireTrigger(
                    (io.mczju.maggoteers.game.MaggoteersGame) game,
                    io.mczju.maggoteers.effect.Trigger.ON_INTERACT);
        }
```
> 注：`ItemService.itemIdOf(stack)` 若不存在，在 `ItemService` 加一个读 PDC `maggoteers:id` 的静态方法（类似 `readKind` 但读 `id`）。若本 Plan 不接魔法武器，可暂用 `stack.getType().name()` 占位，Task 6 验证后补。

- [ ] **Step 3: 构建 + 提交**

`mvn -q -DskipTests compile`。提交：
```bash
git add src/main/java/io/mczju/maggoteers/item/CooldownService.java \
        src/main/java/io/mczju/maggoteers/item/ItemInteractRouter.java \
        src/main/java/io/mczju/maggoteers/item/ItemService.java
git commit -m "feat(plan6): D2 物品 CD(CooldownService 按 PDC-id 时间戳) + ON_INTERACT 门禁"
```

---

## Task 6: D1 净化（ADD_POTION 0s-255 抵消 + damage-cancel fallback）

**Files:**
- Modify: `src/main/java/io/mczju/maggoteers/effect/EffectService.java`（`applyPotionPermanent` 支持"免疫"语义）
- Create: `src/main/java/io/mczju/maggoteers/listener/PurifyListener.java`（fallback：按 DamageCause cancel）

**Interfaces:**
- Produces: `ADD_POTION` 带 params `{immunity: <PotionEffectType>}` → 施加 0s 255 级抵消（实测）；不成立则 `PurifyListener` 监听 `EntityDamageEvent` 按 cause cancel（wither/poison 等）。

- [ ] **Step 1: `applyPotionPermanent` 加免疫分支（0s 255 技巧）**

在 `EffectService.applyPotionPermanent` 末尾、施加药水前，识别"免疫"语义（params 带 `immunity` 键指向要抵消的类型）。先加一个 `EffectKeys.IMMUNITY` 键（`EffectKey<PotionEffectType>`），然后：
```java
    private static void applyPotionPermanent(Player p, PlayerEffect e) {
        PotionEffectType type = e.params().get(EffectKeys.POTION);
        if (type == null) return;
        int amp = e.params().getOrDefault(EffectKeys.AMP, 0);
        // 净化技巧（D1）：amp=255 + duration=0 视为"免疫该效果"（0s 255 级抵消）——实测是否成立
        if (amp >= 255) {
            // 0s 255 级：尝试抵消；Paper 实测若无效，靠 PurifyListener fallback
            p.addPotionEffect(new PotionEffect(type, 20 * 30, 255, false, false, false));
            return;
        }
        p.addPotionEffect(new PotionEffect(type, 20 * 30, amp, false, false, true));
    }
```
（`EffectKeys.IMMUNITY` 本 Plan 可不加——直接用 `amp:255` 触发免疫分支，配置侧 `params: {potion: WITHER, amp: 255}`。）

- [ ] **Step 2: 写 `PurifyListener`（damage-cancel fallback）**

```java
package io.mczju.maggoteers.listener;

import com.github.mczjuops.mczjugamecore.player.PlayerExt;
import io.mczju.maggoteers.game.MaggoteersGame;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;

import java.util.Set;

/**
 * D1 净化 fallback：若"0s 255 级抵消"技巧不成立，按 DamageCause cancel（免疫 wither/poison 等）。
 * <p>对局内玩家若持有效果 id 形如 `immunity_<cause>` 的常驻效果，则对应 cause 伤害被取消。
 * Plan 6 先做 POISON/WITHER 两个示例；实现期实测 D1 技巧后，若技巧成立则可移除本监听。
 */
public final class PurifyListener implements Listener {
    private static final Set<EntityDamageEvent.DamageCause> PURIFIABLE =
            Set.of(EntityDamageEvent.DamageCause.POISON, EntityDamageEvent.DamageCause.WITHER);

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        if (!PURIFIABLE.contains(e.getCause())) return;
        var pe = new PlayerExt(p);
        if (!pe.isInGame() || !(pe.getGame() instanceof MaggoteersGame mg)) return;
        var ps = io.mczju.maggoteers.state.PlayerStateManager.get(mg, p.getUniqueId());
        if (ps == null) return;
        // 持有"免疫该 cause"的常驻效果则取消（效果 id 约定：immunity_<cause 小写>）
        String need = "immunity_" + e.getCause().name().toLowerCase();
        if (ps.effects().stream().anyMatch(x -> x.isPermanent() && x.id().equals(need))) {
            e.setCancelled(true);
        }
    }
}
```

- [ ] **Step 3: 注册 `PurifyListener` + 构建 + 实测**

`MaggoteersPlugin.onEnable` 加 `registerEvents(new PurifyListener(), this)`。
`mvn -q test` + `mvn -q -DskipTests package` 部署。
**手测（D1 必测）**：配置一条 `effect: ADD_POTION, params: {potion: WITHER, amp: 255}` 的常驻效果，给玩家施加，让凋零骷髅打玩家。若玩家**不**获得凋零效果 → 0s-255 技巧成立（可在 dev-log 记录、移除 PurifyListener）；若仍获凋零但**不**掉血 → PurifyListener fallback 生效（保留监听）。dev-log 记录实测结论。

- [ ] **Step 4: 提交**

```bash
git add src/main/java/io/mczju/maggoteers/effect/EffectService.java \
        src/main/java/io/mczju/maggoteers/listener/PurifyListener.java \
        src/main/java/io/mczju/maggoteers/MaggoteersPlugin.java
git commit -m "feat(plan6): D1 净化(ADD_POTION 255 技巧 + PurifyListener damage-cancel fallback)"
```

---

## Plan 6 验收标准（Definition of Done）

- [ ] `mvn package` 产 jar，`mvn test` 绿。
- [ ] **右键空气**能打开菜单（休整/职业/复活币）——用户 flag bug 修复。
- [ ] **复活币**右键开 `ReviveMenu`：点死者复活、点活者 +1 次；扣 1 币。
- [ ] STAT 奖励（+10% 伤害/嗜血/抗性升级）经 `EffectService` 生效；`unique` 抽过后移出池。
- [ ] `ON_INTERACT` 兜底触发；D2 物品 CD 按 PDC-id 不串材质。
- [ ] D1 净化实测有结论（技巧成立 or fallback 生效），dev-log 记录。
- [ ] `docs/dev-log.md` 追加 Plan 6 完成条目。

> **已知遗留（Plan 7）**：`requires_unlock` 持久 `unlocks`（账户货币/商店/解锁/排行榜/结算/通关时间榜）；`ON_INTERACT` 的魔法武器实例（旋转刀片等）；`item_cooldowns.yml` 按 id 配 CD（Plan 8）。
