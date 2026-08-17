# CLAUDE.md — The Maggoteers（卫戍协议）小游戏插件

> 本文是本项目的**项目圣经**。任何 AI agent 接手开发前，**必须先通读本文**，再读 `docs/dev-log.md`（决策脉络），再看 `docs/plan/`（当前阶段任务）。
> 设计的完整叙事与取舍见 `docs/spec/2026-07-02-maggoteers-design.md`。

---

## 0. 一页速览（给赶时间的 agent）

- **是什么**：Minecraft Paper **26.2** 上的 PvE 小游戏插件——**波数防守 + Roguelike**。4 人合作，三阶段（Act 1/2/3，类杀戮尖塔三层）依次清波打 Boss，波间 3 选 1 随机强化。
- **身份**：游戏内中文名 **卫戍协议**；项目英文名 **The Maggoteers**；MGC 游戏 id `maggoteers`；主包 `io.mczju.maggoteers`；主类 `MaggoteersPlugin`。
- **定位**：是 **MCZJUGameCore (MGC)** 的子插件。大厅/房间/组队/玩家档案/排行榜由 MGC 管；我们只写"游戏玩法"。局内物品由 **MCZJUItemCreator** 生成。
- **构建**：Maven + JDK **25**。
- **三条铁律（不要违反）**：
  1. **绝对坐标永不入配置**——图内用相对坐标，层原点只写在 `config.yml`，进层时 `原点 + 相对` 现算。
  2. **定义唯一处**——刷怪策略只在 `waves.yml`、奖励项只在 `rewards.yml`，被各处引用；改一处即全局生效。
  3. **内容增删零代码**——加地图/波次/词缀/奖励/武器 = 加配置条目；只有"新增触发类型 / 新增 Effect 类型"才需要写 Java（有清晰扩展点，见 §14）。

---

## 1. 项目概览

### 1.1 核心玩法循环
玩家经 MGC 大厅加入 → 满员开局 → 为本局生成一个专属虚空世界 + 一份确定性的"剧本"（RunPlanner 异步生成的 `List<ActPlan>`）→ 玩家在 Act 1 清掉 `M+N+1` 波（弱怪×M、强怪×N、Boss×1）→ 波间休整 30s，可花货币开 3 选 1 强化 → 击杀 Act 1 Boss 传送到 Act 2 → … → 击杀 Act 3 Boss 通关结算。任意时刻**全员观察者**则标记失败、停波清怪、**保留房间继续观战**，直到全员 `/mgc leave` 才结算删房；单人掉线**不**终止整局（该玩家标记倒下、其余人继续）。通关/失败都给**账户货币**（持久化），用于局外商店**解锁**更多局内奖励项。

### 1.2 技术栈
| 项 | 值 |
|---|---|
| 服务端 | Paper **26.2**（API `26.2.build.62-beta`） |
| Java | **25**（编译/运行；本地可用 Minecraft runtime JDK 或 Temurin 25） |
| 构建 | Maven（`paper-api` / `MCZJUGameCore` 为 provided，不打进 jar） |
| 硬依赖 | `MCZJUGameCore`（`depend`） |
| 软依赖 | `MCZJUItemCreator`（`softdepend`；缺失时仅警告，不崩） |
| 其他 | 无（**不引入** WorldEdit / MythicMobs）；**InfernalMobs** 为可选 `softdepend`，运行时反射接入，**非** Maven 编译依赖 |

> ⚠️ **版本基线**：本设计以 **MGC GitHub `1.0.7`** 为准（Paper 26.2）。`JsonPlayerData`/`getPlayerDataManager()`/`getData()`/`PlayerDataLeaderboard` 自 1.0.4 起存在。核对 API 须看 GitHub **1.0.7** tag，勿用过时本地 clone（1.0.0）。

### 1.3 依赖坐标（pom.xml）
```xml
<dependency>
  <groupId>io.papermc.paper</groupId>
  <artifactId>paper-api</artifactId>
  <version>26.2.build.62-beta</version>
  <scope>provided</scope>
</dependency>
<dependency>
  <groupId>com.github.mczju-ops</groupId>
  <artifactId>MCZJUGameCore</artifactId>
  <version>1.0.7</version>
  <scope>provided</scope>
</dependency>
<!-- MCZJUItemCreator：jitpack 引入（同组织 com.github.mczju-ops，已被前代 VampireSurvivor/build.gradle 验证）；版本号以该仓库 pom 为准 -->
```
仓库：`https://repo.papermc.io/repository/maven-public/` 与 `https://jitpack.io`。

---

## 2. 接入 MCZJUGameCore (MGC)

> MGC 文档：`https://github.com/mczju-ops/MCZJUGameCore` 的 `docs/dev-guide.md`、`docs/dev-advanced.md`。Paper javadoc：`https://jd.papermc.io/paper/26/`。

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
MenuFacade.registerMenu("maggoteers-shop", UnlockShopMenu.class);   // 供 /menu 或 NPC 打开商店（menu 包）
// + 各 Config 加载、Listener 注册、PluginCommand 注册
```
> **Menu 子类写法（1.0.5，E4）**：`public UnlockShopMenu(Player player, Object... args) { super(player, args); }`，重写 `getTitle()/getRows()(1~6)/getPermission()/setup()`（`setup()` 内 `setSlot(slot, item[, action])`）。`registerMenu` 反射要求子类有 `(Player, Object[])` 构造器。⚠️ **勿照抄**本地 1.0.0 `guide.md` 的 `super(XxxMenu.class, player)` 写法。

### 2.3 生命周期 hook
| Hook | 我们做什么 |
|---|---|
| `onGameInit()` | 第一个玩家加入等待时：**异步**启动 `RunPlanner`（生成剧本）+ **异步预读 NBT**。⚠️ **不在这里建世界**——`WorldCreator.createWorld()` 必须主线程，建世界的调度放在 `onGameStart` 的就绪门闩内（见下）。返回 true。 |
| `onGameStart()` | ⚠️ **此方法返回 `void`、不能阻塞**（MGC 调完立即置 `RUNNING`）。故这里**只启动一个"就绪门闩"** `runTaskTimer`：轮询异步剧本（`List<ActPlan>`）是否就绪（异步产物）；**一旦就绪，由该门闩任务在主线程执行** `WorldService.createWorld()` + 粘贴 Act 1 的 `structure.nbt` → 传送玩家到 Act 1 `playerSpawn` → 初始化各 `PlayerState`（`reviveCount=config.lives.default`、冒险模式）→ `UnlockRegistry` 合并解锁（含职业池）→ 发全员初始装备（§9.4）→ 为每人开 `ClassSelectMenu`，**全员选完（或倒计时兜底）后** → 启动 `WaveScheduler`。门闩未就绪期间玩家暂留等待点。**建世界的主线程责任唯一落在 onGameStart 门闩内（G4），避免 init/start 双触发。** |
| `onGameEnd()` | 胜利：记通关时间榜 + 结算账户货币；失败：按进度结算（少额）。统一走 `cleanup()`。 |
| `onGameAbort()` | 仅 MGC 强制终止（如关服 `onDisable`）时 `cleanup()`。⚠️ **单人掉线不再走此路**（见下「中途退出 / 败局观战」）。 |
| `onGameCancel()` | 等待阶段取消（未开局）：取消异步任务、删半成品世界。 |
> `cleanup()`：清世界内实体/掉落物 → `Bukkit.unloadWorld(w, false)` → 异步删世界目录 → 玩家 profile 切回大厅。
> **结束对局统一调** `MCZJUGameCore.getGameManager().endGame(game)`，不要直接调 `onGameEnd()`。

> **中途退出 / 败局观战**：重写 `getPlayerQuitStrategy()`——单人掉线或 `/mgc leave` 时 `markDown`（标记倒下）**不整局终止**，其余人继续。全员倒下时 `markDefeated()`（`outcome=FAIL` + `WaveScheduler.defeat` 停波清怪、保留 cursor）→ **保留房间观战**；直到全员 leave 后 `finishGame()` 才真正 `endGame`。结算在 `cleanup` 用保留下来的 `progress` 计失败货币。⚠️ **退出玩家不在 `cleanupRun` 的 `getPlayers()` 里，MGC `leaveGame` 只切 profile（物品栏/XP）不碰游戏模式/血量/效果**——quit 策略内须补 per-player 清理（`EffectService.removeAllFor(pl, mg)` 显式传对局 + `setGameMode(SURVIVAL)` + `resetLobbyVitality`），否则观察者模式会带进大厅、MAX_HEALTH 加成跨局累积。**但绝不能清物品栏**（此时当前 profile 已是 lobby，清空会污染 lobby 存档）；残留 profile 物品靠 `onPlayerJoin` 钩子 + `startInWorld`「先清物品栏再 `switchProfile`」从源头清除。

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
  → 异步准备：RunPlanner 种子化生成 List<ActPlan> + 预读 NBT
  → 回主线程：WorldService.createWorld() + 粘贴 Act1（懒粘贴）
  → （就绪门闩）→ 传送 → 初始化 PlayerState → UnlockRegistry 合并解锁 → 发初始装备 → ClassSelectMenu（全员选完）
  → WaveScheduler 循环：
       WaveEngine 执行当波（刷怪策略时间轴：按 delaySec 分批刷）
       → 清空：发通关奖励 → 30s 休整（升级菜单：普通/Boss奖励/跳过/延长）
       → Boss 死 → 下一层（粘贴 Act2，传送）… → Act3 Boss 死 → 胜利结算
  → （任意点全员观察者）→ 标记失败、停波清怪、保留房间观战 → 全员 /mgc leave 后失败结算
  → cleanup()：卸载+删世界
```

### 4.2 子系统（包 `io.mczju.maggoteers.*`）
| 模块 | 职责 |
|---|---|
| `MaggoteersPlugin` | 主类；注册 + 生命周期总调度 |
| `game/` | `MaggoteersGame`、`MaggoteersRoom`、`MaggoteersDeathStrategy`、`AllyTargeting` |
| `world/` | `WorldService`（建/卸/删/清理孤立）、`MapRepository`（读图）、`StructurePaster`（粘贴单文件 `structure.nbt`）、`VoidGenerator`、`ActSpawnHelper` |
| `plan/` | `RunPlanner`（异步种子化）、`ActPlan`、`RunConfig`、`SeededRng` |
| `wave/` | `WaveEngine`、`WaveScheduler`（单可暂停状态机）、`WaveRuntime`、模型 `WaveSpec/SpawnStep` |
| `mob/` | `MobFactory`（原版实体+系数+词缀+装备）、`MobDisplayNames`、`MountedSquadRegistry` |
| `menu/` | `RestMenu`（休整中枢）、`PickMenu`（3 选 1）、`ClassSelectMenu`（开局职业选）、`UnlockShopMenu`（局外商店）、`ReviveMenu`（复活币） |
| `reward/` | `RewardService`（3 选 1 抽取/扣费/应用）、`RewardDrawVisibility`、模型 `RewardPool/RewardOption` |
| `state/` | `PlayerState`（本局真相源）、`PlayerStateManager` |
| `effect/` | `Trigger`、`Effect`、`PlayerEffect`、`EffectContext`、`EffectKey`、`EffectService`（apply/resync/expire）、`WeaponHeldRegistry` |
| `unlock/` | `UnlockRegistry`（开局把解锁并入每玩家个人池） |
| `item/` | `ItemService`（ItemCreator 接入 + 本插件 parseYamlToItems 缓存）、`ItemInteractRouter`（PDC 物品右键路由）、`RunItemTags` |
| `config/` | 各 Config 加载类（`WavesConfig`/`ScalingConfig`/`MessageService`/`MapPoints`/`MobYamlParser`/`AffixService`）+ `ConfigParse` 校验工具 |
| `listener/` | `GameplayTickListener`（光环/药水/满腹维护心跳）、`PurifyListener`、`RunItemGuardListener` 等事件监听 |
| `command/` | Brigadier `/maggoteers ...` |
| `persist/` | `MaggoteersPlayerData`、`Settlement`、`MaggoteersTotalLeaderboard` |
| `integration/` | `InfernalMobsBridge`（反射接入，可选） |
| `util/` | `Coords`（相对→绝对）、`Origins`、`ParticleEffects`、`ItemYaml`、`GameRegistries` |

---

## 5. 世界与地图系统

### 5.1 世界生命周期（`WorldService`，复用并扩展 `MCZJUvampireSurvivor/WorldManager` 已验证实现）
- 世界名 `maggoteers_<8位hex>`（hex 来自 seed）。
- `new WorldCreator(name).environment(NORMAL).generator(new VoidGenerator()).createWorld()`——**必须在主线程调用**（Bukkit 限制；前代 VampireSurvivor 也是同步建世界，仅删目录走异步）。
- `VoidGenerator`：所有 `shouldGenerate*()` 返回 false、`generateSurface` 空实现 → 纯虚空。
- GameRule：`ADVANCE_TIME=false`、`ADVANCE_WEATHER=false`、`NATURAL_HEALTH_REGENERATION=false`、`MOB_GRIEFING=false`、`SPAWN_MOBS=false`；永久白天+晴天；`Difficulty.HARD`。
- **结构粘贴（已验证可行）**：
  ```java
  Structure s = Bukkit.getStructureManager().loadStructure(nbtFile); // 直接吃 java.io.File
  s.place(world, new BlockVector(x,y,z), true, StructureRotation.NONE, Mirror.NONE, 0, 1.0f, new Random());
  ```
- **懒粘贴减卡顿**：开局只粘 Act 1 的 `structure.nbt`；进 Act 2/3 时再粘该层 `structure.nbt`。
- **层原点**（`config.yml` `act_origins`）：如 Act1=(0,64,0)、Act2=(1024,64,0)、Act3=(2048,64,0)；`structure.nbt` 原点角对齐层原点，往 +X/+Y/+Z 展开。
- **销毁**：传送玩家回主世界 → `unloadWorld(false)` → **异步**递归删目录。
- **孤立世界清理（开机）**：`onEnable` 扫描 `Bukkit.getWorlds()` + 世界容器目录，凡 `maggoteers_*` 一律卸载删除（防崩服残留）。

### 5.2 地图仓库（`MapRepository`）
```
plugins/Maggoteers/maps/
  act1/<mapId>/  例如 ruined_keep/
      structure.nbt                   # 结构方块导出的整图
      points.yml                      # playerSpawn + spawnPoints{编号→相对坐标}
      special_waves.yml               # 本图专属波（引用 waves.yml 的 strategy id + 池 + weight）
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
> 相对坐标须按结构原点书写（结构往 +X/+Y/+Z）；缺 `structure.nbt` 时铺玻璃平台兜底（示例 `points.yml` 可含负坐标以适配居中平台）。
> **刷怪点校验（G3）**：`waves.yml` 里 `steps[].point` 引用的编号应在该图 `points.yml` 定义。若该图**非 boss 刷怪点少于 9 个**且引用了未配置点，则回退到 **boss** 点坐标；非 boss 已满 9 个仍缺 id、或无 boss 可回退时，启动/规划报错指到具体 strategy。

### 5.3 抽图
`RunPlanner` 每层从 `maps/actN/` 随机抽 1 张 → 读 `points.yml` + `special_waves.yml` → 把专属波次并入该层对应池。

---

## 6. RunPlanner（异步、种子化、纯计算）

- **时机**：`onGameInit`（等待阶段）/ 进入新层时；在**非主线程**跑（不碰 Bukkit API，只读配置算数据）。
- **输入**：`seed`（= 世界名 hex）、`playerCount`（开局快照锁定，**不随中途变化**）。
- **产出**：`RunPlanner.plan(seed, playerCount)` → `List<ActPlan>` + `RunConfig`（确定性、可重放/调试）：
```
List<ActPlan> { mapId, playerSpawn,        // playerSpawn 已叠层原点（绝对坐标；异步线程不持有 World，主线程绑定 World 时再构造 Location）
                waves:[ Wave{ steps:[Step…已解析绝对坐标&缩放后数值], clearReward } … ] }   // M+N+1 波
RunConfig { origins{act→Vec3}, wavesPerAct{act→[weak,strong]} }   // 层原点 + 每层波数（config.yml 静态）
```
- **流程/层**：抽 1 图 → 并入地图专属波次 → weak roll M、strong roll N、boss roll 1（**加权随机**，地图专属波次权重更高）→ 每条 strategy 展开成 `Wave`（`steps × repeat`）→ 刷怪点编号解析成绝对坐标 → 数值叠 `coeff × affix × scaling`。
- **主线程只消费 ActPlan 列表**：战斗中零随机、零池查询。
> **奖励 3 选 1 不在 RunPlanner 预 roll**——它发生在休整期（非战斗），开销极小，玩家点按钮时按需现抽。

---

## 7. 波次 / 池子引擎

### 7.1 配置侧模型
- `Affix`（词缀，强化层）：`id, hp×, dmg×, speed×, drop×, potions[], on, display`。定义在 `affixes.yml`。
- `SpawnStep`：`point(id), type, count, coeff{hp,dmg,speed}, affixes[], delaySec`（**步前等待**：相对上一步刷怪时刻的秒数；首步相对本波开始；`delaySec=0` 表示不额外等待；`repeat` 多轮时上一轮末步之后继续累加）。
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
REST     ──(到时/跳过)──► 下一波 | 本层 Boss 死 ► 进下一层 | Act3 Boss 清场后亦进 REST（粘滞 act3_boss）► 再 VICTORY
(任意点全员观察者) ──► DEFEATED：停心跳 + 清怪 + 置失败相位（计分板 wave.phase_defeated），保留 cursor 观战；全员 leave → finishGame → 失败结算
```
- **跨层**：本层 Boss 清除 → `WorldService` 粘贴下一层 `structure.nbt` → 全员传送新 `playerSpawn` → scheduler 从该层 wave 0 继续。

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
      - { point: 9, type: ZOMBIE, count: 5, coeff: {hp: 1.0}, delay: 5 }   # 上一批刷出后 5s 再刷本步
  b_iron_golem_guard:
    steps:
      - { point: boss, type: IRON_GOLEM, count: 1, coeff: {hp: 8.0, dmg: 1.5}, affixes: [armored], delay: 0 }
      # 可选 IM 技能（主体 / passengers / on_death 各自独立，见 infernal 块）：
      # - { point: 1, type: ZOMBIE, count: 3, infernal: { level: 3, affixes: [poisonous, sprint] }, delay: 0 }
```

### 7.5 affixes.yml（Maggoteers 原生出生/倍率词缀）
```yaml
affixes:
  armored: { hp: 2.0, display: "<aqua>装甲" }
  berserk: { dmg: 1.5, speed: 1.2, display: "<red>狂暴" }
```

**IM 战斗技能**不在 `affixes.yml`，而在 `waves.yml` 各刷怪节点的 `infernal:` 块（`level` 1–100 + 精确 `affixes` 列表；与原生 `affixes:` 命名空间独立、互不继承）。刷怪顺序：原生 stats/affixes → **IM mechanize** → 显式 `equipment`（覆盖 IM 自动装备）。IM 缺失或技能未知时跳过并 warning，实体仍为普通怪且受 WaveEngine 追踪。本插件 mechanize 的 IM 怪在 `EntityDeathEvent` **LOWEST** 提前 `unregisterMob`，**不**触发 IM 掉落/统计/广播/死亡技能。

### 7.6 config.yml（缩放 / 每层波数 / 全局）
```yaml
wait: { min_players: 1 }                # DefaultGameWaitStrategy(this, 4, min)
lives: { default: 2 }                   # 复活次数
debug: { enabled: true, announce: true }   # 内部测试开关（发布前改 enabled: false）
mob_attributes: { scale: 1.0, follow_range: 10.0 }   # 全局怪物属性倍率
initial_equipment: [maggoteers:detection_radar]       # 开局全员探测雷达；职业装由 class 池发放
class_select: { timeout_sec: 45 }       # 职业选择超时兜底
rewards: { upgrade_level_cap_default: 4 }   # UPGRADE_LEVEL 等级上限兜底
items: { currency_normal, currency_boss, class_ticket, shop_emerald }   # 货币/券物品外观（材质/名称/光效）
run_items: { drop_protection: false }   # 局内奖励物品丢弃保护
act_origins: { act1: [0,64,0], act2: [1024,64,0], act3: [2048,64,0] }
waves_per_act:
  act1: { weak: 3, strong: 3 }          # M, N → 每层 M+N+1 波
  act2: { weak: 3, strong: 3 }
  act3: { weak: 1, strong: 4 }
rest: { duration_sec: 60 }
map: { fallback_platform: true }        # 缺 structure.nbt 时铺玻璃平台兜底
world: { time_lock: night }             # night | day | midnight | none
player: { night_vision: true }          # 进层夜视（配合 time_lock）
act_enter: { prep_sec: 10 }             # 每层粘贴后、开波前准备倒计时
aura: { carrier_fx: { default_interval_sec: 3, by_preset: {...} } }   # AURA 携带者粒子间隔
magic_fx: { defaults, templates, weapons }   # 魔法武器右键 FX 预设
scaling:
  mob_hp:        [1.0, 2.3, 3.6, 5.0]   # 1–4 人
  mob_damage:    [1.0, 1.4, 1.8, 2.2]
  mob_count:     [1.0, 1.0, 1.2, 1.5]   # 向下取整 + 概率补 1；⚠️ 补 1 的随机必须走 RunPlanner 种子 RNG（D6，保确定性/可重放）
settlement:
  win_flat: 100
  fail_per_act: 25
  fail_per_wave: 5
messages: { game, wave, death, menu, pick, class, revive, shop, item, reward, scoreboard, leaderboard, command }   # 玩家面文案（§config.yml 全文）
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
按钮：`[普通奖励] [Boss奖励] [跳过休整]`（结构可扩展）。
- 点 `普通奖励`/`Boss奖励`：扣对应货币 → 弹 3 选 1（从对应池抽 **3 个不同**选项）→ 选 1 应用 → 回主菜单，**可反复**直到货币不足/休整结束。
- **抽池可见性**（`RewardDrawVisibility`）：**SUPPLY** 可重复；**WEAPON** 每 `id` 一次（`acquiredUnique`）；**STAT** 每 `id` 一次（看 `PlayerState.effects`；**UPGRADE_LEVEL** 未满级仍可抽）；与 `unique:`  YAML 字段解耦（STAT 仍建议写 `unique: true` 便于内容校验）。
- **STAT 护符**：选中 STAT 时除写入 `PlayerState` 外，按 `collectibles.yml` 映射发放绑定 **护符**（ItemCreator **RECOVERY_COMPASS（追溯指针）** 底材等；勿用兔子脚——右键骆驼尸壳会被吃掉；PDC `kind=collectible` + `reward_id` + `reward_level`）；GUI 图标来自 ItemCreator 预览，**不再**使用 `rewards.yml` 的 `icon:`。
- **右键手持货币** = 打开本菜单的快捷方式（与点按钮殊途同归）。

### 9.2 池选择规则（RunPlanner 预定）
- **普通池**（看刚清的波类型）：弱怪波 → `actN_weak`；强怪波 / Boss 波 → `actN_strong`。
- **Boss 池**（**粘滞**）= 最近一次击杀 Boss 所在层的 `actN_boss`；新层未杀 Boss 前仍用上一层 Boss 池，杀掉本层 Boss 后才更新。

### 9.3 跳过 = 投票
需**全员（冒险模式的玩家）同意**才生效；投票状态实时显示。

### 9.4 开局职业选择（复用奖励池 + 解锁系统）
玩家进图时**无装备**。`onGameStart` 门闩就绪后：先发**全员相同初始装备**（`config.yml` `initial_equipment`，ItemCreator id 列表，走 `ItemService` + `giveItem(ItemStack)`）；再为每人开 `ClassSelectMenu`——从**职业池**（`rewards.yml` 里 `reward_pools.class`，`cost: 0` 免费）**随机抽 3 个**（同样走可见性过滤 `requires_unlock`/`unique`，**解锁系统原样复用**）→ 选 1 → 应用（属性/武器/补给，与普通奖励同路径）；**等全员选完**（倒计时兜底，超时随机自动选）→ 启动 `WaveScheduler`。
> 加职业 = `rewards.yml` 职业池加一个 option（`category` 任意、可 `requires_unlock: true` 走商店解锁）。**零代码**。借前代 `ClassSelectMenu` 已验证的「全员选完 → 开局」套路。

---

## 10. 效果系统（核心可扩展子系统）

> 范式来自 `MCZJUMagicItems`：**触发（监听器）→ 分发 → 效果**。一个监听器服务多种被动；一个 Effect 实现被多途径复用。
> **核心原则：PlayerState 是效果的"真相源"，Bukkit 属性/药水只是派生视图。**

### 10.1 触发目录（v1 全部实现）
| 类别 | Trigger |
|---|---|
| 生命周期 | `ON_WAVE_CLEAR` / `ON_ACT_ENTER` |
| 战斗 | `ON_KILL` / `ON_DAMAGE_DEALT` / `ON_DAMAGE_TAKEN`（**TAKEN = 仅敌人近战/箭矢攻击**，环境/友军伤害不触发） |
| 休整/进层 | `ON_WAVE_CLEAR` / `ON_ACT_ENTER`（**GRANT_ITEM/SUMMON STAT 专用**） |
| 玩家状态 | `ON_REVIVE` / `ON_DEATH` / `ON_TICK_1S` |

### 10.2 Effect 目录

`ADD_ATTRIBUTE` / `ADD_POTION` / `HEAL` / `DAMAGE_AREA` / `DAMAGE_BEAM` / `HEAL_AREA` / `GRANT_REVIVE` / **`GRANT_ITEM`** / **`SUMMON`** / `BUFF_AREA`（`potions[]` **或** `clear_potions[]` + `targets` + 可选 FX/mark） / **`DISABLE_AI`**（`duration_ticks` + `targets`: enemies|hit_target|attacker） / `AURA`

> **净化 / 沉默 / 免疫**（见 `docs/superpowers/specs/2026-08-12-potion-clear-immunity-design.md`）：
> - **一次性清除**：`params.clear_potions: [TYPE…]`（`DAMAGE_AREA` / `BUFF_AREA`）或武器顶层 `self_clear_potions`（`removePotionEffect`；不写 PlayerState）。
> - **局内免疫**：奖励 `ADD_POTION` + `params.immunity: true`（无 trigger/expiry/recurring；非瞬时药水）→ 清现有效果 + 拦 `EntityPotionEffectEvent` ADDED/CHANGED + `PurifyListener` 拦 POISON/WITHER 伤害。
> - **禁止**用 `amp: 255` + `duration_ticks: 0|1` 伪装清除/免疫：`rewards.yml` / `items/*.yml` **硬失败**加载（`affixes.yml` 的 amp 255 仍是怪物词缀，不在此规则内）。
> - `DAMAGE_AREA` 顺序：**damage → clear_potions → potions**（本击仍受目标已有 Resistance 影响）。

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
| 未来魔法武器 | 触发其 `use_ability` 效果 |

---

## 11. 死亡 / 复活（`MaggoteersDeathStrategy extends AbstractPlayerDeathStrategy`）

- `reviveCount`（开局 = `config.lives.default`，默认 2）= **剩余可自动复活次数**。
- 玩家死亡：若 `reviveCount > 0` → `reviveCount--` 并复活（含 `1→0` 这次仍复活，回 `respawnLoc`、短暂无敌、维持冒险模式）；**若已为 0** → 转**观察者模式**（仍占座，可被复活币救）。
- **复活币**（仅 Boss 池产出；ItemCreator 物品 + PDC `maggoteers:revive_coin`）：右键 → 开**当局玩家头像 GUI**：对**死者**用 = 仅复活（不加次数）；对**活者**用 = `reviveCount +1`。
- **失败判定**：每次死亡/退出后检查——**场上无冒险模式玩家** → `markDefeated()`（`outcome=FAIL` + 停波清怪、保留房间观战），**不立即结束**；直到全员 `/mgc leave`（quit 策略 → `finishGame()`）才 `endGame` 走失败结算。单人掉线/`/mgc leave` 仅 `markDown` 该玩家，不终止整局。

---

## 12. 奖励项 schema（`rewards.yml`）

```yaml
reward_pools:
  act1_weak:                      # 币种无配置：RestMenu 按波次层级推导（RewardService.normalShopTier）
    cost: 1                       # 每次 3 选 1 消耗（现统一为 1）
    options:
      - { id: dmg10,   category: STAT, effect: ADD_ATTRIBUTE, params: {attr: ATTACK_DAMAGE, op: PERCENT, value: 0.10},
          unique: true, stack: ADD, display: "<red>+10% 伤害" }
      - { id: lifesteal, category: STAT, trigger: ON_KILL, effect: HEAL, params: {amount: 2.0},
          unique: true, display: "<dark_red>嗜血：击杀回 2 血" }
      - { id: res_up, category: STAT, effect: ADD_POTION, params: {effect: RESISTANCE, amp: 0},
          unique: true, stack: UPGRADE_LEVEL{max: 4}, display: "<aqua>抗性(可升级)" }
      - { id: lone_wolf, category: STAT, effect: ADD_ATTRIBUTE, params: {attr: MOVEMENT_SPEED, op: FLAT, value: 0.02},
          unique: true, display: "<gray>孤狼（唯一）" }
      - { id: wrench, category: WEAPON, item: maggoteers:wrench,
          requires_unlock: true, unlock_cost: 30, display: "<gold>扳手" }
      - { id: heal_potion, category: SUPPLY, item: maggoteers:healing_potion, amount: 2,
          display: "<red>治疗药剂×2" }
  act1_strong: { cost: 1, options: [...] }
  act1_boss:   { cost: 1, options: [...] }
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
| `display` / `description` | MiniMessage 显示文本 / GUI 说明 |
| `requires_unlock` / `unlock_cost` | true = 需局外商店解锁才能 roll 到；unlock_cost = 商店售价 |
| `unique` | STAT 建议 `true`；WEAPON 每 id 一次；**抽池可见性以 `RewardDrawVisibility` 为准**（见 §9） |
| `stack` | 重复获得的合并策略（见 §10.3）。**触发型被动默认 `IGNORE`**（D7：重复拿到同一条被动不叠加/不刷新，除非显式声明 `ADD`/`REFRESH`） |

### 12.2 3 选 1 可见性判定
```
可见(player, option) =
    (!option.requires_unlock || player.持久.unlocks.contains(option.id))
 && RewardDrawVisibility.isVisible(option, player.本局, acquiredUnique)
    // SUPPLY: 始终可见
    // WEAPON: !acquiredUnique.contains(id)
    // STAT: 无同 id 效果，或 UPGRADE_LEVEL 且 level < upgradeMax
```
护符外观见 `collectibles.yml` + `items/collectibles.yml`（`CollectibleRegistry` / `CollectibleService`）。
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

### 13.3 商店（`UnlockShopMenu`，局外）
- 商品 = 所有池里 `requires_unlock: true` 的选项（自动派生），显示 `unlock_cost` + 图标 + 锁定/已拥有。
- 点击锁定且付得起 → MGC `AlertMenu` 二次确认 → 扣 `balance` → `unlocks.add(id)` → `setModified(true)`。
- 入口：`/maggoteers shop`，并已 `MenuFacade.registerMenu("maggoteers-shop", ...)`（NPC/命令方块可开）。

### 13.4 解锁回流局内（`UnlockRegistry`）
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
| 新地图 | `maps/actN/<mapId>/` 加 `structure.nbt` + points.yml（+ special_waves.yml） | 否 |
| 新波次 | `waves.yml` 加 strategy + 池引用 | 否 |
| 新词缀（原生 hp/dmg/速度/掉落/体型） | `affixes.yml` 加条目 | 否 |
| IM 战斗技能 | `waves.yml` 各节点加 `infernal: { level, affixes }`（需测试服装 InfernalMobs） | 否 |
| 新奖励（属性/武器/补给） | `rewards.yml` 加 option（武器/补给配 ItemCreator 物品） | 否 |
| 新触发类型 | `effect/Trigger` 枚举加值 + 对应监听器分发 | **是**（有模板） |
| 新 Effect 类型 | `effect/Effect` 枚举加值 + `EffectService` 实现 | **是**（有模板） |
| 新魔法武器（旋转刀片等） | ItemCreator 出带 PDC 物品 → 本插件写识别+触发类读 PDC（走 `ItemInteractRouter`） | **是**（见 §15 指南） |

---

## 15. 自定义交互物品开发指南（PDC 路由模式）

未来做魔法武器，**优先 `items/*.yml` 的 `use_ability` + `held_effects`**（零 Java）：

```yaml
use_ability:
  cooldown_sec: 3
  effect: BUFF_AREA | DAMAGE_AREA | DAMAGE_BEAM | HEAL_AREA | DISABLE_AI | GRANT_ITEM | SUMMON | ADD_ATTRIBUTE | ADD_POTION
  params: { radius, targets, potions[], clear_potions[], damage, amount, potion, amp, duration_ticks, immunity, ... }
  self_clear_potions: [WITHER, POISON, ...]   # 可选：施法者一次性清效果（与 self_potions 并列）
  self_potions: [ { potion: INSTANT_DAMAGE, amp: 0, duration_ticks: 1 } ]
  fx: { preset, particle, radius, ... }
  expiry: { trigger: ON_DAMAGE_DEALT, charges: 1 }   # ADD_ATTRIBUTE / ADD_POTION 临时路径
  stack: REPLACE

held_effects:   # 主手持有期间生效；禁止 expiry
  - effect: ADD_ATTRIBUTE
    params: { attr: ATTACK_DAMAGE, op: PERCENT, value: 0.10 }
  - effect: ADD_POTION
    params: { potion: FIRE_RESISTANCE, amp: 0, duration_ticks: 0 }
  - effect: AURA
    params:
      radius: 12.0
      targets: allies
      grant: { effect: ADD_ATTRIBUTE, attr: ATTACK_DAMAGE, op: PERCENT, value: 0.08 }
  - effect: ADD_POTION
    trigger: ON_DAMAGE_DEALT
    params: { potion: POISON, amp: 0, duration_ticks: 100 }
```

**`held_effects` 规则**：生命周期 = 主手持有；id 前缀 `held:<itemId>:<n>`；卸载时按前缀删除（含 `:grant` 子层）。常驻仅 `ADD_ATTRIBUTE` / `ADD_POTION` / `AURA`；战斗型须 `trigger`（`ON_DAMAGE_DEALT` / `ON_DAMAGE_TAKEN` / `ON_KILL`）。禁止 `expiry` / `UPGRADE_LEVEL`。

**`use_ability` ADD_POTION 双路径**：无 `expiry` + `duration_ticks > 0` → 右键即时药水（不写 PlayerState）；有 `expiry` + `duration_ticks: 0` → `ability:<itemId>` 直到事件到期。参数键用 `potion:`（非 `effect:`）。

`BUFF_AREA`：右键给 self/allies/enemies 挂限时药水和/或 `clear_potions` 清除（二者至少其一）；武器 `mark_fx` 对目标列表全员播放。STAT 战斗版见 `rewards.yml` `trigger` + `params.mark_on`。

`DISABLE_AI`：对敌对生物 `setAI(false)` 一段时间后复原；`targets`: `enemies`（需 `radius`）| `hit_target` | `attacker`；再施加取更长剩余；卸载前 restore（防永久 NoAI）。Phase 1：`use_ability` + STAT；`held_effects` 为 Phase 2。

**下次近战加成**（纯配置）：`effect: ADD_ATTRIBUTE` + 必填 `expiry`（如 `{ trigger: ON_DAMAGE_DEALT, charges: 1 }`）+ `params: { attr, op, value }`；可选 `stack: REPLACE`（默认）。右键写入临时 `PlayerEffect`（id=`ability:<itemId>`），**仅玩家近战** `ON_DAMAGE_DEALT` 消耗；弓箭不触发。样例：`maggoteers:power_strike`。

**多步 `use_ability.effects[]`**（2026-08-13）：一次右键可挂多段即时/延期效果；延期魔法（`BUFF_AREA`/`DISABLE_AI`/…）与 ADD_* 同用 `expiry`；触发顺序为先 `fireTrigger` 再 `sweepExpiry`。Cast FX 右键一次，empower FX（`empower_fx` 或回落 `fx`）在命中 victim 播一次。详见 `docs/superpowers/specs/2026-08-13-multi-use-ability-design.md`。

路由顺序：ItemKind → `registerHandler`（可选覆盖）→ `ItemAbilityRegistry` → 无则 vanilla 交互。

**Escape hatch**：仅当 Effect 系统表达不了的多段/投射物技能时，才写 `ItemUseHandler` 并 `registerHandler`。

奖励侧法术强度：`rewards.yml` 用 `ADD_ATTRIBUTE` + `attr: MAGIC_DAMAGE` + `op: PERCENT` + `stack: ADD`（虚拟 stat，不写 Bukkit）。奖励 `fireTrigger` 白名单：`ON_WAVE_CLEAR` / `ON_ACT_ENTER` / `ON_KILL` / `ON_DAMAGE_DEALT` / `ON_DAMAGE_TAKEN` / **`ON_DEATH`** 或省略（常驻）；**禁止** `ON_TICK_1S`、`ON_REVIVE`（`ON_GAME_END` 已从 Trigger 删除）。`GRANT_ITEM`/`SUMMON` **必须**显式 `trigger`（不可省略）。`ON_DEATH` 区域/SUMMON 以**死亡地点**为原点（`eventLocation` / `anchor: death_site`）。

---

## 16. 构建与运行

- **构建**：`mvn package` → `target/Maggoteers-<ver>.jar`。
- **Agent 工作流**：`.cursor/skills/maggoteers-build-deploy/` — 用 **IntelliJ 自带 Maven**（JDK 25）跑 `test`/`package`，并拷贝 jar + 常用 YAML 到 **`E:\MCpaper\plugins`**；脚本 `scripts/build-and-deploy.ps1`。
- **部署**：jar 放 `plugins/`；同目录需有 `MCZJUGameCore`、`MCZJUItemCreator` 两个 jar。
- **本地无 JDK 时**：Windows IntelliJ 构建，或 WSL 内安装 JDK21 + Maven 后构建。
- **资产**：首次启动释放 `plugins/Maggoteers/{items,maps}/...` 默认样例。
- **测试服**：`E:\MCpaper`（Paper 26.2；WSL `/mnt/e/MCpaper`）。

### 16.1 首测前部署清单（plan 阶段 0 须覆盖）
- [ ] 测试服 MGC **1.0.7** + Paper **26.2**（见 `E:\MCpaper`）。
- [ ] `plugins/MCZJUGameCore/rooms/maggoteers/default.json` 存在（由本插件 `onEnable` 自动释放，或 `/mgcop room create maggoteers default`）——否则 `/mgc join maggoteers` 无房间（G1）。
- [ ] `plugins/Maggoteers/{items,maps}/...` 资产就位（首次启动释放默认样例）。
- [ ] `MCZJUItemCreator` 已装（否则物品缺失，仅警告不崩）。
- [ ] 若使用 `infernal:` 块：测试服 `plugins/` 有 **InfernalMobs** JAR；无 IM 时波次仍正常，仅跳过 IM 技能。

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
- **ItemCreator 版本**：本机构建基线 **1.1.0**（仓库 `main`，Paper 26.2）；`pom` `provided` 坐标 `io.mczju:MCZJUItemCreator:1.1.0`。
- **ItemCreator 物品来源**：默认读它自己 `items/`；我们用 `parseYamlToItems` 解析本插件目录下的物品。
- **PlayerData 改动忘 `setModified(true)`**：不会落盘——封装一层 setter 提醒。
- **MGC 版本**：设计基线 **1.0.7**（Paper 26.2）。⚠️ 本地 clone 若仍为 **1.0.0（过时）**，核对 API 须看 GitHub **1.0.7** tag。
- **~~净化技巧待实测（D1）~~（已退役）**：假 amp-255 已由真实 `clear_potions` / `immunity: true` 取代；部署须同步 `rewards.yml` + `items/maggoteers.yml`（`saveResource(false)` 不会覆盖服上旧 YAML）。
- **结构粘贴主线程掉帧（D8）**：粘贴该层 `structure.nbt` 主线程瞬时完成会掉几 tick；接受。已改为"进层时粘该层"（非开局一次性粘三层）摊薄。
- **缩放开局锁定（D8）**：人数中途减少时仍按开局人数算难度（偏难）；接受。
- **InfernalMobs 反射（IM1）**：仅调用 `mechanizeWithAffixes`；禁用 `morph`/`mama`/`mounted`/`vexsummoner`/`ghost`；反射签名变更会导致整局 bridge 禁用（dedupe warning）。死亡前须 unregister，否则 IM 会发战利品。

---

## 19. 术语表

| 术语 | 含义 |
|---|---|
| Act（层/阶段） | 三阶段地图之一（Act1/2/3），类杀戮尖塔三层 |
| ActPlan（单层剧本） | RunPlanner 异步种子化预生成的该层波次/地图/出生点（叠好绝对坐标），主线程只消费 |
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
