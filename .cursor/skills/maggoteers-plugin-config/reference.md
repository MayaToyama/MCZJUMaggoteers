# Maggoteers 配置 reference

## config.yml

| 键 | 类型 | 说明 |
|----|------|------|
| `mob_attributes.scale` | double | 全局体型倍率，再乘 waves/affix（默认 1.0） |
| `mob_attributes.follow_range` | double | 全局索敌倍率（默认 1.0） |
| `wait.min_players` | int | MGC 等待最少人数 |
| `lives.default` | int | 开局自动复活次数 |
| `initial_equipment` | list | 玩家开局 ItemCreator id（非怪物装备） |
| `class_select.timeout_sec` | int | 职业选择超时 |
| `items.<key>` | section | 券种：`item_id`, `kind`, `material`, `name`, `lore`, `glint` |
| `act_origins.act1\|act2\|act3` | `[x,y,z]` | 层原点 |
| `waves_per_act` / `rest` / `settlement` / `scaling` | — | 同前 |
| `world.time_lock` / `player.night_vision` / `act_enter.prep_sec` | — | 昼夜、夜视、进层准备 |
| `aura.carrier_fx` / `magic_fx` | — | AURA 粒子间隔、武器 FX |

## waves.yml

文件头注释为权威摘要：`coeff.scale`、`equipment`、`on_death`、`passengers`、**`infernal`**。

### pools / strategies

与先前相同；未知 strategy → 启动失败。

### strategies.<id> → steps[]

| 字段 | 说明 |
|------|------|
| `point` | `"1"`…`"9"` 或 `boss` |
| `type` / `count` | EntityType + 数量 |
| `coeff` | 见 **coeff** |
| `affixes` | Maggoteers **原生**词缀 id 列表（见 `affixes.yml`） |
| `infernal` | 可选 IM 块：`level`（1–100）+ `affixes`（IM 技能 id 列表） |
| `delay` | 秒，步前等待 |
| `equipment` | 见 **equipment** |
| `on_death` | 见 **mob 条目**（死亡召唤） |
| `passengers` | 见 **mob 条目**（骑乘，可嵌套 `passengers`） |

### coeff（step / passenger / on_death 内）

| 键 | 默认 | 叠乘 |
|----|------|------|
| `hp` / `dmg` / `speed` | 1.0 | × affix × **scaling 人数** |
| `scale` | 1.0 | × affix × **`mob_attributes.scale`**（**不**乘人数 scaling） |
| `follow_range` | 1.0 | × affix × **`mob_attributes.follow_range`** |

运行时对 `Attribute.SCALE` / `FOLLOW_RANGE` 设 base×倍率；实体无对应属性则跳过（日志 fine）。

### equipment[]

```yaml
equipment:
  - { slot: HEAD, item: maggoteers:leather_helmet }
  - { slot: MAIN_HAND, item_id: maggoteers:wooden_sword }
```

| 字段 | 说明 |
|------|------|
| `slot` | `HEAD`/`HELMET`, `CHEST`/`CHESTPLATE`, `LEGS`/`LEGGINGS`, `FEET`/`BOOTS`, `HAND`/`MAIN_HAND`/`MAIN`, `OFF_HAND`/`OFFHAND` |
| `item` 或 `item_id` | **ItemCreator id**（`ItemService.createItem`） |

- 各槽 **dropChance = 0**。
- 物品不存在 → severe 级 warning，该槽跳过。
- **不会**触发 `ItemInteractRouter`；伤害仍以 `coeff.dmg` + 原版 AI 为主。

### mob 条目（`passengers[]` / `on_death[]`）

与 step 共享字段（无 `point`/`delay`）：

| 字段 | 说明 |
|------|------|
| `type` | EntityType |
| `count` | int，默认 1 |
| `coeff` / `affixes` / `equipment` / **`infernal`** | 同上；`infernal` 不继承父节点 |
| `passengers` | 仅 **passengers** 条目；嵌套最深 **8** |
| `on_death` | 可嵌套；最深 **8** |

**on_death 行为**（`WaveEngine.handleMobDeath`）：

- 在本怪死亡位置附近生成；**登记进本波 `livingMobs`**（清波须击杀）。
- 子怪携带解析后的 `on_death` 链（可再死亡再召唤）。
- `RunPlanner` 对 death 条目做与 step 相同的 **coeff × affix × scaling**（hp/dmg/speed/drop）；scale/follow 走 Compose。

**passengers** 行为不变：有乘客才注册骑乘 AI。

### infernal（可选，InfernalMobs 反射）

```yaml
infernal:
  level: 3
  affixes: [poisonous, sprint]
```

| 字段 | 说明 |
|------|------|
| `level` | 1–100，传给 IM `mechanizeWithAffixes` |
| `affixes` | IM 技能 id；**精确列表**，不随机补全 |

- 可用于 `steps[]`、`passengers[]`、`on_death[]`；各节点**独立**，不继承。
- 禁用：`morph`, `mama`, `mounted`, `vexsummoner`, `ghost`。
- 刷怪顺序：原生 stats/affixes → IM mechanize → `equipment`（显式装备优先）。
- IM 未装/反射失败/未知技能：dedupe warning，实体仍为普通怪且计入波次。
- 死亡：`MobDeathListener` 在 **LOWEST** 对受管 UUID `unregisterMob`，抑制 IM 掉落/统计/广播/死亡技能。

### 强度公式小结

```
hp/dmg/speed/drop = coeff × Π(affix) × scaling(1–4 人)
scale/follow_range  = coeff × Π(affix) × mob_attributes.*
```

## affixes.yml

```yaml
affixes:
  armored: { hp: 2.0, scale: 1.1, display: "<aqua>巨型装甲" }
  far_sight: { follow_range: 1.5, display: "<gray>远视" }
```

| 字段 | 默认 | 说明 |
|------|------|------|
| `hp` / `dmg` / `speed` / `drop` | 1.0 | 乘算 |
| **`scale`** | 1.0 | 体型倍率 |
| **`follow_range`** | 1.0 | 索敌倍率 |
| `potions[]` | — | 刷怪时施加（无 `on:` 战斗触发） |
| `display` | id | MiniMessage |

## rewards.yml

### 池结构

```yaml
reward_pools:
  class:
    cost: 0
    options: [ ... ]
  act1_weak:
    cost: 1
    currency: normal
    options: [ ... ]
```

### option 字段

| 字段 | 适用 | 说明 |
|------|------|------|
| `id` / `display` / `description` | 全部 | |
| `category` | 全部 | `STAT` / `WEAPON` / `SUPPLY` |
| `item` / `amount` | WEAPON/SUPPLY | ItemCreator id |
| `effect` / `params` | STAT | 见 Effect 表 |
| `trigger` / `stack` | STAT | 缺省 trigger = 常驻 |
| `expiry` | STAT | `{ trigger, charges }` |
| `requires_unlock` / `unlock_cost` / `unique` | 可选 | STAT 建议 `unique: true` |

**抽池可见性**（`RewardDrawVisibility`）：SUPPLY 可重复；WEAPON 看 `acquiredUnique`；STAT 看 `PlayerState.effects`（UPGRADE 未满仍可抽）。GUI 图标来自 `collectibles.yml` 预览，**不用** `icon:`。

## collectibles.yml

映射 `rewards.yml` 的 `option.id` → ItemCreator 护符 id（仅外观；数值仍在 rewards）。

```yaml
collectibles:
  a1s_magic_dmg:
    item: maggoteers:charm_magic_10
  a1s_resist:
    items_by_level:
      1: maggoteers:charm_resist_1
      2: maggoteers:charm_resist_2
```

| 字段 | 说明 |
|------|------|
| `item` | 单级 STAT 护符 ItemCreator id |
| `items_by_level` | UPGRADE_LEVEL：等级 → 护符 id（升级时删旧发新） |

护符 PDC：`maggoteers:kind=collectible`、`reward_id`、`reward_level`。未映射的 STAT：启动 warning，效果仍生效、无护符。

物品定义在 `plugins/Maggoteers/items/collectibles.yml`（ItemCreator 格式）。

### Effect（STAT）

| effect | params |
|--------|--------|
| `ADD_ATTRIBUTE` | `attr`, `op`, `value` |
| `ADD_POTION` | `potion`, `amp`, `duration_ticks` |
| `HEAL` | `amount` |
| `DAMAGE_AREA` | `damage`, `radius` |
| `GRANT_REVIVE` | `count` |
| `AURA` | `radius`, `targets`, `enemy_scope`, `grant`, `carrier_fx`, `mark_fx`, `mark_head`, `grant_pulse_sec` |

### Trigger / Stack

Trigger：`ON_WAVE_CLEAR`, `ON_ACT_ENTER`, `ON_GAME_END`, `ON_KILL`, `ON_DAMAGE_DEALT`, `ON_DAMAGE_TAKEN`, `ON_REVIVE`, `ON_DEATH`, `ON_TICK_1S`, `ON_INTERACT`

Stack：`ADD`, `UPGRADE_LEVEL`, `REFRESH`, `REPLACE`, `IGNORE`

YAML 仍未接入：`recurring`、`cooldown_sec`。`UPGRADE_LEVEL` 上限 **4** 硬编码。

## maps

### points.yml / special_waves.yml / structure.nbt

相对 `act_origins`；G3 刷怪点回退见 `CLAUDE.md`；`special_waves.yml` 追加池权重。

## items/*.yml

ItemCreator 格式；PDC `maggoteers:kind` 或 `maggoteers:id`。

**武器能力**（优先于 Java handler）：

```yaml
use_ability:
  cooldown_sec: 3          # 权威 CD
  effect: DAMAGE_AREA      # DAMAGE_AREA | DAMAGE_BEAM | HEAL_AREA
  params: { damage: 6.0, radius: 6.0, targets: enemies, enemy_scope: tracked }
  fx: { preset: SPIRAL_RADIUS, particle: CRIT, radius: 6.0 }
```

`useCooldown` 仅 vanilla 热栏显示，应与 `cooldown_sec` 一致。legacy `use_fx` 为 `use_ability.fx` 别名（迁移中）。

内置 kind：`currency_normal`, `currency_boss`, `class_ticket`, `revive_coin`, `supply_healing`, `shop_emerald`。

配置武器示例：`maggoteers:test_blade`, `healing_staff`, `excalibur`（`use_ability` 已内置）。

**怪物 `equipment`** 与奖励 `item` 共用同一套 id（券种通常不给怪穿）。

## magic_fx / carrier_fx

Preset：`THICK_RING`, `SPIRAL_RADIUS`, `RIPPLE_RINGS`, `AIM_RAY`, `SMOOTH_EXPAND_RING`, `DOT_ABOVE`

合并顺序：`magic_fx.defaults` → `magic_fx.weapons.<id>` → `items use_ability.fx`（legacy `use_fx` 别名）。

字段：`preset`, `particle`, `sound`, `sound_volume`, `sound_pitch`, `radius`, `ray_length`, `density`, `ripple_rings`, `expand_steps`, `spiral_ticks`

AURA 重播间隔：`config aura.carrier_fx`（非 rewards 内秒数）。

## 跨文件引用（补充）

```
affixes scale/follow_range ──► Compose ──► SpawnStep / DeathSpawn / PassengerSpawn
waves equipment[].item ──► ItemService
config mob_attributes ──► MobFactory.applyMobStats
waves on_death ──► WaveEngine.spawnOnDeath（计本波怪数）
```

## 启动期 vs 开局期错误

| 阶段 | 典型错误 |
|------|----------|
| onEnable | 未知 strategy；passengers/on_death 嵌套 > 8 |
| RunPlanner | 刷怪点；未知 affix |
| 刷怪/死亡 | 装备 item 不存在 warning；死亡召唤 type 无效 |

## 配置注意

- **SLIME** 等仍可能有 **原版分裂**；若只要 `on_death` 爆怪，建议用非史莱姆主体或服内实测。
- **史莱姆/岩浆怪** 坐骑仍可能 `setSize(≥2)`（代码），与 `coeff.scale` 叠加时注意观感。
- Boss 条规则不变：单根、无乘客、`hpMult ≥ 6` 等。
