# The Maggoteers — Plan 7: 持久化 + 局外商店 + 解锁回流 + 排行榜 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 接入 MGC 持久化（`MaggoteersPlayerData{balance, totalEarned, unlocks}` 跨局保存），实现 **结算**（onGameEnd 按 WIN/FAIL 发账户货币）、**局外商店**（花 `balance` 解锁奖励项 → 写 `unlocks`）、**解锁回流局内**（开局把 `unlocks` 并入个人池 + `RewardService.draw` 过滤 `requires_unlock`）、**排行榜**（`totalEarned` 降序）。

**Architecture:** `MaggoteersPlayerData extends JsonPlayerData`（MGC 落盘 `plugins/MCZJUGameCore/player_data/maggoteers/<uuid>.json`，改完 `setModified(true)`）。`Settlement` 纯函数算 grant（单测）。`onGameEnd` 据 `outcome` 调 `Settlement.grant` 写每位玩家的 `balance/totalEarned`。`menu/UnlockShopMenu`（MGC `Menu`，商品 = 所有池里 `requires_unlock:true` 的选项，花 `unlock_cost` 买 → `unlocks.add`）。入口 `/maggoteers shop`（`CommandExecutor`，plugin.yml 注册）。`unlock/UnlockRegistry`（开局读每玩家 `unlocks` → `RewardService` 过滤）。`MaggoteersTotalLeaderboard extends PlayerDataLeaderboard`（字段 `totalEarned` 降序）。

**Tech Stack:** Paper 1.21.7，Java 21，Maven，MCZJUGameCore 1.0.5（`JsonPlayerData`/`PlayerDataManager`/`PlayerExt.getData`/`PlayerDataLeaderboard`/`LeaderboardManager`，**API 已 javap 核实**），JUnit 5。

## Global Constraints

- **前置条件**：Plan 1–6 + QA#1/#2 已落地（HEAD `54f21bf`）。`RewardOption` 已有 `requiresUnlock`/`unlockCost` 字段；`RewardService.draw(poolId,n,rng,viewer)` 已有 `requires_unlock 过滤留 Plan 7` 钩子（line 73，当前恒可见）；`MaggoteersGame` 有 `outcome`(GameOutcome)/`getOutcome()`/`win()`/`fail()`；`MaggoteersPlugin.onEnable` 已 `registerGame` + 各 `load` + 4 个 `registerMenu`，**无** `registerPlayerData`/`registerLeaderboard`/命令。
- **MGC PlayerData API（已 javap 核实 1.0.5）**：
  - `JsonPlayerData extends AbstractPlayerData`（`AbstractPlayerData`: `setModified(boolean)`/`isModified()`/`getPlayerID()`；`JsonPlayerData.getFilePath()` 由 MGC 按 gameId 构造路径）。
  - `MCZJUGameCore.getPlayerDataManager().registerPlayerData("maggoteers", MaggoteersPlayerData.class)`。
  - 读玩家数据：`new PlayerExt(player).getData(MaggoteersPlayerData.class)`。
  - 改完**必须** `data.setModified(true)`，否则不落盘。
- **MGC Leaderboard API（已核实）**：`PlayerDataLeaderboard extends AbstractLeaderboard`——实现 `getTitle()`/`getSubtitle()`（AbstractLeaderboard 抽象）+ `getPlayerDataClass()`/`getFieldName()`（PlayerDataLeaderboard 抽象，反射字段名）；`fetchEntries()` 由 MGC 实现。`MCZJUGameCore.getLeaderboardManager().registerLeaderboard("maggoteers_total", MaggoteersTotalLeaderboard.class)`。
- **D4 outcome**：`onGameEnd` 据 `getOutcome()`(WIN/FAIL) 分支结算（IN_PROGRESS 不发）。
- **读 PlayerData 的两种上下文**：局内（`onGameEnd`/开局）用 `new PlayerExt(player).getData(MaggoteersPlayerData.class)`（玩家在局里）；**局外**（`/maggoteers shop`，玩家不在局里）`PlayerExt.getData` 可能返回 null——届时改用 `MCZJUGameCore.getPlayerDataManager().getPlayerData(player.getUniqueId().toString(), MaggoteersPlayerData.class)`（Task 3 实现时 javap 确认 `getPlayerData(String, Class)` 的第一个 String 是玩家 UUID 还是 gameId；若是 gameId+playerId 两参版，传 `("maggoteers", uuid)`）。**实现期核实，别猜**。
- **结算公式（§7.6）**：WIN = `win_flat`；FAIL = `fail_per_act × 已过层数 + fail_per_wave × 当前层已过波数`。
- **铁律①②③** 继承。
- **JDK 21 + Maven 在 PATH**。

---

## Task 1: MaggoteersPlayerData + Settlement 纯函数 + config settlement + registerPlayerData（+ 单测）

**Files:**
- Create: `src/main/java/io/mczju/maggoteers/persist/MaggoteersPlayerData.java`
- Create: `src/main/java/io/mczju/maggoteers/persist/Settlement.java`
- Modify: `src/main/resources/config.yml`（加 `settlement` 段）
- Modify: `src/main/java/io/mczju/maggoteers/MaggoteersPlugin.java`（`registerPlayerData`）
- Test: `src/test/java/io/mczju/maggoteers/persist/SettlementTest.java`

**Interfaces:**
- Produces: `MaggoteersPlayerData{public int balance; public int totalEarned; public List<String> unlocks}`（extends JsonPlayerData，public 字段供 Gson 反射 + 排行榜字段名）；`Settlement.grant(Input, winFlat, failPerAct, failPerWave): int`（纯函数）。

- [ ] **Step 1: 写 `MaggoteersPlayerData`**

```java
package io.mczju.maggoteers.persist;

import com.github.mczjuops.mczjugamecore.player.data.JsonPlayerData;
import java.util.ArrayList;
import java.util.List;

/**
 * 跨局持久化（§13.1，MGC JsonPlayerData 落盘到 player_data/maggoteers/<uuid>.json）。
 * <p>字段必须 public（Gson 反射 + 排行榜按字段名 totalEarned 取值）。改完务必 {@link #setModified(true)}。
 */
public class MaggoteersPlayerData extends JsonPlayerData {
    public int balance = 0;            // 可花费账户货币
    public int totalEarned = 0;        // 累计获得（只增不减 = 排行榜积分）
    public List<String> unlocks = new ArrayList<>();   // 已解锁的 reward option.id

    public void grant(int amount) {
        if (amount <= 0) return;
        balance += amount;
        totalEarned += amount;
        setModified(true);
    }

    public boolean spend(int amount) {
        if (amount <= 0 || balance < amount) return false;
        balance -= amount;
        setModified(true);
        return true;
    }

    public boolean unlock(String optionId) {
        if (optionId == null || unlocks.contains(optionId)) return false;
        unlocks.add(optionId);
        setModified(true);
        return true;
    }
}
```

- [ ] **Step 2: 写 `Settlement` 纯函数**

```java
package io.mczju.maggoteers.persist;

import io.mczju.maggoteers.game.GameOutcome;

/**
 * 结算纯函数（§7.6，与 Bukkit 解耦，单测）。
 * <p>WIN = win_flat；FAIL = fail_per_act × 已过层数 + fail_per_wave × 当前层已过波数；IN_PROGRESS = 0。
 */
public final class Settlement {
    public record Input(GameOutcome outcome, int actsCleared, int wavesClearedInCurrentAct) {}

    public static int grant(Input in, int winFlat, int failPerAct, int failPerWave) {
        if (in == null || in.outcome() == null) return 0;
        return switch (in.outcome()) {
            case WIN  -> winFlat;
            case FAIL -> Math.max(0, failPerAct) * Math.max(0, in.actsCleared())
                       + Math.max(0, failPerWave) * Math.max(0, in.wavesClearedInCurrentAct());
            default   -> 0;   // IN_PROGRESS
        };
    }

    private Settlement() {}
}
```

- [ ] **Step 3: 写失败测试 `SettlementTest`**

```java
package io.mczju.maggoteers.persist;

import io.mczju.maggoteers.game.GameOutcome;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SettlementTest {
    @Test
    void winIsFlat() {
        assertEquals(100, Settlement.grant(new Settlement.Input(GameOutcome.WIN, 3, 6), 100, 25, 5));
    }

    @Test
    void failScalesWithProgress() {
        // 2 层过 + 当前层 3 波过 → 25*2 + 5*3 = 65
        assertEquals(65, Settlement.grant(new Settlement.Input(GameOutcome.FAIL, 2, 3), 100, 25, 5));
    }

    @Test
    void inProgressIsZero() {
        assertEquals(0, Settlement.grant(new Settlement.Input(GameOutcome.IN_PROGRESS, 0, 0), 100, 25, 5));
    }

    @Test
    void negativeProgressClampedToZero() {
        assertEquals(0, Settlement.grant(new Settlement.Input(GameOutcome.FAIL, -1, -2), 100, 25, 5));
    }
}
```

- [ ] **Step 4: config.yml 加 `settlement` 段**

在 `config.yml` 末尾追加：
```yaml
settlement:
  win_flat: 100
  fail_per_act: 25
  fail_per_wave: 5
```

- [ ] **Step 5: `MaggoteersPlugin.onEnable` 注册 PlayerData**

在 `registerGame(...)` 之后追加：
```java
        MCZJUGameCore.getPlayerDataManager().registerPlayerData("maggoteers", io.mczju.maggoteers.persist.MaggoteersPlayerData.class);
```

- [ ] **Step 6: 构建并跑测试**

Run: `mvn -q test`
Expected: `BUILD SUCCESS`，`SettlementTest`（4 例）通过，既有测试仍绿。

- [ ] **Step 7: 提交**

```bash
git add src/main/java/io/mczju/maggoteers/persist \
        src/test/java/io/mczju/maggoteers/persist/SettlementTest.java \
        src/main/resources/config.yml src/main/java/io/mczju/maggoteers/MaggoteersPlugin.java
git commit -m "feat(plan7): MaggoteersPlayerData + Settlement 纯函数 + registerPlayerData"
```

---

## Task 2: onGameEnd 结算（outcome → grant → balance/totalEarned）

**Files:**
- Modify: `src/main/java/io/mczju/maggoteers/game/MaggoteersGame.java`（`cleanupRun`/`onGameEnd` 加结算；从 WaveScheduler 取 acts/waves 进度）

**Interfaces:**
- Consumes: `MaggoteersPlayerData`（Task 1）、`Settlement.grant`、`WaveScheduler` 进度（actIndex/waveIndex）、`PlayerExt.getData(MaggoteersPlayerData.class)`、config `settlement.*`。
- Produces: 对局结束每位玩家账户货币按 outcome 入账（`balance`/`totalEarned` 增、`setModified`）。

- [ ] **Step 1: 暴露 WaveScheduler 进度（如缺）**

读 `wave/WaveScheduler.java`，确认 `snapshot(game)`（或等价）能给出当前 `actIndex`（0-based，已进入的第几层）+ `waveIndex`（当前层已清波数）。若 `snapshot` 只有 `actIndex`/`lastTier`，**加一个** `public static int[] progress(AbstractGame game)` 返回 `{actIndex, waveClearedInAct}`（从 `Cursor` 读 `actIndex` + `waveIndex`）。若已有，直接复用。

- [ ] **Step 2: 在 `cleanupRun()` 开头（ON_GAME_END 触发之后、cleanup 之前）加结算**

在 `cleanupRun()` 的最前面（`EffectService.fireTrigger(this, ON_GAME_END)` 之后）插入结算：
```java
        // 结算：按 outcome 发账户货币（D4）
        int winFlat = MaggoteersPlugin.getInstance().getConfig().getInt("settlement.win_flat", 100);
        int failPerAct = MaggoteersPlugin.getInstance().getConfig().getInt("settlement.fail_per_act", 25);
        int failPerWave = MaggoteersPlugin.getInstance().getConfig().getInt("settlement.fail_per_wave", 5);
        int[] prog = io.mczju.maggoteers.wave.WaveScheduler.progress(this);   // {actIndex, wavesClearedInAct}
        var input = new io.mczju.maggoteers.persist.Settlement.Input(
                outcome, prog[0], prog[1]);
        int grant = io.mczju.maggoteers.persist.Settlement.grant(input, winFlat, failPerAct, failPerWave);
        if (grant > 0) {
            for (var pe : getPlayers()) {
                var data = pe.getData(io.mczju.maggoteers.persist.MaggoteersPlayerData.class);
                if (data != null) {
                    data.grant(grant);
                    sender().info("<yellow>" + pe.player().getName() + " 结算获得 " + grant + " 卫戍币。");
                }
            }
        }
```
（`pe.getData(MaggoteersPlayerData.class)` 是 MGC 读玩家持久数据的标准方式；`data.grant` 内部 `setModified(true)` 触发落盘。结算必须在 `cleanupRun` 还能拿到 `getPlayers()` 时跑——它在最前面，满足。）

- [ ] **Step 3: 构建 + 测试**

Run: `mvn -q test`
Expected: `BUILD SUCCESS`（既有测试绿；本 Task 无新单测——Bukkit/MGC 运行时，手测）。

- [ ] **Step 4: 提交**

```bash
git add src/main/java/io/mczju/maggoteers/wave/WaveScheduler.java src/main/java/io/mczju/maggoteers/game/MaggoteersGame.java
git commit -m "feat(plan7): onGameEnd 按 outcome 结算账户货币(WIN/FAIL)"
```

---

## Task 3: 局外商店 UnlockShopMenu + /maggoteers shop 入口

**Files:**
- Create: `src/main/java/io/mczju/maggoteers/menu/UnlockShopMenu.java`
- Create: `src/main/java/io/mczju/maggoteers/command/MaggoteersCommand.java`
- Modify: `src/main/resources/plugin.yml`（注册 `maggoteers` 命令）
- Modify: `src/main/java/io/mczju/maggoteers/MaggoteersPlugin.java`（`registerMenu` + 注册命令 executor）

**Interfaces:**
- Consumes: `RewardService`（遍历所有池的 `requires_unlock:true` 选项）、`RewardOption.requiresUnlock/unlockCost/icon/id/display`、`PlayerExt.getData(MaggoteersPlayerData.class)`（`balance`/`unlocks`）、MGC `Menu`/`MenuFacade`。
- Produces: `/maggoteers shop`（玩家在大厅/任意处）→ 开 `UnlockShopMenu`：列出可解锁项（锁定/已拥有），点击付得起 → `data.spend(unlockCost)` + `data.unlock(id)`。

- [ ] **Step 1: 写 `UnlockShopMenu`（MGC Menu 子类）**

```java
package io.mczju.maggoteers.menu;

import com.github.mczjuops.mczjugamecore.menu.Menu;
import io.mczju.maggoteers.persist.MaggoteersPlayerData;
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

/**
 * 局外解锁商店（§13.3）：商品 = 所有池里 requires_unlock:true 的选项。
 * 点击：balance ≥ unlock_cost → 扣 balance、unlocks.add(id)。
 */
public class UnlockShopMenu extends Menu {

    public UnlockShopMenu(Player player, Object... args) {
        super(player, args);
    }

    @Override protected String getTitle() { return "<gold>卫戍商店 · 解锁"; }
    @Override protected int getRows() { return 6; }
    @Override protected String getPermission() { return "maggoteers.play"; }

    @Override
    protected void setup() {
        Player p = player.player();
        var pe = new com.github.mczjuops.mczjugamecore.player.PlayerExt(p);
        var data = pe.getData(MaggoteersPlayerData.class);
        List<RewardOption> goods = new ArrayList<>();
        for (String poolId : RewardService.allPoolIds()) {
            var pool = RewardService.pool(poolId);
            if (pool == null) continue;
            for (RewardOption o : pool.options()) {
                if (o.requiresUnlock()) goods.add(o);
            }
        }
        int slot = 10;
        for (RewardOption o : goods) {
            if (slot > 43) break;
            if ((slot + 1) % 9 == 0) slot += 2;   // 跳过边列
            boolean owned = data != null && data.unlocks.contains(o.id());
            setSlot(slot++, icon(o, o.unlockCost(), owned), (clicker, ev) -> {
                if (data == null) return;
                if (owned || data.unlocks.contains(o.id())) {
                    p.sendMessage(Component.text("已拥有：" + o.displayPlain(), NamedTextColor.GRAY));
                    return;
                }
                if (!data.spend(o.unlockCost())) {
                    p.sendMessage(Component.text("卫戍币不足（需 " + o.unlockCost() + "）", NamedTextColor.RED));
                    return;
                }
                data.unlock(o.id());
                p.sendMessage(Component.text("已解锁：" + o.displayPlain(), NamedTextColor.GREEN));
                p.closeInventory();
                com.github.mczjuops.mczjugamecore.menu.MenuFacade.open("maggoteers-shop", p);   // 刷新
            });
        }
    }

    private static ItemStack icon(RewardOption o, int cost, boolean owned) {
        ItemStack s = new ItemStack(owned ? Material.ENCHANTED_BOOK : (o.icon() != null ? o.icon() : Material.PAPER));
        ItemMeta m = s.getItemMeta();
        if (m != null) {
            m.displayName(Component.text(o.displayPlain(), owned ? NamedTextColor.GREEN : NamedTextColor.AQUA)
                    .decoration(TextDecoration.ITALIC, false));
            var lore = new ArrayList<Component>();
            lore.add(Component.text(owned ? "已解锁" : ("花费 " + cost + " 卫戍币"), NamedTextColor.YELLOW)
                    .decoration(TextDecoration.ITALIC, false));
            if (o.description() != null && !o.description().isBlank()) {
                lore.add(Component.text(o.description(), NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
            }
            m.lore(lore);
            s.setItemMeta(m);
        }
        return s;
    }
}
```

- [ ] **Step 2: 给 `RewardService` 加 `allPoolIds()`（供商店枚举商品）**

在 `reward/RewardService.java` 加：
```java
    /** 所有奖励池 id（局外商店枚举 requires_unlock 商品用）。 */
    public static java.util.Set<String> allPoolIds() { return POOLS.keySet(); }
```

- [ ] **Step 3: 写 `/maggoteers shop` CommandExecutor**

```java
package io.mczju.maggoteers.command;

import com.github.mczjuops.mczjugamecore.menu.MenuFacade;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/** /maggoteers shop —— 打开局外解锁商店（Plan 8 扩展更多子命令）。 */
public class MaggoteersCommand implements CommandExecutor {
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player p)) { sender.sendMessage("仅玩家可用。"); return true; }
        if (args.length == 1 && "shop".equalsIgnoreCase(args[0])) {
            MenuFacade.open("maggoteers-shop", p);
            return true;
        }
        p.sendMessage("用法：/maggoteers shop");
        return true;
    }
}
```

- [ ] **Step 4: `plugin.yml` 注册命令**

在 `plugin.yml` 加：
```yaml
commands:
  maggoteers:
    description: 卫戍协议命令（shop 等）
    usage: /maggoteers shop
    permission: maggoteers.play
```

- [ ] **Step 5: `MaggoteersPlugin.onEnable` 注册菜单 + 命令**

在现有 `registerMenu` 之后加：
```java
        MenuFacade.registerMenu("maggoteers-shop", io.mczju.maggoteers.menu.UnlockShopMenu.class);
```
在 onEnable 末尾（其它注册之后）加：
```java
        var mc = getCommand("maggoteers");
        if (mc != null) mc.setExecutor(new io.mczju.maggoteers.command.MaggoteersCommand());
```

- [ ] **Step 6: 构建 + 测试**

Run: `mvn -q test` → 全绿。

- [ ] **Step 7: 提交**

```bash
git add src/main/java/io/mczju/maggoteers/menu/UnlockShopMenu.java \
        src/main/java/io/mczju/maggoteers/command/MaggoteersCommand.java \
        src/main/java/io/mczju/maggoteers/reward/RewardService.java \
        src/main/resources/plugin.yml src/main/java/io/mczju/maggoteers/MaggoteersPlugin.java
git commit -m "feat(plan7): 局外解锁商店 UnlockShopMenu + /maggoteers shop 入口"
```

---

## Task 4: 解锁回流局内（UnlockRegistry + draw requires_unlock 过滤）

**Files:**
- Create: `src/main/java/io/mczju/maggoteers/unlock/UnlockRegistry.java`
- Modify: `src/main/java/io/mczju/maggoteers/reward/RewardService.java`（`draw` 加 requires_unlock 过滤；接受 viewer 的 unlocks）
- Modify: `src/main/java/io/mczju/maggoteers/game/MaggoteersGame.java`（开局把 unlocks 喂给 RewardService/每玩家）

**Interfaces:**
- Consumes: `MaggoteersPlayerData.unlocks`、`RewardOption.requiresUnlock`。
- Produces: 3 选 1 只 roll 出"未解锁的 requires_unlock 选项不出现"（§12.2）；已解锁项并入可 roll 池。

- [ ] **Step 1: 写 `UnlockRegistry`（读玩家持久 unlocks）**

```java
package io.mczju.maggoteers.unlock;

import com.github.mczjuops.mczjugamecore.player.PlayerExt;
import io.mczju.maggoteers.persist.MaggoteersPlayerData;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Set;

/** 读玩家持久 unlocks（局外商店买的），供 RewardService.draw 过滤 requires_unlock。 */
public final class UnlockRegistry {
    private UnlockRegistry() {}

    /** 玩家已解锁的 reward option.id 集（无数据返回空）。 */
    public static Set<String> unlocksOf(Player p) {
        var data = new PlayerExt(p).getData(MaggoteersPlayerData.class);
        return data == null ? new HashSet<>() : new HashSet<>(data.unlocks);
    }
}
```

- [ ] **Step 2: `RewardService.draw` 加 requires_unlock 过滤**

在 `draw(String poolId, int n, Random rng, Player viewer)` 的可见性循环里，把"requires_unlock 过滤留 Plan 7"那行替换为真正过滤。当前循环：
```java
        for (RewardOption o : pool.options()) {
            if (o.unique() && acquired.contains(o.id())) continue;     // §12.2 unique 过滤
            // requires_unlock 过滤留 Plan 7（unlocks 持久化前恒可见）
            visible.add(o);
        }
```
改为：
```java
        java.util.Set<String> unlocked = viewer == null ? java.util.Set.of()
                : io.mczju.maggoteers.unlock.UnlockRegistry.unlocksOf(viewer);
        for (RewardOption o : pool.options()) {
            if (o.unique() && acquired.contains(o.id())) continue;                 // §12.2 unique
            if (o.requiresUnlock() && !unlocked.contains(o.id())) continue;        // §12.2 requires_unlock
            visible.add(o);
        }
```
（viewer==null 的旧重载保持"恒可见"——只用于无玩家上下文的场景。）

- [ ] **Step 3: 构建 + 测试 + 提交**

`mvn -q test`（全绿）。本 Task 无新单测（unlocks 来源是 MGC 持久数据，headless 难测；过滤逻辑是 `requiresUnlock && !unlocked.contains` 一目了然）。
```bash
git add src/main/java/io/mczju/maggoteers/unlock src/main/java/io/mczju/maggoteers/reward/RewardService.java
git commit -m "feat(plan7): 解锁回流局内(UnlockRegistry + draw requires_unlock 过滤)"
```

> 注：`PickMenu` 已经把 viewer 传给 `draw`（Plan 6），所以 3 选 1 自动按 unlocks 过滤——无需改 PickMenu。"开局把 unlocks 并入个人池"在 §13.4 的原意是"让已解锁项可 roll 到"——Task 4 的 `requires_unlock && !unlocked.contains(o.id()) → continue` 已实现（未解锁的过滤掉 = 已解锁的可见）。故不需额外"并入池"步骤。

---

## Task 5: 排行榜 MaggoteersTotalLeaderboard + registerLeaderboard

**Files:**
- Create: `src/main/java/io/mczju/maggoteers/persist/MaggoteersTotalLeaderboard.java`
- Modify: `src/main/java/io/mczju/maggoteers/MaggoteersPlugin.java`（`registerLeaderboard`）

**Interfaces:**
- Produces: `MaggoteersTotalLeaderboard extends PlayerDataLeaderboard`（`getTitle`/`getSubtitle`/`getPlayerDataClass`→MaggoteersPlayerData/`getFieldName`→"totalEarned"）；`registerLeaderboard("maggoteers_total", ...)`。

- [ ] **Step 1: 写 `MaggoteersTotalLeaderboard`**

```java
package io.mczju.maggoteers.persist;

import com.github.mczjuops.mczjugamecore.player.data.AbstractPlayerData;
import com.github.mczjuops.mczjugamecore.score.leaderboard.PlayerDataLeaderboard;

/** 累计获得（totalEarned）降序排行榜（§13.5）。展示实体由运维 /mgcop leaderboard create 放置。 */
public class MaggoteersTotalLeaderboard extends PlayerDataLeaderboard {
    @Override public String getTitle() { return "<gold>卫戍协议 · 累计卫戍币"; }
    @Override public String getSubtitle() { return "<yellow>通关与失败结算的累计获得"; }
    @Override protected Class<? extends AbstractPlayerData> getPlayerDataClass() { return MaggoteersPlayerData.class; }
    @Override protected String getFieldName() { return "totalEarned"; }
    // fetchEntries() 由 PlayerDataLeaderboard 按 getFieldName 反射 + 默认降序实现
}
```

> `PlayerDataLeaderboard` 默认 `getSortOrder()`（继承自 AbstractLeaderboard）——若默认非降序，重写 `getSortOrder()` 返回 `SortOrder.DESCENDING`（看 `com.github.mczjuops.mczjugamecore.score.leaderboard.SortOrder` 枚举；累计获得应降序）。编译时确认 SortOrder 的常量名。

- [ ] **Step 2: `MaggoteersPlugin.onEnable` 注册排行榜**

在 `registerLeaderboard`（与 `registerPlayerData` 一起）加：
```java
        MCZJUGameCore.getLeaderboardManager().registerLeaderboard("maggoteers_total", io.mczju.maggoteers.persist.MaggoteersTotalLeaderboard.class);
```

- [ ] **Step 3: 构建 + 测试 + 提交**

`mvn -q test`（全绿；编译验证 SortOrder/API）。
```bash
git add src/main/java/io/mczju/maggoteers/persist/MaggoteersTotalLeaderboard.java src/main/java/io/mczju/maggoteers/MaggoteersPlugin.java
git commit -m "feat(plan7): MaggoteersTotalLeaderboard(totalEarned 降序) + registerLeaderboard"
```

---

## Plan 7 验收标准（Definition of Done）

- [ ] `mvn package` 产 jar，`mvn test` 绿（新增 `SettlementTest`）。
- [ ] 通关一局 → 每人 `balance`+`totalEarned` += `win_flat`；失败 → 按 acts/waves 折算；重启服仍存在（MGC 落盘）。
- [ ] `/maggoteers shop` 开解锁商店；花 `balance` 买 → `unlocks` 写入；重启仍在。
- [ ] 已解锁的 `requires_unlock` 奖励项能在 3 选 1 roll 到；未解锁的不出现。
- [ ] `/mgcop leaderboard create maggoteers_total <entityId>` 放置的排行榜按 `totalEarned` 降序显示（运维操作）。
- [ ] `docs/dev-log.md` 追加 Plan 7 完成条目。

> **已知遗留 / Plan 8**：完整 `/maggoteers` 命令套件（debug state/plan/wave/act/give/coin）、配置 schema 校验、AlertMenu 二次确认（商店目前直接扣），通关时间榜（另注册一个 Leaderboard）。
