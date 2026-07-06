# 开发日志 / 决策记录（dev-log）

> 本文件按时间倒序追加。每条记录：**日期 → 做了什么 → 决策与原因 → 遗留问题**。
> 目的：让后续接手的 AI agent 读懂"为什么是这样设计的"，而不仅仅是"现在长什么样"（现状看 `CLAUDE.md`）。

---

## 2026-07-06 — Plan 8 实现：调试命令 + 配置校验 + 词缀药水 + 收尾（4 任务 SDD）

### 做了什么
- 按 Plan 8 用 SDD 落地 4 任务：
  1. **调试命令**：`/maggoteers` 套件（state/plan/wave/give/coin、强制 act/wave 跳转等），供 in-game 调试与 QA。
  2. **配置 schema 校验**：启动/reload 时对 waves/rewards/affixes 等做引用与结构校验，缺失 strategy 池引用 fail-fast，affix/item 引用 warn/severe。
  3. **词缀药水**：`MobFactory` 应用 `SpawnStep.potions()` 给怪；`CombatAffixListener` 处理 `on: hit-player` 类词缀（如 toxic/vampiric）。
  4. **收尾**：`RewardService.allPoolIds()` 返回 `Collections.unmodifiableSet`；本 dev-log 条目。

### 决策与原因
- **AffixService 先于 WavesConfig 加载**：WavesConfig 校验 strategy 内 affix 引用时需 AffixService 已就绪。
- **strategy 池引用 fail-fast，affix/item 引用 warn/severe only**：池/strategy 缺失会导致运行时无法开局或 roll 波次，必须硬失败；affix/item 引用问题可降级为日志告警，避免单条配置 typo 拖垮整服 reload。

### 里程碑
- **Plan 1–8 全部完成**（从世界/波次/效果/菜单/持久化/商店到调试与校验）。

### 遗留
- **需要 in-game QA**：debug 命令、 toxic/vampiric 等 hit-player 词缀、配置 fail-fast 行为。
- **内容运维**：maps/NBT 资产填充与地图专属波次。
- **可选后续**：通关时间榜（另注册 Leaderboard）、商店 AlertMenu 二次确认。

---

## 2026-07-04 — Plan 7 实现：持久化 + 局外商店 + 解锁回流 + 排行榜（5 任务 SDD）

### 做了什么
- 按 `docs/plan/2026-07-04-plan7-persistence-shop-unlock-leaderboard.md` 用 SDD 落地 5 任务（每任务 implementer+reviewer，全部 Approved）：
  1. **`MaggoteersPlayerData extends JsonPlayerData`**（balance/totalEarned/unlocks + grant/spend/unlock）+ `Settlement` 纯函数（WIN=winFlat, FAIL=failPerAct×acts+failPerWave×waves）+ 4 例单测 + `registerPlayerData` + config settlement 段。
  2. **结算**：`cleanupRun` 据 `outcome`(WIN/FAIL) → `Settlement.grant` → 每人 `getData(MaggoteersPlayerData.class).grant(grant)`（setModified 落盘）。`WaveScheduler.progress(game): int[]` 暴露 actIndex+waveIndex。
  3. **局外商店**：`UnlockShopMenu`（商品=所有 `requires_unlock:true` 选项，花 `balance` 解锁→`unlocks.add`）+ `/maggoteers shop` CommandExecutor + plugin.yml 注册。
  4. **解锁回流**：`UnlockRegistry.unlocksOf(p)`（读持久 unlocks 防御性拷贝）+ `RewardService.draw` 加 `requiresUnlock && !unlocked.contains(id) → continue` 过滤（已解锁才 roll 到）。
  5. **排行榜**：`MaggoteersTotalLeaderboard extends PlayerDataLeaderboard`（`getFieldName`→"totalEarned", 默认降序）+ `registerLeaderboard("maggoteers_total", ...)`。

### 决策与原因
- **`getData` 局外可用**（核实 MGC 源码）：`PlayerExt.getData(Class)` 委托 `PlayerDataManager.getPlayerData(uuid, class)`，按 gameId+UUID 存取，**与 ProfileManager 完全独立**——局外商店（玩家不在局里）也能正确读 balance/unlocks。`cleanupRun` 的 settlement 在 `switchProfile(null)` 后跑也安全（同源）。
- **SortOrder 默认降序**（核实 javap 字节码）：`AbstractLeaderboard.getSortOrder()` 返回 `DESCENDING`——累计卫戍币天然降序，无需 override。
- **结算放 cleanupRun 前段**（ON_GAME_END fire 之后、WaveScheduler.stop 之前）：此时 `getPlayers()` 仍在、cursor 还在、getData 可用。后段 switchProfile 不影响 getData。

### 遗留（Plan 8）
- `/maggoteers` 命令套件（debug state/plan/wave/give/coin、强制 act/wave 跳转）、配置 schema 校验、通关时间榜（另注册 Leaderboard）、AlertMenu 二次确认（商店目前直接扣）、RewardService.allPoolIds 返回不可变视图（Minor）。
- **需要 in-game QA**：通关/失败 → balance 增 + 重启仍在；`/maggoteers shop` → 买解锁 → 下局 3-pick 能 roll 到已解锁项；`/mgcop leaderboard create maggoteers_total` 放置排行榜实体。

---

## 2026-07-04 — 第二轮 QA 修复落地（4 任务 SDD + 最终复审，QA#2）

### 做了什么
- 按 `docs/qa-findings-2026-07-04.md` 用 SDD 落地 4 任务（每任务 implementer+reviewer，最终 opus whole-branch 复审 + M1/M2 修复）：
  1. **常驻药水永久化 + 排除饥饿**：新增 `GameplayTickListener`（全局 1s 心跳，onEnable 启 / onDisable 停，非 per-game 防重入）；每秒对每个冒险玩家 `EffectService.refreshPermanentPotions`（重施加常驻 ADD_POTION，刷新 30s 不过期）+ 维持 food=20/saturation/exhaustion=0（`NATURAL_REGENERATION=false` 已阻断饱食回血 → 恒满不回血）。
  2. **清理恢复大厅 profile**：`cleanupRun` 对每玩家 `clear()` → `switchProfile(null)`（先清后恢复，避免清掉已恢复的大厅背包）+ 复位血/食/火/坠落 → 防下局等待带上一局物品。
  3. **粒子范围提示**：新增 `util/ParticleEffects.playAreaRing`（水平粒子环），`EffectService` DAMAGE_AREA 分支调用（范围打击有视觉反馈）。
  4. **示例魔法武器**：`TestBladeHandler`（右键 `maggoteers:test_blade` → 范围斩+粒子环+3s CD），onEnable `registerHandler` 注册——演示 weapon 地基可用（tier-2 路径：kind 不在枚举→itemIdOf 命中→handler）。
- 最终复审（opus）：0 Critical / 0 Important，4 Minor。补修 M1（TestBlade 不误伤队友，排除所有 Player）+ M2（tier-2 handler 加 isAlive 门禁，防观察者触发）。

### 决策与原因
- **药水永久化用 tick 刷新而非 infinite duration**：§10.3 设计本意（ON_TICK_1S 定期刷新）；infinite 时长在 Paper 跨死亡不存活、且改不了等级。tick 每秒重施加常驻药水（幂等），等级变更（UPGRADE_LEVEL）随真源 params 自动反映。
- **switchProfile(null) belt-and-suspenders**：复审核实 MGC `DefaultGameManager.endGame→solveGameEnd→removeAllPlayer` 在 `onGameEnd` 之后**已自动** `switchProfile(null)`；我们在 `cleanupRun` 的显式调用是冗余但无害的保险（`clear()`-before 顺序无论 MGC 是否恢复都对）。
- **GameplayTick 全局单 task**（非 per-game 引用计数）：避免 Plan 5 那次 tickTask 被 per-game cleanup 误杀的 bug；`getAllGames()` 空时心跳空转。

### 遗留（未修，Minor）
- **M3**：`cleanupRun` 先 `setHealth(20)` 后 `removeAll`（剥 MAX_HEALTH modifier）；若结局时正好有 MAX_HEALTH 负 buff（max<20），Paper clamp 后玩家可能非满血离场。罕见，非回归。建议改用 `PlayerExt.resetState()` 或把 removeAll 提前。
- **M4**：双重 `switchProfile(null)`（我方 + MGC 各一次）冗余异步落盘 + 日志噪音；保留作保险，可去掉靠 MGC。
- **词缀药水对怪无效**（上轮遗留）：`MobFactory` 仍不应用 `SpawnStep.potions()`；`toxic`/`vampiric` 纯药水词缀只显示名。下个 plan 在 MobFactory 加。

### 部署
- jar 已构建 + 复制到 `/mnt/e/MCpaper/plugins/`。
- ⚠️ QA 前删旧 `Maggoteers/` 数据目录的 yml（`saveResource(false)` 不覆盖）。无新增 yml 字段（QA2 全是代码 + 复用已有配置），但上轮（QA#1）的配置变更（9 点/STAT/STRENGTH/shop 删除等）仍需清旧才生效。

---

## 2026-07-04 — 首轮 QA 修复落地（5 任务 SDD + 最终复审）

### 做了什么
- 按 `docs/qa-findings-2026-07-03.md` 用 SDD 落地 5 个任务（每任务 implementer+reviewer，最终 opus whole-branch 复审 + 一次修复）：
  1. **3 bug**：RestMenu 普通池 tier 映射（boss→strong，§9.2）；最终 Boss 清波直接 `win()` 不进休整；`cleanupRun` 重置每人 GameMode→SURVIVAL（防回大厅仍是观察者）。
  2. **复活点复活**：`WaveScheduler.currentSpawnLocation(game)` + DeathStrategy 复活分支传送（保留 2s 无敌）。
  3. **supply_healing 右键瞬回**：`ItemKind.SUPPLY_HEALING` + router handler（瞬回12、取消原版吃、消耗1、满血不消耗）；`RewardService.apply` 不再抽取时回血。
  4. **商店 = 绿宝石物品入口**（用户澄清：不是按钮）：`ItemKind.SHOP_EMERALD`（开局发、不消耗）右键开 `ShopMenu`（读 `config.yml shop:` 段，按 normal/boss 币买固定商品，price>1 循环扣+失败回退）+ `ShopConfig`。
  5. **扩充测试内容**（纯配置）：3 张 points.yml 填满 9 刷怪点；affixes.yml 4→8；waves.yml 6→11 strategy（用足 9 点、词缀挂 strong/boss）；rewards.yml 覆盖全部 5 种 Effect + ADD/UPGRADE_LEVEL/IGNORE + unique + 魔法物品奖励。

### 最终复审（opus）抓到 + 修复
- **I1**：`a2s_str` 用了 `INCREASE_DAMAGE`（1.20.5 改名 `STRENGTH`，1.21.7 `getByName` 返回 null → 奖励静默失效）→ 改 `STRENGTH`；`RewardOption.parseParams` 加 null 警告日志。
- 顺手修 Minor：满血不消耗药水、ShopConfig slot 越界校验、ShopMenu 冗余 `player.player()`、points.yml 点9 移开（原与 boss/spawn 重合）。

### 遗留 / 已知 gap
- **【已知 gap，未修】词缀药水对怪无效**：`MobFactory.spawn` 只套 `hpMult/dmgMult/speedMult`，**从不应用 `SpawnStep.potions()`**（Plan 2/3 遗留）。导致**纯药水词缀**（`toxic` 凋零、`vampiric` 再生）只显示名字、无实际效果；**数值词缀**（armored/berserk/swift/tank/greedy/fortuned）正常。Need#5 想测的"词缀药水"因此测不到 → 下一 plan 在 `MobFactory.spawn` 里给怪加 `step.potions()`（注意 `on: hit-player` 的药水是给被击中的玩家上的，需在监听器里做，不是给怪自己喝）。
- **Bug2 行为差异**：最终 Boss 短路通关会跳过该波 `clearReward` 发放 + `ON_WAVE_CLEAR` 触发——当前配置无 `ON_WAVE_CLEAR` 触发/到期效果，且 `win()` 立即结束，影响可忽略；若以后有"通关前最后一波清奖励"需求，把短路移到发放/触发之后。
- **Minor 未修**：tier-1 路由 `setCancelled` 在 `isInGame` 判定之前（游戏外右键已知 kind 物品会静默 cancel 原版交互，罕现）；final-wave 跳过 clearReward（见上）。

### 部署
- jar 已构建 + 复制到 `/mnt/e/MCpaper/plugins/`（`Maggoteers-0.1.0-SNAPSHOT.jar`）。
- ⚠️ **QA 前需删旧 `Maggoteers/` 数据目录里的旧 yml**（`rewards.yml/points.yml/config.yml/items/maggoteers.yml/affixes.yml/waves.yml`），`saveResource(false)` 不覆盖——否则新内容（STAT 奖励、9 点、shop、STRENGTH 修复）不生效。

---

## 2026-07-03 — Plan 6 实现：奖励接效果引擎 + 物品/菜单收尾 + 可扩展武器地基（SDD）

### 做了什么
- 按 `docs/plan/2026-07-03-plan6-rewards-items.md` 用 SDD 逐 Task 实现（6 Task + 2 轮 critical/important 修复，全部 review 通过）。
- **修了用户 flag 的两个 bug**：复活币不可用（`ItemKind` 加 `REVIVE_COIN` + `ReviveMenu` 玩家头像 GUI：点死者复活/点活者+1、扣币、失败退还）+ 右键空气打不开菜单（`ItemInteractRouter` `ignoreCancelled=false`）。
- **奖励接效果引擎**：`RewardOption` reshape 到 §12 schema（trigger/effect/params/stack/icon/requires_unlock/unlock_cost/unique）；`rewards.yml` 加 STAT 选项（+伤害/嗜血/抗性升级/复活+1/复活+2）；`RewardService.apply` STAT→`EffectService.apply`（引擎有了真实输入）；`unique` 可见性过滤 + `UPGRADE_LEVEL` 升级。
- **可扩展武器地基**（用户重点诉求"后续 agent 能在现有数据链路上加魔法武器"）：`ItemInteractRouter` 三层分发——已知 kind → `weapon_id` 注册表（`registerHandler(weaponId, handler)` public API，**未来武器在此挂 handler，零路由核心改动**）→ `ON_INTERACT` 兜底（仅当该玩家持 ON_INTERACT 效果才触发、只触发 clicker、CD 门禁）。`ItemService` 全路径打 `maggoteers:id` PDC（`itemIdOf`），激活二/三层。`CooldownService`（D2，按 PDC-id 时间戳表，**不用 `setCooldown(Material)`**；可注入时钟 + 4 例单测）作为 handler 的 CD 原语。
- **D1 净化**：`applyPotionPermanent` amp≥255 技巧 + `PurifyListener`（POISON/WITHER damage-cancel fallback）。
- 47 单测全绿。

### 决策与原因（含 review 抓到的关键缺陷，已全部修复）
- **效果引擎 vs 奖励的输入耦合**：Task 3 review 抓到两个 Critical——`applyStat` 直接改共享 `RewardOption.params()`（多人/多次抽会串味）→ 改 `EffectContext.copy()` 克隆；`EffectStacker.merge` UPGRADE_LEVEL 升 level 不升 amp（resync 丢等级）→ 同步 `same.params().amp++`。
- **GRANT_REVIVE 不走引擎**：最终 review 发现 `GRANT_REVIVE` 作为常驻效果是 no-op（`applyDerived` 无此 case），且若加进去 resync 会重复 granting。改为 `RewardService.apply` 直发 `addReviveCount`（一次性，不过引擎/不进 PlayerState）。
- **tier-3 ON_INTERACT 两处坑**：(a) 自从全物品打 `maggoteers:id` 后，tier-3 对所有奖励物品 cancel 右键（开箱被拦）→ 改为"仅当 clicker 持 ON_INTERACT 效果才 cancel+fire"；(b) `fireTrigger` 是全局（波及全员）→ 新增 `fireTriggerPlayer(game, player, trigger)` 只触发 clicker。
- **UPGRADE_LEVEL level 来源**：原用 `acquiredUnique` Set 计数（Set 天然封顶 1，第 3+ 次抽 amp 错）→ 改读真源 `ps.effects()` 里同 id 效果的 level。
- **`Attribute` 在 Paper 1.21.7 不是 Enum**（Registry 类）→ `RewardOption.parseParams` 用专用 `safeAttribute()` 而非 `safeEnum`。
- **`maggoteers:id` PDC 必须打**：否则 `itemIdOf` 恒 null、二/三层死代码；`createItem`/`buildConfigured` 全路径补打。

### 遗留（后续 Plan / 实测）
- **D1 净化技巧需 in-game 实测**：0s-255 级抵消在 Paper 1.21.7 是否成立未知；不成立则 `PurifyListener`（`immunity_<cause>` 永久效果 id 约定）兜底。成立则可删 listener。配置时要给净化效果起 id `immunity_wither`/`immunity_poison` 才能触发 fallback（两路径独立）。
- **in-game 手测**：右键空气/复活币/STAT 奖励生效/unique 过滤/UPGRADE 升级——需部署后人工验证。
- **Plan 7**：`requires_unlock` 持久 unlocks（账户货币/商店/解锁/排行榜/结算）、通关时间榜（`RewardService.draw` 已留过滤钩子）。
- **Plan 8**：`/maggoteers` 命令、配置 schema 校验、按 id 配 CD 的 `item_cooldowns.yml`、`Map<String,Integer>` 替代 `acquiredUnique` 双用途计数（若 UPGRADE_LEVEL 需求超过 4 级）。
- **加魔法武器 = 写 handler + `registerHandler`**（tier-2，零核心改动）；tier-3（无 handler 的 ON_INTERACT）可用但建议优先 tier-2。

---

## 2026-07-03 — Plan 5 实现：效果系统引擎（Trigger/Effect/到期/堆叠/resync，SDD）

### 做了什么
- 按 `docs/plan/2026-07-03-plan5-effects.md` 用 SDD 逐 Task 实现（6 Task，每 Task implementer+reviewer 复核通过）。
- 新增 `effect/` 包：`Trigger`(10)/`Effect`(5)/`Stack`(5) 枚举、`EffectKey<T>`+`EffectContext`(类型化参数)、`EffectKeys`(标准键)、`PlayerEffect`(生命周期模型)、`EffectStacker`(merge/sweepExpiry 纯逻辑)、`EffectService`(apply/resync/removeAll/fireTrigger/executeEffect)、`EffectListener`(战斗+ON_TICK_1S 心跳)。
- `PlayerState` 扩 `List<PlayerEffect>`；接入 fire-points：`WaveScheduler`(ON_WAVE_CLEAR/ON_ACT_ENTER)、`DeathStrategy`(ON_REVIVE+resync/ON_DEATH)、`MaggoteersGame.cleanupRun`(ON_GAME_END+removeAll+stopTick)、`onAllClassesChosen`(startTick)、`Plugin`(注册监听 + onDisable stopTick)。
- 43 单测全绿（新增 `EffectContextTest`/`EffectStackerTest`，纯逻辑覆盖堆叠/到期/merge）。

### 决策与原因
- **引擎与奖励解耦**：Cursor 的 `RewardOption` 是简化版（无 trigger/effect/params/stack），`RewardService.apply` 把 STAT 硬编码成 +4 HP。Plan 5 只造引擎 + 接监听器，**不**动 `RewardOption`/`RewardService`——`RewardOption` reshape + `RewardService` 改走 `EffectService.apply` 留 Plan 6（效果引擎的真实输入源）。故本 Plan 引擎先靠单测 + 编译验证，Plan 6 接通后才有真实效果流入。
- **D5 唯一 NamespacedKey**：常驻 ADD_ATTRIBUTE 每层 `AttributeModifier` key = `id_level_自增序号`（1.21 同 key 重复抛异常）。
- **MGC API 修正**：brief 原写 `getGameManager().getRunningGames()`，javap 核实 MGC 1.0.5 `DefaultGameManager` 无此法，改用 `getAllGames()` + `instanceof MaggoteersGame` 过滤（ON_TICK_1S 心跳）。
- **Attribute 命名**：Paper 1.21.7 用无前缀 `Attribute.MAX_HEALTH`（非 `GENERIC_*`），与 MobFactory 一致。
- **纯逻辑/Bukkit 分层**：`EffectStacker`(merge/sweepExpiry) 与 Bukkit 解耦、全单测；`EffectService` 的 apply/resync/fireTrigger 操作 live Player → 编译 + 手测。

### 遗留（Plan 6 处理）
- `RewardOption` reshape + `RewardService.apply` 改走 `EffectService`（效果引擎才有输入）。
- D1 净化（`ADD_POTION` 0s-255 抵消 + damage-cancel fallback）、D2 物品 CD（`cooldown_sec` 按 PDC-id 时间戳表）、`ON_INTERACT` 触发。
- 右键空气打不开菜单（`ItemInteractRouter.PlayerInteractEvent` 缺陷）+ 复活币 GUI。
- `EffectService.currentGame(p)` 反查可改显式传 game；`removeAll` 已补清常驻药水（Task 3 review fix）。
- in-game 手测（监听器不崩、fireTrigger 无 NPE）需部署后人工验证。

---

## 2026-07-03 — Plan 1–4 合规审计 + 刷怪架构确认 + 物品交互路由提交

### 做了什么
- 对 GitHub 上 `e1cc5ab`（= origin）的 Plan 1–4 实现做合规审计：三个并行只读审计子代理（Plan 2/3/4）+ 控制器复核 Plan 1，对照 `docs/plan/*`。临时 worktree 跑 `mvn test` → 绿。
- 确认"每张图各一份 `points.yml`、刷新点 1–9 映射地图相对坐标"的刷怪架构**已被支持**（见下）。
- 提交未推送的 WIP：物品 PDC 交互路由 + 休整/职业菜单接线 + `ActSpawnHelper` + 物品定义。

### 审计结论
- **Plan 1/2/4 完全合规**；**Plan 3 合规**，仅 `scaling.mob_hp` 由需求方手动调强（`[1.0,1.3,1.6,2.0]→[1.0,2.3,3.6,5.0]`，有意平衡，非 bug）。
- 一处"偏离"实为**修正了 Plan 2 文档自身的坐标 bug**：文档样例 `points.yml y:65` + 玻璃在 `baseY-1` 会让玩家出生在 y=129 悬空 66 格；实现改为 `y:1` + 玻璃在 `baseY`（出生 y=65 踩玻璃 y=64）。→ 待反向同步回 Plan 2 文档。
- `e1cc5ab` 还夹带了 Plan 5/6 内容（`item/ItemService`、`menu/*`、`reward/*`、`ui/RunScoreboard`、`game/ClassSelectGate`），未破坏 Plan 4 死亡/复活/失败链。

### 刷怪架构确认（每图一份 points.yml）
- `MapRepository.load` 扫描 `maps/actN/*/` 下**所有**子目录，凡含 `points.yml` 即为一张图（自动发现，加图零代码）。
- `RunPlanner.planAct` 每层种子化抽一张图，刷怪点 id 经 `map.points().point(id)` 取**该图相对坐标**，再 `Coords.resolve(act_origins[act], 相对)` 得绝对坐标；`waves.yml` 的 `steps[].point` 只写 id（"1"…"9"/"boss"）。
- **G3 校验**：某波引用的 id 不在抽中图的 `points.yml` → 开局即抛错指到 strategy（fail-fast）。
- **契约**：每张图的 `points.yml` 必须定义 `waves.yml` 会引用的全部 id（当前默认只用了 1/9/boss；要用 2–8 就在 points.yml 加点 + 在 waves.yml 加引用即可）。
- 当前 3 个默认 `points.yml`（`ruined_keep/frozen_halls/obsidian_spire`，坐标相同、y=1）= 无 NBT 时的玻璃平台兜底。加真图 = 新建 `maps/actN/<新图id>/{points.yml,4×nbt[,special_waves.yml]}`。

### 遗留
- Plan 2 文档的 `points.yml y:65` + 玻璃 `baseY-1` 样例需改成 `y:1` + `baseY`（免得后人重踩）。
- Plan 5/6（菜单/奖励/计分板/物品路由）尚无独立 plan 文档即已落地；后续补文档或并入 Plan 5/6 正式计划。

---

## 2026-07-03 — Plan 4 实现（PlayerState + 死亡/复活/失败，Cursor 接手）

### 做了什么
- 按 `docs/plan/2026-07-03-plan4-playerstate-death-revive.md` 完成 Task 1–3（Task 4 需 in-game 手测）。
- 新增：`PlayerState`/`PlayerStateManager`（含 `revivePlayer`/`addReviveCount` API 供 Plan 6）、`GameOutcome`、`MaggoteersDeathStrategy`。
- `MaggoteersGame`：`getPlayerDeathStrategy()` 注入；开局 `switchProfile` + 冒险模式 + `PlayerStateManager.init(lives.default)`；`win()`/`fail()` 设 outcome 后 `endGame`；结束钩子 `destroyAll`。
- `WaveScheduler` 通关分支改调 `((MaggoteersGame) game).win()`。
- MGC API 已 javap 核实：`AbstractGame.getPlayerDeathStrategy()`、`AbstractPlayerDeathStrategy.onPlayerDeath(PlayerExt, PlayerDeathEvent)`、`PlayerExt.switchProfile(String)`。
- `mvn test` 31 项全绿（新增 `PlayerStateTest` 4 项）。

### 决策与原因
- **DeathStrategy 取消 PlayerDeathEvent**：不走原版死亡屏/重生；`tryAutoRevive` 含 1→0 仍复活；耗尽转 SPECTATOR + `isAnyAlive` 判失败。
- **outcome 防重入**：`win()`/`fail()` 仅在 `IN_PROGRESS` 时生效，避免重复 endGame。
- **switchProfile 保留**：按 plan + VampireSurvivor 实证在 onGameStart 手动切 profile；若 in-game 见背包双清再移除（dev-log 待实测记录）。

### 遗留
- Task 4 in-game：自动复活链、单人/多人失败、胜利 outcome=WIN；复活币 GUI 端到端留 Plan 6。
- Plan 5 复活路径补 `EffectService.resync`；Plan 7 `onGameEnd` 按 outcome 结算货币。

---

## 2026-07-03 — Plan 3 实现（RunPlanner + 缩放 + 词缀，Cursor 接手）

### 做了什么
- 按 `docs/plan/2026-07-03-plan3-runplanner-scaling-affixes.md` 完成全部 6 个 Task。
- 新增：`SeededRng`、`Affix`/`AffixService`/`affixes.yml`、`Compose`、`ScalingConfig`（count-roll D6）、`WaveDefinitions`/`MapLibrary`、`RunPlanner`、`RunConfig`；`SpawnStep.dropMult`；`MapEntry.specialWaves` + `special_waves.yml` 样例。
- `MaggoteersGame` 改为 onGameStart 异步 `RunPlanner.plan(seed, playerCount)` + 二段就绪门闩；`WorldService.getSeed` 记录世界 seed。
- 删除 `SimplePlanner`（由 RunPlanner 取代）。`mvn test` 27 项全绿。

### 决策与原因
- **playerCount 开局锁定 → RunPlanner 在 onGameStart 异步**：onGameInit 时人数未定，故在 `startInWorld` 取 `getPlayers().size()` 后异步规划；seed = `WorldService.create` 的 world seed（可重放）。
- **WaveEngine/WaveScheduler 零改动**：RunPlanner 产出同一 `List<ActPlan>` 契约，规划与执行解耦。
- **ScalingConfigTest 概率测试**：plan 原文用 `new SeededRng(0..1999)` 的首个 `nextDouble()` 在 JDK 21 下均 ≥0.5（同 seed 低位序列特性），改为单 `SeededRng` 连续抽样。

### 遗留
- `dropMult` 已计算，Plan 6 `CurrencyService` 消费；词缀药水 Plan 5 落地。
- 固定 seed 调试命令 → Plan 8。
- 需 in-game 验证 Boss hp = coeff×affix×scaling（iron_golem armored @1人 ≈ 320）。

---

### 做了什么
- Claude Code 额度用尽后由 Cursor 按 `docs/plan/2026-07-03-plan2-maps-waves.md` 完成 Plan 2 全部 8 个 Task。
- 新增：`waves.yml` + 三张默认 `points.yml`；`WavesConfig`/`MapRepository`/`StructurePaster`（玻璃平台兜底）；`MobFactory`；`WaveEngine`/`WaveRuntime`/`WaveScheduler`/`MobDeathListener`；`SimplePlanner`/`Origins`；`MaggoteersGame` 开局闭环（建世界 → 规划 → 波次）。
- `mvn test` + `mvn package` 通过（`CoordsTest`/`WaveSpecTest`/`OriginsTest`）。

### 决策与原因
- 严格按 plan 照搬 VampireSurvivor 的 WaveManager/WorldManager 模式；Plan 2 不做缩放/词缀/货币发放/clearReward 实发/死亡判定（留 Plan 3–6）。
- `WavesConfig.parseStrategy` 对 Bukkit `MapList` 的 wildcard 做了 `num()` 辅助，避免 `getOrDefault` 泛型编译错误（plan 原文在 JDK 21 下不通过）。

### 遗留
- 需人工部署 `target/Maggoteers-0.1.0-SNAPSHOT.jar` 到 `E:\MCpaper\plugins\` 做 in-game 验证（见 plan Task 8 Step 3）。
- 4 象限 NBT 仍缺，v1 靠 `map.fallback_platform` 玻璃平台兜底。
- 下一步：**Plan 3**（RunPlanner 替换 SimplePlanner）。

---

## 2026-07-03 — 编写 Plan 2 / 3 / 4 实现计划（writing-plans）

### 做了什么
- 按 Plan 1 的约定（`docs/plan/` 分阶段、TDD 步骤、checkbox、照搬前代已验证实现）续写三份计划：
  - `2026-07-03-plan2-maps-waves.md`：结构粘贴 + WaveEngine/Scheduler + 原版怪 + 三层闭环 VICTORY。
  - `2026-07-03-plan3-runplanner-scaling-affixes.md`：SeededRng + Affix + Compose + ScalingConfig(count-roll D6) + RunPlanner（纯函数单测）。
  - `2026-07-03-plan4-playerstate-death-revive.md`：PlayerState + MaggoteersDeathStrategy + outcome + 失败判定。
- 复核并照搬前代 `MCZJUvampireSurvivor` 已验证实现：`WaveManager`（IdentityHashMap + UUID 反查 + 5-tick 安全扫描）、`WorldManager`（结构粘贴）、`ScalingConfig`、`SurvivorPlayerDeathStrategy`、`PlayerSession`、`SurvivorGame` 生命周期。

### 决策与原因
- **Plan 2↔Plan 3 稳定契约**：定义 `ActPlan{mapId,playerSpawn,waves}` / `WaveSpec{steps,repeat,clearReward}` / `SpawnStep{...}`（已解析绝对坐标 + 最终倍率）为跨 Plan 不变量。Plan 2 用同步 `SimplePlanner`（coeff-only、count 固定）产出 `List<ActPlan>`；Plan 3 的异步种子化 `RunPlanner` **替换** `SimplePlanner`，**WaveEngine/WaveScheduler 零改动**——把"规划"与"执行"解耦，最小化返工。
- **RunPlanner 放 onGameStart 异步，不放 onGameInit**：§2.3 字面说 onGameInit 启动 RunPlanner，但 playerCount 是"开局快照锁定"（§6/§1.1），onGameInit 时玩家可能还在集结，count 未定。故放在 `onGameStart` 的就绪门闩内异步跑（取 `getPlayers().size()` 锁定快照），与 G4"建世界唯一在 onGameStart 门闩主线程"一致。world seed 由 `WorldService.create` 生成并 `getSeed` 暴露给 RunPlanner（世界名 hex = seed hex，可重放）。
- **纯函数可测**：`WaveDefinitions`/`MapLibrary`/`RunConfig` 纯数据 record + `RunPlanner.plan(seed,playerCount,defs,maps,scaling,affixes,cfg)` 全注入、不碰 Bukkit；`AffixService.forTesting`/`ScalingConfig.forTesting` 包级工厂绕过单例——Plan 3 全套单测（确定性/G3/count-roll/专属波次并入）无需开服。
- **SpawnStep 字段演进**：Plan 2 定义 9 字段；Plan 3 加 `dropMult`（coeff×affix×scaling 的掉落倍率，Plan 6 CurrencyService 消费）+ 填 `affixes/potions`。`dropMult` 落地字段在 Plan 3，避免 Plan 6 再改模型。
- **死亡策略接入点已源码核实**：`AbstractGame.getPlayerDeathStrategy()` override 返回 `new XxxDeathStrategy(this)`（VampireSurvivor `SurvivorGame:75` 实证）；策略 `onPlayerDeath(PlayerExt, PlayerDeathEvent)` 取消事件 + reviveCount 逻辑。`switchProfile(getId())` 在 onGameStart 切干净游戏 profile（`SurvivorGame:147` 实证）——Plan 1–3 缺失，Plan 4 补上。
- **outcome 标志（D4）**：`GameOutcome{IN_PROGRESS,WIN,FAIL}` 落在 `MaggoteersGame` 实例；`WaveScheduler` 胜利调 `win()`、`DeathStrategy` 失败调 `fail()`，都 `setOutcome + endGame`。Plan 7 的 `onGameEnd` 据此分支结算。

### 遗留 / 待实现期核实
- Plan 2 地图 4 象限 NBT 需运维用结构方块导出；v1 用玻璃平台兜底（`map.fallback_platform`）。
- Plan 4 `switchProfile` 是否与 MGC 自动切换重复——实测后定（若开局背包被清两次则移除手动调用）。
- ItemCreator jitpack 版本号、净化技巧（D1）仍待实现期核实/实测。
- 复活时 `EffectService.resync` 补回药水——Plan 5 在复活路径加 hook。
- 全部三份 Plan 依赖本机装 JDK21+Maven 才能跑 `mvn`/部署（Claude 侧只写文件）。

---

## 2026-07-02 — 设计补充：开局职业选择

### 做了什么
- 需求方补充：玩家进图无装备，需**开局职业选择 + 全员初始装备**。已写入 CLAUDE.md §9.4 / §2.3 / §4.1 / §7.6、spec §8。

### 决策
- **复用奖励池 + 解锁**：职业池 = `reward_pools.class`（`cost: 0` 免费），option 即标准 `RewardOption`（STAT/WEAPON/SUPPLY，支持 `requires_unlock`/`unique`）。开局每人 3 选 1，可见性过滤原样复用——零新概念。
- **初始装备**：`config.yml` `initial_equipment`（ItemCreator id 列表），开局发全员，走 `ItemService` + `giveItem(ItemStack)`（遵守 G2）。
- **开局门闩**：`onGameStart` 门闩就绪后 → 发初始装备 → 开 `ClassSelectMenu` → 全员选完（倒计时兜底）→ 启动 `WaveScheduler`。借前代 VampireSurvivor `ClassSelectMenu` / `onAllClassesChosen` 已验证套路。
- 加职业 = 配置加 option，**零代码**。

---

## 2026-07-02 — 第三轮接入契约复核（§G 整改）

### 做了什么
- 处理核对清单第三轮（对照 1.0.5 clone + 整改后全文）。结论：**无 Blocker，可进 writing-plans**。整改 §G1–G4。

### 决策与原因（源码实证 G1/G2）
- **G1 房间实例**：`registerGame` → `loadGameRoom`（`DefaultGameManager:43`），房间 JSON 在 `plugins/MCZJUGameCore/rooms/maggoteers/*.json`；**无 READY 房间则 `/mgc join` 失败**。→ onEnable 先释放默认 `default.json` 再 registerGame（或运维 `/mgcop room create maggoteers default`）。room 是调度壳、可复用；每局仍自建虚空世界。
- **G2 giveItem 路由**：`PlayerExt.giveItem(String)` 走 MGC ItemManager（**非** ItemCreator）；ItemCreator 物品必须 `ItemService.createItem(id)` + `giveItem(ItemStack)`（`PlayerExt:111`）。
- **G3 刷怪点 schema**：points.yml 示例补 `boss`；RunPlanner 校验 `steps[].point` 编号在该图 points.yml 存在。
- **G4 建世界归属**：唯一在 `onGameStart` 就绪门闩内（主线程 `createWorld` + 粘贴 Act1）；`onGameInit` 只异步 RunPlanner + NBT 预读、**不建世界**——避免双触发。
- 新增 CLAUDE.md §16.1 首测部署清单（MGC 1.0.4→1.0.5、`rooms/maggoteers/default`、资产、ItemCreator）。

### 遗留 / 进 plan
- §G 部署项（MGC 升级、room 创建）作为 plan 阶段 0。
- ItemCreator 版本号、净化技巧（D1）实现期核实/实测。

---

## 2026-07-02 — 第二轮接入契约复核（§E 整改）

### 做了什么
- 处理核对清单第二轮（对照 GitHub 1.0.5 源码 + 测试服 1.0.4 jar），整改 §E1–E8 文档遗漏。

### 决策与原因（源码实证）
- **E1 持久化路径**：`JsonPlayerData.getFilePath()` = `<MGC数据目录>/player_data/<gameId>/<uuid>.json` → 实际 `plugins/MCZJUGameCore/player_data/maggoteers/<uuid>.json`（**不在本插件目录**）。`dev-advanced.md` 的 `player/...` 已过时。
- **E2 落盘周期**：`MCZJUGameCore.java:73 startAutoSave(20*60*30L)` = **30 分钟**（非 5 分钟）+ 退出 `savePlayerDataAsync` + 关服 `saveAllPlayerData`。
- **E3 版本**：测试服 jar=**1.0.4 已含** PlayerData API（非"缺 API 编译不过"）；设计基线 1.0.5，建议升级对齐。本地 clone=1.0.0 过时（无 PlayerData、Menu API 也不同）。
- **E4 Menu（1.0.5）**：子类 `public XxxMenu(Player, Object...){ super(player,args); }` + 重写 `getTitle/getRows/getPermission/setup`。
- **E5 排行榜**：`getLeaderboardManager().registerLeaderboard(id, Class)` + `PlayerDataLeaderboard` 四抽象方法（`getTitle/getSubtitle/getPlayerDataClass/getFieldName`）。
- **E6/E7/E8**：§2.4 交叉引用（→§13.1/§13.5）、spec 与 CLAUDE.md 同步、RunPlan 去除 `World` 引用（异步线程不持有 World，主线程绑定再构造 Location）——均已改。

### 遗留
- ItemCreator jitpack 版本号、净化技巧（D1）实现期核实/实测。

---

## 2026-07-02 — 接入契约复核与整改（review）

### 做了什么
- 处理 `docs/review/2026-07-02-接入契约核对清单.md`（一份逐条对照真实源码的自检清单）。
- 重新核对 GitHub 最新版 MGC 源码；需求方裁定**以 GitHub 最新版为准**（测试服旧版过时）。

### 决策与原因
- 清单 **§A / §B1 / §B2 / §B5 证伪**——它们基于过时的本地源码（无 PlayerData 系统）；GitHub 最新版有完整 `JsonPlayerData`/`getData`/`resetState`/`PlayerDataLeaderboard`，前代 `build.gradle` 也实证了 jitpack groupId `com.github.mczju-ops`。CLAUDE.md 这几处原正确，不整改。
- 清单 **§B3 / §B4 成立**（版本无关的设计疏漏）：`createWorld()` 必须主线程；`onGameStart` 返回 void 不能阻塞 → 引入"就绪门闩"（`runTaskTimer` 轮询）。
- 清单 **§D1–D8 全部成立**，已作为规则/风险写入 CLAUDE.md：CD 走 PDC 时间戳表（不用 `setCooldown(Material)`）、3 选 1 不足灰显不补凑、胜负 `outcome` 标志、属性多层唯一 `NamespacedKey`、补怪走种子 RNG、触发被动默认 `IGNORE`、净化"0s 255 级"技巧待实测。
- **新增要害**：测试服必须升级到 GitHub 最新版 MGC，否则持久化整章失效（写入 CLAUDE.md §1.2 版本基线 + §18）。

### 遗留
- ItemCreator jitpack 版本号待核实。
- 净化技巧实现期实测。

---

## 2026-07-02 — 立项与设计定稿（brainstorm 阶段）

### 做了什么
- 与需求方逐段确认了 The Maggoteers（卫戍协议）的完整设计，产出 `CLAUDE.md`（项目圣经）与 `docs/spec/2026-07-02-maggoteers-design.md`（设计叙事）。
- 阅读并借鉴了三个相关项目：
  - `MCZJUGameCore`（依赖框架）——确认接入契约（`AbstractGame`/`JsonGameRoom`/`JsonPlayerData`/`PlayerExt`/排行榜/Menu）。
  - `MCZJUItemCreator`（物品 API）——`ServicesManager` 取 API；`createItem`/`parseYamlToItems`。
  - `MCZJUvampireSurvivor`（前代同款原型）——直接借鉴**已验证**的世界/虚空/结构粘贴/波次引擎/死亡策略/缩放。
  - `MCZJUMagicItems`（效果范式）——借鉴"触发→分发→效果"PDC 范式。

### 关键决策与原因
- **不引入 WorldEdit/MythicMobs/InfernalMobs**：核心诉求是"纯配置、便于管理"，第三方怪物插件 API 不可控。精英怪=自实现配置词缀层。
- **账户货币自建**：发现 MGC 的 `ScoreManager`/`HistoryScoreManager` 是**空壳**，无可用积分系统。
- **世界=每局新建虚空世界**：多房间天然并行；结构粘贴用 Paper `Structure` API（不依赖 WE），方法已被 VampireSurvivor 验证。
- **波次时序=单一可暂停状态机**：休整期要暂停、跳过/失败要统一取消，链式 task 做不到。
- **效果生命周期=事件到期，非挂钟计时**（需求方关键修正）：限时道具限的是"波次/层级/下次攻击/下次复活"等事件，且效果必须**跨死亡复活存活**——原版药水 infinite 时长做不到。故 PlayerState 为真相源、复活后 resync，删除前代 `BuffInstance` 实时计时。
- **魔法武器 v1 不做框架**：需求方澄清——ItemCreator 出带 PDC 物品、本插件写识别+触发类即可，无需通用接口。留作"PDC 路由"文档化扩展路径。
- **排行榜积分 = `totalEarned`**：免单独计分系统。
- **Boss 池粘滞**：= 最近击杀 Boss 所在层，杀掉本层 Boss 才更新（需求方精化）。
- **缩放开局锁定 + 不允许中途加入**：简化预生成与调试。

### 遗留 / 待实现期核实
- ItemCreator 的 jitpack 版本号。
- `parseYamlToItems` 与 ItemCreator 原 `items/` 加载路径的特性等价性（用一个复杂武器验证）。
- 尚未写任何代码；下一步进入 **writing-plans**，产出 `docs/plan/` 分阶段实现计划。

---

<!-- 新条目按此格式继续追加在最上方（日期倒序） -->
