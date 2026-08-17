# 2026-07-24 玩法扩展设计（乘客怪 / 调试 / 夜天 / 进层准备 / 魔法 FX）

## 1. 乘客怪（waves.yml）

在 `steps[]` 增加可选 `passengers` 列表，语法与 step 类似（无 `point`/`delay`）：

```yaml
- point: boss
  type: CAMEL
  count: 1
  coeff: { hp: 2.0 }
  passengers:
    - { type: ZOMBIE, count: 1, coeff: { dmg: 1.2 }, affixes: [armored] }
    - { type: SKELETON, count: 1, affixes: [] }
```

RunPlanner 对乘客同样做 coeff×affix×scaling；WaveEngine 追踪坐骑与乘客 UUID（任一方死亡均计清波）。

## 2. 调试命令（maggoteers.admin）

| 子命令 | 说明 |
|--------|------|
| `debug jumpto <层> <波>` | 1-based，清怪后跳转；跨层重粘结构 |
| `debug jump [±n]` | 相对当前波偏移 |
| `debug spawnmob <实体> [词缀…]` | 脚下生成带缩放/词缀的怪 |
| `debug effects [玩家]` | 列出 PlayerState 内 PlayerEffect |

## 3. 夜天锁定

`config.yml` → `world.time_lock: night`（默认），`ADVANCE_TIME=false` 且周期性 `setTime`。

## 4. 进层准备

`act_enter.prep_sec: 10`：每层 `enterAct` 后 Phase `PREP`，倒计时结束再 `beginWave(0)`。调试跳波跳过 PREP。

## 5–6. 魔法物品 FX（实现清单）

| 能力 | 状态 | 位置 |
|------|------|------|
| 释放音效 | ✅ | `MagicUseFx.sound*` + `MagicFxService.play` |
| 粒子种类 | ✅ | `GameRegistries.particle` + `use_fx.particle` |
| 预设样式 ×6 | ✅ | `MagicFxPreset` + `MagicFxPresets` |
| 加粗默认环 | ✅ | `ParticleEffects.playThickRing` |
| 全局默认 | ✅ | `config.yml` → `magic_fx.defaults` |
| 按武器覆盖 | ✅ | `magic_fx.weapons` + `items/*.yml` → `use_fx` |
| 可调半径/射线/密度 | ✅ | `radius`, `ray_length`, `density` |
| 波纹/扩散/螺旋时长 | ✅ | `ripple_rings`, `expand_steps`, `spiral_ticks` |
| 局内播放入口 | ✅ | 各 `ItemUseHandler` 调 `MagicFxConfig.forWeapon` + `MagicFxService.play`（示例：`TestBladeHandler`） |

**未做（需新 Java 才值得做）**：乘客/怪物手持长矛（`MobFactory` 装备 schema）；无 handler 的物品仅配置 FX 不会自动播放。

