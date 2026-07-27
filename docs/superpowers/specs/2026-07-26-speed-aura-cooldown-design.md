# 2026-07-26 怪物移速 / 光环粒子 / 物品冷却显示

> 来源：smoke 反馈 + brainstorming（用户确认：问题1→方案1；问题2→单次 FX + 配置间隔；问题3→仅 YAML `useCooldown`）。

## 背景

| # | 现象 / 需求 | 结论 |
|---|-------------|------|
| 1 | 几乎所有波次怪移动极快；骑乘波反而正常或偏慢 | 单怪被误登记进 `MountedSquadRegistry`，每 5 tick 超高速 `pathfinder.moveTo` |
| 2 | 三选一 AURA 脚下粒子过密，扩圈尤其明显 | `AuraService.refresh` 双心跳 + 每秒多次触发完整 FX |
| 3 | ItemCreator 支持原生冷却组件 | 三把魔法武器在 `maggoteers.yml` 增加 `useCooldown`，与 Java CD 秒数一致（v1 不改 Java 单源） |

## 目标

1. **移速**：无乘客的刷怪步不再进入骑乘 AI registry；单怪恢复原版 Mob AI 速度。
2. **光环粒子**：`MagicFxPresets` 仍表示「一次播放完整动画」（螺旋一圈、扩环到最大半径等）；**何时再播**由 `config.yml` 配置间隔控制；AURA 属性/HEAL 派生刷新频率不变（~1 Hz）。
3. **冷却显示**：`items/maggoteers.yml` 为三武器添加 `useCooldown`；玩法门禁仍用现有 `CooldownService` + Handler 常量。

## 非目标

- 调整 `waves.yml` 内 `coeff.speed` 数值平衡。
- 骑乘小队 `moveTo` 速度公式重构（用户确认骑乘体感可接受；仅做 registry 范围收窄）。
- `config.yml` 统一 item CD 数字源（Plan 8 / 问题3 方案2）。
- 在 `tryUse` 成功后额外调用 `HumanEntity.setCooldown(Material)`（避免同材质串 CD，见 CLAUDE D2）。

---

## §1 怪物移速：根因与修复

### 1.1 根因

`MobFactory.spawnStepGroup` 在 **`step.passengers()` 为空** 时仍执行：

```java
MountedSquadRegistry.register(mount, mount);
```

`WaveEngine.tick` → `MountedSquadAiService.tick` 对 registry 内每个 root（且为 `Mob`）调用 `pathfinder.moveTo(target, MOVEMENT_SPEED × 20)`。单怪因此持续被「超快寻路」；有乘客时 root 位移受挂载与 AI 分工影响，体感接近正常或偏慢（与用户 **A** 一致）。

### 1.2 修复（用户选定：方案 1）

- **仅当** `!step.passengers().isEmpty()` 时，在延迟挂载任务完成后 `MountedSquadRegistry.register(root, controller)`。
- **无乘客**：不 register；不调用 `MountedSquadAiService` 对该实体的 `moveTo`（registry 快照中不存在即可）。
- 死亡/清波现有 `MountedSquadRegistry.removeByRoot` 逻辑保持不变。

### 1.3 验收

- 测试服：普通僵尸/骷髅/蜘蛛波明显恢复为原版追击速度。
- 强怪骑乘波（僵尸马/炽足兽等）仍有乘客且 AI 与现网一致。
- 无新增控制台 spam；单测若有 `MobFactory` 相关可补「空 passengers 不 register」（可选，非阻塞）。

---

## §2 光环粒子：单次 FX + 配置间隔

### 2.1 设计原则

| 层 | 职责 |
|----|------|
| **预设实现**（`MagicFxPresets` / `ParticleEffects`） | 一次 `play()` = 完整单次效果：如 `SPIRAL_RADIUS` 转满 `spiral_ticks`，`SMOOTH_EXPAND_RING` 从 0 扩到 `radius`，`THICK_RING` 即时一圈等。不在预设内做「持续光环」逻辑。 |
| **AURA 携带者**（`AuraService.playCarrierFx`） | 按**配置间隔**决定是否再次调用 `MagicFxPresets.play`；间隔未到则不播（避免叠多个未结束的 `BukkitRunnable`）。 |
| **AURA 数值派生**（grant attribute/potion/HEAL） | 仍随 `AuraService.refresh` ~1 Hz 更新，与粒子节流解耦。 |

### 2.2 双心跳合并

- **`GameplayTickListener`**：保留 `AuraService.refresh` + 常驻药水 + 饱腹。
- **`EffectListener.startTick`**：仅 `EffectService.fireTrigger(ON_TICK_1S)`，**移除**其中的 `AuraService.refresh`。

避免携带者粒子 effective 2 Hz。

### 2.3 配置 schema（`config.yml`）

新增段（命名可微调，实现时与 `ConfigManager` 一致即可）：

```yaml
aura:
  carrier_fx:
    default_interval_sec: 1    # THICK_RING、DOT_ABOVE、RIPPLE_RINGS 等未单独指定时
    by_preset:
      SMOOTH_EXPAND_RING: 3    # 扩环动画更重，默认更长间隔
      THICK_RING: 1
      # SPIRAL_RADIUS / AIM_RAY 等若未来用于 carrier_fx 可在此扩展
```

- 读取失败或未配置时：`default_interval_sec = 3`，`SMOOTH_EXPAND_RING = 6`。
- 节流键建议：`game + carrierUuid + auraInstanceKey`（或 `PlayerEffect` id + 列表下标），记录 `lastPlayTick` 或 `lastPlaySec`；当 `nowSec - last >= intervalForPreset(preset)` 时调用 `play()` 并更新。

### 2.4 重叠动画（实现注意）

`SMOOTH_EXPAND_RING` / `SPIRAL_RADIUS` / `RIPPLE_RINGS` 使用 `runTaskTimer` 多 tick 完成。间隔内**不得**重复 `play()` 即可；若间隔极短仍可能重叠，v1 以配置默认保证间隔 ≥ 动画时长量级（扩环 ~20 tick ≈ 1 s，间隔 6 s 足够）。

`rewards.yml` 中 `carrier_fx` 的 `density` / `expand_steps` 保持内容配置，不在本 spec 批量改数值。

### 2.5 验收

- 持有多 AURA（如战旗 + 恢复之环）时，脚下粒子明显稀疏；扩环比厚环更稀。
- AURA 加攻/回血/减速等** gameplay 不受影响**（仍每秒刷新派生）。
- 武器右键 `use_fx`（旋风斩等）仍为**每次使用触发一次**完整动画，不受 `aura.carrier_fx` 影响。

---

## §3 物品原生冷却（ItemCreator `useCooldown`）

### 3.1 范围

仅 PDC 武器（Handler 内已有 CD）：

| item id | Handler CD（秒） | cooldownGroup 建议 |
|---------|------------------|------------------|
| `maggoteers:test_blade` | 3 | `maggoteers:test_blade` |
| `maggoteers:excalibur` | 10 | `maggoteers:excalibur` |
| `maggoteers:healing_staff` | 25 | `maggoteers:healing_staff` |

### 3.2 YAML 写法（与 ItemCreator 新版本一致）

在 `src/main/resources/items/maggoteers.yml` 各条目下增加：

```yaml
useCooldown:
  cooldownGroup: "maggoteers:healing_staff"
  seconds: 25
```

- **v1**：Java 中 `COOLDOWN_SEC` 常量保持不变；运维改 CD 时需同时改 YAML 与 Handler（文档在 `docs/ICReadme.md` 或 plugin-config skill 补一句）。
- 玩法拦截仍走 `CooldownService.tryUse`；原生组件负责**物品栏冷却条显示**。若实测「取消交互仍显示 CD」或「成功无 CD 圈」，再开 follow-up（Paper `setCooldown` on group 或 ItemCreator 文档），本 spec 不预先实现。

### 3.3 验收

- 测试服 reload/重启后，三武器右键使用后物品图标出现与秒数一致的冷却 overlay。
- CD 内再次右键仍被 Handler 拦截并提示「冷却中…」。

---

## §4 测试与部署

- `mvn test package` 通过。
- 拷贝 jar + 变更的 `config.yml` / `items/maggoteers.yml` 至 `E:\MCpaper\plugins\Maggoteers\`（或全量释放目录），重启 Paper。

---

## §5 关联文档

- 前置 smoke：`docs/superpowers/specs/2026-07-25-smoke-round2-design.md`（曾将 use_cooldown 标为非目标，本 spec 单独交付）。
- 铁律：玩法 CD 不以 `Material` 为粒度（CLAUDE §10.3 D2）。
