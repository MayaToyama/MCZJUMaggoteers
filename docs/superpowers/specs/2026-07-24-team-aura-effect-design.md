# 团队光环 Effect（AURA）设计

**状态**：已实现 v1（2026-07-24）  
**决策摘要**：新 `Effect.AURA`；出圈立刻移除派生；多来源可叠加；`include_self` 默认 `true`；范围与 `DAMAGE_AREA` 相同（立方体半径）。扩展：**对友军治疗**、**对敌派生 buff/debuff**。

---

## 1. 目标

- 携带者在 `PlayerState` 持有一条 **AURA**；有效期间以自身为中心，向范围内目标施加 **派生效果**（不写进队友 `PlayerState`）。
- 支持 **3 选 1 常驻**（无 `trigger`）与 **主动开启**（`trigger` + `expiry`，如 `ON_INTERACT` + `ON_TICK_1S` charges）。
- 扩展目标：
  - **友军**：属性/药水 + **周期性治疗**（`grant: HEAL`）。
  - **敌军**：对范围内怪物施加属性/药水（毒、缓速、减防等），与友军共用同一套 `grant` 语法。

---

## 2. `Effect.AURA` 语义

| 项 | 说明 |
|----|------|
| 真相源 | 仅 **携带者** `PlayerState` 中的 `PlayerEffect` |
| 接收者视图 | 玩家 / 怪物实体上的 AttributeModifier、PotionEffect（插件命名空间 key，可剥除） |
| 携带者有效条件 | 同局、`PlayerState.isAlive()`、冒险模式、AURA 条目未到期 |
| 刷新 | 每 `ON_TICK_1S`（全局 1s 心跳末尾）调用 `AuraService.refresh(game)` |
| 出圈 | 下一 tick 不在范围内 → **立刻**移除该 `(sourceUuid, auraInstanceKey)` 的派生 |
| 叠加 | **多来源可叠加**；每个来源独立 key；同玩家 `stack: ADD` 多条 AURA 用不同 `auraInstanceKey` |

**携带者自身**：`include_self: true`（默认）时，携带者作为友军目标参与 refresh（与站在自己圈里一致）。

---

## 3. 参数 schema（`params`）

```yaml
params:
  radius: 12.0                    # 必填，>0，立方体半径（同 DAMAGE_AREA / nearbyEntities）
  include_self: true              # 默认 true
  targets: allies                 # allies | enemies | both
  enemy_scope: tracked            # 仅 targets 含 enemies 时：tracked | all_living
  grant:                          # 必填
    effect: ADD_ATTRIBUTE         # ADD_ATTRIBUTE | ADD_POTION | HEAL
    # ADD_ATTRIBUTE
    attr: ATTACK_DAMAGE
    op: PERCENT
    value: 0.08
    # ADD_POTION
    # potion: POISON
    # amp: 0
    # HEAL（仅对 allies / include_self 有意义）
    # amount: 2.0
  grant_pulse_sec: 1              # grant 为 HEAL 时：每 N 秒跳一次；默认 1；属性/药水为持续派生（每 tick 刷新存在性）
```

### 3.1 `targets`

| 值 | 范围内目标 |
|----|------------|
| `allies` | 同局 **存活 + 冒险模式** 玩家（默认） |
| `enemies` | 见 `enemy_scope` |
| `both` | 友军 + 敌军分别按规则扫描 |

### 3.2 `enemy_scope`

| 值 | 说明 |
|----|------|
| `tracked`（默认） | 仅 `WaveEngine.isTracked(uuid)` 的本局波次怪（推荐，防误伤无关实体） |
| `all_living` | 半径内所有 `LivingEntity` 且非玩家（调试/特殊关卡用） |

### 3.3 `grant` 子效果（v1）

| grant.effect | 友军 | 敌军 | 行为 |
|--------------|------|------|------|
| `ADD_ATTRIBUTE` | ✓ | ✓ | 持续派生 modifier，在圈内每 refresh 保证存在 |
| `ADD_POTION` | ✓ | ✓ | 持续派生药水（玩家 30s 段；怪物用足够长 duration 并由 refresh 续） |
| `HEAL` | ✓ | ✗ | **脉冲**：每 `grant_pulse_sec` 对范围内友军执行一次 `heal(amount)`，不挂常驻 modifier |

不支持：嵌套 `AURA`、`DAMAGE_AREA`、`GRANT_REVIVE`。

**对敌「debuff」**：配置为 `ADD_POTION`（如 POISON、SLOWNESS）或负向 `ADD_ATTRIBUTE`（若 MC 属性允许负 FLAT/PERCENT）。

---

## 4. 配置示例

### 4.1 常驻友军攻光环（3 选 1）

```yaml
- id: war_banner
  category: STAT
  effect: AURA
  params:
    radius: 12.0
    targets: allies
    grant:
      effect: ADD_ATTRIBUTE
      attr: ATTACK_DAMAGE
      op: PERCENT
      value: 0.08
  display: "<gold>战旗：12 格内队友 +8% 攻"
```

### 4.2 友军治疗光环（每秒脉冲）

```yaml
- id: medic_field
  category: STAT
  effect: AURA
  params:
    radius: 8.0
    targets: allies
    include_self: true
    grant:
      effect: HEAL
      amount: 1.0
    grant_pulse_sec: 1
  display: "<green>医疗力场：8 格内每秒 +1 血"
```

### 4.3 对敌毒光环（常驻）

```yaml
- id: miasma
  category: STAT
  effect: AURA
  params:
    radius: 6.0
    targets: enemies
    enemy_scope: tracked
    grant:
      effect: ADD_POTION
      potion: POISON
      amp: 0
  display: "<dark_green>瘴气：6 格内怪物中毒"
```

### 4.4 主动双效（友治疗 + 开 30 秒）

```yaml
- id: holy_chorus
  category: STAT
  effect: AURA
  trigger: ON_INTERACT
  params:
    radius: 10.0
    targets: allies
    grant:
      effect: HEAL
      amount: 2.0
    grant_pulse_sec: 1
  expiry: { trigger: ON_TICK_1S, charges: 30 }
  stack: REFRESH
  display: "<aqua>圣歌：10 格内队友每秒 +2 血（30s）"
```

---

## 5. 运行时：`AuraService.refresh(game)`

**调用点**：`EffectListener` / 全局 1s tick 中，在 `EffectService.fireTrigger(game, ON_TICK_1S)` **之后**。

**算法概要**：

1. 枚举本局所有 **光环源** `(carrierPlayer, PlayerEffect aura)`，`effect == AURA` 且携带者有效。
2. 对每个源，按 `targets` + `radius` 解析目标集合：
   - 友军：同 `AbstractGame.getPlayers()` 过滤 alive + adventure + 距离
   - 敌军：`tracked` → `WaveEngine` 存活列表 ∩ 距离；`all_living` → nearby 非玩家 LivingEntity
3. 构建本 tick 应存在的边集 `E = {(receiverId, sourceUuid, auraInstanceKey, grantKind)}`。
4. 对每个当前持有 **aura 派生** 的接收者（玩家 + 曾受影响的 mob UUID 缓存）：
   - 若边不在 `E` → **strip** 该 `(source, instanceKey)`
5. 对 `E` 中每条边：
   - `ADD_*` → applyDerivedAura(...)
   - `HEAL` → 仅当 `(currentTick / 20) % grant_pulse_sec == 0` 时 `heal`（携带者进局 tick 对齐即可）

**派生 key 约定**（D5 扩展）：

- 玩家属性：`maggoteers:aura/{sourceUuid}/{instanceKey}/attr/{attr}`
- 玩家药水：`maggoteers:aura/{sourceUuid}/{instanceKey}/pot/{potion}`
- 怪物：无 AttributeModifier namespace 冲突时同上；怪物死亡由 `WaveEngine` / 扫描移除派生缓存

`instanceKey` = `{playerEffect.id}_{listIndex}`（apply 时稳定序号，或 merge ADD 时递增）。

**携带者失效**：死亡、观察者、AURA 条目被 sweep 移除 → `stripAllFromSource(sourceUuid)` 对所有已知接收者。

**复活**：`ON_REVIVE` 后可立即 `refresh` 一次（可选优化），否则最多 1s 延迟。

---

## 6. 与现有模块

| 模块 | 变更 |
|------|------|
| `Effect` | +`AURA` |
| `EffectKeys` | +`TARGETS`, `ENEMY_SCOPE`, `INCLUDE_SELF`, `GRANT_EFFECT`, `GRANT_PULSE_SEC`；或 `GRANT` 嵌套 `EffectContext` |
| `RewardOption` / `RewardService` | 解析 `grant` 嵌套、`targets` |
| `EffectService.apply` | AURA 不对携带者单独 `applyDerived`（由 refresh 统一处理 `include_self`） |
| `AuraService` | 新类；refresh / strip / applyGrant |
| `EffectListener` | 1s 末尾 `AuraService.refresh` |
| `MaggoteersCommand` | 可选 `debug aura` 列出携带源与接收关系（非 v1 阻塞） |

**单测**：`AuraServiceTest` — 两友军源叠加、出圈剥除、HEAL 脉冲计数、`tracked` 怪进圈中毒、源死亡全清。

---

## 7. 边界与后续

- 刷新粒度 **1s**；进圈/出圈最多 1 tick 延迟（可接受，与 `ON_TICK_1S` 一致）。
- 对敌 `ADD_ATTRIBUTE` 在部分实体上可能无某 attribute → 跳过并 log 一次。
- v1.1：水平距离选项、`debug aura`、进圈粒子、`grant` 支持限时 `DAMAGE_AREA` 脉冲（非本次）。

---

## 8. 自检

- [x] 与已批决策一致（3 + A + 1 + 立方体 + include_self 默认 true）
- [x] 友军治疗（HEAL 脉冲）与对敌 buff 已写入 schema
- [x] 范围明确、无 TBD
- [ ] 实现计划见后续 `docs/plan/`（writing-plans）
