# Mounted Squad Hardening（骑乘小队加固）Design

> **Status:** Draft — pending user spec review
> **Date:** 2026-08-17
> **Revision:** v1.0
> **Depends on:** 无（独立加固；基于 `docs/spec/2026-07-02-maggoteers-design.md` §1.7 骑乘小队）
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
| M1 | 小队索敌距离 `TARGET_RANGE=32.0` 与驱动速度 `1.0` 硬编码，与可配置 `follow_range` 脱节 | 中 |
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

### 1.2 R2 统一清理

- 新增 `removeGame(game)`，在 **`WaveEngine.clearTracking(game)`** 里调用（它被 `killAllTracked` 和 `stop` 共用 → 两个入口同时覆盖）。
- `stop()` 循环里的逐怪 `removeByRoot` 删除，交给 `clearTracking` 的 `removeGame` 兜底。
- `handleMobDeath` 保留逐根 `removeByRoot(game, uuid)`（战斗中按怪清理）。

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

### 2.2 resolver 重构：纯选择 + 薄运行时

```java
// 纯函数（无 Bukkit 依赖，可单测）：
//   返回 controller 的「索引路径」（如 [0, 1] = 直接乘客0 的 乘客1），无则 null
static List<Integer> pickControllerPath(List<PassengerSpawn> spec, boolean rootIsCamel)
//   规则：
//   1) 任意节点 controller()==true → 返回第一个这样的路径（深度优先）
//   2) 否则 rootIsCamel && size==2 && 双叶 → [0]
//   3) 否则 → 全树最深叶的路径
//   4) 全空 → null

// 运行时薄封装（现有 resolve 签名不变）：
//   path = pickControllerPath(spec, root instanceof Camel)
//   沿 path 走真实实体树，若最终实体是 Mob && isValid && !isDead → 返回
//   否则回落 root
```

现有 `deepestMob` / `maxDepth` / `collectDepth` 三个递归全部删除，由纯路径选择取代。现有 `waves.yml` 零改动（无声明时走回落 2/3）。

---

## 3. M1：小队参数配置化

`config.yml` 现有 `mob_attributes:` 块下新增（跟随 `scale` / `follow_range` 既有风格）：

```yaml
mob_attributes:
  scale: 1.0
  follow_range: 1.0
  squad_target_range: 32.0    # 新增，默认 32（等价当前硬编码）
  squad_move_speed: 1.0       # 新增，普通坐骑 moveTo 倍率
  squad_charge_speed: 4.0     # 新增，冲刺坐骑（马/骆驼等）moveTo 倍率（≈冲刺）
```

`MountedSquadAiService.tick` 每拍读一次（Bukkit config 内存读，代价可忽略），替换 `TARGET_RANGE = 32.0` 与 `movementSpeed() = 1.0` 两个魔法数。

---

## 4. L1 / L3 文档 + L4 单测

- **L1**：不改行为（现状 ≤5 tick 扫描自愈，安全且有界）；`mountPassengerTree` 失败分支补注释：「已 remove 但仍在 livingMobs，由扫描回收；孙乘客弹飞成散怪」。
- **L3**：`WaveEngine.handleMobDeath` 补注释：「坐骑死 → squad 移除、乘客弹飞成独立追踪怪继续战斗（原版 AI 索敌）」。
- **L4 单测**（JUnit 5，无 Mockito，纯逻辑）：
  - `MountControllerResolverTest`：`pickControllerPath` 覆盖——显式 controller（含嵌套）、Camel 双叶回落 `[0]`、最深叶回落、空 spec 返回 null。
  - `MountPassengerLimitsTest`：`directPassengerLimit(EntityType)` 纯重载（Camel=2、AbstractHorse=1、Strider=1、其他=1）+ `isSprintMount(EntityType)`（见 §5）。
  - `MountedSquadRegistry` 是薄 map 封装，依赖 `AbstractGame` 构造不可测，不单测；靠 R2 调用点一致性 + 构建验证。

---

## 5. R4：坐骑自然冲锋（复刻原版蜘蛛骑士/僵尸骑士）

### 5.1 根因

- 马/骆驼的寻路速度走 **`AbstractHorse` 内部 speed 字段（`getSpeed()`/`setSpeed()`）**，**不走** `MOVEMENT_SPEED` 属性 → `coeff: { speed: 2.0 }` 对马/骆驼导航无效；`moveTo(target, 1.0)` 用内部 speed 字段（踱步档）→ 不出冲刺。
- `prepareMount` 对 `AbstractHorse` 调 `setTamed(true)` 改变 AI 状态，可能压制原版骑乘行为（原版僵尸骑士是未驯服状态）。

### 5.2 车辆驱动

- 新增纯分类器 `isSprintMount(EntityType)`（`MountPassengerLimits`）：`CAMEL / HORSE / ZOMBIE_HORSE / SKELETON_HORSE / MULE / DONKEY` 为冲刺坐骑；运行时 `instanceof AbstractHorse` 兜底。
- `MountedSquadAiService.tick`：
  - 冲刺坐骑：`setSprinting(true)` + `moveTo(target, squad_charge_speed)`（默认 **4.0**）。
  - 普通坐骑：`moveTo(target, squad_move_speed)`（默认 **1.0**）。
  - `controller.setTarget(target)` 保留，**新增 `rootMob.setTarget(target)`**（车辆转向 + 触发原版目标 AI）。
- 文档注明：**`coeff.speed` 对马/骆驼无效**，内容作者需用 `squad_charge_speed` 调节。

### 5.3 骑手战斗

- 车辆以冲刺速度把骑手送入近战范围 → 骑手原版攻击目标触发：举矛（持武器 + 有目标 → 举手姿态）、到范围挥矛。
- **不加自定义伤害逻辑**——目标是复刻原版；原版乘客近战在车辆贴脸时本来就会出手。
- 多骑手：controller 被 `setTarget`；其余骑手靠原版索敌（不改）。

### 5.4 `prepareMount` 去驯服

- 删除 `horse.setTamed(true)`（保留 `setAdult()`）。理由：原版僵尸骑士/骆驼队是**未驯服**状态；驯服改 AI 状态可能压制骑乘行为；车辆由我们 moveTo 驱动，驯服无必要。
- ⚠️ 验证：去驯服后马/骆驼不会乱跑/甩骑手（不死马不甩人，骆驼由 moveTo 覆盖）。

---

## 6. 验证步骤

测试服同时生成，与原版自然生成的持矛僵尸骑士逐项对比（冲刺速度、举矛姿态、贴脸挥矛）：

1. 现网内容 **`w2_endless_spear`**（`waves.yml:513-523`）：`ZOMBIE_HORSE` 根（repeat 3）+ 持 `mob_iron_spear` 的 `ZOMBIE` 乘客（`coeff: { hp: 2.0, speed: 2.0, scale: 1.3 }`）。
2. 沙漠骆驼队 **`s1_desert_four_camels`**（`waves.yml`）：`CAMEL_HUSK` 根 + HUSK/PARCHED 双乘客。

对比项：① 车辆是否冲刺（对比踱步）；② 僵尸是否举矛；③ 贴脸是否挥矛造成伤害。据此校准 `squad_charge_speed`（当前默认 4.0）。

---

## 7. 改动文件清单

| 文件 | 改动 |
|---|---|
| `mob/MountedSquadRegistry.java` | 按 game 键控 + `removeGame` |
| `mob/MountedSquadAiService.java` | 只遍历本局 squad + 读配置参数 + 冲刺驱动 + `rootMob.setTarget` |
| `mob/MobFactory.java` | `spawnStepGroup`/`mountPassengersDelayed` 加 game 参数 + L1 注释 |
| `mob/MountControllerResolver.java` | 拆纯 `pickControllerPath` + 薄运行时；删三递归 |
| `mob/MountPassengerLimits.java` | `directPassengerLimit(EntityType)` 重载 + `isSprintMount(EntityType)` + 去 `setTamed` |
| `wave/WaveEngine.java` | `clearTracking` 加 `removeGame`；`stop` 删冗余逐根；L3 注释 |
| `wave/PassengerSpawn.java` | 加 `controller` 字段 |
| `config/PassengerCfg.java` | 加 `controller` 字段 |
| `config/MobYamlParser.java` | 解析 `controller` |
| `plan/RunPlanner.java` | 透传 `controller` |
| `resources/config.yml` | `squad_target_range` / `squad_move_speed` / `squad_charge_speed` |
| `src/test/.../MountControllerResolverTest.java` | 新增 |
| `src/test/.../MountPassengerLimitsTest.java` | 新增 |

不触碰：`InfernalMobsBridge`、`MobDeathListener`、`SummonExecutor`、`WaveSpec`、`DeathSpawn`（死亡召唤不骑乘，`controller` 不适用）。

---

## 8. 风险与遗留

- **冲刺倍率需实测校准**：`squad_charge_speed: 4.0` 是初值（基于"踱步为基础 ×4"），测试服对比原版后可能调整；`setSprinting(true)` 与倍率叠加可能过冲。
- **`setTamed(true)` 删除**：需确认马/骆驼去驯服后不乱跑、不甩骑手。
- **乘客原版骑乘近战**：依赖原版乘客在车辆贴脸时出手；若实测发现乘客骑乘时不挥矛，需在后续 spec 追加自定义近战驱动（本 spec 不含）。
- **`CAMEL_HUSK`/`PARCHED` 实体类型**：默认 `waves.yml` 使用，须在 Paper 26.2 存在（M4 已排除，不改解析行为，仅记录）。
