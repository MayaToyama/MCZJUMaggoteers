# The Maggoteers — Plan 8: 命令 + 配置校验 + 词缀药水 + 打磨 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 补齐运维/QA 工具：`/maggoteers debug` 命令套件（查状态/给物品/跳波）、配置加载时跨引用校验（fail-fast 精确报错）、词缀药水落地（mob-self + on:hit-player）、收尾打磨。**这是最后一个 Plan。**

**Architecture:** 扩展现有 `MaggoteersCommand`（已有 `/maggoteers shop`）增加 `debug` 子命令套件（`maggoteers.admin` 权限），复用 `WaveScheduler.snapshot/progress` + `WaveEngine.livingCount` + `PlayerStateManager.get` + `ItemService.give/giveKind`。配置校验在 `WavesConfig.loadFromFile` + `RewardService.load` 末尾加跨引用检查（strategy↔pool、affix↔step、item↔option），不可达引用精确报错。`MobFactory.spawn` 按 `step.affixes()` 从 `AffixService` 解析、按 `affix.on()` 分流：self/null → 施加给怪；`hit-player` → 新 `CombatAffixListener`（怪打玩家时施加）。

**Tech Stack:** Paper 1.21.7，Java 21，Maven，MCZJUGameCore 1.0.5。

## Global Constraints

- **前置条件**：Plan 1–7 已落地（HEAD `949e385`）。`MaggoteersCommand`（Plan 7 T3）只处理 `shop`；`WaveScheduler.snapshot/progress/isRestPhase` 存在；`WaveEngine.livingCount/runtime` 存在；`PlayerState.getReviveCount/isAlive/effects` 存在；`ItemService.give/giveKind/createItem` 存在；`MobFactory.spawn` **不应用 potions**（gap）。
- `PotionSpec(String effect, int amp, int dur)`——`effect` 是 PotionEffectType 名（如 "POISON"）；`Affix.on()` 返回 "hit-player" 或 null。
- Attribute 无前缀；`PotionEffectType.getByName(String)` 1.21.7 仍可用（deprecated 但功能正常）。
- 铁律①②③ 继承。

---

## Task 1: `/maggoteers debug` 命令套件

**Files:**
- Modify: `src/main/java/io/mczju/maggoteers/command/MaggoteersCommand.java`（加 debug 子命令路由）
- Modify: `src/main/resources/plugin.yml`（加 `maggoteers.admin` 权限）

**Interfaces:**
- Consumes: `WaveScheduler.snapshot/progress/isRestPhase`、`WaveEngine.livingCount`、`PlayerStateManager.get`、`ItemService.give/giveKind`、`PlayerExt.isInGame/getGame`。
- Produces: `/maggoteers debug <state|plan|wave|act|give|coin> [...]`。

- [x] **Step 1: 重写 `MaggoteersCommand`（保留 shop + 加 debug）**

整文件替换：
```java
package io.mczju.maggoteers.command;

import com.github.mczjuops.mczjugamecore.menu.MenuFacade;
import com.github.mczjuops.mczjugamecore.player.PlayerExt;
import io.mczju.maggoteers.game.MaggoteersGame;
import io.mczju.maggoteers.item.ItemKind;
import io.mczju.maggoteers.item.ItemService;
import io.mczju.maggoteers.state.PlayerState;
import io.mczju.maggoteers.state.PlayerStateManager;
import io.mczju.maggoteers.wave.WaveEngine;
import io.mczju.maggoteers.wave.WaveScheduler;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * /maggoteers 命令。
 * <ul>
 *   <li>{@code shop} —— 局外解锁商店（所有人）。</li>
 *   <li>{@code debug state|plan|wave|act|give|coin} —— 运维/调试（需 maggoteers.admin）。</li>
 * </ul>
 */
public class MaggoteersCommand implements CommandExecutor {

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player p)) { sender.sendMessage("仅玩家可用。"); return true; }
        if (args.length == 0) { usage(p); return true; }

        switch (args[0].toLowerCase()) {
            case "shop" -> { MenuFacade.open("maggoteers-shop", p); return true; }
            case "debug" -> { return debug(p, args); }
            default -> { usage(p); return true; }
        }
    }

    private boolean debug(Player p, String[] args) {
        if (!p.hasPermission("maggoteers.admin")) {
            p.sendMessage(Component.text("无权限（需 maggoteers.admin）。", NamedTextColor.RED));
            return true;
        }
        if (args.length < 2) {
            p.sendMessage(Component.text("用法：/maggoteers debug <state|plan|wave|act|give|coin> [...]", NamedTextColor.GRAY));
            return true;
        }
        var pe = new PlayerExt(p);
        MaggoteersGame game = pe.isInGame() ? (MaggoteersGame) pe.getGame() : null;
        switch (args[1].toLowerCase()) {
            case "state" -> {
                if (game == null) { msg(p, "不在局内。"); return true; }
                PlayerState st = PlayerStateManager.get(game, p.getUniqueId());
                if (st == null) { msg(p, "无 PlayerState。"); return true; }
                msg(p, "reviveCount=" + st.getReviveCount() + " alive=" + st.isAlive()
                        + " effects=" + st.effects().size() + " acquiredUnique=" + st.acquiredUnique().size());
            }
            case "plan" -> {
                if (game == null) { msg(p, "不在局内。"); return true; }
                int[] prog = WaveScheduler.progress(game);
                msg(p, "actIndex=" + prog[0] + " waveIndex=" + prog[1]);
            }
            case "wave" -> {
                if (game == null) { msg(p, "不在局内。"); return true; }
                var snap = WaveScheduler.snapshot(game);
                int living = WaveEngine.livingCount(game);
                msg(p, "act=" + (snap == null ? "?" : snap.actIndex()) + " tier=" + (snap == null ? "?" : snap.lastTier())
                        + " rest=" + WaveScheduler.isRestPhase(game) + " livingMobs=" + living);
            }
            case "act" -> {
                if (game == null) { msg(p, "不在局内。"); return true; }
                int[] prog = WaveScheduler.progress(game);
                msg(p, "当前层=" + (prog[0] + 1) + "/3  当前层波=" + (prog[1] + 1));
            }
            case "give" -> {
                // /maggoteers debug give <item_id> [amount]
                if (args.length < 3) { msg(p, "用法：/maggoteers debug give <item_id> [amount]"); return true; }
                String id = args[2];
                int amt = args.length > 3 ? safeInt(args[3], 1) : 1;
                ItemService.give(p, id, amt);
                msg(p, "已给 " + id + " ×" + amt);
            }
            case "coin" -> {
                // /maggoteers debug coin [amount]
                int amt = args.length > 2 ? safeInt(args[2], 1) : 5;
                ItemService.giveKind(p, ItemKind.CURRENCY_NORMAL, amt);
                msg(p, "已给普通卫戍币 ×" + amt);
            }
            default -> msg(p, "未知 debug 子命令：" + args[1]);
        }
        return true;
    }

    private static void usage(Player p) {
        p.sendMessage(Component.text("/maggoteers <shop|debug ...>", NamedTextColor.YELLOW));
    }

    private static void msg(Player p, String text) {
        p.sendMessage(Component.text(text, NamedTextColor.AQUA));
    }

    private static int safeInt(String s, int def) {
        try { return Integer.parseInt(s); } catch (Exception e) { return def; }
    }
}
```

- [x] **Step 2: `plugin.yml` 加权限**

在 `plugin.yml` 末尾（`commands:` 之后）加：
```yaml
permissions:
  maggoteers.play:
    default: true
  maggoteers.admin:
    default: op
```

- [x] **Step 3: 构建 + 测试 + 提交**

`mvn -q test`（全绿）。提交：
```bash
git add src/main/java/io/mczju/maggoteers/command/MaggoteersCommand.java src/main/resources/plugin.yml
git commit -m "feat(plan8): /maggoteers debug 命令套件(state/plan/wave/act/give/coin) + admin 权限"
```

---

## Task 2: 配置跨引用校验（加载时 fail-fast）

**Files:**
- Modify: `src/main/java/io/mczju/maggoteers/config/WavesConfig.java`（`loadFromFile` 末尾加校验）
- Modify: `src/main/java/io/mczju/maggoteers/reward/RewardService.java`（`load` 末尾加校验）

**Interfaces:**
- Produces: 加载时校验策略↔池引用、词缀↔steps 引用、物品↔option 引用；不可达引用 log.SEVERE + 抛 `IllegalStateException`（启动 fail-fast）。

- [x] **Step 1: `WavesConfig.loadFromFile` 末尾加校验**

在 `loadFromFile` 方法末尾（`plugin.getLogger().info(...)` 之后）加：
```java
        // 校验：池里引用的 strategy 必须在 strategies 里定义
        for (var actEntry : INSTANCE.pools.entrySet()) {
            String act = actEntry.getKey();
            for (var tierEntry : actEntry.getValue().entrySet()) {
                String tier = tierEntry.getKey();
                for (var pe : tierEntry.getValue()) {
                    if (!INSTANCE.strategies.containsKey(pe.strategy())) {
                        throw new IllegalStateException(
                            "waves.yml: 池 " + act + "/" + tier + " 引用了未定义的 strategy: " + pe.strategy());
                    }
                    // 校验 steps 里引用的 affix 必须在 AffixService 里（affixes.yml 已加载）
                    SpawnStrategyCfg strat = INSTANCE.strategies.get(pe.strategy());
                    if (strat != null) for (StepCfg step : strat.steps()) {
                        for (String affixId : step.affixes()) {
                            if (io.mczju.maggoteers.config.AffixService.getInstance().get(affixId) == null) {
                                plugin.getLogger().severe("waves.yml: strategy " + pe.strategy()
                                    + " 引用了未定义的词缀: " + affixId + "（继续，但该词缀无效）");
                            }
                        }
                    }
                }
            }
        }
```
（注意：`AffixService` 必须在 `WavesConfig` 之前加载——检查 `onEnable` 的加载顺序：`AffixService.load` 应在 `WavesConfig.loadFromFile` 之前。若不是，调整 onEnable 顺序。）

- [x] **Step 2: `RewardService.load` 末尾加校验**

在 `RewardService.load` 末尾加：
```java
        // 校验：option.item 引用的物品必须可创建（ItemService 已初始化）
        for (var pool : POOLS.values()) {
            for (var opt : pool.options()) {
                if (opt.item() != null && !opt.item().isBlank()
                        && !io.mczju.maggoteers.item.ItemService.hasItem(opt.item())
                        && opt.category() != io.mczju.maggoteers.reward.RewardOption.Category.STAT) {
                    plugin.getLogger().warning("rewards.yml: 池 " + pool.id() + " 的选项 " + opt.id()
                        + " 引用了未知物品: " + opt.item() + "（可能 ItemCreator 未装/物品未定义）");
                }
            }
        }
```
（STAT 选项不依赖 `item`（用 effect/params），跳过；WEAPON/SUPPLY 需要 `item` 可创建。）

- [x] **Step 3: 构建 + 测试 + 提交**

`mvn -q test`（全绿；校验是运行时 log + throw，不影响单测）。提交：
```bash
git add src/main/java/io/mczju/maggoteers/config/WavesConfig.java \
        src/main/java/io/mczju/maggoteers/reward/RewardService.java \
        src/main/java/io/mczju/maggoteers/MaggoteersPlugin.java
git commit -m "feat(plan8): 配置加载时跨引用校验(strategy↔pool/affix/item, fail-fast)"
```

---

## Task 3: 词缀药水落地（MobFactory mob-self + CombatAffixListener hit-player）

**Files:**
- Modify: `src/main/java/io/mczju/maggoteers/mob/MobFactory.java`（spawn 末尾加 mob-self 药水）
- Create: `src/main/java/io/mczju/maggoteers/listener/CombatAffixListener.java`
- Modify: `src/main/java/io/mczju/maggoteers/wave/WaveEngine.java`（加 `entityStep(uuid)` 查询）
- Modify: `src/main/java/io/mczju/maggoteers/MaggoteersPlugin.java`（注册 CombatAffixListener）

- [x] **Step 1: `MobFactory.spawn` 加 mob-self 词缀药水**

在 `MobFactory.spawn(Location, SpawnStep)` 的 `applyName(le, step)` 之后、`return le` 之前加：
```java
        applyAffixPotions(le, step);
```
并加私有方法：
```java
    /** 词缀药水（mob-self：affix.on() 非 hit-player 的药水施加给怪自身）。 */
    private static void applyAffixPotions(org.bukkit.entity.LivingEntity le, SpawnStep step) {
        var affixSvc = io.mczju.maggoteers.config.AffixService.getInstance();
        for (String affixId : step.affixes()) {
            var a = affixSvc.get(affixId);
            if (a == null || "hit-player".equals(a.on())) continue;   // hit-player 由 CombatAffixListener 处理
            for (var ps : a.potions()) {
                var type = org.bukkit.potion.PotionEffectType.getByName(ps.effect().toUpperCase());
                if (type != null) le.addPotionEffect(new org.bukkit.potion.PotionEffect(type, ps.dur(), ps.amp()));
            }
        }
    }
```

- [x] **Step 2: `WaveEngine` 加 `entityStep(uuid)`**

在 `WaveEngine` 加（供 CombatAffixListener 查 mob 的 step）：
```java
    /** 查某怪 UUID 的 SpawnStep（供 CombatAffixListener 查 on:hit-player 药水）。无则 null。 */
    public static io.mczju.maggoteers.wave.SpawnStep entityStep(java.util.UUID uuid) {
        WaveRuntime rt = BY_ENTITY.get(uuid);
        return rt == null ? null : rt.mobSteps.get(uuid);
    }
```

- [x] **Step 3: 写 `CombatAffixListener`**

```java
package io.mczju.maggoteers.listener;

import com.github.mczjuops.mczjugamecore.player.PlayerExt;
import io.mczju.maggoteers.config.Affix;
import io.mczju.maggoteers.config.AffixService;
import io.mczju.maggoteers.game.MaggoteersGame;
import io.mczju.maggoteers.wave.PotionSpec;
import io.mczju.maggoteers.wave.SpawnStep;
import io.mczju.maggoteers.wave.WaveEngine;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * 词缀 on:hit-player 药水：被追踪的怪打玩家时，施加该怪 affix(on=hit-player) 的药水给受害者。
 * 典型：toxic（凋零/中毒）、vampiric 等。
 */
public final class CombatAffixListener implements Listener {

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMobDamagePlayer(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player victim)) return;
        SpawnStep step = WaveEngine.entityStep(e.getDamager().getUniqueId());
        if (step == null) return;    // 不是我们的怪
        // 受害者必须在局里
        var pe = new PlayerExt(victim);
        if (!pe.isInGame() || !(pe.getGame() instanceof MaggoteersGame)) return;

        var affixSvc = AffixService.getInstance();
        for (String affixId : step.affixes()) {
            Affix a = affixSvc.get(affixId);
            if (a == null || !"hit-player".equals(a.on())) continue;
            for (PotionSpec ps : a.potions()) {
                PotionEffectType type = PotionEffectType.getByName(ps.effect().toUpperCase());
                if (type != null) victim.addPotionEffect(new PotionEffect(type, ps.dur(), ps.amp()));
            }
        }
    }
}
```

- [x] **Step 4: 注册 `CombatAffixListener` + 构建 + 提交**

`MaggoteersPlugin.onEnable` 加 `registerEvents(new CombatAffixListener(), this);`（与 MobDeathListener 等一起）。
`mvn -q test`（全绿）。提交：
```bash
git add src/main/java/io/mczju/maggoteers/mob/MobFactory.java \
        src/main/java/io/mczju/maggoteers/wave/WaveEngine.java \
        src/main/java/io/mczju/maggoteers/listener/CombatAffixListener.java \
        src/main/java/io/mczju/maggoteers/MaggoteersPlugin.java
git commit -m "feat(plan8): 词缀药水落地(MobFactory mob-self + CombatAffixListener hit-player)"
```

---

## Task 4: 打磨 + 收尾

**Files:**
- Modify: `src/main/java/io/mczju/maggoteers/reward/RewardService.java`（`allPoolIds` → 不可变视图）
- Modify: `docs/dev-log.md`（Plan 8 + 整体收尾记录）

- [x] **Step 1: `allPoolIds` 返回不可变视图**

把 `RewardService.allPoolIds()` 的 `return POOLS.keySet();` 改为：
```java
    public static java.util.Set<String> allPoolIds() { return java.util.Collections.unmodifiableSet(POOLS.keySet()); }
```

- [x] **Step 2: dev-log 追加 Plan 8 + 整体收尾**

在 `docs/dev-log.md` 顶部追加 Plan 8 完成条目（做了什么 + 遗留 + 整体里程碑：Plan 1–8 全部完成）。

- [x] **Step 3: 最终构建 + 打包 + 提交**

```bash
mvn -q test
mvn -q -DskipTests package
git add src/main/java/io/mczju/maggoteers/reward/RewardService.java docs/dev-log.md
git commit -m "feat(plan8): 打磨(allPoolIds 不可变) + dev-log 收尾"
```

---

## Plan 8 验收标准（Definition of Done）

- [x] `mvn package` 产 jar，`mvn test` 绿（51+ 测试）。
- [ ] `/maggoteers debug state` 在局内显示 reviveCount/alive/effects 数。（需 in-game QA）
- [ ] `/maggoteers debug give maggoteers:test_blade 1` 给物品；`/maggoteers debug coin 10` 给 10 普通币。（需 in-game QA）
- [ ] 配置故意写错（如池引用不存在的 strategy）→ 启动 fail-fast 报精确错误。（需 in-game QA）
- [ ] toxic 词缀的怪打玩家 → 玩家中毒/凋零（CombatAffixListener）；vampiric 怪自身回血（MobFactory）。（需 in-game QA；vampiric 当前配置为 hit-player）
- [x] `docs/dev-log.md` 追加 Plan 8 + 整体里程碑。

> **整个项目（Plan 1–8）至此完成。** 后续是内容运营（加地图 NBT / 波次 / 奖励 / 词缀配置）+ in-game QA 调参。
