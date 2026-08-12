---
name: maggoteers-plugin-config
description: >-
  Use when authoring or validating Maggoteers (卫戍协议) YAML under plugins/Maggoteers/
  or src/main/resources/: rewards.yml, items/*.yml (use_ability / held_effects),
  waves.yml, affixes.yml, config.yml, collectibles.yml, maps; or when deciding
  config-only vs Java for GRANT_ITEM, SUMMON, BUFF_AREA, DISABLE_AI, AURA, UPGRADE_LEVEL.
---

# Maggoteers 插件配置编写

> 与代码对齐：Paper 26.2；奖励含 **GRANT_ITEM / SUMMON / BUFF_AREA / DISABLE_AI / AURA / MAGIC_DAMAGE / BUNDLE / BOUND_EQUIP**；药水 **clear_potions / self_clear_potions / immunity**（禁假 amp-255）；武器 **use_ability + held_effects**；升级上限 **option → pool → config default**。完整字段见 [reference.md](reference.md)。

## 先读什么

1. 铁律：`CLAUDE.md` §0（绝对坐标、定义唯一处、内容增删零代码）。
2. 字段与边界：[reference.md](reference.md)。
3. 改完跑：[maggoteers-build-deploy](../maggoteers-build-deploy/SKILL.md)（`mvn test` + 部署 `E:/MCpaper`）。

**权威源**：加载校验看 `RewardLoadValidator` / `WeaponEffectValidator` / `SummonParamsParser` / `ItemAbilityRegistry`；本 skill 过期时以 Java 为准。

## 配置根目录

运行时：`plugins/Maggoteers/`（jar `saveResource`；**已存在的 YAML 不会被覆盖**）。仓库源：`src/main/resources/`。

| 文件 | 职责（铁律②） |
|------|----------------|
| `config.yml` | 全局：`mob_attributes`、`rewards.upgrade_level_cap_default`、层原点、波数、休整、结算、`aura` / `magic_fx`、券种 |
| `waves.yml` | **唯一**刷怪 strategy + 池（coeff / equipment / on_death / passengers / infernal） |
| `affixes.yml` | **唯一**原生词缀（倍率/出生药水；**无**战斗 `on:`） |
| `rewards.yml` | **唯一**3 选 1 / 职业（STAT / WEAPON / SUPPLY / **BUNDLE**） |
| `collectibles.yml` | STAT id → 护符 ItemCreator id（GUI 外观；**无** `icon:`） |
| `maps/actN/<mapId>/` | `structure.nbt` + `points.yml` + 可选 `special_waves.yml` |
| `items/*.yml` | ItemCreator 物品 + **`use_ability`** / **`held_effects`** |

加载顺序：`affixes` → `waves` → `maps` → `config` → `ItemService` → `rewards`。

## 三条铁律（配置侧）

1. **绝对坐标永不入配置** — 仅 `act_origins`；`points.yml` 相对坐标。
2. **定义唯一处** — 波 / 词缀 / 奖励各改一处；池用 strategy id。
3. **零代码内容** — 地图、波、词缀、IM `infernal`、奖励、物品、怪装备/亡语/体型、**既有 Effect 的 use_ability/held_effects** = YAML；**新 Trigger/Effect、多段投射物 AI** = Java。

## 配置 vs Java（决策）

```
需求可用既有 Effect + 合法 trigger？
  ├─ rewards STAT / items use_ability / held_effects → 写 YAML
  └─ 新触发、新 Effect、或配置表达不了的多段技能 → Java（ItemUseHandler 为 escape hatch）
```

## 常见任务工作流

### 加奖励（`rewards.yml`）

1. 选池 `actN_weak|strong|boss` 或 `class`；设 `cost` / `currency`。
2. `category`: `STAT` | `WEAPON` | `SUPPLY`；有 `grants:` → **BUNDLE**（可省略 category）。
3. STAT：写 `effect` + `params`；需要时写 `trigger` / `expiry` / `stack` / `upgrade_max`。
4. **必须带 trigger**：`GRANT_ITEM`、`SUMMON`、`BUFF_AREA`、`DISABLE_AI`（以及多数战斗型）。
5. **禁止 trigger/expiry**：`AURA`（仅常驻）。
6. fireTrigger 白名单见 reference（**勿**抄全量 Trigger 枚举）；`GRANT_ITEM`/`SUMMON` 额外允许 `ON_ACT_ENTER`。
7. 可升级：`stack: UPGRADE_LEVEL`；上限 = **`upgrade_max`（option）→ `upgrade_level_cap`（pool）→ `config rewards.upgrade_level_cap_default`**（默认 4）。
8. STAT 建议 `unique: true`；护符映射写 `collectibles.yml`。
9. 样例：`wave_revive_coin`（GRANT_ITEM）、`hit_fireball`（SUMMON）、`a1s_*`（BUFF_AREA）。

### 加武器能力（`items/*.yml`）

**`use_ability`**（右键，`cooldown_sec` **必填 >0**）：

| effect | 要点 |
|--------|------|
| `DAMAGE_*` / `HEAL_AREA` / `BUFF_AREA` / `DISABLE_AI` | `params` + 可选 `fx` |
| `GRANT_ITEM` / `SUMMON` | SUMMON 武器路径仅 `anchor: self` |
| `ADD_ATTRIBUTE` | **必须** `expiry`（下次近战加成） |
| `ADD_POTION` | 无 expiry + `duration_ticks>0` = 即时；有 expiry → `duration_ticks` 0/省略 |

**`held_effects`**（主手持有；id=`held:<itemId>:<n>`）：

- 禁止：`expiry`、`UPGRADE_LEVEL`、`GRANT_ITEM`、`SUMMON`、`REVOKE_GRANTS`
- 常驻（无 trigger）：仅 `ADD_ATTRIBUTE` / `ADD_POTION` / `AURA`
- 触发：`ON_DAMAGE_DEALT` | `ON_DAMAGE_TAKEN` | `ON_KILL`；战斗型（含 `BUFF_AREA`/`HEAL` 等）须带 trigger
- `DISABLE_AI` held：Phase 2（代码 validator 可过触发型，内容未铺样例）

### 加波次 / 词缀 / IM

同前：`strategies`+`pools`；原生 `affixes`；`infernal: { level, affixes }` 与原生命名空间独立；可选 `passengers` / `on_death` / `equipment` / `coeff.scale|follow_range`。

## 配置做不到的事

| 需求 | 原因 |
|------|------|
| 新 `Trigger` / `Effect` | 枚举 + `EffectService` |
| 奖励 `recurring` / `cooldown_sec` | `RewardOption` **未解析** |
| 怪用 PDC 武器放玩家同款技能 | 须自定义 AI/handler |
| 玩法 CD 单 YAML 源 | `item_cooldowns.yml` 未做 |
| 自定义 structure 名 / paste 偏移 | 固定 `structure.nbt` |
| 多段/投射物复杂技能 | `ItemUseHandler` + `registerHandler`（覆盖配置路由） |

**已不是硬编码 4**：`UPGRADE_LEVEL` 上限可配置（见上优先级）。

## 校验清单

- [ ] rewards：effect/trigger 过白名单；GRANT_ITEM 有 `item`；SUMMON 有 `entity`+`cleanup`；AURA 无 trigger
- [ ] UPGRADE_LEVEL：cap 来源正确；`collectibles` `items_by_level` 齐
- [ ] items：`use_ability.cooldown_sec>0`；held 无 expiry；PDC `maggoteers:id`
- [ ] waves：strategy / point / EntityType / affix id；equipment item 存在；on_death 嵌套 ≤8
- [ ] 绝对坐标未进 maps/waves；改完 `mvn test`

## 引用速查

- **升级上限**：option `upgrade_max` → pool `upgrade_level_cap` → `rewards.upgrade_level_cap_default`
- **强度**：`hp/dmg/speed/drop` = coeff × affix × 人数 scaling；`scale`/`follow_range` = coeff × affix × `mob_attributes`（不乘人数）
- **装备 slot**：`HEAD`/`CHEST`/`LEGS`/`FEET`/`HAND`/`OFF_HAND`（别名见 reference）

完整 schema → [reference.md](reference.md)。
