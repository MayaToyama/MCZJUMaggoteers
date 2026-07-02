# The Maggoteers（卫戍协议）— 设计文档

- **日期**：2026-07-02
- **状态**：设计已与需求方逐段确认，待实现
- **权威参考**：操作契约、配置 schema、扩展点见仓库根 `CLAUDE.md`（每 session 自动加载）。本文是**设计叙事与决策取舍**的完整记录。

---

## 1. 目标与愿景

在 Paper 1.21.7 上做一个 **4 人合作 PvE · 波数防守 + Roguelike** 小游戏，作为 `MCZJUGameCore` (MGC) 的子插件：

- 玩家依次通关 **3 个阶段（Act 1/2/3）**，每阶段清掉 `M+N+1` 波（弱怪×M、强怪×N、Boss×1）。
- 波间 **休整期** 可花货币做 **3 选 1 随机强化**（属性/武器/补给）。
- 局外用 **账户货币** 在商店 **解锁** 更多局内奖励项，解锁后并入个人随机池。
- 怪物数值与掉落随**人数**缩放。
- **一切内容（波次、地图、奖励、词缀、缩放）由配置文件驱动**——核心诉求是"便于管理"。
- 本项目后续**可能由其他 AI agent 接手**，故需留下详尽的 plan / 日志 / 注释。

## 2. 非目标（v1 不做）

- 不引入 WorldEdit / MythicMobs / InfernalMobs（零额外依赖；精英怪=自实现配置词缀层）。
- 不做插件内 HTTP/网络服务（"对外接口"= 局内商店 + 持久化，非网络）。
- 不做魔法武器框架（旋转刀片等）——v1 只做 ItemCreator 能直接配置生成的武器；魔法武器留作"PDC+插件监听器"模式的文档化扩展路径。
- 不支持中途加入（人数缩放开局锁定）。

---

## 3. 架构决策与取舍

| 决策 | 选择 | 备选 / 为何这么选 |
|---|---|---|
| 接入 MGC | 直接继承 `AbstractGame` + `DefaultGameWaitStrategy(this,4,min)` | 4 人合作不属于 `SinglePlayerGame`/`OpenSessionGame` 预设 |
| 房间类 | `JsonGameRoom` 极简（每局自建世界，房间字段用不上） | — |
| 持久化 | MGC `JsonPlayerData`（每玩家 JSON + 自动落盘） | 自建 YAML/SQLite = 重造轮子 |
| 波次时序引擎 | 单一可暂停 `runTaskTimer` 状态机 | 链式 `runTaskLater`：难暂停/难统一取消 |
| 精英怪 | 自实现"配置词缀层"挂原版实体 | InfernalMobs API 不可控、违背纯配置诉求 |
| 世界 | 每局新建虚空世界，局末卸载+删除 | 多房间共用一图无法并发；多世界实例天然支持并行 |
| 结构粘贴 | Paper `Structure` API（`loadStructure(File)`） | 不依赖 WorldEdit；方法已被前代插件验证 |
| 构建工具 | Maven | 与 MGC/ItemCreator 一致、jitpack 友好 |
| 账户货币 | 自建（`balance`+`totalEarned`，载体 MGC `JsonPlayerData`） | `ScoreManager`/`HistoryScoreManager` 是空壳；`JsonPlayerData` 从 1.0.4 起即有，设计基线 1.0.5 |
| 效果生命周期 | **事件到期**（trigger 驱动），PlayerState 为真相源 | 挂钟计时无法跨死亡复活存活 |
| 缩放锁定 | 开局快照 + 不允许中途加入 | 动态人数会让预生成失效、调试困难 |

### 3.1 两条"复用前代成果"的关键借鉴
- **`MCZJUvampireSurvivor`**（弃用半成品，`E:\Intellij_Idea\pluginTest\MCZJUvampireSurvivor`）：几乎是同游戏的前代原型。其 `WorldManager`（虚空世界 + 结构 NBT 粘贴 + 异步删目录）、`WaveManager`（`IdentityHashMap`+`UUID` 反查 + 5-tick 安全扫描防爆炸怪卡波 + 人数缩放数组）、`ScalingConfig`（按人数倍率数组）均**已验证**，直接借鉴扩展。
- **`MCZJUMagicItems`**（`E:\Intellij_Idea\pluginTest\MCZJUMagicItems-main`）：提供"触发(TriggerType/UseType/TriggerPolicy)→分发(BuffService)→效果(BuffType/BuffContext/BuffKeys)"的解耦范式与 PDC 物品识别模式。我们借其**范式**（typed key 参数、监听器统一分发、效果复用），不搬其具体游戏内容（专用 BuffType/ParamKeys）。

---

## 4. 对局生命周期

详见 `CLAUDE.md` §2.3。要点：

- `onGameInit`：**异步**启动 `RunPlanner` + 异步预读 NBT（⚠️ `createWorld()` 必须主线程，不能放进异步）。
- `onGameStart`：⚠️ 返回 `void`、不能阻塞等待异步结果 → 设"就绪门闩"（`runTaskTimer` 轮询世界+剧本就绪），**就绪后**再：懒粘贴 Act1 → 传送 → 初始化 `PlayerState` → `PoolBuilder` 合并解锁 → 启动 `WaveScheduler`。
- `onGameEnd/Abort/Cancel`：统一 `cleanup()`（清实体、卸载+删世界、profile 切回大厅）。结束对局调 `getGameManager().endGame(game)`。

**失败奖励**（需求方补充）：失败也按"已过层数×层权重 + 当前层已过波数×波权重"发少量账户货币。

---

## 5. 世界与地图

详见 `CLAUDE.md` §5。关键设计：

- **一局 = 一个新世界**（`maggoteers_<seedhex>`，虚空 `VoidGenerator`），三阶段全粘贴进**同一世界**、位于按层硬编码的不同原点；跨层=传送，不重建世界。⚠️ `createWorld()` 必须主线程（异步只做 NBT 预读/删目录）；`onGameStart` 不能阻塞，用"就绪门闩"轮询世界+剧本就绪后再传送/启动。
- **地图 = 4 个结构方块 NBT（NW/SW/NE/SE）拼接**；每层从 `maps/actN/` 随机抽 1 张。
- **懒粘贴**：进哪层粘哪层 4 象限，减开局卡顿。
- **坐标**：图内 `points.yml` 用相对坐标（`playerSpawn` + `spawnPoints{编号}`）；绝对坐标 = `act_origins[act] + 相对`，**绝不入配置**。
- **地图专属波次**：每图可有 `special_waves.yml`（引用 `waves.yml` 的 strategy id + 池 + 权重），抽到该图时并入对应池，且**权重更高**。
- **崩溃恢复**：`onEnable` 扫描清理 `maggoteers_*` 孤立世界。

---

## 6. RunPlanner（异步种子化预生成）

需求方强调：**波次池与奖励池的抽取要异步、种子化**，避免战斗中卡顿。

- 在 `onGameInit`/进层时于**非主线程**纯计算：抽图 → 并专属波 → roll 波次（加权，专属权重高）→ 展开 strategy 成 Wave（`steps×repeat`）→ 解析刷怪点绝对坐标 → 合成 `coeff×affix×scaling` 数值。
- 产出确定性 `RunPlan`，主线程战斗时**零随机、零池查询**。
- **奖励 3 选 1 不预 roll**：发生在休整期（非战斗），按需现抽，开销可忽略。

---

## 7. 波次 / 池子引擎

详见 `CLAUDE.md` §7。

- **池**：每层 `weak/strong/boss`，加权随机；地图专属波次并入且权重更高。
- **刷怪策略**：`steps[]{point,type,count,coeff,affixes,delaySec} + repeat + clearReward`。`delaySec` 表达波内时序（"先 5 僵尸，隔 5s 再 5 骷髅"）。
- **WaveScheduler**：单可暂停状态机 `SPAWNING→ACTIVE→CLEARED→REST→…`，休整期天然暂停，跳过/失败易清理。
- **WaveEngine**：借鉴 VampireSurvivor（`UUID` 反查 + 5-tick 安全扫描防爆炸怪卡波 + BossBar）。
- **每波都发通关奖励**（货币/补给，发到每人）。

---

## 8. 货币 / 休整 / 奖励

- **三种货币**：普通货币（普通波掉落，兑普通池）、Boss 货币（Boss 波掉落，兑 Boss 池）= ItemCreator 物品 + PDC；账户货币（`balance`/`totalEarned`，持久化）。货币每人独立，可"攒钱战未来 / 为队友兜底"。
- **升级菜单**（单 GUI）：`[普通奖励][Boss奖励][跳过休整][延长休整]`；可反复 roll、自选池；右键货币=快捷打开。
- **池选择规则**（需求方精化）：
  - 普通池：弱怪波→`actN_weak`；强怪波/Boss 波→`actN_strong`。
  - Boss 池：**粘滞** = 最近击杀 Boss 所在层的 `actN_boss`；新层未杀 Boss 前仍用上一层。
- **跳过/延长 = 全员（冒险模式玩家）投票同意**。
- **奖励项可自定义 `icon`**（原生物品图标）。
- **奖励三类**：属性强化（存 PlayerState）、武器（ItemCreator）、补给（ItemCreator 消耗品）。
- **开局职业选择**（CLAUDE.md §9.4）：玩家进图无装备；先发**全员初始装备**（`config.yml` `initial_equipment`，ItemCreator id 列表），再从**职业池**（`reward_pools.class`，`cost: 0` 免费）随机 3 选 1——**复用奖励池 + 解锁系统**（`requires_unlock`/`unique` 可见性过滤原样套用）；全员选完（倒计时兜底）后开局。加职业 = 配置加 option，零代码。

---

## 9. 效果系统（核心）

详见 `CLAUDE.md` §10。设计要点与决策：

- **触发→效果解耦**：少量监听器（onKill/onDamageDealt/...）统一分发到 `EffectService`；一个监听器服务多种被动，一个 Effect 被多途径复用（heal 用于嗜血/治疗药剂/群抬）。
- **触发目录（v1 全实现）**：生命周期（`ON_WAVE_CLEAR/ON_ACT_ENTER/ON_GAME_END`）+ 战斗（`ON_KILL/ON_DAMAGE_DEALT/ON_DAMAGE_TAKEN`）+ 玩家状态（`ON_REVIVE/ON_DEATH/ON_TICK_1S`）+ 物品交互（`ON_INTERACT`+UseType，驱动 PDC 物品 GUI）。
- **Effect 目录（5 种）**：`ADD_ATTRIBUTE`（`PERCENT|FLAT`）、`ADD_POTION`、`HEAL`、`DAMAGE_AREA`、`GRANT_REVIVE`。净化首选 `ADD_POTION`（0 秒 255 级抵消）技巧，**实现期实测**，不成立则 fallback 走 damage-cancel。
- **生命周期 = 事件到期，非挂钟计时**（需求方关键修正）：
  - 限时道具限的不是"实际时间"，而是"波次结束/层级结束/下一次攻击/下一次复活"等触发条件。
  - 死亡复活后效果须继续生效 → 不能靠原版药水 infinite 时长。
  - → `PlayerEffect{fireTrigger, expiry{trigger,charges}, recurring, stack}`；PlayerState 为真相源，复活后 `resync` 重施加。**删除前代 `BuffInstance` 的实时计时**。
- **CD 与堆叠**（需求方追问后补齐）：
  - 道具 CD = 物品门禁（`cooldown_sec`，走**按 PDC id 的时间戳表**，不用 `setCooldown(Material)` 以免同材质串 CD）；有限时长药水走原版 `duration_ticks`。
  - 周期 buff = `recurring{interval_sec, spawn}`（由 `ON_TICK_1S` 累计）。
  - 堆叠/升级 = `stack: ADD | UPGRADE_LEVEL{max} | REFRESH | REPLACE | IGNORE`（如抗性 I→II→III）。
- **唯一被动**：选项 `unique: true` → 选一次后从该玩家本局池移除（`acquiredUnique` 集，本局作用域）。
- **物品统一路由**：`ItemInteractRouter` 读 PDC `maggoteers:id` 分发（复活币→复活菜单；货币→升级菜单；未来武器→其触发）。

---

## 10. 死亡 / 复活

- `reviveCount`（默认 2）= 剩余自动复活次数；死亡时 `>0` 则 `--` 复活（含 `1→0` 仍复活），`==0` 再死转观察者。
- 复活币（仅 Boss 池）：右键开**玩家头像 GUI**——对死者=仅复活（不加次数），对活者=`+1` 次数。
- 失败 = 场上无冒险模式玩家 → `endGame`。

---

## 11. 局外：商店 / 解锁 / 持久化 / 排行榜

- `MaggoteersPlayerData{balance, totalEarned, unlocks}`（JsonPlayerData）。
- 商店商品 = 所有 `requires_unlock:true` 选项（自动派生）；花 `balance` 买 → 写 `unlocks`。
- 开局 `PoolBuilder` 把每玩家 `unlocks` 并入其个人池；3 选 1 按需抽，过滤 `requires_unlock`/`unique`。
- **排行榜积分 = `totalEarned`**（只增不减）→ 免单独计分系统；通关时间可另注册一榜。
- 结算：胜利=`win_flat`；失败=进度折算。

---

## 12. 配置文件布局

详见 `CLAUDE.md` §5.2、§7、§12、§13。汇总：

```
plugins/Maggoteers/
  config.yml            # 缩放/每层M,N/命数/休整/结算/层原点/等待/清理
  affixes.yml           # 词缀
  waves.yml             # 池(pools) + 刷怪策略(strategies)
  rewards.yml           # 奖励池 + 选项(含 requires_unlock/unlock_cost/unique/stack/icon)
  items/*.yml           # ItemCreator 物品（parseYamlToItems 自解析）；含货币/复活币(PDC)
  maps/actN/<mapId>/    # 4 nbt + points.yml + special_waves.yml
# 持久化由 MGC 管理（不在本插件目录）：plugins/MCZJUGameCore/player_data/maggoteers/<uuid>.json
```

**三条不变量**（`CLAUDE.md` §0）：绝对坐标不入配置；定义唯一处；内容增删零代码。

---

## 13. 交付物与测试

- 文档：`CLAUDE.md`（圣经）+ 本 spec + `docs/dev-log.md`（决策日志）+ `docs/plan/`（实现计划）+ Javadoc/`package-info`。
- 单元测试（JUnit5，无需开服）：把"纯计算"与 Bukkit 隔离——池加权 roll（种子确定性）、缩放、`coeff×affix×scaling`、3 选 1 抽样、`unique/已选/解锁` 过滤、`stack` 合并、RunPlan 构建。
- 配置校验：启动/reload 失败快，引用不可达精确报错。
- 调试命令：`/maggoteers debug state|plan|wave|act|give|coin`。
- 手测清单：`docs/qa-checklist.md`。

---

## 14. 遗留 / 待实现期核实

- ItemCreator 的 jitpack 版本号。
- `parseYamlToItems` 对全部物品特性（与 ItemCreator 原 items/ 加载路径）的等价性——实现期用一个复杂武器验证。
- 各 YAML schema 的最终字段名以 `CLAUDE.md` 为准，实现时若调整须同步本文与 CLAUDE.md。
