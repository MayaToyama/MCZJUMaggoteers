# 开发日志 / 决策记录（dev-log）

> 本文件按时间倒序追加。每条记录：**日期 → 做了什么 → 决策与原因 → 遗留问题**。
> 目的：让后续接手的 AI agent 读懂"为什么是这样设计的"，而不仅仅是"现在长什么样"（现状看 `CLAUDE.md`）。

---

---

## 2026-07-27 — 奖励护符（build display collectibles）

- **做了什么**：`collectibles.yml` + `CollectibleRegistry`/`CollectibleService`；STAT 选中发放绑定护符（PDC `collectible`）；`RewardDrawVisibility` 统一抽池（SUPPLY 可重复、WEAPON/STAT 一次、UPGRADE 未满可抽）；`EffectStacker.sweepExpiry` 返回移除 id → 同步删护符；`EffectService.resync` + 复活路径双向 `CollectibleService.resync`；`RunItemGuardListener` 禁止丢弃/放入容器；开局装备标 `run_gear`；GUI（Pick/Class/Shop）用 ItemCreator 预览；移除 `rewards.yml` `icon:`；样本 `items/collectibles.yml`。
- **原因**：Spec `2026-07-27-reward-collectibles-design.md`——构筑可见性靠背包护符，玩法真相仍在 `PlayerState`。
- **决策**：未映射 STAT 仅 warning 不阻断；满级 UPGRADE `apply` 返回 false；护符与效果 level 双向 resync。
- **遗留**：多数 STAT 尚未配护符条目；`applyStat` amp 与 `EffectStacker` 双 increment 技术债未修。

---

## 2026-07-27 — 配置驱动魔法武器 + 虚拟 MAGIC_DAMAGE

- **做了什么**：`use_ability` 配置武器（`ItemAbilityRegistry` + `ConfigMagicHandler`）；统一魔法核心 `EffectService.executeMagicEffect`（`DAMAGE_AREA`/`DAMAGE_BEAM`/`HEAL_AREA` + `PlayerCombatStats` 乘数 + `TargetResolver` + `MagicDamageContext` 防递归）；虚拟 stat `MAGIC_DAMAGE` 只进 `PlayerState` 不写 Bukkit；删除 `TestBladeHandler`/`ExcaliburHandler`/`HealingStaffHandler`；奖励池移除 `a2s_damage_area_interact`，新增 `a1s_magic_dmg`/`a2s_magic_dmg`；`RewardLoadValidator` 校验 fireTrigger 白名单。
- **原因**：Spec v1.2 批准——武器与奖励共用伤害核心、零 Java 加武器、法术强度独立叠层；`ON_INTERACT` 奖励与武器路径冲突。
- **决策**：`use_ability.cooldown_sec` 为权威 CD；FX 三层合并；`registerHandler` 保留为 escape hatch；tier-3 ON_INTERACT 兜底已移除。
- **遗留**：P5 Lore/计分板显示法术强度；`flame_cleaver` 等进阶 WEAPON 条目待内容作者补全；测试服需重启 + 同步 `items/maggoteers.yml`。

---

- **做了什么**：`waves.yml` 新增 `infernal: { level, affixes }`（主体/乘客/亡语独立配置）；`InfernalMobsBridge` 反射 `mechanizeWithAffixes` + 受管 UUID 追踪；`MobDeathListener` **LOWEST** 提前 `unregisterMob` 抑制 IM 死亡奖励；删除 `AffixTrigger`/`AffixCombatService`/`CombatAffixListener` 及 12 个 `on:` 词缀与默认波次引用。
- **原因**：内容维护者需在波次 YAML 精确指定 IM 技能；原生 `on:` 监听器与 IM 能力重叠且难维护；不改 IM 源码只能通过反射 + 死亡前注销接入。
- **决策**：IM 为 `softdepend` 非 Maven 依赖；只调用精确列表入口、不随机补全；禁用 `morph`/`mama`/`mounted`/`vexsummoner`/`ghost`；IM 缺失/失败降级为普通怪仍计入波次；显式 `equipment` 在 mechanize 之后应用；**不**自动把旧战斗词缀迁移为 IM 配置。
- **遗留**：默认 `waves.yml` 尚未批量加 `infernal:`（需内容作者按需手工添加）；测试服 smoke 需装 IM JAR 后验收。

---

## 2026-07-26 — 词缀战斗触发重构（AffixTrigger）— 已 superseded

- **做了什么**：引入 `AffixTrigger` 枚举（`SPAWN` / `hit-player` / `hit-by-player` / `hit`）与 `AffixCombatService`；`hit` 改为怪受任意伤害时给怪自身 buff；新增 `hit-by-player` 给攻击玩家反伤；迁移 `poisonous`/`sticky` 配置。
- **原因**：旧 `on: hit` 实现为「玩家打怪 → 玩家 buff」，与 `hidesuwa` 等设计意图相反；字符串 `on` 散落三处无校验。
- **遗留**：同日被 InfernalMobs 接入方案取代并删除上述代码路径。

---

## 2026-07-25 — Smoke 第二轮（乘客 expand + 三选一 apply 链）

### 做了什么
- **`WaveSpec.expand()`** 复制 `passengers`（修复强怪骑乘波裸坐骑）；`WaveSpecTest` 增补用例。
- **三选 1**：RestMenu 先 `draw` 再扣费；PickMenu 接收预抽 `offers`；`RewardService.apply(player, opt, game)` 返回 boolean，绑定 `PlayerState`，失败红字/成功绿字；`EffectService.apply` 显式 game 重载。
- **`GameRegistries.attribute`**：legacy `generic.*` + 1.21 短名双路径。

### 文档
- Spec/plan：`docs/superpowers/specs/2026-07-25-smoke-round2-design.md`、`plans/2026-07-25-smoke-round2.md`。

### 遗留
- 本地 shell 无 `mvn`；请在 IntelliJ 跑 `mvn test` 后部署测试服 smoke。

## 2026-07-25 — Smoke 修复 + 骑乘步 v1（spec OK 已实现）

### 做了什么
- **AURA**：`AttributeModifierKeys` 修复 Paper 26.2 modifier key。
- **骑乘步**：递归 `passengers`、+1tick 挂载、`MountPassengerLimits`/`MountControllerResolver`/`MountedSquadRegistry`+AI（5tick）；`waves.yml` 尸壳骆驼/僵尸马/炽足兽（含嵌套炽足兽塔）；act1–3 strong 池混 roll。
- **夜视** `player.night_vision`；**隐身**空瓶头盔；**旋风斩** CRIT 弧段螺旋 + sweep 音效。

### 文档
- Spec/plan：`docs/superpowers/specs/2026-07-25-smoke-fixes-design.md`、`plans/2026-07-25-smoke-fixes.md`。

## 2026-07-24 — 旋风斩 FX + 五件魔法物（法杖/圣剑/三环 AURA）

### 做了什么
- **旋风斩** `maggoteers:test_blade`：`SPIRAL_RADIUS` + `ANGRY_VILLAGER` + `ENTITY_RAVAGER_ROAR`（items + `magic_fx.weapons`）。
- **ItemCreator**：`healing_staff`（钻石矛模型木棍）、`excalibur`；`HealingStaffHandler`（25s/5格/+3心）、`ExcaliburHandler`（10s/准星长度光束+路径伤害）；`MaggoteersPlugin` 注册 handler。
- **三选一 AURA**：`a1s_ring_strength`（7格 +4 攻，`THICK_RING`+ENCHANT）、`a1s_ring_regen`（7格再生，`SMOOTH_EXPAND_RING`）、`a1s_slow_field`（10格 -30% 移速 + `mark_head`/`DOT_ABOVE`）；武器奖励 `a1s_healing_staff` / `a1s_excalibur` 入 `act1_strong` 池。

### 决策与原因
- 主动武器走 tier-2 handler + `use_fx`；被动环走 AURA + `carrier_fx`/`mark_fx` 与 `AuraService` 已有刷新链。

### 遗留
- 本地未跑 `mvn test`（环境无 mvn）；上服后 `/maggoteers debug give` 实机验粒子与音效。

## 2026-07-24 — AURA 团队/对敌光环（v1 已实现）

### 做了什么
- `Effect.AURA`、`AuraService.refresh`（1s）、友/敌 grant、HEAL 脉冲、`RewardOption` grant/expiry 解析。
- 示例：`a1s_war_banner` / `a1s_medic_field` / `a1s_miasma`；`AuraParamsTest`。
- 计划：`docs/superpowers/plans/2026-07-24-team-aura-effect.md`。

### 决策与原因
- 派生 buff 在接收者实体；携带者死亡 `stripAllFromSource`；局末 `clearGame`。

### 遗留
- 局内实测；`debug aura`；怪物出圈精确 removePotionEffect。

## 2026-07-24 — 乘客怪 / 调试 / 夜天 / 进层准备 / 魔法 FX

### 做了什么
- **waves.yml `passengers[]`**：`StepCfg`/`SpawnStep`/`MobFactory.spawnStepGroup`；乘客走与主体相同的 coeff×affix×scaling。
- **调试**：`debug jumpto|jump|spawnmob|effects`；`WaveScheduler.debugSeekWave/debugShiftWave`。
- **`world.time_lock: night`**（默认），心跳内重设时间防日照。
- **`act_enter.prep_sec: 10`**：进层 Phase `PREP` 后再开波。
- **`magic_fx` + `use_fx`**：六种粒子预设 + 加粗圆环；`TestBladeHandler` 走 `MagicFxService`。
- 设计文档：`docs/superpowers/specs/2026-07-24-gameplay-polish-design.md`。

### 决策与原因
- 乘客单独追踪 UUID，与 WaveEngine 死亡/扫尾一致。
- 调试跳波清怪并直接 `beginWave`，跳过休整与进层 PREP。
- 魔法 FX 预设用短任务动画（SPIRAL/RIPPLE/SMOOTH），参数可配置 radius/ray/density。

### 遗留
- 本地需 `mvn test` 验证；Paper `Registry.SOUNDS` 若编译报错可退回纯 `Sound.valueOf`。
- 示例骆驼+乘客 strategy 尚未写入默认 `waves.yml`（仅 schema 支持）。

## 2026-07-24 — 波次 delay 语义 + 剧本 RNG / 可观测性

### 做了什么
- **`delay` 改为步前等待（相对上一步）**：`WaveSpec.expand()` 按 steps 顺序与 `repeat` 累加时间轴，不再使用「距本波开始的绝对 delay + period 补间」。`RunPlanner` 不再按 `delaySec` 排序 steps，保留 `waves.yml` 书写顺序。
- **强怪 roll 独立性**：`RunPlanner` 用 `rollSalt(tier,index)` 派生 RNG，避免旧 `derive(200+i)` 跨层撞车导致多波共用同一随机序列。
- **`WaveSpec.strategyId`**：剧本写入所 roll 的 strategy id；开战广播与 **`/maggoteers debug plan`** 输出每层每波 `[序号:tier=id]`，便于核对「全图 strong 都是 s1_crp_many」类问题。
- **`SeededRng.weightedIndex`**：跳过 weight≤0 的池条目；`WaveDefinitions` 深拷贝 pool 列表防误改。
- **配置**：`waves.yml` act3 去掉重复 `weak:` 键；资源注释标明 delay 语义。
- **单测**：`WaveSpecTest` 对齐新展开时间轴；新增 `StrongWaveRollTest`（强怪池多样性，需本地 `mvn test`）。

### 决策与原因
- 策划口径「步前延时」与 repeat 链式累加一致；`s1_crp_many` 等若需轮与轮之间的空档，应把**该轮首 step 的 delay** 配大，而不是引擎用 period 硬补 1 tick。
- strategyId + debug plan 把「池子配置 vs 剧本 vs 刷怪」拆开，避免 repeat 时序 bug 与 roll bug 混在一起排查。

### 遗留
- 步前语义下 `s1_crp_many`（repeat:3、首步 delay:0）第二轮会与第一轮末步同 tick 叠刷；若需间隔，在 waves.yml 把 repeat 轮首步 delay 改为 6（或单独 strategy）。
- 改 `waves.yml` 后须**重启服**（`WavesConfig` 仅 onEnable 加载）；部署 jar 建议 `mvn clean package`。
- in-game 验证：新 jar + `/maggoteers debug plan` 强怪 strategy 列表。

---

## 2026-07-24 — ItemCreator 1.1.0（Paper 26.2 main）+ GameRules 新名

### 做了什么
- 从 `mczju-ops/MCZJUItemCreator` **main**（无独立 `26.2` 分支；`api-version: 26.2`，版本 **1.1.0**）本地 `mvn package`/`install`，jar 已复制到 `E:\MCpaper\plugins\`。
- Maggoteers `pom` → `MCZJUItemCreator:1.1.0`；`plugin.yml` `api-version: 26.2`。
- `WorldService`：弃用 `GameRule.DO_*` → `GameRules.ADVANCE_TIME` / `ADVANCE_WEATHER` / `NATURAL_HEALTH_REGENERATION` / `SPAWN_MOBS`；并设白天+晴天。

### 决策与原因
- 远程仅有 `main`；Paper 26.2 适配已合入 main，按 1.1.0 使用。
- 编译 warning 实为 GameRule 弃用（非 ItemCreatorApi 签名变化；API 与 1.0.1 兼容）。

### 遗留
- in-game 冒烟：ItemCreator 加载 + Maggoteers 物品发放。

---

## 2026-07-24 — 稀疏刷怪点回退 boss（G3 放宽）

### 做了什么
- `MapPoints.resolvePointOrBossFallback`：非 boss 点数少于 9 且引用缺失点 → 用 `boss` 坐标；已满 9 非 boss 或缺 boss 仍 fail-fast。
- `RunPlanner` 改用该解析；单测覆盖回退 / 满 9 仍抛错。

### 决策与原因
- 地图可只配 1–5 + boss，而 `waves.yml` 仍可共用引用 1–9 的 strategy，避免每图重写波次。

### 遗留
- ItemCreator 26.2 私有仓本机无 SSH/HTTPS 权限，尚未本地构建与修依赖 warning。

---

## 2026-07-24 — Plan 10：单结构 NBT 地图加载

### 做了什么
- `MapEntry`：`hasStructure` 取代四象限 `nbtFiles`；探测 `structure.nbt`。
- `StructurePaster`：层原点单次粘贴；缺文件仍 64×64 玻璃兜底。
- `CLAUDE.md` §5 与相关「4 象限」表述同步。

### 决策与原因
- 硬切换、固定文件名、原点角对齐——与结构方块工作流一致，少配置分支。
- 不保留四象限兼容；示例相对坐标在有真实 NBT 后需按结构原点重写。

### 遗留
- 运维导出并放置各图 `structure.nbt`；按结构重写 `points.yml`。
- in-game：有/无 nbt 两种路径冒烟。

---

## 2026-07-24 — Plan 9：Paper 26.2 + MGC 1.0.7 升级（ItemCreator 暂不装）

### 做了什么
- **构建基线**：`pom.xml` → Paper `26.2.build.62-beta`、MGC `1.0.7`、JDK **25**；`plugin.yml` `api-version: 1.21`。
- **`GameRegistries`**：集中 Registry 解析药水/属性/实体（替代 `getByName`/`valueOf`）。
- **部署**：`E:\MCpaper\plugins\` 放置 `MCZJUGameCore-1.0.7.jar` + 新构建 `Maggoteers-*.jar`；**无 ItemCreator**（决策 C）。
- **构建脚本**：`scripts/build-with-jdk25.sh`（WSL 需配合 Windows JDK25；Windows 推荐 IDEA `mvn.cmd` + `%APPDATA%\.minecraft\runtime\java-runtime-epsilon`）。

### 决策与原因
- **ItemCreator 仅 compile-time**：测试服不装；`ItemService` warning + `RewardService` 物品 warning 可接受；QA 侧重 STAT/波次/菜单/MGC。
- **JDK 25 强制**：Paper 26.2 / MGC 1.0.7 为 class file 69；JDK 21 无法编译。本地用 Minecraft 自带 JDK 25 跑 Maven。

### 遗留
- **in-game 冒烟**（Plan 9 Task 6 Step 4）：join → 波次 → 菜单 → 结算。
- **ItemCreator** 恢复后另测 WEAPON/SUPPLY/货币。
- **Windows 新环境**：需 `mvn install:install-file` 安装 `MCZJUItemCreator-1.0.1.jar`（或从 WSL `~/.m2` 拷贝）才能编译。

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
