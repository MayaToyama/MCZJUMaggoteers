# QA 发现清单 — 2026-07-03（Plan 1–6 首轮真服 QA）

> 部署 `Maggoteers-0.1.0-SNAPSHOT.jar`（HEAD `435c2fd`）到 `E:\MCpaper` 后的首轮 QA 发现。
> 3 个 bug + 4 个新需求/增强 + 1 个待确认。每条含**根因分析 + 修法 + 文件位置**，供下一会话直接动手。
> 优先级：🔴bug必修 / 🟠增强 / 🟡内容 / ❓待确认。

---

## 🔴 Bug 1：休整期用普通货币能买到 Boss 池战利品（1 金粒换 1 下界之星）

**现象**：Boss 波后的休整期，"普通奖励"按钮花普通币（金粒）却能开出 Boss 池的东西（下界星/Boss 币）。

**根因**：`menu/RestMenu.setup()` 计算
```java
String poolNormal = RewardService.poolForWave(snap.actIndex(), snap.lastTier());
```
Boss 波清掉后 `snap.lastTier()=="boss"` → `poolNormal = "actN_boss"`。于是"普通奖励"按钮（slot 11，扣 `CURRENCY_NORMAL`）打开的是 **Boss 池**，但收普通币。违反 §9.2（普通池：弱→weak，强/Boss→**strong**）。

**修法**：普通池按 §9.2 映射 tier，Boss 波后走 `strong` 而非 `boss`。在 `RestMenu.setup()`：
```java
String normalTier = "weak".equals(snap.lastTier()) ? "weak" : "strong";
String poolNormal = RewardService.poolForWave(snap.actIndex(), normalTier);
```
（Boss 池 `poolBoss` 保持 `poolForWave(act,"boss")`；§9.2 的"粘滞=最近击杀 Boss 所在层"是更细的语义，可后续再细化——当前用当前层 boss 池即可。）

**文件**：`src/main/java/io/mczju/maggoteers/menu/RestMenu.java`

---

## 🔴 Bug 2：通关最终 Boss 后仍进休整期，应立即判定通关

**现象**：打完 Act3 Boss，不立即 `win()`，而是先进 30s 休整期，到时才通关。

**根因**：`wave/WaveScheduler` 的相位机 `onWaveCleared` 无条件进 `REST`；`REST` 倒计时到才 `advance()` → 检测到最后层最后波 → `win()`。最后一波（Act3 Boss）也走了 REST。

**修法**：在 `onWaveCleared(game, c)` 里，若当前是**最终波**（`c.actIndex == acts.size()-1` 且 `c.waveIndex == 该层最后一波`），跳过 REST 直接 `((MaggoteersGame)game).win()` 并把 phase 置 `DONE`。否则维持现有 REST→advance 流程。

**文件**：`src/main/java/io/mczju/maggoteers/wave/WaveScheduler.java`（`onWaveCleared`）

---

## 🔴 Bug 3：失败结算返回大厅后仍是观察者模式

**现象**：最后一人死亡转 SPECTATOR → 触发失败结算 → 玩家回大厅后 GameMode 仍是观察者。

**根因**：`MaggoteersDeathStrategy` 把耗尽复活的玩家 `setGameMode(SPECTATOR)`；游戏结束 `cleanupRun()` 调 MGC `endGame` 把玩家送回大厅，但**没有重置 GameMode**（MGC 的 profile 切换不一定恢复 GameMode）。

**修法**：在 `game/MaggoteersGame.cleanupRun()`（或 `onGameEnd`）里，对每个玩家显式恢复：
```java
for (var pe : getPlayers()) {
    pe.player().setGameMode(org.bukkit.GameMode.SURVIVAL);  // 或 ADVENTURE
    pe.player().setFallDistance(0f);
    // 视情况：new PlayerExt(pe.player()).resetState();  清血/饱食/药水
}
```
（胜利方同理——通关玩家也可能在冒险模式，回大厅应恢复 SURVIVAL。）

**文件**：`src/main/java/io/mczju/maggoteers/game/MaggoteersGame.java`（`cleanupRun`）

---

## 🟠 需求 4：休整期加"商店"按钮；货币只作货币

**现象/诉求**：休整期把钱花光后无事可做（只能等 30s 自动推进或点跳过）。希望加一个**商店按钮**打开商店，货币作为通用购买力，而非唯一强化入口。

**注意**：本质是 §13 局外商店的**局内化**——和 Plan 7（持久化账户货币/商店/解锁）强相关。两个方向二选一：
- **轻量（Plan 6 收尾）**：RestMenu 加一个"商店"按钮，打开一个用普通/Boss 币直接买指定物品的菜单（非 3 选 1、固定商品）。
- **正式（Plan 7）**：接账户货币 + 解锁系统的局外商店（`ShopMenu`，§13.3）。

**待澄清**：用户说"把钱花光后就不能结束休整期了"——但 RestMenu 有"跳过休整"按钮（slot 15 → `WaveScheduler.skipRest`），REST 也会 30s 自动到时。需确认：是 skipRest 不生效，还是只是"没事干"的体验问题？QA 时验证下"跳过"按钮是否真的立即进下一波。

**文件**：`menu/RestMenu.java`（加按钮）+ 可能新增 `menu/ShopMenu.java`

---

## 🟡 需求 5：扩充测试内容（波次/刷怪点/词缀/奖励）

**现状**：默认 `waves.yml` 每池 1 条 strategy、只用了刷怪点 `1/9/boss`、词缀几乎没挂、STAT 奖励种类少——测不出 §7 词缀/§10 效果/§12 奖励的全貌。

**要做（纯配置，零代码，铁律③）**：
- **`maps/actN/<map>/points.yml`**：填满 9 个刷怪点（`"1"…"9"` + `boss`），各点分布在玻璃平台/地图各处，测多点同时刷。
- **`waves.yml`**：每层 weak/strong/boss 池各加多条 strategy，引用不同刷怪点组合（如"先点 1/2 刷僵尸，隔 5s 点 5/6 刷骷髅，再点 9 刷苦力怕"），用足 `delay`/`repeat`/`count`。
- **`affixes.yml` + `waves.yml` steps[].affixes**：给 strong/boss 波的怪挂词缀（armored/berserk/toxic/greedy），测 `coeff×affix×scaling` 合成 + 词缀药水（toxic 凋零）。
- **`rewards.yml`**：每池加足 STAT 选项覆盖 5 种 Effect——`ADD_ATTRIBUTE`(攻击/移动/血) / `ADD_POTION`(抗性/力量/速度) / `HEAL`(嗜血 ON_KILL) / `DAMAGE_AREA`(ON_INTERACT 或 ON_KILL 范围) / `GRANT_REVIVE`；加 `unique`、`UPGRADE_LEVEL`、`stack: ADD` 各一例；挂 1–2 个带 `maggoteers:id` 的"魔法物品"奖励，配 `registerHandler` 测武器地基。
- **`special_waves.yml`**：act1 的地图专属波（高权重）测并入。

**文件**：`src/main/resources/{waves,affixes,rewards,maps/*/points.yml,maps/act1/ruined_keep/special_waves.yml}`

---

## 🟠 需求 6：复活应在地图复活点，不在原地

**现象**：耗尽复活次数的那次自动复活（`tryAutoRevive` 成功），在死亡原位复活，容易被怪堆立刻再打死。

**根因**：`MaggoteersDeathStrategy.onPlayerDeath` 复活分支只 `healAndInvuln(p)`（原地），没传送。

**修法**：复活时传送到当前层的 `playerSpawn`。需要拿到 spawn 绝对坐标——`WaveScheduler.snapshot(game)` 或 `ActPlan.playerSpawn`。可由 `WaveScheduler` 暴露一个 `currentPlayerSpawn(game): Location`，DeathStrategy 复活分支调它 + `p.teleport(spawn)` + `healAndInvuln`。2s 无敌保留（`setNoDamageTicks(40)` 已有），给玩家跑开的时间。

**文件**：`game/MaggoteersDeathStrategy.java`（复活分支）+ `wave/WaveScheduler.java`（暴露 spawn）

---

## ❓ 待确认 7：金苹果"瞬间回血"魔法物品，右键仍要慢慢吃

**现象**：一个写着"瞬间恢复生命值"的金苹果（`maggoteers:supply_healing`，material=GOLDEN_APPLE），右键（对空气/墙）都要原版吃苹果动画（很久），没有瞬回。

**根因（推测）**：`RewardService.apply` 对 `maggoteers:supply_healing` 有特例——**抽到时**瞬间 +12 血（`apply` 里）。但物品本体是原版 `GOLDEN_APPLE`，玩家**自己右键使用**走原版吃动画，没接 handler（`ItemKind` 无 `SUPPLY_HEALING`，路由 tier-3 不 cancel 原版吃）。所以"瞬回"只在抽到那一刻生效，使用时不生效。

**修法（二选一）**：
- **加使用 handler**：`ItemKind` 加 `SUPPLY_HEALING` + 在 `ItemInteractRouter.registerHandler`（或 HANDLERS）注册一个 handler：右键 → `player.setHealth(min(max, health+12))` + `event.setCancelled(true)`（取消原版吃）+ 扣 1 个。这样右键瞬回。同时 RewardService.apply 的 SUPPLY 分支就只负责"发放物品"，不再瞬间回血（避免抽到就回）。
- **换材质**：把 `supply_healing` 换成无原版吃动画的 material（如 `GLISTERING_MELON_SLICE`/`FERMENTED_SPIDER_EYE` 配合 handler），从根上避免吃动画。

**待 QA 确认**：用户是不是期望"右键使用 = 瞬回"（而非"抽到就回"）。若是，按上面加 handler。

**文件**：`item/ItemKind.java` + `item/ItemInteractRouter.java`（+ handler）+ `reward/RewardService.java`（去掉 apply 里的瞬回特例，改由 handler 负责）+ `items/maggoteers.yml`

---

## 修法优先级建议（下一会话）

1. 🔴 Bug 2（最终 Boss 立即通关）— 体验最直接，几行代码。
2. 🔴 Bug 3（回大厅 GameMode）— 几行，影响每个失败局。
3. 🔴 Bug 1（货币买错池）— 一行 tier 映射。
4. 🟠 需求 6（复活点复活）— 中等，影响手感。
5. ❓ #7（金苹果使用）— 先跟用户确认期望，再改。
6. 🟡 需求 5（扩充内容）— 纯配置，可放开头做，便于后续 QA。
7. 🟠 需求 4（商店按钮）— 先确认是"轻量按钮"还是走 Plan 7 商店。

> **部署提醒**：修完重新 `mvn package` 后，`cp target/Maggoteers-0.1.0-SNAPSHOT.jar /mnt/e/MCpaper/plugins/`；改了默认 yml 的话，`Maggoteers/` 数据目录里的同名旧 yml 不会被覆盖（`saveResource(false)`），手动删旧文件让其重新释放。
