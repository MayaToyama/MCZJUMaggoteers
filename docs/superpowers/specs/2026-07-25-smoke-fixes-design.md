# 2026-07-25 Smoke 修复与骑乘步扩展设计

> 来源：测试服 smoke（骆驼无乘客、AURA 无效、旋风斩粒子、夜视、隐身可见性）+ 用户确认坐骑 **`CAMEL_HUSK`**、**Act1/2/3 strong 池均 roll 多种骑乘波**（选项 C）。

## 目标

1. **骑乘步**：任意 `steps[].type` + **`passengers[]`（可递归嵌套）** 可靠生成「看起来正常骑乘、会追玩家」的组合；**链上每一层活体**均计入清波追踪。
2. **内容**：除尸壳骆驼小队外，补充 **僵尸马**、**炽足兽** 骑乘 strategy，三层 strong 池均可 roll。
3. **AURA**：三选一被动光环在 Paper 26.2 下正常施加 attribute/potion。
4. **旋风斩**：可见旋转剑气粒子 + 合适音效（不用上升 anger 粒子）。
5. **夜视**：进局冒险模式玩家默认夜视（配合 `world.time_lock: night`）。
6. **隐身词缀**：怪本体隐身，**头盔空玻璃瓶** 作为可见标记（对齐服内另一玩法）。

---

## 1. 骑乘步（Mounted Step）

### 1.1 配置契约（铁律②）

**Step（坐骑根）** 与 **乘客条目** 使用同一字段子集：`type`、`count`、`coeff`、`affixes`、可选 **`passengers`**（嵌套）。乘客 **无** `point`/`delay`。

扁平示例（尸壳骆驼双乘客）：

```yaml
- point: "1"
  type: CAMEL_HUSK
  count: 1
  coeff: { hp: 2.2, dmg: 1.0, speed: 1.0 }
  delay: 0
  affixes: []
  passengers:
    - { type: ZOMBIE, count: 1, coeff: { hp: 1.0, dmg: 1.35, speed: 1.0 }, affixes: [toxic] }
    - { type: SKELETON, count: 1, coeff: { hp: 0.85, dmg: 1.25, speed: 1.1 }, affixes: [] }
```

**嵌套示例（炽足兽 → 炽足兽 → 怪物）**——v1 必须支持，仅 YAML、无新 Java 枚举：

```yaml
- point: "2"
  type: STRIDER
  count: 1
  coeff: { hp: 2.0, dmg: 1.0, speed: 1.1 }
  delay: 0
  passengers:
    - type: STRIDER
      count: 1
      coeff: { hp: 1.5, dmg: 1.0, speed: 1.0 }
      passengers:
        - { type: ZOMBIFIED_PIGLIN, count: 1, coeff: { hp: 1.0, dmg: 1.3, speed: 1.0 }, affixes: [] }
```

**语义**：

- 某条目的 **`passengers` 仅挂在该条目生成的实体上**（直接乘客）；载客上限按 **该实体** 的族规则（炽足兽直接乘客 **1** → 上例合法）。
- **`count`**：在该节点生成 N 个同构子树（每个复制同一嵌套 `passengers`）；每个仍受直接载客上限约束。
- **最大嵌套深度**：**8**（`WavesConfig` 解析时校验，超限报错指 strategy/id）。
- `RunPlanner`：递归展开为 `PassengerSpawn` **树**（或等价结构）；每层 coeff×affix×scaling、`count` 缩放与现规则一致。
- `WaveEngine`：对树 **DFS 扁平化** 得到的 **全部** `LivingEntity` UUID 加入 `livingMobs`；任一层死亡均减计数，**全部清空才清波**。

### 1.2 运行时：`MobFactory` + 延迟挂载

**问题**：同 tick `spawn` 后立即 `addPassenger` 在部分实体（骆驼/马）上不稳定，smoke 表现为「只有坐骑、无乘客」。

**做法**：

1. 主线程生成坐骑，应用 coeff/词缀/`MountSetup`（见 1.3）。
2. 登记待挂载队列：`PendingMount(mountUuid, world, passengerSpawns, stepIndex)`。
3. **下一 tick**（`runTaskLater(1L)`）：若坐骑仍存活，对 `passengerSpawns` **树**执行 `mountPassengerTree(carrier, nodes)`（见 **§1.6**）；每生成实体 coeff/词缀/隐身瓶/`prepareMount`（若该节点也是可骑乘族）；UUID 在生成时立即交给 `WaveEngine` 登记。
4. 若 `addPassenger` 失败：打 **WARNING**（strategy id、point、mount type、passenger type）。
5. **载客上限**：仅限制 **`carrier.addPassenger(directChild)`** 的直接子节点数；嵌套子树在各自父节点上再判上限。

**追逐玩家（§1.7 骑乘小队 AI）**：

- 全体 `Mob`：`setAware(true)`、`setRemoveWhenFarAway(false)`。
- **决策实体（controller）** 负责选目标、驱动整队追击；**根坐骑**负责位移（Pathfinder 追 controller 的目标/位置）；链上其余乘客保留攻击判定（原版 passenger 攻击）。
- 马匹族：`setTamed(true)`、`setAdult(true)`。
- **不再**采用「仅根坐骑 AI、乘客只攻击」的旧假设。

### 1.7 骑乘小队 AI 控制（v1）

原版中 passenger 上的 Mob 往往 **不会** 带着载体一起追人；v1 用插件 **登记小队 + 心跳同步**（与 `WaveEngine` 5-tick 扫描同频或 `GameplayTickListener` 1s，实现取一，建议 **5 tick** 与清波扫描对齐）。

**Controller 选取规则**（挂载完成后一次性计算，存 `MountedSquad(rootUuid, controllerUuid)`）：

| 场景 | Controller |
|------|------------|
| 嵌套栈（如炽足兽→炽足兽→猪灵） | 乘客树中 **深度最大** 的 `Mob`（最顶端/叶子）；同深度按配置 DFS **先序** 第一个 |
| 根为 **`Camel` / `CamelHusk`** 且 **仅 2 个直接乘客、均无 nested `passengers`** | **前座**：`passengers[]` 配置 **第一项** 生成的实体（文档约定 = 前座；挂载顺序保证该项先 `addPassenger`） |
| 根为 **Camel 系** 且任一直接乘客 **带 nested** | **前座子树** 的 controller：对 `passengers[0]` 子树套用「嵌套栈」规则（最深 Mob）；后座不参与控制 |
| 仅 1 个直接乘客 | 该乘客子树的 controller（若无 nested 即该乘客本身） |
| 根坐骑无乘客 | 根坐骑自身为 controller |

**每 tick/5tick 同步**（`MountedSquadAiService`）：

1. 若 controller 或 root 已死/无效 → 注销小队。
2. 在 controller 或 root 位置 **选取最近冒险模式玩家**（与波次怪一致的范围，如 32 格）作为 `target`。
3. **Root 坐骑**（`Mob`）：`getPathfinder().moveTo(target, speed)`（speed 取根实体 movement 属性或 coeff 后速度）。
4. **Controller**（`Mob`）：`setTarget(target)`，保证远程/近战由 **顶端/前座** 发起攻击。
5. 根坐骑 **不** 与 controller 抢目标：若根不是 controller，根仅 pathfind，**不**单独 `setTarget`（避免 AI 冲突）。

**登记时机**：`mountPassengerTree` 结束 + 根 step 坐骑生成后，由 `MobFactory`/`WaveEngine` 调用 `MountedSquadRegistry.register(root, controller)`。

**配置**：无需新 YAML 字段；换 controller 规则 = 调整 `passengers` 顺序（骆驼前座）或嵌套深度（栈顶）。

**验收**：炽足兽塔追玩家时 **顶层怪转向/攻击**、底层炽足兽跟随移动；尸壳骆驼双乘客时 **`passengers[0]`** 转向攻击、骆驼随之前进。

### 1.3 `MountSetup`（按实体**族**，非坐骑白名单）

**原则（铁律③）**：Java **禁止**维护「允许骑乘的 EntityType 枚举」。任意 `GameRegistries.entityType` 可解析的类型均可写进 `waves.yml` 的 `type:`；新坐骑 **只加 YAML**，除非该族在 vanilla 上需要后处理（驯服、体型等）才扩展 `MountSetup` 的 **instanceof 分支**（与具体马/骆驼种类无关）。

| 族 | 识别方式 | 动作 |
|----|----------|------|
| `Camel` | `instanceof Camel`（含 **`CAMEL`、`CAMEL_HUSK`**） | 通用 Mob；载客上限 **2**（见 1.5） |
| `AbstractHorse` | `instanceof AbstractHorse`（含 **`HORSE`、`ZOMBIE_HORSE`、`SKELETON_HORSE`** 等） | `setAdult(true)`、`setTamed(true)`；载客上限 **1** |
| `Strider` | `instanceof Strider` | 通用 Mob；载客上限 **1** |
| `Slime` / `MagmaCube` | `instanceof Slime` / `MagmaCube` | 可选默认 `setSize(≥2)`（smoke 定默认值，便于骑乘可见；**不**在 YAML 暴露 size v1） |
| 其余（熊、蜘蛛、猪兽、劫掠兽、海豚、守卫者等） | fallback | 仅 `setAware(true)`、`setRemoveWhenFarAway(false)` + coeff/词缀 |

新增 `prepareMount(LivingEntity mount)`，在 `spawnMount` 末尾调用。乘客与坐骑共用词缀/隐身瓶逻辑。

### 1.4 隐身词缀（与目标 §6 一致）

`applyAffixVisuals(le, affixIds)`：若含 `invisible`，头盔 `GLASS_BOTTLE`（`EquipmentSlot.HEAD`，drop 率 0）。坐骑与乘客均走 `applyName` + 词缀药水 + 视觉。

### 1.5 策划坐骑目录（**不必全部进 waves.yml**）

以下为用户期望可作 **骑乘步 `type`** 的坐骑；实现后 **仅配配置即可出场**，无需为某一种单独写 Java。

| 配置 `type`（EntityType） | 说明 | 建议载客上限（实现常量/族规则） |
|---------------------------|------|----------------------------------|
| `ZOMBIE_HORSE` | 僵尸马 | 1 |
| `SKELETON_HORSE` | 骷髅马 | 1 |
| `HORSE` | 马 | 1 |
| `POLAR_BEAR` | 北极熊 | 1 |
| `STRIDER` | 炽足兽 | 1 |
| `SPIDER` | 蜘蛛 | 1 |
| `HOGLIN` | 疣猪兽 | 1 |
| `ZOGLIN` | 僵尸疣猪兽 | 1 |
| `RAVAGER` | 劫掠兽 | 1 |
| `CAMEL` | 骆驼 | **2** |
| `CAMEL_HUSK` | 僵尸骆驼（26.2） | **2** |
| `MAGMA_CUBE` | 岩浆怪 | 1（大体型用 Slime 族 size 处理） |
| `SLIME` | 史莱姆 | 1 |
| `DOLPHIN` | 海豚 | 1 |
| `GUARDIAN` | 守卫者 | 1 |
| `ELDER_GUARDIAN` | 远古守卫者 | 1 |

- **乘客类型**同样不限枚举：任意 `EntityType` + `passengers[]`。
- 若某组合在 26.2 上 `addPassenger` 失败，打 WARN 日志；**不**在 Java 里禁用该组合。
- **v1 进池内容**仍仅 §2 三条 strategy（尸壳骆驼 / 僵尸马 / 炽足兽）；其余坐骑留给后续 `waves.yml` 与地图 `special_waves.yml`。

**可选后续（非 v1）**：step 上 `passenger_slots: N` 覆盖该 step 根坐骑的直接载客上限。

### 1.6 嵌套乘客挂载算法（v1）

对载体 `carrier` 与配置节点列表 `nodes`（已解析为 `PassengerSpawn` 树）：

```
mountPassengerTree(carrier, nodes):
  slots = remainingDirectSlots(carrier)
  for each node in nodes (配置顺序):
    repeat node.count times (after scaling):
      if slots == 0: WARN; break
      child = spawnPassengerEntity(node)   // coeff/affix/视觉/prepareMount
      track(child)
      mountPassengerTree(child, node.passengers())  // 先挂子树到 child
      if !carrier.addPassenger(child): WARN
      else slots--
```

- **顺序**：对每个直接子节点，**先**递归挂好其 nested，**再** `carrier.addPassenger(child)`，保证「内层叠在内层实体上、最外层才挂到父载体」。
- **骆驼双乘客**：同一 `carrier` 上按配置顺序 `addPassenger`；**列表第一项 = 前座**（§1.7 controller）。
- 挂载结束：计算 controller UUID → `MountedSquadRegistry.register(rootMount, controller)`（§1.7）。
- **底层 step 坐骑**不在此函数内生成；tick+1 仅对其 `step.passengers()` 调用 `mountPassengerTree(rootMount, …)`。

**Java 新增（v1）**：

- `mob/MountedSquadRegistry` + `mob/MountedSquadAiService`（或并入 `WaveEngine` 5-tick 循环，避免多计时器）。
- 挂载流程返回或回调 **controller UUID** 供登记。

**模型改动（一次）**：`PassengerCfg` / `PassengerSpawn` 增加递归 `List<…> passengers()`；`WavesConfig.parsePassengers` / `RunPlanner` 递归展开；**不**为 STRIDER/Camel 写挂载特例（controller 规则在 §1.7 统一）。

---

**池**：`act1/2/3` → `strong` 均增加（权重与 `s1_camel_squad` 同级，均为 `weight: 2`）：

- `s1_camel_squad`（**改坐骑** `CAMEL` → `CAMEL_HUSK`，乘客配置保留）
- `s1_zombie_horse_squad`（**新增**）
- `s1_strider_squad`（**新增**）

> 策略 id 保持 `s1_` 前缀与现文件一致；三层共用同一 strategy 定义（难度由全局 scaling / 层数另途体现）。若后续要 Act2/3 加强系数，可再拆 `s2_`/`s3_` 副本。

### 2.1 `s1_camel_squad`（修订）

- Step1/2：`type: CAMEL_HUSK`；乘客不变。**`passengers[0]` 放应在前座控制的小队**（如僵尸），`passengers[1]` 为后座（如骷髅）。

### 2.2 `s1_zombie_horse_squad`（新增）

示例（可调）：

```yaml
s1_zombie_horse_squad:
  repeat: 1
  clearReward: [ { item: maggoteers:currency_normal, amount: 4 } ]
  steps:
    - point: "1"
      type: ZOMBIE_HORSE
      count: 1
      coeff: { hp: 1.8, dmg: 1.0, speed: 1.15 }
      delay: 0
      passengers:
        - { type: SKELETON, count: 1, coeff: { hp: 0.9, dmg: 1.4, speed: 1.0 }, affixes: [] }
    - point: "3"
      type: ZOMBIE_HORSE
      count: 1
      coeff: { hp: 1.8, dmg: 1.0, speed: 1.2 }
      delay: 8
      passengers:
        - { type: HUSK, count: 1, coeff: { hp: 1.1, dmg: 1.35, speed: 1.0 }, affixes: [toxic] }
```

### 2.3 `s1_strider_squad`（新增）

- **Step1**：嵌套炽足兽塔 + 顶猪灵（覆盖 §1.1 嵌套 smoke）。
- **Step2**：扁平炽足兽 + 烈焰人（可选，保留一种简单组合）。

```yaml
s1_strider_squad:
  repeat: 1
  clearReward: [ { item: maggoteers:currency_normal, amount: 4 } ]
  steps:
    - point: "2"
      type: STRIDER
      count: 1
      coeff: { hp: 2.0, dmg: 1.0, speed: 1.1 }
      delay: 0
      passengers:
        - type: STRIDER
          count: 1
          coeff: { hp: 1.5, dmg: 1.0, speed: 1.0 }
          passengers:
            - { type: ZOMBIFIED_PIGLIN, count: 1, coeff: { hp: 1.0, dmg: 1.3, speed: 1.0 }, affixes: [] }
    - point: "4"
      type: STRIDER
      count: 1
      coeff: { hp: 2.0, dmg: 1.0, speed: 1.15 }
      delay: 10
      passengers:
        - { type: BLAZE, count: 1, coeff: { hp: 0.85, dmg: 1.25, speed: 1.0 }, affixes: [] }
```

**实体名**：均通过 `GameRegistries.entityType`（`camel_husk`、`zombie_horse`、`strider` 等）。

### 2.4 `WaveEngine` 与延迟乘客追踪

若乘客在 tick+1 才生成，**追踪登记**必须在乘客生成后执行：  
`spawnStep` 改为「坐骑立即 track + 延迟任务内 track 乘客」，或 `spawnStepGroup` 拆为两阶段回调。**禁止**乘客未生成就已清波计数偏少。

---

## 3. AURA（三选一被动光环）

### 根因

Paper 26.2 `AttributeModifier.getKey()` 为 Adventure `Key`；`AuraService` 使用 `NamespacedKey.equals` / 旧构造路径导致每秒 refresh **无法稳定 add/remove modifier**，表现为光环无属性/药水、环粒子也可能中断。

### 修复

- 新增小工具类（如 `io.mczju.maggoteers.effect.AttributeModifierKeys`）：
  - `NamespacedKey toBukkitKey(Key)` / `boolean matches(Modifier m, NamespacedKey expected)`
  - `void replaceModifier(AttributeInstance inst, NamespacedKey key, double value, Operation op)`
- `AuraService.applyAttributeDerived` / `stripAuraDerived` 与 `EffectService.stripDerived` **统一** Key 语义。
- 单测可选：纯逻辑测 key 匹配（若可 mock-free 则手测）。

### 验收

- 休整期选 `a1s_ring_strength` / `a1s_ring_regen` / `a1s_slow_field`：范围内友/敌有对应效果；`debug effects` 见 `AURA` 条目。

---

## 4. 旋风斩 `SPIRAL_RADIUS`

- **粒子**：`CRIT` 或 `SWEEP_ATTACK`（配置 + `items/maggoteers.yml` + `magic_fx.weapons`）；移除 `ANGRY_VILLAGER`。
- **动画**：2–3 条弧段随玩家 yaw 旋转；`spiral_ticks` 默认 **40–48**；每 tick 粒子数低于现 14 辐条×满半径。
- **音效**：默认 `ENTITY_PLAYER_ATTACK_SWEEP`（可配置）。

---

## 5. 进局夜视

- `config.yml`：`player.night_vision: true`（默认 true）。
- 就绪门闩内对每名队员：`PlayerState` 添加常驻 `ADD_POTION`（`NIGHT_VISION`，duration 常驻/刷新策略与现有 `EffectService.resync` 一致）。
- 观察者/局外不施加。

---

## 6. 测试清单

| # | 步骤 |
|---|------|
| 1 | Act1/2/3 strong roll 三种骑乘波；**炽足兽 step1 目视双层炽足兽+顶怪**；F3 乘客链；杀光才清波 |
| 2 | 僵尸马/炽足兽追玩家：**栈顶/前座转向攻击**，根坐骑跟随；非根坐骑乱转 |
| 3 | 三选一 AURA 属性/药水/减速+头顶标记 |
| 4 | 旋风斩旋转可见 |
| 5 | 进图夜视；死亡复活后仍在 |
| 6 | `invisible` 词缀：空瓶在头、身体隐身 |

---

## 7. 非目标

- **不做** Java 侧「坐骑类型白名单」；§1.5 实体均靠 YAML 启用。
- 不做乘客独立 pathfinding（不脱离 mount AI）。
- 不在此 spec 把 §1.5 全部坐骑写入 `waves.yml`（v1 仅 §2 三条骑乘 strategy）。
- 不在此 spec 改 Boss 池或 weak 池组成。

---

## 8. 实现顺序建议（供 plan 用）

1. `AttributeModifierKeys` + AURA 修复（独立可测）  
2. `PassengerCfg`/`PassengerSpawn` 递归 + 骑乘步延迟挂载 + `mountPassengerTree` + `MountSetup` + `MountedSquadRegistry`/`MountedSquadAiService` + WaveEngine 全树追踪  
3. `waves.yml` 三 strategy + 池引用 + `CAMEL_HUSK`  
4. 隐身瓶 + 夜视 + 旋风斩配置/预设  
5. 测试服打包 smoke  

---

*Spec 版本：2026-07-25（§1.7 顶端/前座 AI 控制）。递归 passengers v1；三层 strong 混 roll。*
