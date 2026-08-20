# 开发日志 / 决策记录（dev-log）

> 本文件按时间倒序追加。每条记录：**日期 → 做了什么 → 决策与原因 → 遗留问题**。
> 目的：让后续接手的 AI agent 读懂"为什么是这样设计的"，而不仅仅是"现在长什么样"（现状看 `CLAUDE.md`）。

---

## 2026-08-20 — 怪物技能系统 1.1：计数器 + 计时器合一 + 怪物技能（plan）

按 `docs/superpowers/plans/2026-08-20-maggoteers-1.1-counters-mob-skills.md` 完成 1.1 三部分：

1. **计数器（玩家侧）**：`CounterService` + `CounterSpec` + `CounterCondition`（HOLD_ITEM_PDC / PLAYER_IN_RADIUS，AND/OR/NOT 组合）；新 trigger `ON_COUNTER`。
2. **计时器并入计数器**：计时器 = `count: tick` 的计数器（每秒 1 次，`amount: N` = N 秒触发一次，清零循环）。**删除独立 ON_TIMER / tickTimers 状态机**——同一套 `ON_COUNTER`。
3. **怪物技能系统**：移除 InfernalMobs 依赖与旧 affixes 系统；`mob_skills.yml` 可配置 `trigger（attack/damage_taken/killed/spawn/tick/counter）+ effect（health/attribute/potion/summon/teleport）`；怪物计数器出生加载、死亡卸载；条件以怪物为中心。waves.yml 已迁移（affixes/infernal → skills）。

**决策与原因**
- 计数器与计时器同构 → 只保留 `ON_COUNTER`（避免双 trigger 状态机）。
- 怪物技能链路复用玩家 CounterCondition 范式但以怪物为中心判断。
- homing 弹道纯原版每 tick 转向（不依赖 IM 追踪能力）。
- 迁移脚本用 PyYAML dump（丢注释），waves.yml 头部注释顺带去 IM。
- `MobCounterRegistry.signal` 用 visited 集合防两个 `count: COUNTER` 计数器互 ping-pong 死循环（Task 8 硬化）。
- TELEPORT 安全判定以当前层原点（ActPlan.playerSpawn 绝对坐标）为中心，修复 Act2/3 恒 null（Task 8）。
- `1up` 复活 = KILLED 死亡事件 cancel + setHealth 拉满；`MobDeathListener.onDeath` 改 `ignoreCancelled=true`，复活怪保留波次追踪（Task 8）。
- DAMAGE_TAKEN 反伤 target 改为攻击者（quicksand/vengeance 修复，Task 8）。

**遗留 / 待实现期核实**
- mob_skills.yml 旧 affix 数值语义迁移后的平衡性测试（新补 6 技能 confusing/refrigerate/swap/vengeance/wardenwrath/1up 数值未实测）。
- **1up 不朽怪（已解决）**：原担心 KILLED trigger 每次死亡复活卡波清不空——Task 8 最终 review 以**零代码**修复：从 `b2_silent_guard` HOGLIN boss 的 PIGLIN passenger skills 删除 `- 1up`（→ `[molten, ghastly]`）；mob_skills.yml 的 `1up` 定义保留（unused，schema 测试 used⊆defined 仍绿，供未来内容）。**若未来内容重新引用 1up，须先给 fire()/wantsRevive 加 cooldown_sec 或次数上限**。
- swap/1up 依赖 Task 8 的 TELEPORT 层原点修复（Act2/3）与 1up cancel+setHealth 复活链路，未实测。
- TELEPORT 的 `outsideMap` 常量半径（160）在正式地图尺寸确认后调整。
- ItemCreator 版本 / jitpack 坐标（沿用前代）。

**最终 whole-branch review（2026-08-21，fable）**：范围 `b92239f..HEAD` 11 commits + cleanup `9a8f20d`，**PASS** → 进入 finishing；测试基线 **365 pass / 0 fail / 5 skip**。cleanup commit 四项：① `plugin.yml` 删 `softdepend: [InfernalMobs]`（Java 零引用，死声明）；② git rm `wave/PotionSpec.java`（死记录）；③ `MobDeathListenerTest` 测试名去 "InfernalNormalListener" 残留；④ `EffectService.processTriggerEffects` counter map 惰性构建（热路径去空分配）。跨任务硬验证全 PASS（EffectListener 单路径 / skills 接通链 / homing 双端生命周期 / MobCounterRegistry ping-pong 防死锁 / waves block 形态解析）。**唯一遗留 = 服务器实测**（8 项清单见 `.superpowers/sdd/final-review.md` §5，需部署 `E:\MCpaper` 人工验证）：1up 复活血量、swap 层原点传送、vengeance/quicksand 反伤、爆炸怪 KILLED 顺序、homing 手感（TURN_FACTOR=0.35）、TeleportSafety 边界、mob_skills 平衡性、waves 迁移整体跑通。

## 2026-08-17 — 版本去掉内部测试 rc 标记 → `1.0.0`

- **做了什么**：`pom.xml` / jar 最终名由 `1.0.0-rc.1` 改为 `1.0.0`；`config.debug.enabled/announce` 保持 `false`。
- **决策**：包体不再带内部测试版标识；正式服部署产物为 `Maggoteers-1.0.0.jar`。
- **遗留**：测试服需删旧 `Maggoteers-1.0.0-rc.1.jar`（部署脚本会清同前缀旧 jar）。

## 2026-08-18 — leave 跨世界送回大厅（returnLocation）

- **根因**：`/mgc leave` 不传送；`returnLocation` 只在 `cleanupRun` 对仍在 `getPlayers()` 的人生效，leave 玩家已移出名单，人停在 `maggoteers_*`。
- **修复**：quit 策略在 `finishGame`/卸图前调 `MaggoteersRoom.teleportReturn`（优先 `returnLocation`，否则 `waitLocation`；`Location` 自带世界，可跨世界）。
- **运维**：`/mgcop room edit maggoteers default` 把 `returnLocation` 设在 lobby 已加载世界内并保存。

## 2026-08-17 — leave 清计分板 / 药水；复活剥临时效果再 resync


- **根因**：`/mgc leave` 时玩家已不在 `getPlayers()`，`RunScoreboard.stop` 清不到；`removeAllFor` 只剥常驻 ADD_POTION，光环/临时药水残留。死亡 `cancel` 后原版也不清药水。
- **修复**：`RunScoreboard.clear(player)` 在 quit 策略调用；`removeAllFor` 清全部活跃药水 + `AuraService.stripAllFromReceiver/Source`；自动复活与复活币路径先 `clearAllActivePotions` 再 `resync` 常驻。
- **遗留**：无。

## 2026-08-17 — MGC 房间字段：等待点 / 回程点


- **做了什么**：`MaggoteersRoom` 增加 `waitLocation`、`returnLocation`（public Location）。join 成功后传到等待点；`cleanupRun` 在卸图前把仍在局内的玩家传到回程点。注册方式不变：`registerGame(MaggoteersGame.class, MaggoteersRoom.class)`。
- **决策**：本仓库 MGC **1.0.7** 没有图中的 `@FieldDescription`，编辑菜单只显示字段名；类型须 Location / 包装类。绝对坐标放 MGC 房间 JSON，不进本插件 YAML。
- **遗留**：已有 `default.json` 为 `{}` 时两字段为 null（不传送），须运维 `/mgcop room edit maggoteers default` 点地保存。

## 2026-08-17 — 复活不再补齐护符 / 绑定装备

- **做了什么**：自动复活与复活币救回后，不再扫描背包并补发缺失的收藏品（护符）和 `BOUND_EQUIP` 绑定装备。`EffectService.resync` 只重施加 held 被动与属性/药水派生视图；`CollectibleService.resync` / `BoundEquipService.resync` 仍用于对局结束剥离。
- **决策**：死亡已 `setCancelled`，物品栏本应保留；补发会把玩家主动丢掉或消耗掉的物品又塞回来。选中奖励时的 `grant`/`sync` 不变。
- **遗留**：无。

## 2026-08-17 — Mounted Squad Hardening（骑乘小队加固，SDD 六任务 @ 9487c0a..2bccf18）

- **做了什么**：分支 `feat/mounted-squad-hardening`，6 个任务全部经 task review（Approved）与 whole-branch final review（opus，无 Critical/无 Important，13 个 Minor 全部分流 FALSE-POSITIVE/DEFER）。全量 `mvn -o test` = **329 pass / 0 fail / 5 skip**；`package` 产出 `Maggoteers-1.0.0-rc.1.jar`。
  - **R3 数据面**：`PassengerSpawn`/`PassengerCfg`/`MobYamlParser`/`RunPlanner` 贯穿 `controller` 布尔字段（`parseController` 省略=false、布尔原样、非布尔硬失败，仿 `parseBossBar`）。
  - **R3 逻辑面**：`MountControllerResolver` 拆纯函数 `controllerCandidates(List, rootIsCamel)`（无 Bukkit、可单测，9 用例）+ 薄运行时 `resolve`/`walkPath`；显式段→默认回落段（camel 双叶→[[0]]；camel 嵌套→[前子树最深, 全树最深]；非 camel→全树最深），去重，子树内 `>=` 后胜 / 跨子树 `>` 先胜（复刻旧语义）。
  - **R1**：`MountedSquadRegistry` 从静态全局表改 `IdentityHashMap<AbstractGame, Map<UUID,Squad>>` 按局隔离；`MobFactory.spawnStepGroup`/`mountPassengersDelayed` 加 `AbstractGame` 首参。
  - **R2**：`WaveEngine.clearTracking` 与 `stop` **各自**调 `removeGame(game)`；`stop` 删逐根 `removeByRoot`；`handleMobDeath` 保留逐根。
  - **R4 座位**：`MountPassengerLimits.directPassengerLimit(EntityType)`（Camel 族 `CAMEL||CAMEL_HUSK→2`，余 1）；`prepareMount` 删 `setTamed(true)`（原版僵尸骑士/骆驼队未驯服，车辆由 moveTo 驱动）。
  - **M1 + R4 移动**：`MountedSquadAiService` 每 tick 读 `mob_attributes.squad_target_range/squad_move_speed/squad_charge_speed`（getDouble 带默认值，旧配置缺键不崩）；`sprint = root instanceof AbstractHorse` 门控 `moveTo(target, sprint ? chargeSpeed : moveSpeed)`；无 root `setTarget`。
- **关键决策与原因**：
  - **`setSprinting` 删除（spec §5.2 偏离）**：`setSprinting(boolean)` 在 paper-api 26.2 **仅 `Player` 有**（javap 确认 build.112-stable，LivingEntity/Mob/AbstractHorse 全无），`root.setSprinting(true)` 无法编译。裁决：删行，`sprint` 仅作 moveTo 速度倍率门控——spec 自述 sprint 只是可能动画，R4 实际提速来自倍率，需求完整保留。
  - **冲刺检测用族分类而非 EntityType 白名单（铁律③）**：`instanceof AbstractHorse` 覆盖 Camel/CAMEL_HUSK/未来子类；座位上限是唯一豁免（显式列 Camel 族，spec §8 授权）。
  - **Config 键带默认值**：三新键 `getDouble(..., 默认)`，旧服缺键不崩。
  - **纯函数先行**：候选链逻辑无 Bukkit 依赖才可单测；Bukkit 交互（registry/AiService/WaveEngine）靠编译 + 测试服验证，不强行 mock。
- **遗留 / 移交**：
  - **测试服验证（spec §6 Must，合前阻塞）**：部署 jar 到 `E:\MCpaper`，生成 `w2_endless_spear`（ZOMBIE_HORSE 根 + 持矛 ZOMBIE）与 `s1_desert_four_camels`（CAMEL_HUSK 根 + HUSK/PARCHED）：① 车辆位移明显提速 ② controller 举矛姿态 ③ `squad_charge_speed` 可调档校准默认 4.0。Stretch ④ 贴脸挥矛伤害（失败记 follow-up，不挡本计划）。
  - Reviewer 建议：未来若有 camel 前座嵌套乘客，补 1 条 `controllerCandidates`「camel 子树内显式 controller」用例；可选 `MountedSquadRegistry` 加 `Bukkit.isPrimaryThread()` 守卫；顺手补 `MobYamlParserTest` 末尾换行。

---

## 2026-08-17 — 设计冗余清理 plan（§1–§14：合并重复实现 + 删半死参数 + 文档修正）

- **做了什么**：执行「设计冗余清理」plan 全部 §1–§14。与同日死代码清理互补：上一步删**纯死符号**，本步合并「同一逻辑多处拷贝」、删「定义了但无运行时消费者」的半死参数/分支，并修正 CLAUDE.md 与代码的错位。每节删前 `grep` 引用面、合并保持行为不变。
  - **§1 半死/死符号**：`Trigger.ON_GAME_END` 枚举值 + `MaggoteersGame` 的 fire 调用删除；`ItemAbility.single(...)` 工厂删除；@Deprecated `ItemService.CURRENCY_NORMAL/BOSS` 删除；`TriggeredGrantAttribute.isRevokeOp/normalizeOp` 删除；`rewards.yml` 9 个 `currency:` 键删除。
  - **§2 toward 死链 + 不可达 AURA**：`EffectKeys.PROJECTILE_TOWARD`/解析 case/`SummonParams.toward`/`SummonParamsParser.weaponPath` 参数全链删除，`items/maggoteers.yml` 的 `toward: look` 删除；确认触发型 AURA 无来源能构造 → `executeEffect` 的 `case AURA` 与 `activateAura` 删除。
  - **§3 GRANT_ITEM 双 createItem**：`executeGrantItem` 整段收敛到 `GrantItemParams.parse().map(ItemService.give)`；`MagicFxConfig.forWeapon` → `currentDefaults()`（返回**配置加载的** defaults，非 `MagicUseFx.defaults()` 写死值——两者半径 4.5 vs 4.0 不同）。
  - **§4 items 目录扫描共享**：新 `util/ItemYaml`（`normalizeItemId` + `forEachYaml`）；`ItemAbilityRegistry`/`WeaponHeldRegistry`/`MagicFxConfig`/`ItemService` 四处目录扫描与 id 规范化共用之。
  - **§5 交付/消耗共享**：`ItemService.deliver` 提 public 供 `CollectibleService` 复用；新 `spendOneFromMainHand`；`ConfigMagicHandler`/`ItemInteractRouter`(SUPPLY_HEALING) 改调；保留 kind 级 `spendOne/spendOneKind`（遍历全背包，语义不同）。
  - **§6 Act 粘贴+传送去重**：`WaveScheduler.pasteActForCursor/findMap` 删除，统一走 `ActSpawnHelper.pasteAndTeleport`。
  - **§7 PDC kindKey + 参数清理**：`ItemService.kindKey()` 提包可见，`RunItemTags` 内联复用；`ItemAbilityRegistry.defaultStackForStep` 去未用 `deferred` 参数（测试同步）。记录 held `ADD` 默认 vs deferred `REPLACE` 的**有意不一致**。
  - **§8 治疗统一**：新 `EffectService.heal(Player,double)`（`min(max,hp+amount)`，返回实际量）；`applyHealOrSelfDamage` 正分支 / HEAL_AREA / `AuraService` HEAL grant / `ItemInteractRouter`(SUPPLY_HEALING) 统一调之；负分支（自伤 HP 精确扣除）不动。
  - **§9 玩家活力重置合并**：新 `PlayerStateManager.clearFallFire` + `healToMax`；`applyAdventure`/`restoreFullHealth`/`MaggoteersGame.resetLobbyVitality`/`MaggoteersDeathStrategy.stabilizeAlive`/`ActSpawnHelper` 统一调用（用户拍板「三处全部统一」）。`resetLobbyVitality` 刻意**不加** `noDamageTicks`。
  - **§10 配置解析工具 + MobYamlParser**：新 `config/ConfigParse`（`num`/`trimToNull`）；替换 `MapRepository`/`AffixService`/`WavesConfig`/`MobYamlParser` 的私有 num；`parsePassengersDepth`/`parseOnDeathDepth` 抽共享 `parseMobNode`（PassengerCfg/DeathSpawnCfg 构造参数顺序已核对：onDeath 与 passengers 互相前置）。
  - **§11 MobFactory 刷怪配置块提取**：新 `configureSpawnedLiving(...)` 统一 death/mount/passenger 三个 spawn 配置序列；`setAware(true)` 只在 death-mob 与 passenger（mount 无，保持现状）。
  - **§12 game 解析器统一**：新 `PlayerStateManager.gameOf(Player)`（先 registry 级 `gameForPlayer`，回退 MGC `PlayerExt` instanceof）；`EffectService.currentGame`/`EffectListener`/`WeaponHeldListener`/`AllyTargeting.resolveForPlayer`/`PotionImmunity.stateOf`/`SummonFriendlyFireListener` 统一委托；顺带修复旧裸 cast `(MaggoteersGame) pe.getGame()` 的潜在 CCE。`ItemInteractRouter:129` **保留** `new PlayerExt`——需要 `AbstractGame` hint 传给 `resolveForPlayer`，记录为 §12 例外。
  - **§13 明确不合并项（只加注释）**：两个 20-tick 心跳（`EffectListener.startTick` 分发 ON_TICK_1S vs `GameplayTickListener` 光环/药水/满腹维护）互引注释；`executeEffect`（无 defaults）vs `executeMagicEffect`（先 `withDefaults`）双 switch 互引注释；`RewardOption.parseParams` vs `EffectParamsParser` **不整体合并**（前者吃 `Map<?,?>`、后者吃 `ConfigurationSection`，且默认值 radius 8.0 vs null 真实不同）；`RewardOption.unique` 保持；config.yml `scaling:` 下加 `# mob_speed` 注释占位（不启用）。
  - **§14 文档修正（CLAUDE.md）**：§4.2 子系统表按实际包结构重写（`plan/RunPlan`→`ActPlan`+`RunConfig`、`mob/` 移除 `AffixService`、菜单移入新 `menu/` 行、删 `currency/` 与 `shop/` 行、`PoolBuilder`→`UnlockRegistry`、`PdcKeys`→`RunItemTags`、`ConfigManager`→各 Config 类，并补 `listener/`/`persist/`/`integration/` 行）；§6 剧本结构块按 `RunPlanner.plan` → `List<ActPlan>` + `RunConfig` 重写；§7.6 配置块与 config.yml 逐键同步（`initial_equipment`→detection_radar、`rest`→60、act2/act3 波数、`scaling` 数值，补 `debug`/`mob_attributes`/`class_select`/`items`/`run_items`/`aura`/`magic_fx`/`world`/`player`/`act_enter`/`map`/`rewards`/`messages`）；§12 schema 删 `currency:` 与 cost 示例（统一 `cost: 1`），加注币种由 RestMenu 按波次层级推导（`RewardService.normalShopTier`）；§10.1/§15 同步删已移除的 `ON_GAME_END`。
- **原因**：三线并行审计确认一批「同一逻辑多处拷贝」与「定义了但无运行时消费者」的配置项/参数/分支；全部合并或删除，行为不变。
- **决策**：
  - **`Trigger.ON_REVIVE` 保留**（未来会用，fire 点不动）——§1 只删 `ON_GAME_END`。
  - **`RewardOption.parseParams` vs `EffectParamsParser` 不整体合并**——数据结构与默认值有真实差异，只共享 `parseGrantBlock` 子解析器，残余字段漂移记为**可选未来工作**。
  - **两个心跳 / GRANT_ITEM+SUMMON+GRANT_REVIVE 双 switch 有意不合并**——职责/枚举源/默认值语义不同，合并会改变行为（§13 注释已记录）。
  - **`ItemInteractRouter:129` 保留 PlayerExt**——需要 `AbstractGame` hint，`gameOf` 返回 `MaggoteersGame` 不满足签名。
  - **config.yml `# mob_speed` 仅注释占位**——当前速度缩放不生效，取消注释即启用。
- **遗留**：`parseGrantBlock` 之外 `RewardOption.parseParams` 的残余字段漂移；最终验收（本机 WSL 仅 JDK 21 无法编译 Java 25）在 **Windows IntelliJ `mvn test`** 完成。

## 2026-08-17 — 波次关卡中文名（`关卡名称`）：开波提示 + 计分板

- **做了什么**：`waves.yml` 每条 strategy 增加 `关卡名称`（取自原中文注释，如「烟花秀」）；`SpawnStrategyCfg` / `WaveSpec` 携带该字段；`wave.begin` 的 `{strategy}` 改用中文名；计分板新增 `scoreboard.line_strategy`（`关卡 · {strategy}`）。缺省回落 strategy id。
- **决策**：YAML 键用中文 `关卡名称`（需求原文）；运行时字段英文 `displayName`。服上已有 `waves.yml`/`config.yml` 不会被 jar `saveResource(false)` 覆盖，部署须显式同步。
- **遗留**：无。

## 2026-08-17 — 中途退出玩家残留状态修复：观察者模式进大厅 + 上局档案（物品栏/血量加成）跨局残留

- **问题（QA 反馈）**：玩家死亡转观察者后 `/mgc leave`，回大厅仍保持观察者模式；再次进入房间时，会拿到上局退出时的玩家档案（物品栏、MAX_HEALTH 加成等）。
- **根因**：MGC `DefaultPlayerManager.leaveGame` 只调 `switchProfile(null)`，而 MGC `ProfileCapture` **只保存/恢复物品栏 + XP**，完全不碰 GameMode、血量、属性加成、药水效果。退出玩家又不在 `cleanupRun` 的 `getPlayers()` 清理范围，故：
  - `setGameMode(SPECTATOR)` 无人复位 → 观察者进大厅；
  - `EffectService.removeAll` 从未对退出玩家调用 → MAX_HEALTH 属性加成/常驻药水跨局累积（且 `removeAll` 走 `currentGame(p)` 查 MGC game map，退出玩家已移除 → 即便调用也拿不到 PlayerState 提前 return）；
  - `switchProfile(null)` 会把局内物品 capture 进 `maggoteers` profile，下次 `joinGame` 的 `switchProfile` 又 apply 回来 → 等待房展示陈货。
- **修复**：
  - `EffectService` 新增 `removeAllFor(Player, MaggoteersGame)`（显式传对局对象），`removeAll(p)` 委托之——解决「退出玩家查不到 game」的问题，剥除派生属性/药水并 `clearEffects`。
  - `MaggoteersPlayerQuitStrategy` 补 per-player 清理：`removeAllFor` + `setGameMode(SURVIVAL)`（修观察者模式）+ `MaggoteersGame.resetLobbyVitality`（血量/饥饿复位）；`AuraService.stripAllFromSource` 改为无条件调用（幂等）。**刻意不清物品栏**——此刻当前 profile 已是 lobby，若清空，下次 `joinGame` 的 `switchProfile` 会把空物品栏写进 lobby profile，永久清空大厅物品。
  - `MaggoteersGame.getGameWaitStrategy()` 返回子类，`onPlayerJoin`/`onPartyJoin` 先清物品栏——`tryJoin` 里 `joinGame` 先 apply 陈旧 `maggoteers` 快照、随后才调 wait 策略的 `onPlayerJoin`，此处清掉即隐藏等待房陈货。
  - `startInWorld` 调整顺序：**先 `getInventory().clear()` 再 `switchProfile(this.getId())`**——让 profile capture 把空物品栏写进 `maggoteers`，从源头清掉残留快照（此后再 apply 也是空）。
- **决策**：
  - 未直接改 MGC（`ProfileManager.playerProfileCache` 私有、无公开 per-player ProfileData 访问），故「清空残留 profile」靠「先清物品栏再 switchProfile」达成，不依赖 MGC 内部。
  - 死亡转观察者时**不**清物品栏（复活币要还回背包），残留清理统一放在 quit 路径。
- **遗留**：`maggoteers` profile 若残留 XP 等级（capture 含 level/exp），不在本次处理范围（游戏不用 XP，属次要外观问题）。
- **验证**：JDK25 定向编译 EXIT 0；Windows IntelliJ 实测通过——死亡后 `/mgc leave` 回大厅为生存模式，再次进入不再出现上局物品/MAX_HEALTH 加成残留。

---

## 2026-08-17 — 死代码清理 plan（R1 drop× 链 + 战斗/波次 + 效果/物品 + 奖励 + 可见性收紧）

- **做了什么**：执行 `docs/plan/2026-08-17-dead-code-cleanup.md` 全部五节。每节流程：删前 `grep` 0 引用（含 src/test）→ 删除 → 测试同步 → 删后 `grep` 复核残留为 0。
  - **§1 R1 drop× 货币掉落死代码链**（`CurrencyService` 类不存在、货币仅来自清波 `clearReward`）：`plan/Compose` 移除 drop 倍率计算与 compose 产物 drop 参数；`RunPlanner` 不再传 `dropMult`；`wave/MobSpawnProfile` 移除 `dropMult` 字段及 from* 参数；`wave/SpawnStep`/`PassengerSpawn`/`DeathSpawn` 移除 `dropMult` 参数（构造器全量同步）；`config/Affix` 移除 `drop`；`config/ScalingConfig` 移除 `currencyDrop` 及读取；`config.yml` 删 `scaling.currency_drop`；`affixes.yml` 删 `greedy` 词缀。
  - **§2 战斗/波次**：`MobFactory.spawn(Location,SpawnStep)`、`spawnStepGroup` 2 参重载；`WaveEngine.entityStep(UUID)` + `WaveRuntime.mobSteps` Map；`MountedSquadRegistry.clearAll()`；`WaveScheduler.pause/resume`、`isPrepPhase`；`WaveRuntime.cleared` 字段及置位；`Phase.CLEARED`；`MobSpawnProfile` 未读字段 `affixes/infernal/name`；`DeathSpawn.count`（恒 1，靠外部 for 展开）；`MapRepository.pickRandom` + 非种子 `RNG`；`WavesConfig.getStrategy/getPool`（统一走 `definitions()`）。
  - **§3 效果/物品**（15 生产符号 + 5 测试方法）：`EffectService.dispatchTriggeredEffects`（5 参包装）→ 只留 `...Collecting` 并更新 javadoc；`executeEffect` 3 参重载 → 只留 4 参；`EmpowerFxCoalesce.pickFxLabel`；`MagicFxConfig.mergedFxForAbility`（2 参 deprecated）、`hasExplicitBinding/boundWeaponIds`；`BeamLogic.sampleSteps`（调用点内联）；`AttributeModifierKeys.matchesManagedEffect`；`MagicFxPresets.followMarkAbove` + `EffectKeys.MARK_DURATION_SEC` + `EffectParamsParser` 对应 case；`TriggerContext.withAttacker`；`WeaponAbilityIds.isPackMember`；`WeaponTempEffect.EFFECT_ID_PREFIX/effectId`；`ItemAbility` 7 个访问器（record 重构为只留 `isDeferredOnly`+`single`）；`ItemAbilityRegistry.snapshot()`；`ItemKind.fromPdc` 精确重载（留 `fromPdcLoose`）；`SummonExecutor.applyProjectile` 相同 if/else 合并为一行。测试：`EmpowerFxCoalesceTest`/`BeamLogicTest`/`TriggerContextWithersTest`/`WeaponAbilityIdsTest` 删对应用例、`WeaponTempEffectTest` 改用 `WeaponAbilityIds.effectId`。
  - **§4 奖励**：`RewardPool.currency` 组件 + `currencyKind()/currencyItemKind()` + 4 参 legacy 构造 + `RewardService` 解析局部变量（`POOLS.put` 改 3 参 record）；`UpgradeLevelCaps.defaultCap()`；`collectibles.yml` 删 4 个 `a3b_*` 映射（复用 act2 护符）；`RewardPool` 相关 2 处测试构造同步。
  - **§5 可见性收紧**：`ItemService.itemId/deliver/countCurrency/spendOne` 由 public 收紧为 private（0 外部调用，均内部私有助手）；`ParticleEffects.playAreaRing` 4 参重载删除（留 3 参便捷版，EffectService 消费）；`GameRegistries.PARTICLE_ALLOWLIST` + `particle()` 回退遍历删除——**确认为不可达**：`Particle.valueOf` 用大写名、allowlist 是枚举常量子集，不可能比 valueOf 匹配更多，非 Paper 粒子重命名预留。
- **原因**：四线审计确认一批「定义了但从未被消费」的方法/字段/枚举/配置项；全量符号删前 `grep` 0 引用、删后复核无残留。
- **决策**：
  - `ItemInteractRouter.registerHandler`/`WEAPON_HANDLERS` **保留**——CLAUDE.md §15 记录的设计逃生口（Escape hatch），运行时恒空但属公开扩展点 API，不做 deprecated 标注以免误删。
  - `Fake255Rules` 保留（仍在服役：禁止 amp-255 伪装清除/免疫，硬失败校验）。
  - `RewardOption.unique` **不删**（半死：仅加载告警，不参与运行判定）。
  - `ItemAbility.single(...)` 工厂 0 引用但不在 plan 列举范围 → 保留未动。
  - `projectile.toward`：`SummonExecutor` 两分支原相同、运行时无消费者，仅合并 if/else；`items/maggoteers.yml` 的 `toward: look` 配置保留（疑似预留），未删。
  - @Deprecated `ItemService.CURRENCY_NORMAL/CURRENCY_BOSS` 常量 0 引用，不在 plan §5 范围 → 未删。
- **遗留**：
  - `rewards.yml` 仍有 9 个 `currency:` 键（normal/boss），`RewardService` 已不再解析（币种由 RestMenu 硬编码）→ 现为「半死」配置，未在本 plan 删；建议单独确认后移除。
  - **验收**：本机 WSL 仅 JDK 21 无法编译 Java 25 字节码，WSL 侧无法编译；最终由 Windows IntelliJ `mvn test` 全绿 + 测试服实测通过确认（2026-08-17）。

---

## 2026-08-17 — 战斗/触发/奖励链路审计：修复 7 处 + 删 3 处冗余 + 2 个后续 plan

- **做了什么**：
  - 四线并行审计（战斗数据链路 / 触发→效果 / 三选一奖励 / 跨模块死代码），逐条与需求方确认处置后落地。
  - **修复 7 处**：
    - R2 `WaveEngine` 新增 `clearPendingSplitCancel()`，`onWaveCleared` 与 `stop` 清空史莱姆分裂取消集合（防 `SlimeSplitEvent` 先于 `EntityDeathEvent`/被取消导致的无界增长）。
    - R6 `EffectService.executeMagicEffect` 补 `GRANT_REVIVE` 分支（此前 use_ability 配 `GRANT_REVIVE` 加载通过但运行时静默失败）。
    - R9 `MaggoteersPlayerQuitStrategy` 退出时清 `CooldownService` CD 表（防跨局泄漏/陈旧 CD）。
    - R10 三选一抽签缓存：`RewardService.drawCached/invalidateDraw/clearDraws`；ESC 退出保留选项，仅购买/换池刷新（此前可 ESC 重进白刷新不耗币）。
    - R11 bundle 可见性按 bundle 自身 id 的 `acquiredUnique` 判定（此前含 WEAPON/SUPPLY grant 的 bundle 恒可见、无限重复发放）。
    - R13 `ItemService.countCurrency` 改遍历 `getSize()`（含副手），与 `spendOne` 范围一致。
    - R14 `UnlockShopMenu` 解锁前经 MGC `AlertMenu(Player, Runnable)` 二次确认。
  - **删 3 处**：延长休整（`config.yml` `rest.extend_sec/extend_max` + CLAUDE.md §9.1/§9.3/§7.6）；`Trigger.ON_INTERACT`（枚举 + `RewardLoadValidator` 引用 + 2 测试占位改 `ON_KILL`/`ON_TICK_1S`）；孤立世界清理开关（`world.cleanup_orphans_on_enable` 键 + CLAUDE.md §7.6）。
- **原因**：审计发现 `drop×` 货币掉落整链是死代码（`CurrencyService` 类不存在、`MobSpawnProfile.dropMult` 无读取）、`scaling.mob_speed`/`currency_drop` 等配置键未被消费、多处文档-代码错位。
- **决策**：`drop×` 掉落链（`Affix.drop`/`scaling.currency_drop`/`MobSpawnProfile.dropMult`/`Compose` 掉落倍率）与其余死方法/死字段一并纳入「死代码清理 plan」确保安全再删；`debug.enabled=true` 为测试期开关、上线前改 false；`mob_speed` 保留（后续扩展「词条」）；`friendly_fire` 召唤物误触 `ON_DAMAGE_TAKEN` 作风味保留。
- **遗留**：
  - **R3（延迟挂载乘客竞态，需单独 plan 重做）**：`MobFactory.spawnStepGroup`/`mountPassengersDelayed` 用 `runTaskLater(1L)` 挂载乘客、回调闭包捕获当时的 `rt`；若这 1 tick 内 `WaveScheduler.defeat/stop` 先跑完，延迟任务仍会把乘客写进陈旧 runtime + `BY_ENTITY`，实体无人移除。建议挂载前校验 `RUNTIMES.get(game)==rt`，或挂载前判 game 是否已 `cleanedUp`。
  - 本机 WSL 仅 JDK 21（读不了 Java 25 依赖 jar），本次改动未能在 WSL 编译；建议 Windows IntelliJ 跑一次完整 `mvn test` 收尾。

---

## 2026-08-16 — 全员倒下改为观战保留房间，全员 leave 才结束

- **做了什么**：
  - `MaggoteersGame` 拆出三个结束入口：`fail()`（启动失败 / 世界创建失败等**即时**结束）、`markDefeated()`（全员倒下：`outcome=FAIL` + 停波清怪，但**不** endGame，保留房间）、`finishGame()`（全员 leave 后真正 endGame；若 `outcome` 仍 `IN_PROGRESS` 视为失败）。
  - `WaveScheduler.defeat()`：停心跳 + 清怪 + 置 `DEFEATED`，但**保留 cursor** 供结算读进度（区别于 `stop()` 会移除 cursor）。`Phase` 新增 `DEFEATED` 枚举，计分板经 `wave.phase_defeated`（config.yml 可配文本）显示「失败」。
  - 死亡策略「全员倒下」与退出策略「最后存活者离开」都改调 `markDefeated()`；退出策略「已无人」改调 `finishGame()`。新增消息 `game.defeat`。
- **原因**：原「全员倒下 → 立即 fail → 删房」体验生硬，玩家来不及看结算/复盘；希望倒下后继续以观察者观战，直到全员 `/mgc leave` 才真正结束并删房。
- **决策**：`markDefeated` 在败局时就置 `outcome=FAIL` 但**保留 cursor**——否则 `WaveScheduler.stop` 移除 cursor 会让结算 `progress` 读到 {0,0}、败局货币归零；真正结束统一由 `finishGame` 触发，结算在 `cleanupRun` 用保留下来的 progress 计算 FAIL 部分货币。
- **遗留**：掉线（DISCONNECT）走同一 quit 策略，最后一人掉线也会 `markDefeated` 而非直接结束。

---

## 2026-08-16 — 多人生命周期加固：掉线不停局 + 中止后孤儿定时器 + 结算幂等

- **做了什么**：
  - 重写 `getPlayerQuitStrategy()`，新增 `MaggoteersPlayerQuitStrategy`：单人掉线/退出不再走 MGC 默认的 `abortGame`（整局终止），改为 `markDown`（alive=false，等同死亡耗尽转观察者）+ `AuraService.stripAllFromSource` 剥离光环来源，其余人继续；退出后已无人（含 STATING 窗口 world/PlayerState 尚未初始化的极端情况）或局已开局且无存活玩家时才 `fail()`。
  - `MaggoteersGame` 新增 `volatile boolean cleanedUp`：`cleanupRun` 首行幂等守卫；`win/fail/startInWorld`、plan 轮询、职业选择轮询、`onAllClassesChosen` 全部加存活守卫。
  - 新增消息 key `death.to_down_quit`（config.yml + `MessageService.REQUIRED_KEYS`）。
- **原因**：
  - MGC `DefaultPlayerQuitStrategy` 对「任何一人退出」都 `abortGame`，与本设计文档假设的「onGameAbort = 全员退出」矛盾——4 人局一人网络闪断即整局清退、其余人 0 收益（P0-1）。
  - abort 时 `outcome` 保持 `IN_PROGRESS`，原 `outcome != IN_PROGRESS` 守卫对 abort 路径形同虚设，导致 `startInWorld` 的 plan 轮询定时器在局已中止后仍 `beginClassSelect → onAllClassesChosen → WaveScheduler.start` 在已删世界的死局上重启波次状态机；`runWhenReady` 延迟的 `startInWorld` 也会在中止后建孤儿世界（P1-1）。
  - `onGameCancel/Abort/End` 三者直连 `cleanupRun` 且无幂等，重复触发会重复发账户货币（P1-2）。
- **决策**：掉线者标记 `alive=false` 后，`isAnyAlive`/`skipVoteTotal`/光环治疗等所有 `isRunParticipant` 过滤自动排除他，无需额外清 `BY_UUID`（P0-2 一并解决）；`cleanedUp` 在 `cleanupRun` 同步置位作为唯一存活信号；`Settlement` 对 `IN_PROGRESS` 仍返 0，不改失败结算语义。
- **遗留**：
  - 掉线者不能再被复活币救（`ReviveMenu` 遍历 `getPlayers()`，离线者已被 MGC 移出）——无中途重连故无实际意义，接受。
  - 服上旧 config.yml 缺 `death.to_down_quit` 会退回字面量 `messages.death.to_down_quit`，正式发版随 config 一并更新。
  - 本机 WSL 仅 JDK 21（读不了 Java 25 依赖 jar），已用 `javac.exe` 定向编译 `MaggoteersGame` / `MaggoteersPlayerQuitStrategy` / `MessageService` 验证通过；建议 Windows IntelliJ 跑一次完整 `mvn test` 收尾。

---

## 2026-08-16 — 修复 ADD_ATTRIBUTE 百分比乘算 bug + op 白名单校验

- **做了什么**：`AttributeModifierKeys.attributeOperation` 把 `PERCENT` 从 `MULTIPLY_SCALAR_1`（乘算）改为 `ADD_SCALAR`（加算）；新增 `op: MULTIPLY` 显式映射到 `MULTIPLY_SCALAR_1`（预留乘算扩展点）；新增 `isValidAttributeOp`，`RewardLoadValidator.validateAttributeOp` 对 `ADD_ATTRIBUTE` 的 `op` 做 `FLAT/PERCENT/MULTIPLY` 白名单校验（非法值加载即报错）。`EffectService`/`AuraService` 两处属性施加统一走 `attributeOperation`。
- **原因**：`PERCENT` 语义应为"百分比加算"（`base × (1 + Σvalue)`），但代码从计划阶段（`plan5-effects.md`）就错写成 `MULTIPLY_SCALAR_1`（`base × Π(1+value)` 逐层连乘），导致 `stack: ADD` 的百分比物理属性实际按乘算叠加、数值膨胀，且与魔攻（`1.0 + Σ`）语义不一致。错误源头在计划文档，代码照抄。
- **决策**：`PERCENT`＝加算、`MULTIPLY`＝乘算（当前 rewards.yml 未使用）；`FLAT`/缺省仍 `ADD_NUMBER`；魔攻（虚拟属性）继续只支持 `PERCENT` 加算，不走 `attributeOperation`。
- **遗留**：`MAGIC_DAMAGE` 若要乘算需在 `PlayerCombatStats` 单独加逻辑；`op: MULTIPLY` 目前是可用但未启用的扩展点；建议 Windows IntelliJ 跑一次完整 `mvn test` 收尾确认。

---

## 2026-08-15 — 玩家可见文案外置 config.yml messages

- **做了什么**：新增 MessageService（MiniMessage + escapeTags 占位）；config.yml 嵌套 messages:；局内广播/记分板/GUI/物品提示/商店/排行榜/GameMeta/命令壳改走配置。onEnable：saveDefaultConfig → MessageService.load → 
egisterGame/
egisterLeaderboard。
- **原因**：运维改提示无需改 Java；占位须 escape 防 sender() 二次 MM；嵌套片段禁带颜色标签（记分板用 line_wave/line_wave_resting）。
- **决策**：缺 key 无代码回退，load 清单 warn；debug 子命令与 
o_permission 仍硬编码；{tier} 保持英文 id。
- **遗留**：服上已有 config.yml 不会自动合并 messages:，升级须手工合并或备份后重放默认；仅重启生效。

---

## 2026-08-15 — 同层同档波次 strategy 不放回抽取

- **做了什么**：`RunPlanner` 每层 weak/strong/boss 各自维护已用 strategy id；去重后加权抽；候选空则 `IllegalStateException`（`planError` 终止对局）。单测扩 fixture；`StrongWaveRollTest` 对齐现网 `waves_per_act` 并为 Act1 挂 special。
- **原因**：有放回会出现同档重复关卡；跨档跨层本无同名 strategy。
- **决策**：容量按 **distinct id**（非条目数）；池不够硬失败不兜底；同 seed 仍确定，但相对改前序列会变。
- **遗留**：新 Act1 图若无足够 strong special/全局条目会开局炸；启动期 distinct-id 扫描仍可选（spec §5）。

---

## 2026-08-15 — 怪物配置名 + boss_bar 开关 + 拦截 IM 命名

- **做了什么**：waves.yml 可选 
ame:（MiniMessage）与 oss_bar: true；MobDisplayNames.lock 在 mechanize+equipment 之后锁定/清除头顶名；Adventure BossBar；废除 hp>=6 启发式；附录 A 迁移（真 Boss 补开关；鸭本/哨兵溺尸等误伤不补）。
- **原因**：IM 覆盖自定义名；启发式误挂高血小怪血条；Bukkit String 血条无法着色 MiniMessage。
- **决策**：仅布尔 oss_bar；无名则清自定义名（含 IM）；5 tick 纠偏 + 刷怪后 0/1/2 tick reassert。
- **遗留**：服上需同步 waves.yml；可选手测 IM 名闪一下是否可接受；RewardItemPresentationConfigTest 护符材质失败与本改无关。

---

## 2026-08-14 — 内部测试版 1.0.0-rc.1

- **做了什么**：版本改为 `1.0.0-rc.1`；`config.debug.enabled/announce`；debug 在 enabled 时对 `maggoteers.play` 开放；增 `debug balance` / `unlock`；部署脚本清理旧 jar；文档 `docs/release/2026-08-14-internal-rc1.md`。
- **原因**：先发内部可跑测试版完成签收 A，正式功能全开且方便 debug；正式服再关 debug、升 `1.0.0`。
- **遗留**：签收 A 通过后关 `debug.enabled` 再正式发版。

---

## 2026-08-14 — debug give 空成功

- **做了什么**：`debug give` 先按 `rewards.yml` option id 走 `RewardService.apply`（效果+护符/武器）；否则发 ItemCreator 物品；`ItemService.give` 改返回 boolean、满包掉脚下、可省略 `maggoteers:`。
- **原因**：旧逻辑只 `ItemService.give` 且无论成败都回「已给」；奖励 id 找不到物品时静默失败。
- **遗留**：奖励须局内；重启后手测 `a1b_atk150` / `maggoteers:herafinger`。

---

## 2026-08-14 — use_ability effects[] 嵌套 Map 丢 params

- **做了什么**：`ItemAbilityRegistry.mapToSection` 对嵌套 `Map` 改用 `createSection`；幻景改为即时抗性 + 延期 `BUFF_AREA clear_potions:[RESISTANCE]`。
- **原因**：`getMapList` 得到的 `params`/`expiry` 是 Map，`y.set(Map)` 后 `getConfigurationSection` 为 null → 误报 duration≤0 / 缺 expiry（宏愿/雷杖/匕首/赫拉芬格同源）。
- **决策**：YAML 里 duration>0 本身没错；赫拉芬格配置已齐，无需再补。
- **遗留**：重启后确认启动日志不再 skip 这 5 件武器。

---

## 2026-08-14 — 孤儿世界清理 + 经济闭环

- **做了什么**：
  1. `OrphanWorldDirs` 递归扫描 world container（深至 6），命中 `maggoteers_*`；`WorldService.cleanupOrphansOnEnable` 覆盖 Paper 26.2 嵌套路径 `world/dimensions/minecraft/maggoteers_*`。
  2. `rewards.yml`：约 25 件精品武器加 `requires_unlock`/`unlock_cost`（35–100，对齐 `win_flat:100`）；`UnlockShopMenu` 按 option id 去重并用 `RewardOptionIcons`。
  3. 扩充 `act3_boss`；`act2_boss` 补终局武器；**终局 Boss 清场后也进 REST**（粘滞已是 act3），休整结束/跳过再 `win()`——否则 act3_boss 永远不可达。
- **原因**：旧清理只扫 container 直接子目录；商店无 `requires_unlock` 商品；终局跳过休整使 sticky act3_boss 成死配置。
- **决策**：不改 §9.2 粘滞语义；用终局休整消费 Boss 币。护符 `a3b_*` 暂复用 `a2b_*` 外观。
- **遗留**：重启 Paper 后确认孤儿目录被删、`/maggoteers shop` 有货、通关前能开 act3_boss。

---

## 2026-08-14 — 下次攻击伤掉到拳头 + 腐化宝珠无伤

- **做了什么**：
  1. `AttributeModifierKeys.effect/` 前缀 + `stripPluginModifiers` 仅剥 `effect/` 键；不再误剥 ItemCreator 物品上的 `maggoteers:*_attack_damage`。
  2. `HEAL` 负值改为 `setHealth` 精确扣血；`DAMAGE_AREA`/`DAMAGE_BEAM` 经 `MagicDamage.applyIndependent`（清 `noDamageTicks`/`lastDamage`）再 `damage`。
- **原因**：下次攻击 buff 到期 `resyncDerived` 把整命名空间 modifier 剥光 → 武器伤永久丢失（≈2 血拳头）；腐化宝珠 `p.damage` 自伤被护甲/抗性/无敌帧稀释，且同 tick 光束可能撞原版无敌窗。
- **决策**：物品/光环键与效果派生键隔离；自伤不走伤害事件；魔法伤强制破无敌窗。
- **遗留**：重启 Paper 后手测赫拉芬格/破碎君王下次近战加成，以及腐化宝珠自扣 20 + 光束 50。

---

## 2026-08-13 — 下次攻击强化类 empower 打击特效

- **做了什么**：纯延期 `use_ability`（下次攻击消耗）在无 `empower_fx`、cast 仅为 `preset: NONE`/缺省时，命中 victim 播内置 `deferredStrikeFx`（CRIT + DOT_ABOVE + 暴击音）；`playOnEntity` 对非玩家补播 sound 并尊重 NONE。
- **原因**：战斧/八十等「右键蓄力 → 下次出手」与多步包 empower 路径应对齐，否则 buff 模板只有施法音、出手无感。
- **决策**：不强制改 YAML；显式 `empower_fx` / 可见 cast fx 仍优先。即时-only 包仍无默认打击闪。
- **遗留**：作者仍需手写 emp/herafinger 等多步内容；测试服手测战斧/八十出手闪。

---

## 2026-08-13 — Multi use_ability + deferred next-hit FX

- **做了什么**：use_ability.effects[] 多步包 + legacy 单 effect:；ireTrigger 先于 sweepExpiry；延期魔法 ireTrigger=expiryTrigger 且命中时走 weaponPath；即时步共享 TriggerContext（DAMAGE_BEAM 写 hitTarget）；cast / empower FX（victim coalesce）；WeaponAbilityIds 分隔符规则；延期默认 stack: REPLACE。
- **原因**：EMP/赫拉芬格/雷霆之杖等 lore 需要一次右键多段或下次攻击多段；旧 sweep-first 使同 trigger 延期魔法永远执行不到。
- **决策**：内容 YAML 本 PR 不改（作者后改）；eighty_hammer 为唯一静默行为变化（无需改 YAML 即下次近战眩晕）；empower：无可见 fx 时纯延期包播内置打击闪（不吃 defaults）。
- **遗留**：作者待改 emp / herafinger（含 held 普攻缓慢）/ 	hunder_rod / mission_sure / rostmourne held；测试服手测多步与 empower。

---

## 2026-08-12 — 测试服上线前 Critical + Important 修复（批次 A/B）

- **做了什么**：按审阅文档批次 A（C1–C5）+ 批次 B（I4–I7 + I14 YAML 同步）修复：
  - **C1**：`PlayerCombatStats.magicDamagePercentContribution` 跳过 `fireTrigger != null` 的模板层，仅常驻 grant 计入法伤倍率。
  - **C2**：`MagicDamageContext` 从 `HashSet` 改为 `HashMap<String,Integer>` 引用计数，嵌套 `run()` 不再提前清除标记。新增单测 `nestedRunPreservesOuterMark`。
  - **C3**：`MaggoteersGame.startInWorld` 世界创建失败（`w==null`）和剧本超时（30s）两处加 `fail()`，确保 `endGame()` → `cleanupRun()` 不被跳过。
  - **C4**：`RestMenu.openPick` 不再预扣货币；`PickMenu` 接收 `currencyKind` + `cost`，在 `RewardService.apply` 成功后才 `spendOneKind` 扣费。
  - **C5**：`WaveScheduler.onWaveCleared` 重构——终局波先发 `clearReward` + `ON_WAVE_CLEAR` + `SummonRegistry.onWaveClear`，再 `win()`。
  - **I4**：`MobAiLockRegistry` 新增 `ENTITY_GAME` 映射 + `restoreAll(Object game)` 按局恢复；`MaggoteersGame.cleanupRun` 改为 `restoreAll(this)`；`onDisable` 保留无参 `restoreAll()` 全清。
  - **I5**：`WaveEngine.stop` 中 MountedSquad 清理改为遍历本局 `ourMobs` 快照（`removeByRoot`），不再清全局 `snapshot().keySet()`。
  - **I6**：`ItemInteractRouter` 的 `SHOP_EMERALD` handler 加 `WaveScheduler.isRestPhase` 门控，战斗期右键提示"仅休整期可使用"。
  - **I7**：`MaggoteersGame.cleanupRun` 循环中加 `CooldownService.clear(uuid)`，防同 UUID 连开两局继承 CD。
  - **I14**：源码 `rewards.yml` / `items/maggoteers.yml` / `waves.yml` / `affixes.yml` / `config.yml` / `collectibles.yml` / `items/collectibles.yml` 同步到测试服 `E:\MCpaper\plugins\Maggoteers\`。
- **原因**：审阅 `docs/review/2026-08-12-测试服上线前审阅.md` 建议修复完再开多人测；批次 A 阻塞稳定与经济正确，批次 B 防多局干扰与配置漂移。
- **决策**：C2 用引用计数（非栈式）保持同 tick 同 key 语义；C4 扣费放 PickMenu（非 RewardService）保持 RewardService 无副作用；I4 用 `ENTITY_GAME` map 而非改造 LOCKS 结构以最小侵入。
- **遗留**：批次 C 剩余（I10 解锁商品、扩充 act3_boss）+ 批次 D（集成测/版本号）未修；手测清单 P0–P3 需在测试服实机验证。

---

## 2026-08-12 — Boss 池粘滞 + 休整跳过全员投票（I1/I2）

- **做了什么**：
  - **I1 Boss 池粘滞**：`WaveScheduler.Cursor` 加 `lastBossKillAct`；`onWaveCleared` 在 `lastTier == "boss"` 时更新粘滞层；新增 `bossPoolActIndex(game)`；`RestMenu` Boss 池查询改用该值替代当前层。
  - **I2 休整跳过全员投票**：`Cursor` 加 `skipVotes`（每休整期进入时清空）；`skipRest` 改为 `voteSkip(game, uuid)`（仅冒险模式玩家可投、全员同意才 `advance`）；新增 `skipVoteCount` / `skipVoteTotal`；`RestMenu` 跳过按钮 lore 显示 `投票进度 x/y`，未通过时重开菜单刷新。
- **原因**：对齐 CLAUDE §9.2（Boss 池=最近击杀 Boss 所在层）与 §9.3（跳过需全员同意）；补玩法完整性。
- **决策**：不做延长按钮（需求方裁减）；投票人数仅显示在现有跳过按钮 lore，不动 UI 布局；投票仅统计冒险模式玩家，观战者不计。
- **遗留**：`mvn test` 262 通过；Boss 池粘滞与投票需测试服多人手测（P1）。

---

## 2026-08-12 — 测试服上线前全仓审阅文档

- **做了什么**：产出 `docs/review/2026-08-12-测试服上线前审阅.md`（英文别名 `2026-08-12-preflight-review.md`）；交叉核验生命周期/效果/配置/部署；`mvn test` 261 通过 / 5 跳过。
- **原因**：内容基本完成后准备开测服，需统一风险清单与准入门槛。
- **决策**：Critical 五项（魔攻模板泄漏、MagicDamageContext 嵌套、开局失败不 fail、3 选 1 扣费非原子、终局跳过 clearReward）建议修完再多人测；测服 `rewards.yml`/`items/maggoteers.yml` 与源码哈希不一致须显式同步。
- **遗留**：按审阅文档批次 A→D 排期修复；手测清单未关。

---

## 2026-08-12 — Guardian 尖刺反弹 + 刺猬胸甲 StackOverflow

- **做了什么**：`MagicDamageContext` 同 tick 守卫扩展到 `ON_DAMAGE_TAKEN`（`EffectListener`）；单测覆盖 mark 生命周期。已部署测试服 jar。
- **原因**：crash 栈是 `DAMAGE_AREA`→`Guardian.hurtServer` 尖刺反伤→再触发 `ON_DAMAGE_TAKEN` 无限递归；`waves.yml` 远古守卫者上的 IM `swap` 只是同怪巧合，栈内无 InfernalMobs 帧。
- **决策**：不禁用 `swap`；补齐本就应覆盖 TAKEN 的 re-entry 抑制（原先只拦 `ON_DAMAGE_DEALT`）。
- **遗留**：重启 Paper 后手测近战带刺 Guardians / 刺猬胸甲不再崩服。

---

## 2026-08-12 — 药水清除 / 局内免疫（退役假 amp-255）

- **做了什么**：实现真实 `clear_potions` / `self_clear_potions`（`removePotionEffect`）与奖励 `ADD_POTION` + `immunity: true`（事件拦 ADDED/CHANGED + PurifyListener 伤）；加载硬失败假 255；迁移 `cataclysm`/`emp`/`holy_water`/`phantasm` 与 `a3w_imm_*`；部署默认同步 `items/*.yml`。
- **原因**：假 amp-255 经 PotionMerge 会变成真实永久/1-tick 高等级药水，沉默/净化/免疫语义全错。
- **决策**：不新增 Effect 枚举；`DAMAGE_AREA` 固定 damage→clear→potions；CD 仅成功计次；服上 YAML 靠显式同步（`saveResource(false)` 不覆盖）。
- **遗留**：服内手测沉默/净化/免疫与过期假 YAML 硬失败；`affixes.yml` amp 255 仍是怪物词缀（本任务未改）。

---

## 2026-08-12 — 近战触及无限：stripDerived 漏剥 ENTITY_INTERACTION_RANGE

- **做了什么**：根因确认后修复——`EffectService.stripDerived` / `AuraService.stripAuraDerived` 改为经 `DerivedViewAttributes` 剥离完整属性列表（含 `entity_interaction_range`）；`EffectService.resync` 在 `WeaponHeldService.sync` 之后**始终** `resyncDerived`（主手未变时 sync early-return 会跳过派生重算）。
- **原因**：`resyncDerived` 用唯一 KEY_SEQ 重加 modifier，但 strip 只清 HP/伤害/移速/攻速；触及等属性每次 apply/切武器/到期扫尾都会叠一层，实际上限常表现为实体追踪距离（约 48 格）。
- **决策**：维护显式 key 列表（可单测、不依赖 RegistryAccess）；新 ADD_ATTRIBUTE 属性必须同步进该列表。
- **遗留**：需部署新 jar 后局内 `/attribute` 或手测近卫职业触及不再随 resync 膨胀。

---

## 2026-08-11 — BOUND_EQUIP 保护胸甲内容与 GUI 预览

- **做了什么**：`RewardOptionIcons` STAT 分支对 `BOUND_EQUIP` 走 `BoundEquipParams.previewLevel` + `ItemService.createItem`（不经 collectibles）；`items/maggoteers.yml` 新增 `prot_chest_l1`–`l8`（保护 II–XVI、0 护甲/韧性）；`rewards.yml` `act1_strong` 池加入 `prot_chest` STAT（`UPGRADE_LEVEL` max 8）；reference Effect 表补 `BOUND_EQUIP` 行。
- **原因**：spec `2026-08-11-bound-equip-protection-design.md` Task 6——首条可玩 YAML 内容与 3 选 1 预览。
- **决策**：强怪池投放（Act1 中后期）；无护符映射；`unique: true` 仅首抽去重，升级靠 `RewardDrawVisibility` UPGRADE 路径。
- **遗留**：服内手测绑定/冲突销毁/槽位锁；其他层池是否追加同类奖励待内容迭代。

---

## 2026-08-08 — DISABLE_AI Effect（Phase 1）

- **做了什么**：`Effect.DISABLE_AI`；`MobAiLockRegistry`（longer-wins、`EntityRemoveEvent` 卸载 restore、1-tick 扫描）；`DisableAiTargets`（enemies|hit_target|attacker + 跳过 Player/`SummonRegistry`）；`executeEffect`/`executeMagicEffect` 接线；武器 `use_ability` 与 STAT `RewardLoadValidator` 加载矩阵（含 `ON_KILL`+`hit_target` warn+skip）。
- **原因**：需要可配置的短暂瘫痪敌对生物（停 AI），选择器与 BUFF_AREA 战斗语义对齐。
- **决策**：仅 `setAI`；再施加取更长剩余；卸载必须先 restore 防永久 NoAI；held_effects 接线延后 Phase 2。
- **遗留**：Phase 2 held；服内手测右键 AoE / 命中晕 / 反震来源。

---

## 2026-08-08 — 主手武器 held_effects + use_ability ADD_POTION 双路径

- **做了什么**：`WeaponHeldRegistry` / `WeaponHeldService` / `WeaponHeldListener`（主手 PDC 挂载 `held:<itemId>:<n>`，含 drop/pickup 同步、`AuraService.refresh`、`:grant` 前缀清理）；`WeaponEffectValidator` 加载校验；`EffectParamsParser` 共享 params 解析（含 AURA `grant:`）；`WeaponTempAttribute` 重命名为 `WeaponTempEffect` 并支持 expiry ADD_POTION；`ItemAbilityRegistry` 解析 `potion`/`amp`/`duration_ticks`；`EffectService.executeAbility` 即时 ADD_POTION 与 PlayerState expiry 分流。
- **原因**：spec rev.3——rewardplan 武器需要右键 5s 力量等即时药水，以及主手被动（常驻/触发型）零 Java 配置。
- **决策**：held 禁止 expiry（生命周期=持有）；use_ability ADD_POTION 禁止 expiry+duration 混用；触发型 held ADD_POTION 必须 `duration_ticks > 0`；AURA held 用嵌套 `grant:`（同 rewards 战旗）。
- **遗留**：`BuffPotionParserTest` / `MagicEffectParamsBuffAreaTest` 在无 MockBukkit 环境仍依赖 `PotionEffectType` 静态初始化（3 errors，非本特性引入）；服内手动验证 swap/drop/右键药水待测。

---

## 2026-08-06 — 触发型 ADD_ATTRIBUTE grant 层 + REVOKE_GRANTS

- **做了什么**：`TriggeredGrantAttribute`（`{id}:grant` 叠层、`op: REVOKE_GRANTS` + `source_id`）；`EffectService` 双 pass 分发（同 trigger 先 REVOKE）；触发型 ADD_ATTRIBUTE 命中时 spawn grant 而非直接改 stat；`PlayerCombatStats` 仅计 `fireTrigger=null` 的 grant；`RewardLoadValidator` 允许 STAT `ON_WAVE_CLEAR`、校验 REVOKE 与 bundle `source_id`；`EffectKeys.SOURCE_ID` + `RewardOption` 解析 `source_id`；单测覆盖 grant/revoke/校验。
- **原因**：spec rev.2 通过——需纯 YAML 实现「受击叠魔攻 + 波末可选清空」「波末 +5% 魔攻」等，且修复模板被误计入 `MAGIC_DAMAGE` 的 bug。
- **决策**：REVOKE 泛用于任意 attr grant（含 ATTACK_DAMAGE）；Bukkit attr grant 撤销后 `resyncDerived`；YAML 不要求 clear 条目排在 stack 前；`ON_ACT_ENTER` 仍不在 STAT 白名单（后续 rewardplan）。
- **遗留**：本地 shell 无 JDK 25，Maven test 未在本环境跑通；服内验证 bundle 样例（静水流涌 / 灵魂立方）待 rewards.yml 内容作者补条目。

---

## 2026-08-05 — 武器纯配置「下次近战加成」

- **做了什么**：`use_ability.effect: ADD_ATTRIBUTE` + 必填 `expiry`/`stack`；右键 `EffectService.apply` 临时效果 `ability:<itemId>`；`resyncDerived` 在 apply（ADD_ATTRIBUTE）与 `sweepExpiry` 后整表重同步，避免 modifier 残留；样例 `maggoteers:power_strike`。
- **原因**：内容需要「右键后下一刀伤害加成」且零 Java；引擎已有事件到期模型，缺武器路径接入与 expiry 清属性。
- **决策**：仅近战 `ON_DAMAGE_DEALT`；默认 `stack: REPLACE`；不做主效果+旁挂 grant。
- **遗留**：弓箭不消耗/不享受；未做 DAMAGE_AREA 与下次加成组合。

---

## 2026-08-05 — ON_DEATH 每次掉命触发 + 死亡点 origin

- **做了什么**：`MaggoteersDeathStrategy` 在自动复活与最终观战两条路径**之前**统一 `fireTriggerPlayer(ON_DEATH, atEvent(deathLoc))`；自动复活成功后另发 `ON_REVIVE`；`TriggerContext.eventLocation` + `resolveOrigin()` 供 `DAMAGE_AREA`/`SUMMON death_site` 等在倒下点结算；`RewardLoadValidator` 白名单加入 `ON_DEATH`；`rewards.yml` 样例 `death_burst`。
- **原因**：需求「每次掉命都触发」——含消耗 reviveCount 的自动复活与 reviveCount=0 转观察者，语义与 `ON_REVIVE`（仅自动复活成功）分离。
- **决策**：死亡点 = `PlayerDeathEvent` 时 `player.getLocation().clone()`，在传送/切模式前触发；区域效果不因 `markDown` 后 `isAlive=false` 被 gate；复活币路径暂不 fire `ON_REVIVE`（Phase D 可选）。
- **遗留**：复活币 `ON_REVIVE` 未接；服内验证 `death_burst` 与 `SUMMON anchor=death_site` 表现。

---

## 2026-08-05 — GRANT_ITEM / SUMMON Effect（rev.2 spec）

- **做了什么**：新增 `Effect.GRANT_ITEM`（ItemCreator 发物品）与 `Effect.SUMMON`（锚点召唤 + PDC 追踪）；`SummonRegistry` 生命周期（波清/进层/局末）；`SummonExecutor` 生成；`RewardLoadValidator` 扩展 trigger 白名单；武器 `use_ability.consume`；示例 YAML（field_ration / wolf_whistle / fire_orb + rewards 样例）。
- **原因**：spec rev.2 审查通过，需 STAT 被动与魔法武器共用 Effect 管线，且召唤物需友伤/自激/清理策略。
- **决策**：validate 与 Registry 解耦（单测不依赖 Bukkit Registry）；战斗 SUMMON 包 `MagicDamageContext`；`friendly_fire` 默认 false；进层全局清召唤物；consume 仅 success 时扣物。
- **遗留**：EVOKER_FANGS 等实体需服内实测；MockBukkit 集成测未补。

---

## 2026-07-28 — 可升级上限配置化 + 职业 bundle grants

- **做了什么**：`UpgradeLevelCaps` 三级解析（`option.upgrade_max` → 池 `upgrade_level_cap` → `config.yml` `rewards.upgrade_level_cap_default`）；`RewardOption.grants[]` 组合奖励（武器+STAT 等）；`RewardService.applyBundle`；`RewardOptionIcons` GUI 预览；`act1_weak` cap=2、`act1_strong` cap=3；`class_vanguard` 示例 bundle。
- **原因**：原 `UPGRADE_LEVEL` 硬编码 max=4，无法按层/阶段限制（如 Act1 弱波生命恢复仅 II）；职业需一次选项含多奖励。
- **决策**：抽池可见性与 `applyStat` 均读**当前池** cap，跨池可继续升级；bundle 仅父 id 进 `acquiredUnique`；grant id 默认 `{parentId}_g{n}`。
- **遗留**：act2/act3 池 cap 待内容作者补；嵌套 bundle 不支持。

---

## 2026-07-27 — BUFF_AREA 统一魔法 Buff

- **做了什么**：`Effect.BUFF_AREA` 共享核心（武器 `use_ability` + STAT 战斗触发）；`PotionMerge` / `BuffAreaTargets` / `TriggerContext`；`EffectListener` 改为单玩家战斗触发 + `ON_DAMAGE_TAKEN` 仅敌人攻击；样本 `maggoteers:field_shield` + `a1s_hit_poison` / `a1s_absorb_on_hit`。
- **原因**：Spec `2026-07-27-buff-area-unified-design.md` v1.2——配置挂状态、命中上毒、受击吸收，与现有魔法 FX 体系对齐。
- **决策**：武器 caster FX 只在 handler 播一次；dead entity 跳过药水但允许 mark；`ON_KILL`/`ON_DAMAGE_DEALT` 全队被动修正为仅行动玩家（如 lifesteal）。
- **遗留**：弓/三叉戟 `ON_DAMAGE_DEALT` 延后；永久 `ADD_POTION` 与同类型 BUFF 冲突靠作者 convention。

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
