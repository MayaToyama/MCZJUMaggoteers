# Mounted Squad Hardening（骑乘小队加固）Design

> **Status:** Draft — pending user spec review
> **Date:** 2026-08-17
> **Revision:** v1.1 — 修正 R2 清理假设、root setTarget 与 §1.7 冲突、Camel 嵌套回落回归、DeathSpawn 适用范围、R4 验收拆 Must/Stretch
> **Depends on:** 无（独立加固；基于 `docs/spec/2026-07-02-maggoteers-design.md` §1.7 骑乘小队 与 `2026-07-25-smoke-fixes-design.md` §1.7）
> **Delivery scope:** 框架 + 单测；不改内容 YAML（`waves.yml`/`config.yml` 仅在需要新增字段时提供默认值，现有内容零改动）

**Summary:** 加固骑乘怪物的生产与运行逻辑：① squad 表按对局作用域隔离 + 清理路径统一；② controller 选择支持显式声明；③ 小队索敌/驱动参数配置化；④ 复刻原版蜘蛛骑士/僵尸骑士的自然冲锋表现（马/骆驼冲刺、僵尸乘客举矛冲锋）。排除 M2（座位超限校验）、M3（乘客原版 AI 分散）、M4（未知实体类型硬失败）三项，不改。

---

## 0. 背景与问题

排查骑乘生产链路（`MobFactory.spawnStepGroup` → `mountPassengerTree` → `MountControllerResolver` → `MountedSquadRegistry` → `MountedSquadAiService`）发现的风险：

| # | 问题 | 严重度 |
|---|---|---|
| R1 | `MountedSquadRegistry` 静态全局表，不按对局作用域；`MountedSquadAiService.tick(game)` 遍历**所有**对局的 squad | 中高 |
| R2 | squad 清理分散 4 处且不一致：`stop` 清、`clearTracking`（被 `killAllTracked` 用）**不清**，靠 5-tick 扫描碰巧自愈 | 中 |
| R3 | controller 选择是隐式位置依赖（Camel 取首位 / 最深 Mob），无配置字段可声明，内容作者调整乘客顺序即改变控制器 | 中高 |
| M1 | 小队索敌距离 `TARGET_RANGE=32.0` 与驱动速度 `1.0` 硬编码，无法调（与 `follow_range` 有意解耦，§3） | 中 |
| L1 | `addPassenger` 失败后 child 已 remove 但仍 tracked，≤5 tick 扫描回收；孙乘客弹飞成散怪（行为可接受，需文档化） | 低 |
| L3 | root 坐骑死亡 → squad 移除、乘客弹飞成独立追踪怪（行为可接受，需文档化） | 低 |
| L4 | 骑乘机械零单测（最复杂递归无覆盖） | 低 |
| R4 | 马/骆驼坐骑用踱步速度移动（`moveTo(target, 1.0)` 走 `AbstractHorse` 内部 speed 字段而非 `MOVEMENT_SPEED` 属性），不出冲刺；僵尸乘客无法表现举矛冲锋 | 中高 |

用户已确认：**M2/M3/M4 不改**。

---

## 1. R1 + R2：squad 表按对局作用域 + 清理统一

### 1.1 `MountedSquadRegistry` 改按 game 键控

```java
public record Squad(AbstractGame game, UUID rootUuid, UUID controllerUuid) {}

// 内部：Map<AbstractGame, Map<UUID, Squad>>   （rootUuid 全局唯一，内层 key 即可）
public static void register(AbstractGame game, LivingEntity root, LivingEntity controller)
public static void removeByRoot(AbstractGame game, UUID rootUuid)
public static List<Squad> squadsFor(AbstractGame game)      // snapshot，供 AiService 遍历
public static void removeGame(AbstractGame game)            // 整局清空（R2 核心）
```

### 1.2 R2 统一清理（显式写清两个入口）

⚠️ 当前 `WaveEngine.stop()` **不调用** `clearTracking()`（`WaveEngine.java:139-154` 自带逐怪循环）；`clearTracking` 只被 `killAllTracked` 调用。因此必须**两处各自显式清**：

- **`clearTracking(game)`**：追加 `MountedSquadRegistry.removeGame(game)` → 覆盖 `killAllTracked`（败局清怪路径）。
- **`stop(game)`**：也追加 `MountedSquadRegistry.removeGame(game)`，并删除其循环内逐怪 `removeByRoot(uuid)`（冗余，removeGame 整局清空）。覆盖 `WaveScheduler.stop` / `advance` 终局路径。
- **`handleMobDeath`**：保留逐根 `removeByRoot(game, uuid)`（战斗中按怪清理）。

不做"stop 复用 clearTracking"的假设——两处各写一行显式 `removeGame`，杜绝局末 squad 表泄漏。

### 1.3 `MountedSquadAiService.tick` 只遍历本局 squad

```java
for (var squad : MountedSquadRegistry.squadsFor(game)) { ... }
```

消除跨局遍历的 O(N) 浪费与正确性隐患。

### 1.4 调用点

`MobFactory.spawnStepGroup` / `mountPassengersDelayed` 各加一个 `AbstractGame game` 参数（各自只有 1 个调用方：`WaveEngine.spawnStep` / `WaveEngine.spawnOnDeath`），延迟任务里 `register(game, mount, controller)`。

---

## 2. R3：controller 显式字段

### 2.1 schema

- `PassengerCfg` / `PassengerSpawn` 各加 `boolean controller`（默认 false）。
- `MobYamlParser.parsePassengersDepth` 解析可选 `controller: true`（非布尔 → 与 `parseBossBar` 一致硬失败）。
- `RunPlanner.buildPassengerTrees` 透传 `pc.controller()`。
- ⚠️ **record 兼容构造**：`PassengerSpawn`（主构造 + 8 参 + 6 参旧构造）、`PassengerCfg`（主构造 + 扁平 + 8 参旧构造）**所有构造都要带 `controller` 参数**，改漏一处编译挂；`RunPlanner` 里所有 `new PassengerSpawn(...)` 调用同步透传。

### 2.2 resolver 重构：纯候选路径 + 薄运行时（**保持 §1.7 回落语义**）

```java
// 纯函数（无 Bukkit 依赖，可单测）：
//   返回「候选路径」列表（按优先级降序），每个路径 = 孩子索引序列（如 [0,1] = 直接乘客0 的 乘客1）
static List<List<Integer>> controllerCandidates(List<PassengerSpawn> spec, boolean rootIsCamel)
//   候选列表 = 显式段 + 默认回落段，整体去重（与既有 §1.7 行为对齐，不做静默改写）：
//   ① 显式段：所有 controller()==true 的节点路径，按 DFS 序全部加入
//      （第一个即可用；后续显式节点仍留在回落链，与「非 Mob 自然落到下一候选」一致）
//   ② 默认回落段（显式段之后追加，去重后继续）：
//      2)  rootIsCamel && size==2 && 双叶 → [ [0] ]（短路，不进入 2.5）
//      2.5) rootIsCamel && 有乘客（非规则 2 的双叶情形）→ [ 第一棵子树最深叶路径, 整棵树最深叶路径 ]
//           // 对齐现代码：先前座子树，非 Mob 再落整树；顺序上必须排在规则 2 之后
//      3)  非 camel → [ 整棵树最深叶路径 ]
//      4)  空 spec → []

// 运行时薄封装（现有 resolve 签名不变）：
//   rootIsCamel = root instanceof Camel
//   依次走候选路径（沿真实实体树取实体），返回第一个「Mob && isValid && !isDead」；
//   全部不可用 → 回落 root
//   显式 controller 落在非 Mob/已死 → 自然落到下一候选/root（写进单测）
```

现有 `deepestMob` / `maxDepth` / `collectDepth` 三个递归全部删除，由纯候选路径取代。现有 `waves.yml` 零改动（无声明时回落链 2/2.5/3 与现行为一致）。

### 2.3 多处 `controller: true`

- `controllerCandidates` 按 DFS 取第一个（确定性）。
- 加载期在 `MobYamlParser` 递归计数，**>1 处声明时 `LOG.warning` 一次**（不硬失败，避免新增校验面；内容作者据此发现误配）。

---

## 3. M1：小队参数配置化

`config.yml` 现有 `mob_attributes:` 块下新增（跟随既有风格）：

```yaml
mob_attributes:
  scale: 1.0
  follow_range: 10.0              # 既有（原样）
  squad_target_range: 32.0        # 新增，默认 32（等价当前硬编码）
  squad_move_speed: 1.0           # 新增，普通坐骑 moveTo 倍率
  squad_charge_speed: 4.0         # 新增，冲刺坐骑（马/骆驼等）moveTo 倍率（≈冲刺）
```

`MountedSquadAiService.tick` 每拍读一次（Bukkit config 内存读，代价可忽略），替换 `TARGET_RANGE = 32.0` 与 `movementSpeed() = 1.0` 两个魔法数。

> **解耦说明**：`squad_target_range` 只控制**小队驱动**的索敌距离（AiService 是否驱动车辆逼近）；原版 `FOLLOW_RANGE` 属性（`follow_range` 倍率）控制生物自身原版索敌。两者独立——作者改 `follow_range` **不会**改小队驱动范围，属有意解耦。

---

## 4. L1 / L3 文档 + L4 单测

- **L1**：不改行为（现状 ≤5 tick 扫描自愈，安全且有界）；`mountPassengerTree` 失败分支补注释：「已 remove 但仍在 livingMobs，由扫描回收；孙乘客弹飞成散怪」。
- **L3**：`WaveEngine.handleMobDeath` 补注释：「坐骑死 → squad 移除、乘客弹飞成独立追踪怪继续战斗（原版 AI 索敌）」。
- **L4 单测**（JUnit 5，无 Mockito，纯逻辑）：
  - `MountControllerResolverTest`：`controllerCandidates` 覆盖——显式 controller（含嵌套、多个 controller 取首个）、Camel 双叶 `[[0]]`、Camel 嵌套（先前座子树、非 Mob 落整树）、非 camel 最深叶、空 spec。
  - `MountPassengerLimitsTest`：`directPassengerLimit(EntityType)`（见 §5.5，生产实方法）——**CAMEL→2、CAMEL_HUSK→2**（Camel 族两边都断言）、HORSE→1、STRIDER→1、未知类型默认 1。
  - `MountedSquadRegistry` 薄 map 封装依赖 `AbstractGame` 构造不可测，不单测；靠 R2 两处显式调用 + 构建验证。

---

## 5. R4：坐骑自然冲锋（复刻原版蜘蛛骑士/僵尸骑士）

### 5.1 根因

- 马/骆驼的寻路速度走 **`AbstractHorse` 内部 speed 字段（`getSpeed()`/`setSpeed()`）**，**不走** `MOVEMENT_SPEED` 属性 → `coeff: { speed: 2.0 }` 对马/骆驼导航无效；`moveTo(target, 1.0)` 用内部 speed 字段（踱步档）→ 不出冲刺。
- `prepareMount` 对 `AbstractHorse` 调 `setTamed(true)` 改变 AI 状态，可能压制原版骑乘行为（原版僵尸骑士是未驯服状态）。

### 5.2 车辆驱动

- **冲刺坐骑判定**：运行时 `root instanceof AbstractHorse`（族分类，**不维护 EntityType 白名单**——符合铁律③；`Camel`/`CAMEL_HUSK` 预期均落此族，测试服确认）。
- `MountedSquadAiService.tick`：
  - 冲刺坐骑：`setSprinting(true)` + `moveTo(target, squad_charge_speed)`（默认 **4.0**）。
  - 普通坐骑：`moveTo(target, squad_move_speed)`（默认 **1.0**）。
  - ⚠️ **`setSprinting(true)` 对马可能只生效动画**，真正提速靠 `moveTo(target, charge_speed)` 倍率；实现时不得把 sprint 当冲刺充要条件（速度以倍率为准）。
- **不给 root 单独 `setTarget`**——保留 `2026-07-25-smoke-fixes §1.7`「根不是 controller 时仅 pathfind、不 setTarget（避免 AI 冲突）」的既有决策；冲锋速度完全由 `moveTo(charge_speed)` 提供，`moveTo` 本身处理转向。若测试服发现车辆不转向/原版骑手近战依赖车辆 target，**另行评估**「仅冲刺坐骑 setTarget」的局部例外（记 follow-up，不挡本 spec）。
- 文档注明：**`coeff.speed` 对马/骆驼无效**，内容作者需用 `squad_charge_speed` 调节。

### 5.3 骑手战斗

- `controller.setTarget(target)` 保留（controller 举矛 + 原版近战目标）。
- 车辆以冲刺速度把骑手送入近战范围 → 骑手原版攻击目标触发：举矛（持武器 + 有目标 → 举手姿态）、到范围挥矛。
- **不加自定义伤害逻辑**——目标是复刻原版；原版乘客近战在车辆贴脸时是否出手**需实测确认**（见 §6 Must/Stretch 拆分）。
- 多骑手：controller 被 `setTarget`；其余骑手靠原版索敌（不改）。

### 5.4 `prepareMount` 去驯服

- 删除 `horse.setTamed(true)`（保留 `setAdult()`）。理由：原版僵尸骑士/骆驼队是**未驯服**状态；驯服改 AI 状态可能压制骑乘行为；车辆由我们 moveTo 驱动，驯服无必要。
- ⚠️ 验证：去驯服后马/骆驼不会乱跑/甩骑手（不死马不甩人，骆驼由 moveTo 覆盖）。

### 5.5 `directPassengerLimit` 改纯 EntityType 实现（生产实方法）

```java
public static int directPassengerLimit(EntityType type) {
    if (type == EntityType.CAMEL || type == EntityType.CAMEL_HUSK) return 2;  // Camel 族（CAMEL_HUSK 是 Camel 子类，座位须随族）
    return 1;                                  // 其余含全部 AbstractHorse 子类/Strider，默认 1
}
public static int directPassengerLimit(LivingEntity le) { return le == null ? 0 : directPassengerLimit(le.getType()); }
```

生产方法（`carrier.getType()` 调用），纯可单测，且把既有 `instanceof Camel/AbstractHorse/Strider` 分支收敛成封闭映射。**Camel 族必须显式列全 `CAMEL || CAMEL_HUSK`**——只写 `EntityType.CAMEL` 会把 `s1_desert_four_camels` 的 CAMEL_HUSK 打成 1、第二乘客被跳过，破坏「现有内容零改动」。若未来内容新增 Camel 族实体类型，须同步加入本分支与单测（见 §8）。

---

## 6. 验证步骤（Must / Stretch 拆分）

测试服同时生成，与原版自然生成的持矛僵尸骑士逐项对比：

1. 现网内容 **`w2_endless_spear`**（`waves.yml:513-523`）：`ZOMBIE_HORSE` 根（repeat 3）+ 持 `mob_iron_spear` 的 `ZOMBIE` 乘客（`coeff: { hp: 2.0, speed: 2.0, scale: 1.3 }`）。
2. 沙漠骆驼队 **`s1_desert_four_camels`**（`waves.yml`）：`CAMEL_HUSK` 根 + HUSK/PARCHED 双乘客。

**Must（不满足则本 spec 不合入）：**
- ① 车辆位移明显提速（对比踱步），冲刺坐骑达到≈原版僵尸骑士的追击速度；
- ② controller（持矛僵尸）有目标时**举矛姿态**（若原版有该姿态）；
- ③ `squad_charge_speed` 可在测试服调到合适档（校准默认 4.0）。

**Stretch（失败记 follow-up，不挡合入）：**
- ④ 贴脸**挥矛造成伤害**——依赖原版乘客骑乘时近战是否出手，可能需后续 spec 追加自定义近战驱动（本 spec 不含）。

---

## 7. 改动文件清单

| 文件 | 改动 |
|---|---|
| `mob/MountedSquadRegistry.java` | 按 game 键控 + `removeGame` |
| `mob/MountedSquadAiService.java` | 只遍历本局 squad + 读配置参数 + 冲刺驱动（`instanceof AbstractHorse`）|
| `mob/MobFactory.java` | `spawnStepGroup`/`mountPassengersDelayed` 加 game 参数 + L1 注释 |
| `mob/MountControllerResolver.java` | 拆纯 `controllerCandidates` + 薄运行时；删三递归 |
| `mob/MountPassengerLimits.java` | `directPassengerLimit(EntityType)` 生产实现（**Camel 族 CAMEL/CAMEL_HUSK→2**）+ 去 `setTamed` |
| `wave/WaveEngine.java` | `clearTracking` 与 `stop` 各加 `removeGame`；`stop` 删逐根 removeByRoot；L3 注释 |
| `wave/PassengerSpawn.java` | 加 `controller` 字段（**所有构造**） |
| `config/PassengerCfg.java` | 加 `controller` 字段（**所有构造**） |
| `config/MobYamlParser.java` | 解析 `controller` + 多处声明 warning |
| `plan/RunPlanner.java` | 透传 `controller`（**所有 `new PassengerSpawn` 调用点**） |
| `resources/config.yml` | `squad_target_range` / `squad_move_speed` / `squad_charge_speed` |
| `src/test/.../MountControllerResolverTest.java` | 新增 |
| `src/test/.../MountPassengerLimitsTest.java` | 新增 |

**适用范围修正**：`controller` 字段活在 `PassengerSpawn`/`PassengerCfg` 上，**亡语树同样解析**——`DeathSpawn.passengers()` 与 `spawnOnDeath` → `mountPassengersDelayed` 会挂乘客并 register squad，死亡召唤的骑乘树同样走 `controllerCandidates`。`DeathSpawn.java` 本身无需加字段。

不触碰：`InfernalMobsBridge`、`MobDeathListener`、`SummonExecutor`、`WaveSpec`。

---

## 8. 风险与遗留

- **冲刺倍率需实测校准**：`squad_charge_speed: 4.0` 是初值（用户指定：以踱步为基础 ×4）；测试服对比原版后可能调整；`setSprinting(true)` 与倍率叠加可能过冲（sprint 非充要条件，见 §5.2）。
- **`setTamed(true)` 删除**：需确认马/骆驼去驯服后不乱跑、不甩骑手。
- **Stretch ④（贴脸挥矛）**：依赖原版乘客骑乘时近战是否出手，若失败记 follow-up（自定义近战驱动）不挡合入。
- **root 不 setTarget**：若测试服发现车辆不转向或原版骑手近战依赖车辆 target，另行评估「仅冲刺坐骑 setTarget」局部例外（§5.2）。
- **`CAMEL_HUSK`/`PARCHED` 实体类型**：默认 `waves.yml` 使用，须在 Paper 26.2 存在且 `CAMEL_HUSK` 落 `AbstractHorse` 族（M4 已排除，不改解析行为，仅记录 + 测试服确认）。
- **座位上限按 Camel 族**：`directPassengerLimit` 显式列出 `CAMEL || CAMEL_HUSK → 2`。若未来内容新增 Camel 族实体类型，**必须**同步加入该分支与 `MountPassengerLimitsTest`，否则误打成 1、第二直接乘客被跳过（违反「现有内容零改动」）。
