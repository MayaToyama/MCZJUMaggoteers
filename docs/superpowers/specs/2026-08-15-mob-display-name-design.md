# 波次怪物配置显示名 + Boss 血条开关 + 拦截 InfernalMobs 命名

**日期：** 2026-08-15  
**状态：** 已实现（见 plan 2026-08-15-mob-display-name；待服内同步 waves.yml）  
**范围：** 只修改 Maggoteers；不修改 MCZJUInfernalMob 源码  
**配置审计基线：** `src/main/resources/waves.yml`（2026-08-15）

## 1. 问题

1. Boss / 精英缺少可配置的中文显示名；Boss 血条标题目前是 `EntityType.name()`（如 `CREEPER`）。
2. 挂 `infernal:` 后，IM 在 `mechanize` / `refreshDisplayName` 会写入「Infernal LvN + 类型名」，覆盖本插件意图。
3. 现有 `MobFactory.applyName` 把原生 `affixes` id 拼成头顶名（如 `plastic infected`），既非内容名，也与「无配置则不命名」冲突。
4. 现有 Boss 血条启发式（`count==1` && 无 passengers && `hpMult >= 6`）会把本不该挂条的高血单体当成 Boss。  
   **现网例子（勿写错数字）：** `w2_territory` 的 **boss** 苦力怕为 `coeff.hp: 10.0`（`scale: 2.5`），`hp >= 6` 即命中启发式；同波其余小苦力怕当前为默认 hp（无 ×100）。另如 `w3_duck_ops` 的 `hp: 10` 鸡/北极熊也会挂条。

## 2. 已确定决策

| 项 | 决策 |
|---|---|
| 用途 | 主要为 Boss / 精英起名；头顶与 Maggoteers Boss 血条标题共用配置名 |
| IM 命名 | 本插件刷出的怪（含 IM）一律禁止 IM 改名；仅当配置了 `name:` 才显示自定义名，否则保持原版无自定义名 |
| Boss 血条 | **废除启发式**；仅当配置 `boss_bar: true` 时挂条；省略或 `false` = 不挂。有条时标题优先 `name:`，否则类型名 |
| 实现路径 | 配置 `name:` / `boss_bar:` + **在 `mechanize` 之后** `MobDisplayNames.lock` + tick/短延迟纠偏；不反射改 IM |
| 文本格式 | MiniMessage；血条标题见 §4.4（Adventure BossBar） |
| 内容迁移 | **必须按附录 A 清单执行**（保住现网已有条 + 产品确认「今天没有条的真 Boss」） |
| 非目标 | 不改 IM 击杀播报内部文案；不加全局类型→默认名表；不保留旧启发式作回退 |

## 3. 配置格式

可选字段（均可写在任意会生成怪物的节点：step / passenger / on_death 及嵌套）：

| 字段 | 类型 | 默认 | 含义 |
|---|---|---|---|
| `name` | string (MiniMessage) | 省略 / 空白（**trim 后空**）= 无自定义名 | 头顶名；有 Boss 条时兼作标题 |
| `boss_bar` | boolean | `false`（省略同） | `true` 才挂 Maggoteers Boss 血条 |

```yaml
- point: boss
  type: CREEPER
  count: 1
  name: "<gold>巢主</gold>"
  boss_bar: true
  coeff: { hp: 10.0, scale: 2.5 }
  affixes: [infected]
  infernal: { level: 1, affixes: [refrigerate] }
```

`name` 规则：

- 省略，或字符串 **trim 后为空** → 无配置名（锁定清除自定义名）。
- 非字符串 → **加载硬失败**（指到 strategy / 节点上下文）。
- MiniMessage：加载期不做严格语法硬失败；运行时 `MiniMessage.deserialize`。

`boss_bar` 规则：

- 省略 → `false`。
- 仅接受 YAML 布尔（SnakeYAML `Boolean`）。**不**把字符串 `"true"` / `"false"` 当成合法值（出现则硬失败），避免静默歧义。
- 与 `count` / `hp` / passengers **无关**；`count > 1` 且 `boss_bar: true` → 该节点刷出的**每一只**各挂一条（内容自负；常规只给 `count: 1`）。
- 仅有 `name:` 不挂条；仅有 `boss_bar: true` 可挂条（标题回落类型名）。

## 4. 运行时架构

### 4.1 `MobDisplayNames`

新建 `mob/MobDisplayNames.java`：

- PDC（plugin namespace）：
  - `name_lock`（BYTE）：锁定命名策略。
  - `display_name`（STRING）：非空配置名的 MiniMessage 原文；无配置名时 **remove** 该 key（不用空串）。
- `lock(LivingEntity, String nameOrNull)`：有非空（已 trim）名 → Adventure `customName` + visible；否则清除名 + invisible；始终写 `name_lock`。
- `reassertIfNeeded(LivingEntity)`：有 `name_lock` 且当前显示与意图不符则再 `lock`。
- `lockedDisplayName(LivingEntity)`：供血条标题读取（有则返回 MM 串，无则 empty）。

### 4.2 刷怪时序（`MobFactory`）— 保持现网顺序

**不得**改成「先装备再 mechanize」。现网顺序保留为：

1. spawn + `applyMobStats`
2. ~~`applyName(affixes)`~~ → **删除**
3. `applyAffixPotions` / `applyAffixVisuals`
4. `InfernalMobsBridge.mechanize`（若有）
5. `applyEquipment`（**继续覆盖 IM 自动装备**）
6. **`MobDisplayNames.lock(le, configuredName)`**（必须在 `mechanize` **之后**；放在 equipment 之后即可）

同一顺序适用于 step / passenger / on_death，以及 **`spawnDebugMob`**（调试刷怪也必须 `lock`，否则测试服仍会看到 affix 拼接名或 IM 名）。

### 4.3 数据贯通

贯通 `String name`（null = 无）与 `boolean bossBar`：

- `StepCfg` / `PassengerCfg` / `DeathSpawnCfg`
- `SpawnStep` / `PassengerSpawn` / `DeathSpawn`
- `MobSpawnProfile`（亡语 `trackSpawned(..., step=null)` 时从 profile 读 `bossBar` / `name`）
- `MobYamlParser` / `WavesConfig` / `RunPlanner`

不参与缩放或随机。

### 4.4 Boss 血条（`WaveEngine`）

- **删除**启发式 `count==1 && passengers.isEmpty() && hpMult >= 6`。
- `trackSpawned`：若来源 step 或 `MobSpawnProfile.bossBar()` 为 true → `attachBossBar`。
- **标题 API（写死）：** 将 `WaveRuntime.bossBars` 从 Bukkit `org.bukkit.boss.BossBar`（`createBossBar(String, …)`）改为 **Adventure** `net.kyori.adventure.bossbar.BossBar`：
  - 标题：`MiniMessage.deserialize(name)`；无 `name` 时用 `Component.text(entityType.name())`（或等价纯文本）。
  - 进度：`bar.progress(health/max)`（0..1）。
  - 显示：`player.showBossBar(bar)`；清理：`player.hideBossBar(bar)` / 对全员 hide。
  - **禁止**把 MiniMessage 原文丢进 Bukkit `BossBar#setTitle(String)`（会原样显示标签或丢色）。
- `tick`（见 §4.5）：刷新进度 + `reassertIfNeeded`。

### 4.5 IM 名纠偏频率

- `WaveScheduler` 每 **5 tick** 调一次 `WaveEngine.tick` → 仅靠 tick 纠偏时，IM 若同 tick / 更频地 `refreshDisplayName`，玩家可能短暂看到「Infernal …」（最长约 5 tick）。
- **本版接受**该上限作为稳态兜底，并叠加刷怪后短延迟加固：
  - `lock` 之后 `runTask` / `runTaskLater(1L)` / `runTaskLater(2L)` 各再 `reassertIfNeeded` 一次（共至多 3 次短延迟），压掉 mechanize 当帧与紧随刷新。
- 若日后仍闪，再单开任务加密或钩 IM；本设计不要求每 tick 纠偏。

### 4.6 退役

- 删除 `MobFactory.applyName`。
- 删除血条启发式；未补 `boss_bar: true` 的怪不再有 Maggoteers 血条。

## 5. 错误与边界

| 情况 | 行为 |
|---|---|
| `name` 非字符串 / `boss_bar` 非布尔 | 加载失败 |
| `boss_bar: "true"`（字符串） | 加载失败 |
| IM 缺失 | 仍 `lock` |
| IM 事后刷新名 | 短延迟 reassert + 5-tick tick 兜底（§4.5） |
| 亡语/乘客未写 `name` | 锁定清除自定义名 |
| `boss_bar: true` + `count > 1` | 每只各一条 |
| 仅 `name` 无 `boss_bar` | 有头顶名、无 Maggoteers 血条 |
| `spawnDebugMob` | 必须走 `lock`（无名则清 IM/affix 名） |
| GRANT_ITEM / SUMMON | **不在本范围** |

## 6. 测试计划

- 解析：`name` / `boss_bar` 合法、省略、trim 空白、非法类型、字符串 `"true"`（失败）。
- `MobDisplayNames.lock`：有名/无名；覆盖后 `reassertIfNeeded`；短延迟路径可测。
- **有 `infernal:`、无 `name:` → 头顶无自定义名**（对齐验收 4；单测或桥接 mock）。
- Boss 条：仅 `boss_bar: true` 挂条；标题为 Adventure Component（有 `name` 带色；无则类型名）；高 `hpMult` 但无开关不挂条。
- 回归：无 `name` 无 affix 拼接名；`spawnDebugMob` 无名无 IM 头顶名。

## 7. 验收标准

1. `waves.yml` 可写 `name:`，头顶显示该名。
2. 本插件刷出的 IM 怪不出现 IM 默认「Infernal …」头顶名（允许 ≤5 tick 闪一下，稳态无）。
3. 仅 `boss_bar: true` 有 Maggoteers Boss 血条；标题与 `name:` 一致（有名时，含颜色）。
4. 未写 `name:` 保持无自定义名（含有 `infernal:`）。
5. 附录 A 清单中「须保住」项已补 `boss_bar: true`；「产品确认」项有结论并落盘。
6. 不修改 IM jar 作为硬依赖。

---

## 附录 A — 内容迁移清单（可执行）

> 实现 PR **必须**完成 A0 + A1，并对 A2/A3 给出产品勾选（写回本附录或 PR 说明）。  
> 旧启发式：`count==1` && step 无 `passengers` && `hpMult >= 6`。  
> **结构性缺口：** 带 `passengers` 的坐骑/高血主体、以及 `trackSpawned(step=null)` 的亡语/乘客路径，**今天本来就没有** Maggoteers 血条；废除启发式后也不会自动出现，除非显式 `boss_bar: true`（profile 贯通后才支持亡语/乘客挂条）。

### A0. 实现前再跑一遍审计

对 `waves.yml` 再扫：`hpMult>=6 && count==1 && 无 passengers` 的 step；以及所有 `point: boss` / 明显精英（含 `hp<6` 的 Boss）。以仓库当前文件为准，勿沿用过时数字（例如已不存在的 `hp: 100` 小苦力怕）。

### A1. 现网**已有**血条 → 废除后须补 `boss_bar: true` 才能保住（或明确放弃）

| strategy | 节点 | 类型 | hpMult | 备注 |
|---|---|---|---|---|
| `w2_nest` | `point: boss` | ENDERMITE | 10 | 巢穴 Boss |
| `w2_territory` | `point: boss` | CREEPER | 10 | 领地意识 Boss（例：可加 `name:`） |
| `s2_forbidden` | `point: "3"` | IRON_GOLEM | 6 | 非 boss 点，但现网有条 |
| `b2_frost_farewell` | `point: boss` ×2 | STRAY | 10 | 双 Boss step |
| `w3_duck_ops` | `point: "1"` | CHICKEN | 10 | 现网有条；**确认是否保留** |
| `w3_duck_ops` | `point: "2"` | POLAR_BEAR | 10 | 同上 |
| `s3_rhine_guard` | `point: "1"` SLIME（hp 6） | SLIME | 6 | 确认是否保留 |
| `b3_into_eternal` | `point: "5"` | PIGLIN | 25 | 高血精英，现网有条 |
| `b3_into_eternal` | `point: boss` | WITHER_SKELETON | 25 | Act3 Boss |
| `b3_sentinel` | `point: "1"` DROWNED | DROWNED | 6 | `repeat: 15` 单体高血，现网**每只**有条；**强烈建议产品决定是否还要条** |
| `b3_human_radiance` | `point: boss` | SLIME | 20 | Act3 Boss |

### A2. `point: boss` / 明显 Boss 但**现网无条**（hp&lt;6 或默认 1.0 等）→ 实现前产品确认是否新开 `boss_bar: true`

| strategy | 节点 | 类型 | 为何今天无条 |
|---|---|---|---|
| `b1_*` / 早期 Boss 等 | `point: boss` 且 `hp < 6` | 各异 | 启发式未命中（如 hp 0.2～3、或默认 1） |
| `b3_sentinel` | `point: boss` ELDER_GUARDIAN | ELDER_GUARDIAN | `coeff` 无 hp 倍率（=1 &lt; 6） |
| 其他 `point: boss` 且 hpMult&lt;6 | （实现时 A0 扫全表勾选） | | |

> 实现计划任务应包含：列出**全部** `point: boss` step，对每个勾选「补 `boss_bar: true` / 不挂条」。

### A3. 结构性缺口（现网从未挂 Maggoteers 条）→ 产品确认是否借本次补上

| 类型 | 例子 | 说明 |
|---|---|---|
| 带 `passengers` 的主体 | 坐骑波、部分精英 | 旧启发式直接排除 |
| 乘客本体高血 | `w2_fireworks` 上 `GHAST` hp 10 | 乘客路径；旧逻辑不挂条 |
| 亡语高血 | `b3_human_radiance` on_death `CREEPER` hp 20；`s2_grave_blind_box` 等 | `step==null`，旧逻辑不挂条 |

默认建议：**A3 本版可不挂**，除非玩法明确需要；若需要，在对应 passenger / on_death 节点写 `boss_bar: true`（依赖 profile 贯通）。

### A4. `name:` 内容

本版不强制全表起中文名；至少建议给 A1/A2 中确认挂条的 Boss 补 `name:`（血条标题可读）。可与 `boss_bar` 同一 PR 或紧随内容 PR。
