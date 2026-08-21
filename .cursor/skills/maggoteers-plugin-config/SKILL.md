---
name: maggoteers-plugin-config
description: >-
  Use when authoring or validating Maggoteers (卫戍协议) YAML under plugins/Maggoteers/
  or src/main/resources/: rewards.yml, items/*.yml (use_ability / held_effects),
  waves.yml, mob_skills.yml, config.yml, collectibles.yml, maps; or when deciding
  config-only vs Java for GRANT_ITEM, SUMMON, BUFF_AREA, DISABLE_AI, AURA,
  UPGRADE_LEVEL, ON_COUNTER, or mob skills (trigger+effect).
---

# Maggoteers 插件配置编写（1.1）

> 与代码对齐：**1.1** = 玩家 `ON_COUNTER` + 怪物 `mob_skills.yml`；**已退役** `affixes.yml` / InfernalMobs / waves `affixes`·`infernal`·`potions`。奖励含 GRANT_ITEM / SUMMON / BUFF_AREA / DISABLE_AI / AURA / MAGIC_DAMAGE / BUNDLE / BOUND_EQUIP；药水 clear_potions / immunity（禁假 amp-255，**仅** rewards/items）；武器 use_ability + held_effects。完整字段见 [reference.md](reference.md)。

## 先读什么

1. 铁律：`CLAUDE.md` §0（绝对坐标、定义唯一处、内容增删零代码）。
2. 字段与边界：[reference.md](reference.md)。
3. 改完跑：[maggoteers-build-deploy](../maggoteers-build-deploy/SKILL.md)（`mvn test` + 部署 `E:/MCpaper`）。

**权威源**：`RewardLoadValidator` / `WeaponEffectValidator` / `SummonParamsParser` / `CounterSpecParser` / `MobSkillSpecParser` / `MobSkillRegistry` / `ItemAbilityRegistry`；本 skill 过期时以 Java 为准。

## 配置根目录

运行时：`plugins/Maggoteers/`（jar `saveResource`；**已存在的 YAML 不会被覆盖**）。仓库源：`src/main/resources/`。

| 文件 | 职责（铁律②） |
|------|----------------|
| `config.yml` | 全局：`mob_attributes`、`rewards.upgrade_level_cap_default`、层原点、波数、休整、结算、`aura` / `magic_fx`、券种 |
| `waves.yml` | **唯一**刷怪 strategy + 池（coeff / equipment / on_death / passengers / **`skills`**） |
| `mob_skills.yml` | **唯一**怪物技能（trigger + effect；1.1 替代 affixes/IM） |
| `rewards.yml` | **唯一**3 选 1 / 职业（STAT / WEAPON / SUPPLY / **BUNDLE**） |
| `collectibles.yml` | STAT id → 护符 ItemCreator id（GUI 外观；**无** `icon:`） |
| `maps/actN/<mapId>/` | `structure.nbt` + `points.yml` + 可选 `special_waves.yml` |
| `items/*.yml` | ItemCreator 物品 + **`use_ability`** / **`held_effects`** |

加载顺序：`mob_skills` → `waves` → `maps` → `config`/`scaling` → `ItemService` → `rewards`。

**禁止新建/编辑** `affixes.yml`（已删除）。旧 waves 里 `affixes:` / `infernal.affixes` 仅 **加载 fallback**（id 当 skill id）；**新内容只写 `skills:`**。

## 三条铁律（配置侧）

1. **绝对坐标永不入配置** — 仅 `act_origins`；`points.yml` 相对坐标。
2. **定义唯一处** — 波只改 `waves.yml`；怪物技能只改 `mob_skills.yml`；奖励只改 `rewards.yml`；池用 strategy / skill id 引用。
3. **零代码内容** — 地图、波、怪物技能、奖励、物品、怪装备/亡语/体型、**既有 Effect 的 use_ability/held_effects/ON_COUNTER** = YAML；**新 Trigger/Effect（玩家或 Mob*）、多段投射物 AI** = Java。

## 配置 vs Java（决策）

```
需求可用既有 Effect / MobEffect + 合法 trigger？
  ├─ rewards STAT / items use_ability / held_effects / mob_skills → 写 YAML
  └─ 新触发、新 Effect、或配置表达不了的多段技能 → Java（ItemUseHandler 为 escape hatch）
```

## 常见任务工作流

### 加怪物技能（`mob_skills.yml`）+ 挂到波（`waves.yml`）

1. 在 `mob_skills.yml` `skills.<id>` 写技能（map key = id）。
2. `trigger`：`spawn` | `attack` | `damage_taken` | `killed` | `tick` | `counter`。
3. 规则：`tick` 须 `cooldown_sec > 0`；`counter` 须有 `counter:` 块；`effects` 可空（纯装饰如 `infected`）；隐身用顶层 `invisible: true`（**勿** INVISIBILITY 药水）。
4. `effects[]`：`health` / `attribute` / `potion` / `summon` / `teleport`；`target`: `self` | `target` | `area`。
5. 可选 `condition`（`player_in_radius` / `hold_item_pdc` / AND/OR/NOT，以怪为中心）。
6. `waves.yml` step / passenger / on_death 写 `skills: [id, …]`（**不要**写 `affixes` / `infernal`）。
7. 未知 skill id：waves 加载 warn；怪仍刷、无该技能。

### 加玩家计数器被动（`rewards.yml` / `held_effects`）

1. `trigger: ON_COUNTER` + `params.counter: { id, count, amount, reset?, condition? }`。
2. `count`：`tick`（=计时器，每秒+1）| `attack` | `damage_taken` | `kill` | `wave_clear` | `on_counter`（串联，跳过自身）。
3. 计满只输出 `ON_COUNTER`（带 counter id）；**无**独立 `ON_TIMER`。
4. **禁止**：`GRANT_ITEM` / `SUMMON` 用 `ON_COUNTER`（白名单不含）；AURA / BOUND_EQUIP 仍禁 trigger。
5. held：`ON_COUNTER` 合法（须 `params.counter`）；常驻仍仅 ADD_ATTRIBUTE / ADD_POTION / AURA。

### 加奖励（`rewards.yml`）

1. 选池 `actN_weak|strong|boss` 或 `class`；设 `cost` / `currency`。
2. `category`: `STAT` | `WEAPON` | `SUPPLY`；有 `grants:` → **BUNDLE**。
3. STAT：`effect` + `params`；需要时 `trigger` / `expiry` / `stack` / `upgrade_max` / `counter`。
4. **必须带 trigger**：`GRANT_ITEM`、`SUMMON`、`BUFF_AREA`、`DISABLE_AI`（以及多数战斗型）。
5. **禁止 trigger/expiry**：`AURA`、`BOUND_EQUIP`。
6. fireTrigger 白名单见 reference（含 **`ON_COUNTER`**；**勿**抄全量 Trigger）；`GRANT_ITEM`/`SUMMON` 另加 `ON_ACT_ENTER`，**不含** `ON_COUNTER`。
7. 可升级：`stack: UPGRADE_LEVEL`；上限 = **`upgrade_max` → pool `upgrade_level_cap` → `config rewards.upgrade_level_cap_default`**。
8. STAT 建议 `unique: true`；护符映射写 `collectibles.yml`。
9. SUMMON 可选 `homing_target: nearest_enemy`（玩家侧追本局怪）。

### 加武器能力（`items/*.yml`）

**`use_ability`**（右键，`cooldown_sec` **必填 >0**）：

| effect | 要点 |
|--------|------|
| `DAMAGE_*` / `HEAL_AREA` / `BUFF_AREA` / `DISABLE_AI` | `params` + 可选 `fx` |
| `GRANT_ITEM` / `SUMMON` | SUMMON 武器路径仅 `anchor: self`；可加 `homing_target` |
| `ADD_ATTRIBUTE` | **必须** `expiry`（下次近战加成） |
| `ADD_POTION` | 无 expiry + `duration_ticks>0` = 即时；有 expiry → `duration_ticks` 0/省略 |

**`held_effects`**（主手；id=`held:<itemId>:<n>`）：

- 禁止：`expiry`、`UPGRADE_LEVEL`、`GRANT_ITEM`、`SUMMON`、`REVOKE_GRANTS`
- 常驻（无 trigger）：仅 `ADD_ATTRIBUTE` / `ADD_POTION` / `AURA`
- 触发：`ON_DAMAGE_DEALT` | `ON_DAMAGE_TAKEN` | `ON_KILL` | **`ON_COUNTER`**
- 战斗型须带 trigger

### 加波次（无词缀/IM）

`strategies` + `pools`；可选 `passengers` / `on_death` / `equipment` / `coeff.hp|dmg|speed|scale|follow_range`；技能用 `skills:`。强度 = **`coeff × 人数 scaling`**（不再乘 affix）。

## 配置做不到的事

| 需求 | 原因 |
|------|------|
| 新 `Trigger` / `Effect` / `MobTrigger` / `MobEffect` | 枚举 + EffectService / MobEffectExecutor |
| 奖励 `recurring` / `cooldown_sec` | `RewardOption` **未解析** |
| 怪用 PDC 武器放玩家同款技能 | 须自定义 AI/handler |
| 玩法 CD 单 YAML 源 | `item_cooldowns.yml` 未做 |
| 自定义 structure 名 / paste 偏移 | 固定 `structure.nbt` |
| 多段/投射物复杂技能 | `ItemUseHandler` + `registerHandler` |
| 再写 `affixes.yml` / `infernal:` 新内容 | **1.1 退役**；迁到 `mob_skills.yml` + `skills:` |

## 校验清单

- [ ] **无**新 `affixes.yml` / waves `infernal:`；技能在 `mob_skills.yml`，waves 用 `skills:`
- [ ] mob_skills：`tick` 有 cooldown；`counter` 有 counter 块；引用 id ⊆ registry
- [ ] rewards：effect/trigger 过白名单；`ON_COUNTER` 有 `params.counter`；GRANT_ITEM 有 `item`；SUMMON 有 `entity`+`cleanup`；AURA/BOUND_EQUIP 无 trigger
- [ ] UPGRADE_LEVEL：cap 来源正确；`collectibles` `items_by_level` 齐
- [ ] items：`use_ability.cooldown_sec>0`；held 无 expiry；PDC `maggoteers:id`
- [ ] waves：strategy / point / EntityType / skill id；equipment item 存在；on_death 嵌套 ≤8
- [ ] 绝对坐标未进 maps/waves；改完 `mvn test`
- [ ] 测试服同步含 **`mob_skills.yml`**（勿再拷 `affixes.yml`）

## 引用速查

- **升级上限**：option `upgrade_max` → pool `upgrade_level_cap` → `rewards.upgrade_level_cap_default`
- **强度**：`hp/dmg/speed` = coeff × 人数 scaling；`scale`/`follow_range` = coeff × `mob_attributes.*`（MobFactory；不乘人数）
- **装备 slot**：`HEAD`/`CHEST`/`LEGS`/`FEET`/`HAND`/`OFF_HAND`（别名见 reference）
- **计时器**：`params.counter: { id, count: tick, amount: N }` + `trigger: ON_COUNTER`

完整 schema → [reference.md](reference.md)。
