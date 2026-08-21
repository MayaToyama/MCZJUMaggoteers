# Maggoteers 配置 reference（1.1）

解析/校验权威类：`RewardLoadValidator`、`UpgradeLevelCaps`、`WeaponEffectValidator`、`SummonParamsParser`、`CounterSpecParser`、`MobSkillSpecParser`、`MobSkillRegistry`、`EffectParamsParser`、`ItemAbilityRegistry`、`WeaponHeldRegistry`。

## config.yml

### messages

玩家可见系统提示（聊天/记分板/GUI/命令壳/排行榜/GameMeta）。格式：MiniMessage + `{snake_case}`；占位值经 `escapeTags`。嵌套片段（`prep_clause` / `strategy_clause` / `phase_*`）**不得**含 MM 颜色标签。记分板休整用 `scoreboard.line_wave_resting`。缺 key 显示字面量 `messages.<key>`；load 时对照清单 warn。详见 `docs/superpowers/specs/2026-08-15-config-messages-design.md`。

加载顺序：`saveDefaultConfig` → `MessageService.load` → `registerGame` / `registerLeaderboard`。

| 键 | 说明 |
|----|------|
| `mob_attributes.scale` / `.follow_range` | 全局体型/索敌倍率（再乘 waves coeff；**无 affix 层**） |
| `mob_attributes.squad_*` | 骑乘小队追击/移速相关 |
| `rewards.upgrade_level_cap_default` | UPGRADE_LEVEL 默认上限（默认 4） |
| `wait.min_players` / `lives.default` / `initial_equipment` / `class_select.timeout_sec` | 等待、复活、开局装、职业超时 |
| `items.<key>` | 券种：`item_id`, `kind`, `material`, `name`, `lore`, `glint` |
| `act_origins` / `waves_per_act` / `rest` / `settlement` / `scaling` | 层原点、波数、休整、结算、人数缩放（`mob_hp`/`mob_damage`/`mob_speed`/`mob_count`；**无** `currency_drop`） |
| `world.*` / `player.night_vision` / `act_enter.prep_sec` | 昼夜、夜视、进层准备 |
| `aura.carrier_fx` / `magic_fx` | AURA 粒子间隔、武器 FX 默认 |

## waves.yml

文件头注释为权威摘要：`coeff`、`equipment`、`on_death`、`passengers`、**`skills`**（1.1 替代 affixes/infernal）。

### strategies.<id> → steps[]

| 字段 | 说明 |
|------|------|
| `point` | `"1"`…`"9"` 或 `boss` |
| `type` / `count` | EntityType + 数量 |
| `name` | 可选 MiniMessage 头顶名；省略/空白=无自定义名 |
| `boss_bar` | 可选布尔，默认 false；**仅 `true` 挂 Maggoteers Adventure BossBar** |
| `coeff` | 见下表 |
| **`skills`** | `mob_skills.yml` 技能 id 列表（定义唯一处） |
| `delay` | 秒，相对**同 point**上一刷怪时刻（该点尚无事件则相对本轮起点）；不同 point 并行 |
| `repeat` | 可选，默认 1：该 step 在同 point 时间轴上按 `delay` 再刷几次（≠ strategy 顶层 `repeat`） |
| `equipment` / `on_death` / `passengers` | 见下；`name`/`boss_bar`/`skills` 也可写在 passenger / on_death 节点 |

**加载 fallback（仅兼容旧文件）**：无 `skills` 时可读旧 `affixes:` 或 `infernal.affixes` 列表，**当作 skill id**。新内容**只写 `skills:`**。已删除字段：`potions`（出生药水改 SPAWN 技能）。

### coeff

| 键 | 叠乘 |
|----|------|
| `hp` / `dmg` / `speed` | × **人数 scaling**（`Compose`；**不再**乘 affix） |
| `scale` / `follow_range` | × **`mob_attributes.*`**（`MobFactory`；不乘人数） |

**无 `drop`**（货币仅来自清波 `clearReward`）。

### equipment[]

```yaml
equipment:
  - { slot: HEAD, item: maggoteers:leather_helmet }
  - { slot: MAIN_HAND, item_id: maggoteers:wooden_sword }
```

slot 别名：`HEAD`/`HELMET`, `CHEST`/`CHESTPLATE`, `LEGS`/`LEGGINGS`, `FEET`/`BOOTS`, `HAND`/`MAIN_HAND`/`MAIN`, `OFF_HAND`/`OFFHAND`。dropChance=0；不走玩家 `ItemInteractRouter`。

### passengers / on_death（mob 条目）

与 step 共享字段（无 `point`/`delay`）；嵌套最深 **8**。`on_death` 生成点计入本波 `livingMobs`。各节点独立 `skills`。

刷怪顺序：stats（coeff×scaling）→ `MobSkillService.attach(skills)` → 显式 `equipment`。

## mob_skills.yml

定义唯一处。结构：

```yaml
skills:
  plastic:
    trigger: spawn
    effects:
      - { effect: potion, target: self, potion: resistance, amp: 4, duration_ticks: 600 }
  archer:
    trigger: tick
    cooldown_sec: 6
    condition: { player_in_radius: 24 }
    effects:
      - { effect: summon, target: self, entity: arrow, count: 1,
          homing_target: nearest_player, projectile_speed: 1.6 }
  invisible:
    trigger: spawn
    invisible: true          # LivingEntity.setInvisible + 玻璃瓶盔；勿用隐身药水
  infected:
    trigger: spawn           # effects 可空（纯装饰）
```

| 字段 | 规则 |
|------|------|
| map key | = skill id（parser 缺 `id` 时注入） |
| `trigger` | `spawn` / `attack` / `damage_taken` / `killed` / `tick` / `counter` |
| `cooldown_sec` | `tick` **必须 >0**；其它可选 |
| `counter` | `trigger: counter` **必填**；见下 |
| `condition` | `player_in_radius` / `hold_item_pdc` / `and`/`or`/`not`（以怪为中心） |
| `invisible` | 布尔；可与 effects 并用 |
| `effects[]` | 可空 |

### MobEffect（effects[].effect）

| effect | 常用 params（顶层或 `params:` 子块） |
|--------|--------------------------------------|
| `health` | `amount`（治疗）或 `damage`；`target` + 可选 `radius`（area） |
| `attribute` | `attr`, `op`（如 `multiply`）, `value`, `duration_ticks` |
| `potion` | `potion`, `amp`, `duration_ticks`（**允许 amp 255**；非 rewards 假 255 规则） |
| `summon` | `entity`, `count`；弹道：`projectile_speed` + 可选 `homing_target` |
| `teleport` | `target: self\|target`；`offset_y` **当前 parser 未读**（执行默认约 2.0） |

`target`：`self` | `target`（攻击/受击上下文）| `area`（需 `radius`）。

### 怪物计数器

```yaml
counter:
  id: every_5_hits
  count: damage_taken    # MobTrigger 名：attack/damage_taken/killed/spawn/tick/counter
  amount: 5
  reset: wave_clear      # 可选
  condition: { ... }     # 可选
```

出生加载、死亡卸载；`count: counter` 可串联（跳过自身防自环）。

### homing_target（怪物 SUMMON）

`nearest_player` | `attack_target` | `damage_source` | `killer` | `none`/缺省=不追踪。有 `projectile_speed`（或 projectile 块）才走弹道。

**禁用旧 IM 技能名空间**（不再 mechanize）：内容侧用原生 `summon`/`teleport`/`health` 等表达。

## rewards.yml

### 池

```yaml
reward_pools:
  act1_weak:
    cost: 1
    currency: normal          # normal | boss
    upgrade_level_cap: 2      # 可选；UPGRADE_LEVEL 池上限
    options: [ ... ]
```

### option 字段

| 字段 | 适用 | 说明 |
|------|------|------|
| `id` / `display` / `description` | 全部 | |
| `category` | 全部 | `STAT` / `WEAPON` / `SUPPLY` / `BUNDLE`（有 `grants` 时自动 BUNDLE） |
| `item` / `amount` | WEAPON/SUPPLY | ItemCreator id |
| `grants` | BUNDLE | 嵌套 option 列表 |
| `effect` / `params` | STAT | 见 Effect 表 |
| `trigger` | STAT | 缺省=常驻；GRANT_ITEM/SUMMON/**多数战斗型必填**；计数器用 `ON_COUNTER` |
| `expiry` | STAT | `{ trigger, charges }`；**禁止** `ON_INTERACT` |
| `stack` | STAT | `ADD` / `UPGRADE_LEVEL` / `REFRESH` / `REPLACE` / `IGNORE` |
| `upgrade_max` | STAT | 该 option 的 UPGRADE_LEVEL 上限（>0 时优先） |
| `requires_unlock` / `unlock_cost` / `unique` | 可选 | STAT 建议 `unique: true`（可见性不读 unique） |

**无 `icon:`** — GUI 用 `collectibles.yml` + ItemCreator 预览。

**未解析**：`recurring`、`cooldown_sec`。

### UPGRADE_LEVEL 上限

```
option.upgrade_max (>0) → pool.upgrade_level_cap (>0) → config rewards.upgrade_level_cap_default (默认 4)
```

类：`UpgradeLevelCaps.resolve`。

### 抽池可见性（`RewardDrawVisibility`）

- SUPPLY：始终
- WEAPON：每 id 一次（`acquiredUnique`）
- STAT / BUNDLE：已拥有则隐藏，除非 UPGRADE_LEVEL 且 level < cap；BUNDLE 当且仅当全部 grants 可见

### fireTrigger 白名单（`RewardLoadValidator`）

**一般 STAT**（可省略=常驻）：  
`ON_KILL` | `ON_DAMAGE_DEALT` | `ON_DAMAGE_TAKEN` | `ON_DEATH` | `ON_WAVE_CLEAR` | **`ON_COUNTER`**

**仅 GRANT_ITEM / SUMMON**（**必须**有 trigger）：  
`ON_WAVE_CLEAR` | `ON_ACT_ENTER` | `ON_KILL` | `ON_DAMAGE_DEALT` | `ON_DAMAGE_TAKEN` | `ON_DEATH`  
→ **不含** `ON_COUNTER`

**禁止**（奖励 fire）：`ON_TICK_1S`、`ON_INTERACT`、`ON_GAME_END`、`ON_REVIVE`；非 grant/summon 也禁止 `ON_ACT_ENTER`。

### 玩家计数器（`params.counter`）

```yaml
- id: every_5_melee_burst
  category: STAT
  trigger: ON_COUNTER
  effect: DAMAGE_AREA
  params:
    counter: { id: melee5, count: attack, amount: 5 }
    radius: 4.0
    damage: 6.0
    targets: enemies
  unique: true
  stack: IGNORE

# 计时器 = count: tick（每秒 +1，amount=N 秒）
- id: aura_pulse_heal
  category: STAT
  trigger: ON_COUNTER
  effect: HEAL
  params:
    counter: { id: tick5, count: tick, amount: 5 }
    amount: 1.0
  unique: true
```

| 键 | 说明 |
|----|------|
| `id` | 计满信号来源；被动须匹配 |
| `count` | `tick` \| `attack` \| `damage_taken` \| `kill` \| `wave_clear` \| `on_counter` |
| `amount` | 计满阈值（默认 1） |
| `reset` | 可选，玩家 `Trigger` 名 |
| `condition` | `hold_item_pdc` / `player_in_radius` / and/or/not |

`ON_COUNTER` **必须**有可解析的 `params.counter`。无独立 `ON_TIMER`。

### Effect（STAT）

| effect | 必填要点 | params |
|--------|----------|--------|
| `ADD_ATTRIBUTE` | — | `attr`, `op`, `value`；虚拟 `MAGIC_DAMAGE` **必须** `op: PERCENT`；触发型写 `{id}:grant` |
| `ADD_ATTRIBUTE` + `op: REVOKE_GRANTS` | 须 trigger | `source_id`（无 attr/value）；bundle 内须指向同 bundle grant id |
| `ADD_POTION` | — | `potion`, `amp`, `duration_ticks`（0=常驻）；**免疫**：`immunity: true`（无 trigger/expiry/recurring；非瞬时；忽略 amp/duration） |
| `HEAL` | — | `amount` |
| `DAMAGE_AREA` / `DAMAGE_BEAM` / `HEAL_AREA` | 战斗型宜带 trigger | `damage`/`amount`, `radius`, `targets`, `enemy_scope`…；`DAMAGE_AREA` 可选 `clear_potions[]`（顺序：damage → clear → potions） |
| `GRANT_REVIVE` | — | `count` |
| `BUFF_AREA` | **须 trigger** | `potions[]` **或** `clear_potions[]`（至少其一）, `targets` (`self`\|`allies`\|`enemies`\|`hit_target`), `radius`, `include_self`, `enemy_scope`, `fx`, `mark_fx`, `mark_on` |
| `DISABLE_AI` | **须 trigger** | `duration_ticks>0`, `targets` (`enemies`\|`hit_target`\|`attacker`), enemies 需 `radius`/`enemy_scope` |
| `AURA` | **禁** trigger/expiry | `radius>0`, `targets`, `grant: { effect, … }`, 可选 `carrier_fx`/`mark_*`/`grant_pulse_sec` |
| `GRANT_ITEM` | **须 trigger** | `item`（IC id）, `count` 或 `amount` |
| `SUMMON` | **须 trigger** | 见 SUMMON params |
| `BOUND_EQUIP` | **禁** trigger/expiry | `slot: CHEST`（v1）, `items_by_level` 1..`upgrade_max`；须 `stack: UPGRADE_LEVEL` + `upgrade_max`；无 `collectibles.yml`；GUI 预览走 `items_by_level`；`unique: true` **不**阻止 UPGRADE 重抽 |

#### 示例

```yaml
# GRANT_ITEM — 每波复活币
- id: wave_revive_coin
  category: STAT
  trigger: ON_WAVE_CLEAR
  effect: GRANT_ITEM
  params: { item: maggoteers:revive_coin, count: 1 }
  unique: true
  stack: IGNORE

# SUMMON — 命中火球 + 追踪
- id: hit_fireball
  category: STAT
  trigger: ON_DAMAGE_DEALT
  effect: SUMMON
  params:
    entity: SMALL_FIREBALL
    anchor: hit_target
    cleanup: duration
    duration_sec: 5
    projectile: { speed: 0.8, toward: look }
    homing_target: nearest_enemy
  unique: true

# BUFF_AREA — 命中上毒
- id: a1s_poison_on_hit
  category: STAT
  trigger: ON_DAMAGE_DEALT
  effect: BUFF_AREA
  params:
    targets: hit_target
    potions: [ { potion: POISON, amp: 0, duration_ticks: 60 } ]
    mark_on: hit_target

# ADD_POTION — 局内免疫（非瞬时）
- id: a3w_imm_wither
  category: STAT
  effect: ADD_POTION
  params: { potion: WITHER, immunity: true }
  unique: true
  stack: IGNORE

# MAGIC_DAMAGE 常驻
- id: a1s_magic_dmg
  category: STAT
  effect: ADD_ATTRIBUTE
  params: { attr: MAGIC_DAMAGE, op: PERCENT, value: 0.10 }
  unique: true
  stack: ADD
```

> **禁止假 255**：`rewards.yml` / `items/*.yml` 中 `amp: 255` 且 `duration_ticks` ∈ `{0,1}` 且非 `immunity: true` → **硬失败**加载。清除用 `clear_potions` / `self_clear_potions`；免疫用 `immunity: true`。**`mob_skills.yml` 出生/免疫药水 amp 255 合法**（不在此规则内）。

### SUMMON params

| 键 | 说明 |
|----|------|
| `entity` | EntityType 名（必填） |
| `cleanup` | `wave_clear` \| `duration`（duration 须 `duration_sec>0`） |
| `anchor` | `self` \| `hit_target` \| `attacker` \| `death_site`（death_site ⇒ trigger=`ON_DEATH`） |
| `count` / `offset_y` / `tamed` / `friendly_fire` | 可选 |
| `attributes` | `{ ATTR_NAME: double }` |
| `projectile` | `{ speed, toward }`（投射物） |
| `homing_target` | 玩家侧常用 `nearest_enemy`；`none`/缺省=不追踪 |

**武器路径**：仅 `anchor: self`（其它会被强制/校验失败）。

### BUFF_AREA / DISABLE_AI 兼容提示

- `BUFF_AREA`：`potions` 与 `clear_potions` 都空 → 校验失败；仅 `clear_potions`（无 potions）合法
- `targets=hit_target` 与 `ON_DAMAGE_TAKEN` 不兼容（BUFF）
- `mark_on=attacker` 与 `ON_KILL`/`ON_DAMAGE_DEALT` 会被强制 `none`（警告）
- `mark_on=hit_target` 与 `ON_DAMAGE_TAKEN` → `none`
- 武器右键：`hit_target`/`attacker`/`mark_on` 无效或忽略

## collectibles.yml

```yaml
collectibles:
  a1s_magic_dmg:
    item: maggoteers:charm_magic_10
  a1s_resist:
    items_by_level:
      1: maggoteers:charm_resist_1
      2: maggoteers:charm_resist_2
```

未映射 STAT：warning，效果仍生效、无护符。物品在 `items/collectibles.yml`。

## maps

相对 `act_origins`；刷怪点回退见 `CLAUDE.md` G3；`special_waves.yml` 追加池权重。固定文件名 `structure.nbt`。

## items/*.yml

ItemCreator 格式；PDC `maggoteers:id`（STRING）。

### use_ability

单步（legacy，仍合法）或 `effects[]` 多步包。整次技能共用一个 `cooldown_sec`；任一步成功（含仅 `self_*`）进 CD。

```yaml
use_ability:
  cooldown_sec: 15          # 必填 >0（权威 CD；勿靠 Material cooldown）
  fx: { preset: DOT_ABOVE, particle: CRIT }   # Cast FX（右键一次）；优先于 legacy use_fx
  empower_fx: { preset: DOT_ABOVE, particle: CRIT }  # 可选：延期包消耗时在 victim 播一次
  self_clear_potions: [WITHER, POISON, ...]
  self_potions: [ ... ]
  consume: false
  # 二选一：effects[] 或多用单字段 effect:/params:/expiry:
  effects:
    - effect: ADD_ATTRIBUTE
      params: { attr: ATTACK_DAMAGE, op: PERCENT, value: 1.0 }
      expiry: { trigger: ON_DAMAGE_DEALT, charges: 1 }
      stack: REPLACE          # 延期步省略时默认 REPLACE（非 D7 IGNORE）
    - effect: BUFF_AREA
      params: { radius: 3.0, targets: enemies, enemy_scope: tracked, potions: [...] }
      expiry: { trigger: ON_DAMAGE_DEALT, charges: 1 }
```

禁止同时写顶层 `effect:` 与 `effects:`。拒 `AURA` / `BOUND_EQUIP`。

支持 effect：`DAMAGE_AREA` | `DAMAGE_BEAM` | `HEAL_AREA` | `BUFF_AREA` | `DISABLE_AI` | `GRANT_ITEM` | `SUMMON` | `ADD_ATTRIBUTE`（须 expiry）| `ADD_POTION`（即时或 expiry 双路径）。

**即时 vs 延期**：步无 `expiry` → 右键立刻执行（共享可变 `TriggerContext`；`DAMAGE_BEAM` 会写入 `hitTarget` 供后续 `hit_target`）。步有 `expiry` → 写入 `PlayerState`：ADD_* 为常驻临时层；魔法为 `fireTrigger=expiryTrigger`（命中时先执行再 sweep）。战斗 expiry 白名单：`ON_DAMAGE_DEALT` | `ON_DAMAGE_TAKEN` | `ON_KILL`。

**Id**：单步 ADD_* 兼容 `ability:<itemId>`；多步/延期魔法 `ability:<itemId>:<i>`。包成员匹配须 `equals(ability:id)` 或 `startsWith(ability:id:)`（禁止裸前缀，防 `herafinger`/`herafinger_x` 误伤）。

**FX**：Cast = 顶层 `fx`（多步包不用某步 effect-template）。Empower = `empower_fx` → 可见 cast fx（preset≠NONE）→ **纯延期包**内置 `deferredStrikeFx`（CRIT + DOT_ABOVE）→ 否则不播（**不**吃 `magic_fx.defaults`）；播在 victim。同包同触发 coalesce 一次。

**ADD_POTION**：无 expiry + `duration_ticks>0` = 右键即时；有 expiry → `duration_ticks` 须 0/省略。武器勿用 `immunity: true`。

**清除**：`params.clear_potions` 或 `self_clear_potions`；CD 仅在 `executeAbility` **成功**后计入。

完整设计：`docs/superpowers/specs/2026-08-13-multi-use-ability-design.md`。

### held_effects

```yaml
held_effects:
  - effect: ADD_ATTRIBUTE
    params: { attr: ATTACK_DAMAGE, op: PERCENT, value: 0.10 }
  - effect: HEAL
    trigger: ON_KILL
    params: { amount: 2.0 }
  - effect: DAMAGE_AREA
    trigger: ON_COUNTER
    params:
      counter: { id: held_atk3, count: attack, amount: 3 }
      radius: 3.0
      damage: 4.0
      targets: enemies
  - effect: AURA
    params:
      radius: 12.0
      targets: allies
      grant: { effect: ADD_ATTRIBUTE, attr: ATTACK_DAMAGE, op: PERCENT, value: 0.08 }
```

| 规则 | 值 |
|------|-----|
| 生命周期 | 主手持有；卸载删 `held:<itemId>:` 前缀（含 `:grant`） |
| 禁止 | `expiry`、`UPGRADE_LEVEL`、`GRANT_ITEM`、`SUMMON`、`REVOKE_GRANTS` |
| 常驻 effect | `ADD_ATTRIBUTE` / `ADD_POTION` / `AURA` |
| 触发 trigger | `ON_DAMAGE_DEALT` / `ON_DAMAGE_TAKEN` / `ON_KILL` / **`ON_COUNTER`**（须 `params.counter`） |
| 触发 ADD_POTION | `duration_ticks > 0` |

校验类：`WeaponEffectValidator`。

内置 kind：`currency_normal`, `currency_boss`, `class_ticket`, `revive_coin`, `supply_healing`, `shop_emerald`。

怪物 `equipment` 与奖励 `item` 共用同一套 id。

## magic_fx / carrier_fx

Preset：`THICK_RING`, `SPIRAL_RADIUS`, `RIPPLE_RINGS`, `AIM_RAY`, `SMOOTH_EXPAND_RING`, `DOT_ABOVE`

合并：Cast 多步包 = `magic_fx.weapons.<id>` → `use_ability.fx`（无 step template）；legacy 单步仍可带 effect template。Empower = `empower_fx` → 可见顶层 `fx` → 纯延期包内置打击闪 → 否则不播。

字段：`preset`, `particle`, `sound`, `sound_volume`, `sound_pitch`, `radius`, `ray_length`, `density`, `ripple_rings`, `expand_steps`, `spiral_ticks`

AURA 重播间隔：`config aura.carrier_fx`。

## 跨文件引用

```
mob_skills.yml ──► waves skills[] ──► MobSkillService.attach
waves equipment[].item ──► ItemService
rewards STAT id ──► collectibles.yml ──► items/collectibles.yml
items use_ability / held_effects ──► ItemAbilityRegistry / WeaponHeldRegistry
params.counter ──► CounterService（玩家）/ MobCounterRegistry（怪）
config mob_attributes ──► MobFactory
waves on_death ──► WaveEngine（计本波怪数）
```

## 启动期 vs 运行期错误

| 阶段 | 典型 |
|------|------|
| onEnable / load | 未知 strategy；skill id 不在 registry；rewards 校验失败 skip+warn；passengers/on_death >8；held/use_ability 校验失败；mob_skills 解析失败跳过 |
| RunPlanner | 刷怪点缺失 |
| 刷怪/死亡 | 装备 item 不存在；死亡召唤 type 无效；attach/detach 泄漏（须死亡/清波 detach） |
| 交互 | CD 中拦截 |

## 配置注意

- **SLIME** 可能原版分裂；只要 `on_death` 时慎用。
- 史莱姆/岩浆怪坐骑可能 `setSize` 与 `coeff.scale` 叠观感。
- Boss 条：仅 `boss_bar: true`（无旧 hp≥6 启发式）。
- `config.yml` 旧注释若写「池优先于 option」以代码为准（option 优先）。
- **1up**（`trigger: killed` 满血）勿挂在会卡波的乘客上；引用前须 cooldown/次数上限策略。
- 测试服：**同步 `mob_skills.yml`**；删除服上残留 `affixes.yml`（可选清理）；`saveResource(false)` 不覆盖已有 YAML。
