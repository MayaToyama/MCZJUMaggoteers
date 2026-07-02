# CLAUDE.md — The Maggoteers（卫戍协议）小游戏插件

> 本文是本项目的**项目圣经**。任何 AI agent 接手开发前，**必须先通读本文**，再读 `docs/dev-log.md`（决策脉络），再看 `docs/plan/`（当前阶段任务）。
> 设计的完整叙事与取舍见 `docs/spec/2026-07-02-maggoteers-design.md`。

---

## 0. 一页速览（给赶时间的 agent）

- **是什么**：Minecraft Paper 1.21.7 上的 PvE 小游戏插件——**波数防守 + Roguelike**。4 人合作，三阶段（Act 1/2/3，类杀戮尖塔三层）依次清波打 Boss，波间 3 选 1 随机强化。
- **身份**：游戏内中文名 **卫戍协议**；项目英文名 **The Maggoteers**；MGC 游戏 id `maggoteers`；主包 `io.mczju.maggoteers`；主类 `MaggoteersPlugin`。
- **定位**：是 **MCZJUGameCore (MGC)** 的子插件。大厅/房间/组队/玩家档案/排行榜由 MGC 管；我们只写"游戏玩法"。局内物品由 **MCZJUItemCreator** 生成。
- **构建**：Maven + JDK 21。
- **三条铁律（不要违反）**：
  1. **绝对坐标永不入配置**——图内用相对坐标，层原点只写在 `config.yml`，进层时 `原点 + 相对` 现算。
  2. **定义唯一处**——刷怪策略只在 `waves.yml`、奖励项只在 `rewards.yml`，被各处引用；改一处即全局生效。
  3. **内容增删零代码**——加地图/波次/词缀/奖励/武器 = 加配置条目；只有"新增触发类型 / 新增 Effect 类型"才需要写 Java（有清晰扩展点，见 §14）。

---

## 1. 项目概览

### 1.1 核心玩法循环
玩家经 MGC 大厅加入 → 满员开局 → 为本局生成一个专属虚空世界 + 一份确定性的"剧本（RunPlan）"→ 玩家在 Act 1 清掉 `M+N+1` 波（弱怪×M、强怪×N、Boss×1）→ 波间休整 30s，可花货币开 3 选 1 强化 → 击杀 Act 1 Boss 传送到 Act 2 → … → 击杀 Act 3 Boss 通关结算。任意时刻**全员观察者**则失败结算。通关/失败都给**账户货币**（持久化），用于局外商店**解锁**更多局内奖励项。

### 1.2 技术栈
| 项 | 值 |
|---|---|
| 服务端 | Paper 1.21.7 |
| Java | 21 |
| 构建 | Maven（`paper-api` / `MCZJUGameCore` 为 provided，不打进 jar） |
| 硬依赖 | `MCZJUGameCore`（`depend`） |
| 软依赖 | `MCZJUItemCreator`（`softdepend`；缺失时仅警告，不崩） |
| 其他 | 无（**不引入** WorldEdit / MythicMobs / InfernalMobs） |

> ⚠️ **版本基线**：本设计以 **MGC GitHub `1.0.5`** 为准（`docs/dev-guide.md` + `docs/dev-advanced.md`）。`JsonPlayerData`/`getPlayerDataManager()`/`getData()`/`PlayerDataLeaderboard` **从 1.0.4 起即存在**（测试服 jar=1.0.4 已含）；建议测试服 **1.0.4 → 1.0.5 对齐设计**（**不是**"缺 API 编译不过"）。⚠️ 本地 `MCZJUGameCore-main` clone 是 **1.0.0（过时：无 PlayerData、Menu API 也不同）**——核对任何 API 必须看 GitHub 1.0.5，勿用本地 clone。详见 dev-log 2026-07-02 复核条。

### 1.3 依赖坐标（pom.xml）
```xml
<dependency>
  <groupId>io.papermc.paper</groupId>
  <artifactId>paper-api</artifactId>
  <version>1.21.7-R0.1-SNAPSHOT</version>
  <scope>provided</scope>
</dependency>
<dependency>
  <groupId>com.github.mczju-ops</groupId>
  <artifactId>MCZJUGameCore</artifactId>
  <version>1.0.5</version>
  <scope>provided</scope>
</dependency>
<!-- MCZJUItemCreator：jitpack 引入（同组织 com.github.mczju-ops，已被前代 VampireSurvivor/build.gradle 验证）；版本号以该仓库 pom 为准 -->
```
仓库：`https://repo.papermc.io/repository/maven-public/` 与 `https://jitpack.io`。

---

## 2. 接入 MCZJUGameCore (MGC)

> MGC 文档：`https://github.com/mczju-ops/MCZJUGameCore` 的 `docs/dev-guide.md`、`docs/dev-advanced.md`。Paper javadoc：`https://jd.papermc.io/paper/1.21.7/`。

### 2.1 必须的两个类
- **`MaggoteersGame extends AbstractGame`**（4 人合作 → 直接继承 `AbstractGame`，不继承 `SinglePlayerGame`/`OpenSessionGame`）。
  - `getId()` → `"maggoteers"`
  - `getGameMeta()` → displayName `<gold>卫戍协议`、icon、author、description
  - `getGameWaitStrategy()` → `new DefaultGameWaitStrategy(this, 4, min)`（至多 4 人；`min` 见 `config.yml`）
  - **不实现 `MidGameJoinable`**（不允许中途加入；人数缩放开局锁定）
  - 重写生命周期 hook（见 §2.3）
- **`MaggoteersRoom extends JsonGameRoom`**：每局自建世界，房间字段几乎不用，留空占位即可（MGC 要求"必须有房间类"）。
- > ⚠️ **房间实例是 join 前置（G1）**：`registerGame` 只注册类；MGC 还会从 `plugins/MCZJUGameCore/rooms/maggoteers/*.json` 加载**房间实例**（`JsonGameRoom.getFilePath()`）。`/mgc join maggoteers` → `createGame` → `getRandomLeisureGameRoom`，**无 READY 房间则返回 null**（提示"无空闲房间"）。**故 `onEnable` 里先（若缺失）写出默认 `rooms/maggoteers/default.json`，再 `registerGame`**（MGC 此时才会加载到它）；或运维 `/mgcop room create maggoteers default`。room 只是 MGC 调度壳、**局末置回 READY 可复用**；**每局仍自建独立虚空世界**，与 room 无关。

### 2.2 注册（`onEnable`）
```java
MCZJUGameCore.getGameManager().registerGame(MaggoteersGame.class, MaggoteersRoom.class);
MCZJUGameCore.getPlayerDataManager().registerPlayerData("maggoteers", MaggoteersPlayerData.class);
MenuFacade.registerMenu("maggoteers_shop", ShopMenu.class);   // 供 /menu 或 NPC 打开商店
// + 各 Config 加载、Listener 注册、PluginCommand 注册
```
> **Menu 子类写法（1.0.5，E4）**：`public ShopMenu(Player player, Object... args) { super(player, args); }`，重写 `getTitle()/getRows()(1~6)/getPermission()/setup()`（`setup()` 内 `setSlot(slot, item[, action])`）。`registerMenu` 反射要求子类有 `(Player, Object[])` 构造器。⚠️ **勿照抄**本地 1.0.0 `guide.md` 的 `super(XxxMenu.class, player)` 写法。

### 2.3 生命周期 hook
| Hook | 我们做什么 |
|---|---|
| `onGameInit()` | 第一个玩家加入等待时：**异步**启动 `RunPlanner`（生成剧本）+ **异步预读 NBT**。⚠️ **不在这里建世界**——`WorldCreator.createWorld()` 必须主线程，建世界的调度放在 `onGameStart` 的就绪门闩内（见下）。返回 true。 |
| `onGameStart()` | ⚠️ **此方法返回 `void`、不能阻塞**（MGC 调完立即置 `RUNNING`）。故这里**只启动一个"就绪门闩"** `runTaskTimer`：轮询 `RunPlan` 是否就绪（异步产物）；**一旦就绪，由该门闩任务在主线程执行** `WorldService.createWorld()` + 粘贴 Act 1 的 4 象限 → 传送玩家到 Act 1 `playerSpawn` → 初始化各 `PlayerState`（`reviveCount=config.lives.default`、冒险模式）→ 发初始物资 → `PoolBuilder` 合并解锁 → 启动 `WaveScheduler`。门闩未就绪期间玩家暂留等待点。**建世界的主线程责任唯一落在 onGameStart 门闩内（G4），避免 init/start 双触发。** |
| `onGameEnd()` | 胜利：记通关时间榜 + 结算账户货币；失败：按进度结算（少额）。统一走 `cleanup()`。 |
| `onGameAbort()` | 运行中全员退出：直接 `cleanup()`。 |
| `onGameCancel()` | 等待阶段取消（未开局）：取消异步任务、删半成品世界。 |
> `cleanup()`：清世界内实体/掉落物 → `Bukkit.unloadWorld(w, false)` → 异步删世界目录 → 玩家 profile 切回大厅。
> **结束对局统一调** `MCZJUGameCore.getGameManager().endGame(game)`，不要直接调 `onGameEnd()`。

### 2.4 常用 MGC API
- `new PlayerExt(player)`：`isInGame(MaggoteersGame.class)`、`getGame()`、`getParty()`、`giveItem(id)`、`getData(MaggoteersPlayerData.class)`、`switchProfile(null)`、`resetState()`。
- `JsonPlayerData` 子类 `MaggoteersPlayerData`（§13.1）：改完务必 `setModified(true)`。
- `Sender`：`AbstractGame` 自带 `sender()` 向局内全员发消息。
- 排行榜：继承 `PlayerDataLeaderboard`，字段名 `totalEarned`（§13.5）。

---

## 3. 接入 MCZJUItemCreator

```java
ItemCreatorApi api = Bukkit.getServicesManager().load(ItemCreatorApi.class);
if (api == null) { log.warn("ItemCreator 未安装，物品将缺失"); }
api.hasItem(id); api.createItem(id); api.createItem(id, amount); api.parseYamlToItems(yaml);
```
- **物品配置位置**：`plugins/Maggoteers/items/*.yml`（本插件目录，配置集中）。启动时用 `parseYamlToItems` 解析缓存；运行时按 id 取。
- **货币/复活币/未来武器**都是 ItemCreator 物品 + PDC `maggoteers:id` 供本插件识别。
- > ⚠️ **发放规则（G2）**：`PlayerExt.giveItem(String id)` 走 **MGC ItemManager**（**不是** ItemCreator）。本插件物品发放一律 `ItemStack is = ItemService.createItem(id); player.giveItem(is);`（`giveItem(ItemStack)` 走背包，满则丢脚边）——**绝不**对 ItemCreator id 调 `giveItem(String)`。`clearReward`、3 选 1 武器/补给、货币掉落均遵守此规则。

---

## 4. 架构总览与模块划分

### 4.1 数据流（一次对局骨架）
```
玩家满员 → onGameStart
  → 异步准备：RunPlanner 种子化生成 RunPlan + 预读 NBT
  → 回主线程：WorldService.createWorld() + 粘贴 Act1（懒粘贴）
  → （就绪门闩）→ 传送 → 初始化 PlayerState → PoolBuilder 合并解锁
  → WaveScheduler 循环：
       WaveEngine 执行当波（刷怪策略时间轴：按 delaySec 分批刷）
       → 清空：发通关奖励 → 30s 休整（升级菜单：普通/Boss奖励/跳过/延长）
       → Boss 死 → 下一层（粘贴 Act2，传送）… → Act3 Boss 死 → 胜利结算
  → （任意点全员观察者）→ 失败结算
  → cleanup()：卸载+删世界
```

### 4.2 子系统（包 `io.mczju.maggoteers.*`）
| 模块 | 职责 |
|---|---|
| `MaggoteersPlugin` | 主类；注册 + 生命周期总调度 |
| `game/` | `MaggoteersGame`、`MaggoteersRoom`、`MaggoteersDeathStrategy` |
| `world/` | `WorldService`（建/卸/删/清理孤立）、`MapRepository`（读图）、`StructurePaster`（粘贴 4 象限 NBT）、`VoidGenerator` |
| `plan/` | `RunPlanner`（异步种子化）、`RunPlan`、`SeededRng` |
| `wave/` | `WaveEngine`、`WaveScheduler`（单可暂停状态机）、`WaveRuntime`、模型 `WaveSpec/SpawnStep` |
| `mob/` | `MobFactory`（原版实体+系数+词缀+装备）、`AffixService` |
| `reward/` | `RewardService`（3 选 1 抽取/扣费/应用）、`PickMenu`、模型 `RewardPool/RewardOption` |
| `currency/` | `CurrencyService`（普通/Boss 货币 PDC 识别 + 掉落） |
| `shop/` | `ShopMenu`（局外商店） |
| `state/` | `PlayerState`（本局真相源）、`PlayerStateManager`、`EffectService`（apply/resync/expire） |
| `effect/` | `Trigger`、`Effect`、`PlayerEffect`、`EffectContext`、`EffectKey`、`ItemInteractRouter` |
| `unlock/` | `UnlockRegistry`、`PoolBuilder`（开局把解锁并入每玩家个人池） |
| `item/` | `ItemService`（ItemCreator 接入 + 本插件 parseYamlToItems 缓存）、`PdcKeys` |
| `config/` | `ConfigManager` + 各 config POJO + 校验 |
| `command/` | Brigadier `/maggoteers ...` |
| `util/` | 位置计算、日志、文件 |

---

## 5. 世界与地图系统

### 5.1 世界生命周期（`WorldService`，复用并扩展 `MCZJUvampireSurvivor/WorldManager` 已验证实现）
- 世界名 `maggoteers_<8位hex>`（hex 来自 seed）。
- `new WorldCreator(name).environment(NORMAL).generator(new VoidGenerator()).createWorld()`——**必须在主线程调用**（Bukkit 限制；前代 VampireSurvivor 也是同步建世界，仅删目录走异步）。
- `VoidGenerator`：所有 `shouldGenerate*()` 返回 false、`generateSurface` 空实现 → 纯虚空。
- GameRule：`DO_DAYLIGHT_CYCLE=false`、`DO_WEATHER_CYCLE=false`、`NATURAL_REGENERATION=false`、`MOB_GRIEFING=false`、`DO_MOB_SPAWNING=false`；`Difficulty.HARD`。
- **结构粘贴（已验证可行）**：
  ```java
  Structure s = Bukkit.getStructureManager().loadStructure(nbtFile); // 直接吃 java.io.File
  s.place(world, new BlockVector(x,y,z), true, StructureRotation.NONE, Mirror.NONE, 0, 1.0f, new Random());
  ```
- **懒粘贴减卡顿**：开局只粘 Act 1 的 4 个象限；进 Act 2/3 时再粘该层 4 个。
- **层原点**（`config.yml` `act_origins`）：如 Act1=(0,64,0)、Act2=(1024,64,0)、Act3=(2048,64,0)；4 象限 NBT 在原点基础上按固定 NW/SW/NE/SE 偏移。
- **销毁**：传送玩家回主世界 → `unloadWorld(false)` → **异步**递归删目录。
- **孤立世界清理（开机）**：`onEnable` 扫描 `Bukkit.getWorlds()` + 世界容器目录，凡 `maggoteers_*` 一律卸载删除（防崩服残留）。

### 5.2 地图仓库（`MapRepository`）
```
plugins/Maggoteers/maps/
  act1/<mapId>/  例如 ruined_keep/
      nw.nbt sw.nbt ne.nbt se.nbt      # 结构方块导出的 4 象限
      points.yml                        # playerSpawn + spawnPoints{编号→相对坐标}
      special_waves.yml                 # 本图专属波（引用 waves.yml 的 strategy id + 池 + weight）
  act2/...  act3/...
```
`points.yml`：
```yaml
playerSpawn: { x: 12.5, y: 65, z: 8.5 }
spawnPoints:
  1:   { x: 4,  y: 65, z: 4 }
  9:   { x: -8, y: 65, z: 10 }
  boss: { x: 0,  y: 65, z: 0 }      # Boss 刷怪点（waves.yml 里 point: boss 引用它）
```
> 所有坐标相对该图原点；运行时绝对坐标 = `act_origins[act] + 相对值`。**绝对坐标绝不出现在配置里。**
> **刷怪点校验（G3）**：`waves.yml` 里 `steps[].point` 用到的每个编号（含 `boss`）**必须**在该图 `points.yml` 有定义；`RunPlanner` 解析时校验，缺失则启动/reload 报错指到具体 strategy。

### 5.3 抽图
`RunPlanner` 每层从 `maps/actN/` 随机抽 1 张 → 读 `points.yml` + `special_waves.yml` → 把专属波次并入该层对应池。

---

## 6. RunPlanner（异步、种子化、纯计算）

- **时机**：`onGameInit`（等待阶段）/ 进入新层时；在**非主线程**跑（不碰 Bukkit API，只读配置算数据）。
- **输入**：`seed`（= 世界名 hex）、`playerCount`（开局快照锁定，**不随中途变化**）。
- **产出 `RunPlan`**（确定性、可重放/调试）：
```
RunPlan { seed, playerCount, scalingSnapshot,
  acts: [ { mapId, spawnPointAbs{id→{x,y,z}},        // 纯坐标：异步线程不持有 World（E8）；主线程绑定 World 时再构造 Location
           waves:[ Wave{ steps:[Step…已解析绝对坐标&缩放后数值], clearReward } … ],  // M+N+1 波
           bossPoolAct } … ×3 ] }
```
- **流程/层**：抽 1 图 → 并入地图专属波次 → weak roll M、strong roll N、boss roll 1（**加权随机**，地图专属波次权重更高）→ 每条 strategy 展开成 `Wave`（`steps × repeat`）→ 刷怪点编号解析成绝对坐标 → 数值叠 `coeff × affix × scaling`。
- **主线程只消费 RunPlan**：战斗中零随机、零池查询。
> **奖励 3 选 1 不在 RunPlanner 预 roll**——它发生在休整期（非战斗），开销极小，玩家点按钮时按需现抽。

---

## 7. 波次 / 池子引擎

### 7.1 配置侧模型
- `Affix`（词缀，强化层）：`id, hp×, dmg×, speed×, drop×, potions[], on, display`。定义在 `affixes.yml`。
- `SpawnStep`：`point(id), type, count, coeff{hp,dmg,speed}, affixes[], delaySec`（`delaySec=0` 与上步同刷）。
- `SpawnStrategy`（池内一条）：`id, steps[], repeat, clearReward[]`。roll 出来 = 一波。
- `WavePool`：每层 `weak[]/strong[]/boss[]`，每条 `{strategy, weight}`。
- 怪物强度 = **`coeff(波次内) × affix(词缀) × scaling(人数)`** 三层叠加。

### 7.2 WaveEngine（执行，主线程，借鉴 `MCZJUvampireSurvivor/WaveManager`）
- `IdentityHashMap<MaggoteersGame, WaveRuntime>` + `UUID→WaveRuntime` 死亡反查 + `livingMobs`/`mobEntries`。
- `spawnStep`：`spawnEntity` → 套 `coeff×affix×scaling` 的 HP/伤害/速度 → 给药水 → 装备（drop 率 0）→ 可选 BossBar → 记入追踪。
- `handleMobDeath`：移除追踪 → 按 `drop×` 掉落货币/物品 → `livingMobs` 空则**发本波通关奖励** → 进入休整。
- **5-tick 安全扫描**：把已消失/爆炸的实体强制计入击杀（防苦力怕等卡波）；刷新 BossBar——**沿用前代已验证逻辑**。

### 7.3 WaveScheduler（单一可暂停状态机任务）
一个 `runTaskTimer` 心跳驱动相位机（暂停=取消任务保留游标；恢复=重建）：
```
SPAWNING ──(按 delaySec 推进游标, 到点的 step 立即刷)──► ACTIVE
ACTIVE   ──(livingMobs==0)──► CLEARED(发通关奖励) ──► REST(30s, 升级菜单; 全员同意可跳过/延长)
REST     ──(到时/跳过)──► 下一波 | 本层 Boss 死 ► 进下一层 | Act3 Boss 死 ► VICTORY
```
- **跨层**：本层 Boss 清除 → `WorldService` 粘贴下一层 4 象限 → 全员传送新 `playerSpawn` → scheduler 从该层 wave 0 继续。

### 7.4 waves.yml 草案
```yaml
pools:
  act1:
    weak:   [ {strategy: w_zombie_swarm, weight: 3} ]
    strong: [ {strategy: s_creeper_rush, weight: 2} ]
    boss:   [ {strategy: b_iron_golem_guard, weight: 1} ]
strategies:
  w_zombie_swarm:
    repeat: 2
    clearReward: [ {item: currency_normal, amount: 2} ]   # 每人发
    steps:
      - { point: 1, type: ZOMBIE, count: 5, coeff: {hp: 1.0}, affixes: [], delay: 0 }
      - { point: 9, type: ZOMBIE, count: 5, coeff: {hp: 1.0}, delay: 5 }   # 5s 后（"先5僵尸,隔5s再5僵尸"）
  b_iron_golem_guard:
    steps:
      - { point: boss, type: IRON_GOLEM, count: 1, coeff: {hp: 8.0, dmg: 1.5}, affixes: [armored], delay: 0 }
```

### 7.5 affixes.yml
```yaml
affixes:
  armored: { hp: 2.0, display: "<aqua>装甲" }
  berserk: { dmg: 1.5, speed: 1.2, display: "<red>狂暴" }
  toxic:   { potions: [{type: POISON, amp: 1, dur: 200}], on: hit-player, display: "<green>毒素" }
  greedy:  { drop: 1.5, display: "<gold>贪婪" }
```

### 7.6 config.yml（缩放 / 每层波数 / 全局）
```yaml
scaling:
  mob_hp:        [1.0, 1.3, 1.6, 2.0]   # 1–4 人
  mob_damage:    [1.0, 1.15, 1.3, 1.5]
  mob_count:     [1.0, 1.0, 1.2, 1.5]   # 向下取整 + 概率补 1；⚠️ 补 1 的随机必须走 RunPlanner 种子 RNG（D6，保确定性/可重放）
  currency_drop: [1.0, 1.4, 1.8, 2.2]
waves_per_act:
  act1: { weak: 3, strong: 2 }          # M, N → 每层 M+N+1 波
  act2: { weak: 4, strong: 3 }
  act3: { weak: 4, strong: 3 }
lives: { default: 2 }                   # 复活次数
rest: { duration_sec: 30, extend_sec: 15, extend_max: 2 }
settlement:
  win_flat: 100
  fail_per_act: 25
  fail_per_wave: 5
act_origins: { act1: [0,64,0], act2: [1024,64,0], act3: [2048,64,0] }
wait: { min_players: 1 }                # DefaultGameWaitStrategy(this, 4, min)
world: { cleanup_orphans_on_enable: true }
```

---

## 8. 货币体系

| 货币 | 形态 | 来源 | 用途 | 生命周期 |
|---|---|---|---|---|
| **普通货币** | ItemCreator 物品 + PDC `maggoteers:currency_normal` | 普通波掉落 | 3 选 1 普通池 | 每局重置、每人独立 |
| **Boss 货币** | ItemCreator 物品 + PDC `maggoteers:currency_boss` | Boss 波掉落 | 3 选 1 Boss 池 | 每局重置、每人独立 |
| **账户货币** | `MaggoteersPlayerData.balance`（整数） | 通关/失败结算 | 局外商店解锁 | **持久化**跨局 |

> 货币作为物品在局内 profile 背包流通；数量识别靠 PDC + `amount`，不靠堆叠。

---

## 9. 休整 / 升级菜单 / 3 选 1

### 9.1 升级菜单（单 GUI，MGC `Menu`）
按钮：`[普通奖励] [Boss奖励] [跳过休整] [延长休整]`（结构可扩展）。
- 点 `普通奖励`/`Boss奖励`：扣对应货币 → 弹 3 选 1（从对应池抽 **3 个不同**选项）→ 选 1 应用 → 回主菜单，**可反复**直到货币不足/休整结束。
- **右键手持货币** = 打开本菜单的快捷方式（与点按钮殊途同归）。

### 9.2 池选择规则（RunPlan 预定）
- **普通池**（看刚清的波类型）：弱怪波 → `actN_weak`；强怪波 / Boss 波 → `actN_strong`。
- **Boss 池**（**粘滞**）= 最近一次击杀 Boss 所在层的 `actN_boss`；新层未杀 Boss 前仍用上一层 Boss 池，杀掉本层 Boss 后才更新。

### 9.3 跳过 / 延长 = 投票
需**全员（冒险模式的玩家）同意**才生效；延长每次 `rest.extend_sec` 秒，上限 `rest.extend_max` 次。投票状态实时显示。

---

## 10. 效果系统（核心可扩展子系统）

> 范式来自 `MCZJUMagicItems`：**触发（监听器）→ 分发 → 效果**。一个监听器服务多种被动；一个 Effect 实现被多途径复用。
> **核心原则：PlayerState 是效果的"真相源"，Bukkit 属性/药水只是派生视图。**

### 10.1 触发目录（v1 全部实现）
| 类别 | Trigger |
|---|---|
| 生命周期 | `ON_WAVE_CLEAR` / `ON_ACT_ENTER` / `ON_GAME_END` |
| 战斗 | `ON_KILL` / `ON_DAMAGE_DEALT` / `ON_DAMAGE_TAKEN` |
| 玩家状态 | `ON_REVIVE` / `ON_DEATH` / `ON_TICK_1S` |
| 物品交互 | `ON_INTERACT`（含 UseType：右/左键 × 空气/方块/实体/攻击） |

### 10.2 Effect 目录（5 种，够用）
`ADD_ATTRIBUTE`（`op: PERCENT | FLAT`）、`ADD_POTION`、`HEAL`、`DAMAGE_AREA`、`GRANT_REVIVE`。
> **净化**：首选 `ADD_POTION` 技巧（免疫凋零 = 持续给 0 秒 255 级凋零抵消）；⚠️ **该技巧实现期须实测**（D1），若 MC 不认则 fallback = 监听 `EntityDamageEvent` 按 `DamageCause` cancel（作为 `ADD_POTION` 的免疫子能力实现）。

### 10.3 生命周期模型（**事件到期，非挂钟计时**）
```
PlayerEffect {
  effect: Effect
  params: EffectContext           // 类型化参数（EffectKey<T>，仿 BuffKey）
  fireTrigger: Trigger?           // null = 常驻(自动施加属性/药水); 非null = 命中触发时执行
  expiry: {trigger: Trigger, charges: int}?   // null = 整局; 命中 trigger 时 charges-- 到 0 移除
  recurring: {interval_sec: int, spawn: PlayerEffect}?  // 周期性生成子效果（由 ON_TICK_1S 累计）
  stack: ADD | UPGRADE_LEVEL{max} | REFRESH | REPLACE | IGNORE
  cooldown_sec: int?              // 仅物品交互门禁用
}
```
- **常驻型**（`fireTrigger=null`，如 +10% 伤害）：写进 PlayerState → 重算 Bukkit 属性/药水。⚠️ **`stack: ADD` 多层叠加时，每层 `AttributeModifier` 必须用唯一 `NamespacedKey`**（D5）——1.21 起同 key 重复添加会抛异常；按"效果 id + 层数"生成 key。
- **触发型**（`fireTrigger=ON_KILL`，如嗜血）：命中触发时执行 effect。
- **限时 = 事件到期**（不是定时器）：
  - "持续到波次结束" → `expiry={ON_WAVE_CLEAR, -1}`
  - "下次攻击 +50%" → `fireTrigger=ON_DAMAGE_DEALT` + `expiry={ON_DAMAGE_DEALT, 1}`（一次性）
  - "本层有效" → `expiry={ON_ACT_ENTER, -1}`
  - "每 5s 下次攻击 +50%" → `recurring={interval_sec:5, spawn: <上面那个一次性>}`
- **跨死亡复活存活**：效果存在 PlayerState（不依赖 Bukkit 实时药水）。玩家复活后 `EffectService.resync(player)` 把所有常驻型重新施加——死亡清掉的药水会被补回。✓
- **CD（物品门禁）**：物品配置 `cooldown_sec`；`ItemInteractRouter` 分发前查 CD → 在 CD 则拦截，否则执行并计 CD。⚠️ **不要用 `HumanEntity.setCooldown(Material,ticks)`**（D2）——粒度是材质，同材质不同 PDC 武器会串 CD；**默认走"按 PDC id 的时间戳表"**（`Map<UUID, Map<pdcId, expireMillis>>`）。**有限时长药水**（如 5s 力量）走原版药水 `duration_ticks`，瞬时、不存 PlayerState。
- **无实时计时**（除 `ON_TICK_1S` 本身）；过期靠各 trigger 触发时扫一遍到期项。

### 10.4 物品交互统一路由（`ItemInteractRouter`）
监听 `PlayerInteract*Event` → 读手持物品 PDC `maggoteers:id` → 分发到已注册处理器。一套机制管所有右键 PDC 物品：
| 物品 PDC id | 右键行为 |
|---|---|
| `revive_coin` | 开复活菜单（玩家头像 GUI） |
| `currency_normal` / `currency_boss` | 开升级菜单（快捷入口） |
| 未来魔法武器 | 触发其 `ON_INTERACT` 效果 |

---

## 11. 死亡 / 复活（`MaggoteersDeathStrategy extends AbstractPlayerDeathStrategy`）

- `reviveCount`（开局 = `config.lives.default`，默认 2）= **剩余可自动复活次数**。
- 玩家死亡：若 `reviveCount > 0` → `reviveCount--` 并复活（含 `1→0` 这次仍复活，回 `respawnLoc`、短暂无敌、维持冒险模式）；**若已为 0** → 转**观察者模式**（仍占座，可被复活币救）。
- **复活币**（仅 Boss 池产出；ItemCreator 物品 + PDC `maggoteers:revive_coin`）：右键 → 开**当局玩家头像 GUI**：对**死者**用 = 仅复活（不加次数）；对**活者**用 = `reviveCount +1`。
- **失败判定**：每次死亡/模式切换后检查——**场上无冒险模式玩家** → `getGameManager().endGame(game)`（走失败结算）。

---

## 12. 奖励项 schema（`rewards.yml`）

```yaml
reward_pools:
  act1_weak:
    currency: normal            # normal | boss
    cost: 3                     # 每次 3 选 1 消耗
    options:
      - { id: dmg10,   category: STAT, effect: ADD_ATTRIBUTE, params: {attr: ATTACK_DAMAGE, op: PERCENT, value: 0.10},
          stack: ADD, icon: IRON_SWORD, display: "<red>+10% 伤害" }
      - { id: lifesteal, category: STAT, trigger: ON_KILL, effect: HEAL, params: {amount: 2.0},
          icon: GOLDEN_APPLE, display: "<dark_red>嗜血：击杀回 2 血" }
      - { id: res_up, category: STAT, effect: ADD_POTION, params: {effect: RESISTANCE, amp: 0},
          stack: UPGRADE_LEVEL{max: 4}, icon: SHIELD, display: "<aqua>抗性(可升级)" }
      - { id: lone_wolf, category: STAT, effect: ADD_ATTRIBUTE, params: {attr: MOVEMENT_SPEED, op: FLAT, value: 0.02},
          unique: true, icon: LEATHER_BOOTS, display: "<gray>孤狼（唯一）" }
      - { id: wrench, category: WEAPON, item: maggoteers:wrench,
          requires_unlock: true, unlock_cost: 30, icon: CARROT_ON_A_STICK, display: "<gold>扳手" }
      - { id: heal_potion, category: SUPPLY, item: maggoteers:healing_potion, amount: 2,
          icon: POTION, display: "<red>治疗药剂×2" }
  act1_strong: { currency: normal, cost: 5, options: [...] }
  act1_boss:   { currency: boss,   cost: 1, options: [...] }
```

### 12.1 选项字段
| 字段 | 说明 |
|---|---|
| `id` | 稳定唯一 id（解锁/唯一判定用） |
| `category` | `STAT` / `WEAPON` / `SUPPLY` |
| `trigger` | 仅 STAT；省略=常驻；给值=触发型被动 |
| `effect` | STAT 用（见 §10.2） |
| `item` / `amount` | WEAPON/SUPPLY 用（ItemCreator id） |
| `params` | effect 的类型化参数 |
| `icon` | 原生物品图标（GUI 显示） |
| `display` | MiniMessage 显示文本 |
| `requires_unlock` / `unlock_cost` | true = 需局外商店解锁才能 roll 到；unlock_cost = 商店售价 |
| `unique` | true = 本局该玩家只能选一次，选后移出其池 |
| `stack` | 重复获得的合并策略（见 §10.3）。**触发型被动默认 `IGNORE`**（D7：重复拿到同一条被动不叠加/不刷新，除非显式声明 `ADD`/`REFRESH`） |

### 12.2 3 选 1 可见性判定
```
可选(player, option) =
    (!option.requires_unlock || player.持久.unlocks.contains(option.id))
 && (!option.unique         || !player.本局.acquiredUnique.contains(option.id))
```
> **不足 3 个可见选项时的 fallback（D3）**：按实际可见数开格，**不足位灰显空位**——不补凑、不重复；可见数为 0 则该池按钮禁用并提示。保证"见到的都是真选项"。

---

## 13. 局外：账户货币 / 商店 / 解锁 / 持久化

### 13.1 持久化载体
```java
public class MaggoteersPlayerData extends JsonPlayerData {
    public int balance = 0;            // 可花费账户货币
    public int totalEarned = 0;        // 累计获得（只增不减，= 排行榜积分）
    public List<String> unlocks = new ArrayList<>();  // 已解锁的 option.id
}
```
由 **MGC 统一管理**，落盘到 **`plugins/MCZJUGameCore/player_data/maggoteers/<uuid>.json`**（**不在本插件目录**；`JsonPlayerData.getFilePath()`，E1）。自动保存 **每 30 分钟**（`startAutoSave(20*60*30L)`）+ 玩家退出 `savePlayerDataAsync` + 关服 `saveAllPlayerData`（E2）。**改完务必 `setModified(true)`**，否则不落盘。

### 13.2 结算
- ⚠️ 胜利与失败**都走** `endGame`→`onGameEnd`，须在游戏状态里存**显式 `outcome`（`WIN`/`FAIL`）标志**（D4），`onGameEnd` 据此分支结算。
- 胜利：`grant = config.settlement.win_flat`。
- 失败：`grant = fail_per_act × 已过层数 + fail_per_wave × 当前层已过波数`。
- `balance += grant; totalEarned += grant; setModified(true)`。

### 13.3 商店（`ShopMenu`，局外）
- 商品 = 所有池里 `requires_unlock: true` 的选项（自动派生），显示 `unlock_cost` + 图标 + 锁定/已拥有。
- 点击锁定且付得起 → MGC `AlertMenu` 二次确认 → 扣 `balance` → `unlocks.add(id)` → `setModified(true)`。
- 入口：`/maggoteers shop`，并已 `MenuFacade.registerMenu("maggoteers_shop", ...)`（NPC/命令方块可开）。

### 13.4 解锁回流局内（`UnlockRegistry` + `PoolBuilder`）
- **开局**（`onGameStart`）：对每个玩家，把其 `unlocks` 对应的选项并入该玩家个人奖励池。
- 3 选 1 在休整期**按需现抽**（非战斗，开销极小）。

### 13.5 排行榜（E5）
```java
MCZJUGameCore.getLeaderboardManager()
    .registerLeaderboard("maggoteers_total", MaggoteersTotalLeaderboard.class);   // onEnable 里
```
子类 `extends PlayerDataLeaderboard`，实现 `getTitle() / getSubtitle() / getPlayerDataClass()→MaggoteersPlayerData.class / getFieldName()→"totalEarned"`（降序）。放置展示实体：`/mgcop leaderboard create|edit maggoteers_total <entityId>`（详见 `dev-advanced.md` §四）。通关时间榜同理另注册一个（可选）。

---

## 14. 扩展点清单（给接手者）

| 想加… | 怎么做 | 要写 Java 吗 |
|---|---|---|
| 新地图 | `maps/actN/<mapId>/` 加 4 nbt + points.yml（+ special_waves.yml） | 否 |
| 新波次 | `waves.yml` 加 strategy + 池引用 | 否 |
| 新词缀 | `affixes.yml` 加条目 | 否 |
| 新奖励（属性/武器/补给） | `rewards.yml` 加 option（武器/补给配 ItemCreator 物品） | 否 |
| 新触发类型 | `effect/Trigger` 枚举加值 + 对应监听器分发 | **是**（有模板） |
| 新 Effect 类型 | `effect/Effect` 枚举加值 + `EffectService` 实现 | **是**（有模板） |
| 新魔法武器（旋转刀片等） | ItemCreator 出带 PDC 物品 → 本插件写识别+触发类读 PDC（走 `ItemInteractRouter`） | **是**（见 §15 指南） |

---

## 15. 自定义交互物品开发指南（PDC 路由模式）

未来做"旋转刀片"等魔法武器，统一走这套模式（与复活币/货币一致）：
1. **ItemCreator 造物品**：在 `items/*.yml` 定义，带上 PDC `maggoteers:id = <weapon_id>`（必要时带原生 component 改动）。
2. **本插件写处理器**：实现一个挂在 `ItemInteractRouter` 上的 handler（按 `<weapon_id>` 注册），在 `PlayerInteract*Event` 时被调用；内部可触发 `PlayerEffect`（`fireTrigger=ON_INTERACT`）或直接执行逻辑。
3. **效果复用**：能复用 `EffectService` 的就别重写（HEAL/ADD_ATTRIBUTE/DAMAGE_AREA…）。
> 复杂武器（粒子/飞行物）可参考 `MCZJUMagicItems`（路径见 §17），但**不照搬其专用 BuffType/ParamKeys**，只借范式。

---

## 16. 构建与运行

- **构建**：`mvn package` → `target/Maggoteers-<ver>.jar`。
- **部署**：jar 放 `plugins/`；同目录需有 `MCZJUGameCore`、`MCZJUItemCreator` 两个 jar。
- **本地无 JDK 时**：Windows IntelliJ 构建，或 WSL 内安装 JDK21 + Maven 后构建。
- **资产**：首次启动释放 `plugins/Maggoteers/{items,maps}/...` 默认样例。
- **测试服**：`E:\MCpaper`（Windows）；WSL 下 `/mnt/e/MCpaper`。

### 16.1 首测前部署清单（plan 阶段 0 须覆盖）
- [ ] 测试服 MGC **1.0.4 → 1.0.5**（对齐设计基线；1.0.4 已含 PlayerData API，升级为稳妥）。
- [ ] `plugins/MCZJUGameCore/rooms/maggoteers/default.json` 存在（由本插件 `onEnable` 自动释放，或 `/mgcop room create maggoteers default`）——否则 `/mgc join maggoteers` 无房间（G1）。
- [ ] `plugins/Maggoteers/{items,maps}/...` 资产就位（首次启动释放默认样例）。
- [ ] `MCZJUItemCreator` 已装（否则物品缺失，仅警告不崩）。

---

## 17. 参考资源（接手时优先读这些）

| 资源 | 路径 | 用途 |
|---|---|---|
| MGC 源码/文档 | `https://github.com/mczju-ops/MCZJUGameCore` | 接入契约；`docs/dev-guide.md`、`dev-advanced.md` |
| ItemCreator | `https://github.com/mczju-ops/MCZJUItemCreator` | 物品 API |
| **前代原型 VampireSurvivor** | `E:\Intellij_Idea\pluginTest\MCZJUvampireSurvivor`（WSL `/mnt/e/...`） | **世界/虚空/结构粘贴/波次引擎/死亡策略/缩放 已验证实现，直接借鉴** |
| **效果范式 MagicItems** | `E:\Intellij_Idea\pluginTest\MCZJUMagicItems-main` | **触发→效果→PDC 范式；BuffContext/Key/Instance/Manager** |
| Paper javadoc | `https://jd.papermc.io/paper/1.21.7/` | Bukkit/Paper API |

---

## 18. 风险 / 已知坑

- **结构 NBT 粘贴**：已用 VampireSurvivor 的方法验证可行（`Bukkit.getStructureManager().loadStructure(File)` + VoidGenerator）。务必照搬。
- **崩服残留孤立世界**：`onEnable` 必须扫描清理 `maggoteers_*`。
- **爆炸怪卡波**：5-tick 安全扫描强制计入击杀（沿用 VampireSurvivor `WaveManager`）。
- **ItemCreator 版本**：jitpack 版本号接手时核实。
- **ItemCreator 物品来源**：默认读它自己 `items/`；我们用 `parseYamlToItems` 解析本插件目录下的物品。
- **PlayerData 改动忘 `setModified(true)`**：不会落盘——封装一层 setter 提醒。
- **MGC 版本**：设计基线 **1.0.5**；测试服 **1.0.4 已含** PlayerData API，建议升 1.0.5 对齐。⚠️ 本地 clone 是 **1.0.0（过时）**，核对 API 须看 GitHub 1.0.5（`dev-advanced.md` 在线版）。
- **净化技巧待实测（D1）**：`ADD_POTION` 0 秒 255 级抵消不一定成立；fallback 走 damage-cancel。
- **结构粘贴主线程掉帧（D8）**：4 象限粘贴主线程瞬时完成会掉几 tick；接受。已改为"进层时粘该层"（非开局一次性粘 12 个）摊薄。
- **缩放开局锁定（D8）**：人数中途减少时仍按开局人数算难度（偏难）；接受。

---

## 19. 术语表

| 术语 | 含义 |
|---|---|
| Act（层/阶段） | 三阶段地图之一（Act1/2/3），类杀戮尖塔三层 |
| RunPlan（剧本） | 开局异步种子化预生成的本局全部波次/地图/缩放，主线程只消费 |
| 刷怪策略（SpawnStrategy） | 池内一条；按时序（steps + delay + repeat）定义一波如何刷怪 |
| 刷怪点 | points.yml 里编号化的相对坐标（如"刷怪点9"），进层叠层原点变绝对 |
| 词缀（Affix） | 挂在原版实体上的强化层（hp/dmg/speed/potion/drop），纯配置 |
| 通关奖励（clearReward） | 每波清空后发到每人手里的物品（货币/补给） |
| PlayerState | 每玩家**本局**运行期容器：reviveCount、模式、效果列表（真相源） |
| PlayerEffect | 一条效果实例（effect + 触发 + 到期 + 堆叠策略） |
| 账户货币 / totalEarned | 持久化余额 / 只增不减的累计（=排行榜积分） |
| 复活次数（reviveCount） | 剩余自动复活次数；0 时再死转观察者 |
| 升级菜单 | 休整期中枢 GUI（普通/Boss奖励/跳过/延长） |

---

## 20. 给新 AI agent 的工作守则

1. **动手前**：通读本 CLAUDE.md → `docs/dev-log.md` → `docs/plan/` 当前阶段。
2. **遵守三条铁律**（§0）。
3. **改代码前先看 VampireSurvivor 对应实现**（世界/波次/缩放/死亡）——大概率有现成可借鉴。
4. **加效果先看 `effect/` 扩展点**，复用 `EffectService`。
5. **每完成一个阶段**：在 `docs/dev-log.md` 追加一条（日期 + 做了什么 + 决策原因 + 遗留问题）。
6. **配置改动**：保持 schema 一致；新增字段在 `docs/spec/` 与本文件同步记录。
