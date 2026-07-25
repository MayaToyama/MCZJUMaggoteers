# 2026-07-25 Smoke 第二轮修复设计（乘客丢失 + 三选一无效）

> 来源：smoke 复测反馈 + brainstorming 确认。  
> 前置：`docs/superpowers/specs/2026-07-25-smoke-fixes-design.md` 已落地骑乘/AURA/FX 等，但测试服仍有两类回归。

## 背景（测试现象）

| # | 现象 | 用户确认 |
|---|------|----------|
| 1 | 炽足兽、僵尸马生成后**无乘客** | — |
| 2 | 三选一道具**无粒子、无实际效果** | 点选项后**无**绿色「获得：…」，仅关界面（**C**）；打开 3 选 1 时**能看到 3 个有名字的选项**（**A**） |

**推论**

- **乘客**：配置与 `MobFactory` 挂载链已在 repo/测试服 YAML 中存在，更可能是**运行时 step 数据丢失**，而非 YAML 未部署。
- **三选 1**：能看见三格但无「获得」→ `RewardService.apply` **大概率未成功跑完**（MGC 菜单点击未进回调、或回调内异常），不能先假设只是 `EffectService`/AURA 派生问题。

---

## 目标

1. **骑乘**：`expand()` 后 `SpawnStep.passengers` 与 RunPlanner 产出一致；smoke 强怪池 roll 到 `s1_zombie_horse_squad` / `s1_strider_squad` 时可见乘客（含嵌套 strider）。
2. **三选 1**：点击中间选项 → **必有**绿字成功或红字失败反馈；STAT/AURA/WEAPON/SUPPLY 按类别生效；空池/零可见选项不 silently 吞币。
3. **属性解析**：Paper 26.2 下 `rewards.yml` 的 `ATTACK_DAMAGE` 等能解析到 `Registry.ATTRIBUTE`（`attack_damage` 与 legacy `generic.*` 双路径）。

## 非目标

- 物品原生 `use_cooldown` component 显示（另项）。
- 重写 MGC `Menu` 框架。
- 大规模 `rewards.yml` 内容增删。

---

## §1 乘客丢失：根因与修复

### 1.1 根因（代码已证实）

`WaveSpec.expand()` 在展开 `repeat` 与累加 `delayTicks` 时，使用 10 参构造 `SpawnStep`，**未复制 `passengers`**，等价于运行时一律 `List.of()`。

调度器消费的是 `expand()` 结果 → `WaveEngine` → `MobFactory.spawnStepGroup` 中 `step.passengers().isEmpty()` 恒为 true → 只生成裸坐骑。

### 1.2 修复

- `WaveSpec.expand()` 构造新 step 时传入 **`s.passengers()`**（与 point/type/count/系数/affix/potions 同级复制）。
- 新增单测：`expand()` 前后 `passengers` 深度/内容不变（至少 1 条嵌套 `PassengerSpawn` 非空）。

### 1.3 验收

- 单测 `WaveSpecTest` 通过。
- 测试服：强怪波出现僵尸马/炽足兽时，**1 tick 内**（现有 `runTaskLater(1L)`）可见乘客；控制台无大量 `addPassenger 失败`（若有则另查实体族，本 spec 不扩大 scope）。

---

## §2 三选 1：GUI 链 + 奖励应用

### 2.1 现象约束（A + C）

- GUI 已展示 3 个命名选项 → `RewardService.draw` **非空**，`PickMenu.setup` 已 `setSlot(11/13/15)`。
- 点击后无「获得」→ 优先排查：
  1. **MGC `Menu` 点击**：是否未触发 `setSlot` 回调（例如被当成取走物品、shift 点击、事件 cancelled）。
  2. **回调内未捕获异常**：`ItemService.give` / `displayPlain()` / `EffectService.apply` 抛错导致无绿字（效果亦未生效）。

### 2.2 PickMenu 对齐 ClassSelectMenu

| 项 | 现状问题 | 改法 |
|----|----------|------|
| 图标 | STAT 无 `item`，`createItem(null)` → 纸；易与「空选项」混淆 | 与 `ClassSelectMenu` 一致：优先 `RewardOption.icon()`（`rewards.yml` 的 `icon:`），否则按 `category` 默认材质 |
| Lore | 无「点击选择」提示 | 增加 `▶ 点击选择` |
| 回调 | 仅 `apply` + 关界面 | `apply` 返回 `boolean` 或抛受控结果；失败不 reopen rest 吞掉错误 |

**MGC 契约（实现前核对 GitHub MCZJUGameCore 1.0.7 `Menu#setSlot`）**

- 确认点击类型（左键）是否触发 action。
- 若菜单允许把展示物拖入背包，改为 **禁止移出**（与 MGC 其它菜单一致的模式）；避免「点了像选了，实际只拿了物品关 GUI」。

### 2.3 RestMenu 扣费顺序

**采用：先 `draw` 预览可见数，再扣费，再开 PickMenu。**

- `openPick` 内：`draw(poolId, 3, rng, player)`，若 `visible.isEmpty()` → 红字「当前没有可选奖励」、**不扣币**、不开 Pick。
- 若 `visible.size() < 3` → 仍开 Pick，空位保持灰显（已有 D3 精神；PickMenu 仅渲染 `offers.size()` 格）。

（若实现发现 MGC 必须构造时 draw，可构造前在 RestMenu 预 draw 一次，PickMenu 构造器接收已抽好的 `List<RewardOption>`，避免 double-random。）

### 2.4 RewardService.apply 与 PlayerState

新增重载（或必选参数）：

```text
apply(Player player, RewardOption opt, AbstractGame game)
```

- `PlayerState` 一律 `PlayerStateManager.get(game, uuid)`，**不**仅依赖 `EffectService` 内 `PlayerExt.getGame()`（避免 profile/对局引用不一致）。
- `game == null` 或 `PlayerState == null` → 返回 false，红字「奖励未能应用（本局状态异常）」+ `LOG.warning`（含 pool option id、uuid）。
- 成功 → 绿字「获得：…」；**仅在成功路径**写入 `acquiredUnique`（避免「失败了仍占 unique」）。

`EffectService.apply` 可增 package-private / 包内 overload 传入 `MaggoteersGame game`，或 `RewardService` 在确认 state 后直接操作 state + 调现有派生逻辑（最小 diff 优先：给 `EffectService` 传 game 的 overload）。

### 2.5 GameRegistries.attribute（Paper 26.2）

`ATTACK_DAMAGE` / `MAX_HEALTH` / `MOVEMENT_SPEED` / `ATTACK_SPEED` 解析顺序：

1. legacy 表：`generic.max_health` 等（兼容旧配置字符串）。
2. 1.21+ 短名：`max_health`、`attack_damage`、`movement_speed`、`attack_speed`。
3. 仍失败 → `LOG.warning` 一次（带原始 config 字符串），`applyAttribute*` 早退。

保证 AURA `grant` 与 STAT `ADD_ATTRIBUTE` 在 smoke 选项（如 `a1w_dmg10`、`a1s_ring_strength`）下非 silent no-op。

### 2.6 验收

- 局内休整：点「普通奖励」→ 3 选 1 → 点任一选项 → **聊天必有**绿或红反馈。
- 选 STAT（+10% 伤害）：F3 或 `/attribute` 可见修饰（或实测伤害上升）；选带 `carrier_fx` 的 AURA → 1s 内可见粒子。
- 选 WEAPON/SUPPLY → 背包有对应 ItemCreator 物品（`ItemService.give`）。
- 控制台无未捕获异常栈（若曾复现，修复后同一操作无栈）。

---

## §3 测试与部署

- **单元测试**：`WaveSpecTest`（passengers）；可选 `GameRegistriesTest` 或 `RewardOption` 解析 smoke 样例（attribute 非 null）。
- **构建**：`mvn test`。
- **部署**：jar + 若仅 Java 修复则不必覆盖服上 `waves.yml`；`rewards.yml` 已有时可不覆盖（`saveResource false` 行为不变）。
- **人工 smoke**：1 人进局 → 强怪波看乘客 → 清波休整 → 三选 1 全流程。

---

## §4 风险与回滚

| 风险 | 缓解 |
|------|------|
| MGC Menu API 与本地假设不符 | 实现前读 1.0.7 源码；必要时仅改 click 优先级/取消拖拽 |
| apply 双路径（旧 overload）漏改 | 全 repo grep `RewardService.apply`，统一带 `game` |
| 扣费顺序改动导致重复 draw 不同结果 | RestMenu 预 draw 列表传入 PickMenu |

回滚：revert 本 spec 对应 commit；乘客问题会复现，三选 1 行为回到现网。

---

## §5 与第一轮 smoke spec 关系

- 第一轮已实现骑乘/AURA/FX **代码路径**；本 spec 修 **WaveSpec.expand 遗漏** 与 **PickMenu/apply 链**。
- 不在此重复 §1.7 AI、§AURA 算法细节；若 apply 修好后 AURA 仍无效，再开第三轮针对 `AuraService` 单点排查。

---

## 决策记录

| 日期 | 决策 | 原因 |
|------|------|------|
| 2026-07-25 | expand 必须复制 passengers | 代码审查确认，与 smoke「无乘客」一致 |
| 2026-07-25 | 三选 1 先 draw 再扣费 | 避免空池吞币；用户 C 反馈 |
| 2026-07-25 | apply 绑定 AbstractGame | 用户 A+C：有 UI 无反馈，优先 GUI/应用链 |
| 2026-07-25 | attribute 双路径解析 | Paper 26.x registry 与 generic.* 并存 |

---

**状态**：设计已获需求方口头 **OK**（2026-07-25）。待书面 spec 审阅通过后 → `writing-plans` 产出 `docs/superpowers/plans/2026-07-25-smoke-round2.md`。
