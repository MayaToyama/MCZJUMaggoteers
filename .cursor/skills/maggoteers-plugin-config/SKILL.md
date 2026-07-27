---
name: maggoteers-plugin-config
description: >-
  Author and validate Maggoteers (卫戍协议) plugin YAML under plugins/Maggoteers/.
  Covers config.yml (mob_attributes), waves.yml (passengers, on_death, equipment,
  infernal, coeff scale), affixes.yml, rewards.yml (AURA/expiry), maps, items/*.yml.
  Use when adding maps, waves, mob tuning, death spawns, mob gear, rewards, or
  config-only vs Java questions.
---

# Maggoteers 插件配置编写

> 与后端对齐：Paper 26.2、波次 **passengers / on_death / equipment / scale**、效果 **AURA**、魔法 **magic_fx**。完整字段见 [reference.md](reference.md)。

## 先读什么

1. 项目铁律：`CLAUDE.md` §0（绝对坐标、定义唯一处、内容增删零代码）。
2. 字段与边界细节：[reference.md](reference.md)。
3. 改完运行：[maggoteers-build-deploy](../maggoteers-build-deploy/SKILL.md)（`mvn test` + 部署 `E:/MCpaper`）。

## 配置根目录

运行时路径：`plugins/Maggoteers/`（jar 首次启动 `saveResource`；**已存在的 `waves.yml` 等不会被 jar 覆盖**）。

| 文件 | 职责（铁律②） |
|------|----------------|
| `config.yml` | 全局：`mob_attributes`、层原点、波数、休整、结算、昼夜/夜视、**aura / magic_fx**、券种 |
| `waves.yml` | **唯一**刷怪 strategy + 池（**coeff**、**equipment**、**on_death**、**passengers**、**infernal**） |
| `affixes.yml` | **唯一**Maggoteers **原生**词缀（倍率/出生药水/标记；**无** `on:` 战斗触发） |
| `rewards.yml` | **唯一**3 选 1 / 职业（**AURA**、**expiry**） |
| `maps/actN/<mapId>/` | `structure.nbt` + `points.yml` + 可选 `special_waves.yml` |
| `items/*.yml` | ItemCreator 物品（可被 **怪物 equipment** 引用） |

加载顺序：`affixes.yml` → `waves.yml` → `maps` → `config.yml` → `ItemService` → `rewards.yml`。

## 三条铁律（配置侧）

1. **绝对坐标永不入配置** — 仅 `act_origins` 写层原点；`points.yml` 只写相对坐标。
2. **定义唯一处** — 波次 / 词缀 / 奖励各改一处；池用 **strategy id** 引用。
3. **零代码内容** — 地图、波、原生词缀、IM 技能（`infernal`）、奖励、物品、**怪体型/死亡召唤/怪装备** = YAML；**新 Trigger/Effect、新 PDC 武器机制** = Java。

## 怪物高级能力（纯配置）

以下字段可用于 **`steps[]` 根怪**、**`passengers[]`**、**`on_death[]`**（语法见 reference「mob 条目」）。

| 能力 | 配置 | 说明 |
|------|------|------|
| **体型** | `coeff.scale` + 词缀 `scale` + `config mob_attributes.scale` | 使用 `Attribute.SCALE`；无该属性的实体忽略 |
| **索敌距离** | `coeff.follow_range` + 词缀 `follow_range` + `mob_attributes.follow_range` | 乘算 `FOLLOW_RANGE` |
| **ItemCreator 装备** | `equipment: [ { slot: HEAD, item: maggoteers:xxx } ]` | `ItemService.createItem`；**掉落率 0**；仅外观/原版持械，**不是**玩家魔法武器机制 |
| **死亡召唤** | `on_death: [ { type: ZOMBIE, count: 3, coeff: {...}, ... } ]` | 死亡点生成并 **计入本波 livingMobs**；可嵌套 `on_death`（最深 8 层） |
| **IM 战斗技能** | `infernal: { level: 3, affixes: [poisonous, sprint] }` | 可选；主体/乘客/亡语**各自配置、不继承**；需 InfernalMobs JAR；禁用 `morph`/`mama`/`mounted`/`vexsummoner`/`ghost` |

示例（史莱姆壳 + 死亡爆僵尸）：

```yaml
steps:
  - point: "1"
    type: SLIME
    count: 1
    coeff: { hp: 2.0, scale: 1.8 }
    on_death:
      - { type: ZOMBIE, count: 4, coeff: { hp: 0.8, dmg: 1.2 } }
    equipment:
      - { slot: HEAD, item: maggoteers:leather_helmet }
```

## 常见任务工作流

### 加一条波次

1. `strategies` + `pools` 引用。
2. 原生 `affixes` id 须在 `affixes.yml` 存在；IM 技能写在 `infernal.affixes`（InfernalMobs 命名空间）。
3. 可选：`passengers`、`on_death`、`equipment`、`infernal`、`coeff.scale` / `follow_range`。
4. `clearReward[].item` 须 `ItemService` 可创建。

### 加词缀（Maggoteers 原生）

`hp` / `dmg` / `speed` / `drop` / **`scale`** / **`follow_range`**、`potions`（刷怪时施加）、`display`。战斗触发类词缀已退役；战斗效果改用 `waves.yml` 的 `infernal:`（见 reference）。

### 加 IM 技能

在 `steps[]`、`passengers[]` 或 `on_death[]` 加：

```yaml
infernal:
  level: 3          # 1–100
  affixes: [poisonous, sprint]
```

- 与原生 `affixes:` **独立**，不继承父节点。
- 显式 `equipment` 在 IM mechanize **之后**应用，最终覆盖 IM 自动装备。
- IM 缺失/未知/禁用技能：warning 一次，实体仍为普通怪。

### 加奖励 / 物品 / 魔法 FX

（与前一版相同：AURA、`expiry`、`use_fx`、`magic_fx`、Handler 注册武器 — 见 reference。）

## 配置做不到的事（须 Java 或暂不支持）

| 需求 | 原因 |
|------|------|
| 新 `Trigger` / `Effect` 类型 | 枚举 + `EffectService` |
| 奖励 `recurring` / `cooldown_sec` | `RewardOption` 未解析 |
| `UPGRADE_LEVEL` 上限 ≠ 4 | `RewardService` 硬编码 |
| IM 未安装时的 `infernal:` | 跳过 mechanize，实体仍为普通怪 |
| 怪用 PDC 武器放玩家同款技能 | 须自定义 AI/handler |
| 玩法 CD 单 YAML 源（无 Handler 常量） | `item_cooldowns.yml` 未做 |
| 自定义 structure 名 / paste 偏移 | 固定 `structure.nbt` |

## 校验清单

- [ ] strategy / point / EntityType / affix id 合法。
- [ ] **`equipment[].item`** 在 ItemCreator / 本插件 items 存在（否则 warning，槽位空）。
- [ ] **`on_death[].type`** 合法；嵌套深度 ≤ 8。
- [ ] **`coeff.scale`** 合理（过小被 clamp；实体无 SCALE 则无效）。
- [ ] AURA / 魔法武器 / `act_origins` 等同前 checklist。

## 引用速查

- **怪物装备 slot**：`HEAD`/`CHEST`/`LEGS`/`FEET`/`HAND`/`OFF_HAND`（别名见 reference）。
- **强度叠乘**：`hp/dmg/speed/drop` = coeff × affix × **人数 scaling**；**scale / follow_range** = coeff × affix × **mob_attributes**（不受人数 scaling 数组影响）。

完整 schema 见 [reference.md](reference.md).
