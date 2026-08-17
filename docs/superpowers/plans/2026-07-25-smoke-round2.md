# Smoke Round 2 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix mounted-step passengers lost after `WaveSpec.expand()`, and restore 3-choice rewards (GUI click → apply → feedback + STAT/AURA/物品生效).

**Architecture:** One-line `expand()` fix + tests; `RewardService.apply(Player, RewardOption, AbstractGame)` returns `boolean` and binds `PlayerState` to the menu’s game instance; `EffectService.apply` overload with explicit game; RestMenu pre-draws offers before charging currency; PickMenu receives fixed offer list and aligns UX with `ClassSelectMenu`; `GameRegistries.attribute` tries legacy `generic.*` then 1.21 short names.

**Tech Stack:** Paper 26.2 API, Java 25, Maven, MCZJUGameCore 1.0.7 (`Menu` / `MenuFacade`).

## Global Constraints

- Spec: `docs/superpowers/specs/2026-07-25-smoke-round2-design.md`（非目标：use_cooldown component、重写 MGC Menu、大规模 rewards 改内容）。
- Paper **26.2**；属性解析需 `attack_damage` 与 `generic.attack_damage` 双路径。
- 三选 1：**先 draw 再扣费**；apply **仅成功**写入 `acquiredUnique`。
- 全 repo `RewardService.apply` 调用点需传入 `game`（或走带 game 的重载）。

**Spec coverage map:** §1 → Task 1 | §2.2–2.4 → Tasks 3–5 | §2.5 → Task 2 | §3 → Task 6

---

### Task 1: `WaveSpec.expand()` 保留 passengers

**Files:**
- Modify: `src/main/java/io/mczju/maggoteers/wave/WaveSpec.java:34-37`
- Test: `src/test/java/io/mczju/maggoteers/wave/WaveSpecTest.java`

**Interfaces:**
- Produces: `expand()` 输出的每个 `SpawnStep` 与源 step 的 `passengers()` 列表一致（引用不可变拷贝即可）。

- [ ] **Step 1: 写失败单测**

在 `WaveSpecTest.java` 末尾追加：

```java
import io.mczju.maggoteers.wave.PassengerSpawn;
import org.bukkit.entity.EntityType;

@Test
void expandPreservesPassengers() {
    PassengerSpawn child = new PassengerSpawn(
            EntityType.SKELETON, 1.0, 1.0, 1.0, 1.0, List.of(), List.of());
    SpawnStep step = new SpawnStep(
            new Vec3(0, 64, 0), EntityType.STRIDER, 1,
            1.0, 1.0, 1.0, 1.0, 0, List.of(), List.of(), List.of(child));
    WaveSpec w = new WaveSpec(List.of(step), 1, List.of());
    List<SpawnStep> exp = w.expand();
    assertEquals(1, exp.size());
    assertEquals(1, exp.get(0).passengers().size());
    assertEquals(EntityType.SKELETON, exp.get(0).passengers().get(0).type());
}

@Test
void expandPreservesPassengersAcrossRepeat() {
    PassengerSpawn child = new PassengerSpawn(
            EntityType.ZOMBIE, 1.0, 1.0, 1.0, 1.0, List.of(), List.of());
    SpawnStep step = new SpawnStep(
            new Vec3(0, 64, 0), EntityType.ZOMBIE_HORSE, 1,
            1.0, 1.0, 1.0, 1.0, 0, List.of(), List.of(), List.of(child));
    WaveSpec w = new WaveSpec(List.of(step), 2, List.of());
    assertEquals(2, w.expand().size());
    assertFalse(w.expand().get(0).passengers().isEmpty());
    assertFalse(w.expand().get(1).passengers().isEmpty());
}
```

- [ ] **Step 2: 运行单测确认 FAIL**

Run: `mvn -q test -Dtest=WaveSpecTest#expandPreservesPassengers,WaveSpecTest#expandPreservesPassengersAcrossRepeat`

Expected: FAIL（passengers 为空）

- [ ] **Step 3: 修复 `expand()`**

`WaveSpec.java` 中 `out.add(new SpawnStep(...))` 改为 11 参构造，最后一参 `s.passengers()`：

```java
                out.add(new SpawnStep(
                        s.point(), s.type(), s.count(),
                        s.hpMult(), s.dmgMult(), s.speedMult(), s.dropMult(),
                        timeline, s.affixes(), s.potions(), s.passengers()));
```

- [ ] **Step 4: 运行单测确认 PASS**

Run: `mvn -q test -Dtest=WaveSpecTest`

Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/io/mczju/maggoteers/wave/WaveSpec.java src/test/java/io/mczju/maggoteers/wave/WaveSpecTest.java
git commit -m "fix(wave): preserve passengers in WaveSpec.expand"
```

---

### Task 2: `GameRegistries.attribute` Paper 26.2 双路径

**Files:**
- Modify: `src/main/java/io/mczju/maggoteers/util/GameRegistries.java`
- Test: `src/test/java/io/mczju/maggoteers/util/GameRegistriesAttributeTest.java`（新建）

**Interfaces:**
- Produces: `GameRegistries.attribute(String)` 对配置名 `ATTACK_DAMAGE` 等返回非 null（在 Paper 测试环境；若 CI 无 Registry，见 Step 2 备选）。

- [ ] **Step 1: 实现解析顺序**

替换 `attribute(String name)` 为：先规范化大写 → 按序尝试 Registry lookup：

1. `ATTRIBUTE_LEGACY` 映射值（`generic.max_health` 等）
2. `u.toLowerCase().replace('_', '.')`（如 `attack_damage`）
3. 去掉 `generic.` 前缀后再试短名

仍 null 时：`MaggoteersPlugin.getInstance().getLogger().warning("未知属性: " + name)`（注意 `getInstance()` 在单测可能 null — 仅在有 plugin 时 log）。

示例核心逻辑：

```java
public static Attribute attribute(String name) {
    if (name == null || name.isBlank()) return null;
    String u = name.trim().toUpperCase(Locale.ROOT);
    List<String> candidates = new ArrayList<>();
    if (ATTRIBUTE_LEGACY.containsKey(u)) candidates.add(ATTRIBUTE_LEGACY.get(u));
    candidates.add(u.toLowerCase(Locale.ROOT).replace('_', '.'));
    String dotted = candidates.get(candidates.size() - 1);
    if (dotted.startsWith("generic.")) candidates.add(dotted.substring("generic.".length()));
    for (String key : candidates) {
        Attribute a = Registry.ATTRIBUTE.get(NamespacedKey.minecraft(key));
        if (a != null) return a;
    }
    return null;
}
```

- [ ] **Step 2: 单测（纯逻辑候选列表，不依赖 Registry）**

新建 `GameRegistriesAttributeTest`，仅测 **候选 key 顺序**（可将 `candidatesForAttribute(String u)` 包可见 static 提取供测，或复制最小断言表）。若不想提取方法，跳过自动测，在 Task 6 smoke 验证 `a1w_dmg10`。

- [ ] **Step 3: `mvn test`**

- [ ] **Step 4: Commit**

```bash
git add src/main/java/io/mczju/maggoteers/util/GameRegistries.java
git commit -m "fix(config): resolve attributes on Paper 26.2 registry keys"
```

---

### Task 3: `EffectService.apply` 显式 game 重载

**Files:**
- Modify: `src/main/java/io/mczju/maggoteers/effect/EffectService.java`

**Interfaces:**
- Produces: `public static void apply(Player p, PlayerEffect incoming, MaggoteersGame game)`
- Consumes: Task 4 `RewardService` 在 STAT 路径调用此重载。

- [ ] **Step 1: 添加重载并委托**

```java
public static void apply(Player p, PlayerEffect incoming, io.mczju.maggoteers.game.MaggoteersGame game) {
    if (game == null) {
        LOG.warning("EffectService.apply: game null " + p.getName());
        return;
    }
    PlayerState st = PlayerStateManager.get(game, p.getUniqueId());
    if (st == null) {
        LOG.warning("EffectService.apply: 无 PlayerState " + p.getName());
        return;
    }
    var merged = EffectStacker.merge(st.effects(), incoming);
    st.effects().clear();
    st.effects().addAll(merged);
    if (incoming.isPermanent() && incoming.effect() != Effect.AURA) {
        applyDerived(p, incoming);
    }
}
```

原 `apply(Player, PlayerEffect)` 改为：

```java
public static void apply(Player p, PlayerEffect incoming) {
    apply(p, incoming, currentGame(p));
}
```

- [ ] **Step 2: 编译**

Run: `mvn -q -DskipTests compile`

- [ ] **Step 3: Commit**

```bash
git add src/main/java/io/mczju/maggoteers/effect/EffectService.java
git commit -m "feat(effect): apply PlayerEffect with explicit game"
```

---

### Task 4: `RewardService.apply` 返回 boolean + 绑定 game

**Files:**
- Modify: `src/main/java/io/mczju/maggoteers/reward/RewardService.java`

**Interfaces:**
- Produces: `public static boolean apply(Player player, RewardOption opt, AbstractGame game)`
- Produces: `public static boolean apply(Player player, RewardOption opt)` → 委托 `PlayerExt.getGame()`

- [ ] **Step 1: 重写 apply 主逻辑**

```java
public static boolean apply(Player player, RewardOption opt, AbstractGame game) {
    if (opt == null || game == null) return false;
    var ps = PlayerStateManager.get(game, player.getUniqueId());
    if (ps == null) {
        LOG.warning("RewardService.apply: 无 PlayerState option=" + opt.id() + " uuid=" + player.getUniqueId());
        player.sendMessage(Component.text("奖励未能应用（本局状态异常）", NamedTextColor.RED));
        return false;
    }
    try {
        switch (opt.category()) {
            case SUPPLY, WEAPON -> ItemService.give(player, opt.item(), opt.amount());
            case STAT -> {
                if (opt.effect() == Effect.GRANT_REVIVE) {
                    int count = opt.params() == null ? 1
                            : opt.params().getOrDefault(EffectKeys.COUNT, 1);
                    PlayerStateManager.addReviveCount(game, player.getUniqueId(), count);
                } else {
                    if (!applyStat(player, opt, game)) return false;
                }
            }
        }
    } catch (Exception ex) {
        LOG.warning("RewardService.apply 失败 " + opt.id() + ": " + ex.getMessage());
        player.sendMessage(Component.text("奖励应用失败：" + opt.id(), NamedTextColor.RED));
        return false;
    }
    ps.acquiredUnique().add(opt.id());
    player.sendMessage(Component.text("获得：" + opt.displayPlain(), NamedTextColor.GREEN));
    return true;
}

public static boolean apply(Player player, RewardOption opt) {
    var pe = new PlayerExt(player);
    if (!pe.isInGame()) return false;
    return apply(player, opt, pe.getGame());
}
```

- [ ] **Step 2: `applyStat` 接受 game，失败返回 false**

```java
private static boolean applyStat(Player player, RewardOption opt, AbstractGame game) {
    if (!opt.isStatEffect()) {
        LOG.warning("RewardService: STAT 配置不完整 id=" + opt.id());
        return false;
    }
    if (!(game instanceof io.mczju.maggoteers.game.MaggoteersGame mg)) return false;
    // ... 现有 build PlayerEffect 逻辑 ...
    EffectService.apply(player, pe, mg);
    return true;
}
```

删除旧 `applyStat(Player, RewardOption)` 或无 game 版本。

- [ ] **Step 3: 更新调用方**

| 文件 | 改法 |
|------|------|
| `menu/PickMenu.java` | Task 5 |
| `menu/ClassSelectMenu.java` | `RewardService.apply(p, opt, game)` |
| `game/ClassSelectGate.java` | `RewardService.apply(p, opt, game)` |

- [ ] **Step 4: `mvn test`**

- [ ] **Step 5: Commit**

```bash
git add src/main/java/io/mczju/maggoteers/reward/RewardService.java \
  src/main/java/io/mczju/maggoteers/menu/ClassSelectMenu.java \
  src/main/java/io/mczju/maggoteers/game/ClassSelectGate.java
git commit -m "fix(reward): apply rewards bound to game with user feedback"
```

---

### Task 5: RestMenu 先 draw 再扣费；PickMenu 预传 offers

**Files:**
- Modify: `src/main/java/io/mczju/maggoteers/menu/RestMenu.java`
- Modify: `src/main/java/io/mczju/maggoteers/menu/PickMenu.java`

**Interfaces:**
- Consumes: `RewardService.draw(poolId, 3, Random, player)` → `List<RewardOption>`
- Consumes: `RewardService.apply(p, opt, game)` → `boolean`
- MenuFacade: `MenuFacade.open("maggoteers-pick", p, game, offers)` — **args[1] 改为 `List<RewardOption>`**，不再是 `String poolId`。

- [ ] **Step 1: RestMenu.openPick**

```java
private void openPick(Player p, String poolId, ItemKind currencyKind) {
    RewardPool pool = RewardService.pool(poolId);
    if (pool == null) {
        p.sendMessage(Component.text("奖励池未配置：" + poolId, NamedTextColor.RED));
        return;
    }
    List<RewardOption> offers = RewardService.draw(poolId, 3, new Random(), p);
    if (offers.isEmpty()) {
        p.sendMessage(Component.text("当前没有可选奖励。", NamedTextColor.RED));
        return;
    }
    if (ItemService.countKind(p, currencyKind) < pool.cost()) {
        p.sendMessage(Component.text("货币不足！", NamedTextColor.RED));
        return;
    }
    if (!ItemService.spendOneKind(p, currencyKind)) {
        p.sendMessage(Component.text("扣费失败。", NamedTextColor.RED));
        return;
    }
    p.closeInventory();
    MenuFacade.open("maggoteers-pick", p, game, offers);
}
```

- [ ] **Step 2: PickMenu 构造器**

```java
@SuppressWarnings("unchecked")
public PickMenu(Player player, Object... args) {
    super(player, args);
    this.game = (AbstractGame) args[0];
    this.offers = (List<RewardOption>) args[1];
}
```

删除构造器内 `RewardService.draw`。

- [ ] **Step 3: PickMenu.setup 图标 + 回调（对齐 ClassSelectMenu）**

```java
private static ItemStack icon(RewardOption opt) {
    Material mat = opt.icon() != null ? opt.icon() : switch (opt.category()) {
        case WEAPON -> Material.IRON_SWORD;
        case STAT -> Material.GOLDEN_APPLE;
        case SUPPLY -> Material.CHEST;
    };
    ItemStack stack = (opt.item() != null && !opt.item().isBlank())
            ? ItemService.createItem(opt.item(), 1).orElse(new ItemStack(mat))
            : new ItemStack(mat);
    // displayName / lore 含 description + "▶ 点击选择"
    ...
}

// click handler:
boolean ok = RewardService.apply(p, opt, game);
p.closeInventory();
if (ok) MenuFacade.open("maggoteers-rest", p, game);
else p.sendMessage(Component.text("请重试或联系管理员。", NamedTextColor.GRAY));
```

- [ ] **Step 4: 核对 MGC Menu 1.0.7**

打开依赖 jar 或 GitHub `Menu.java`：确认 `setSlot` 的 action 在 **LEFT** 点击时触发；若文档要求 `event.setCancelled(true)` 在 listener 内已做，则无需改。若测试仍「拿走物品」：查 MGC 是否提供 `setSlot(..., cancelMove)` 或等价 API，按 1.0.7 最小补丁（**不** fork 整个 Menu）。

- [ ] **Step 5: Commit**

```bash
git add src/main/java/io/mczju/maggoteers/menu/RestMenu.java \
  src/main/java/io/mczju/maggoteers/menu/PickMenu.java
git commit -m "fix(menu): pre-draw pick offers and fix apply callback chain"
```

---

### Task 6: 验证、dev-log、部署说明

**Files:**
- Modify: `docs/dev-log.md`（追加 2026-07-25 round2 条目）

- [ ] **Step 1: 全量测试**

Run: `mvn test`

Expected: BUILD SUCCESS

- [ ] **Step 2: dev-log**

记录：expand passengers bug；三选 1 apply+game；attribute registry；smoke 验收项。

- [ ] **Step 3: 打包部署测试服**

Run: `mvn -q package -DskipTests`

复制 `target/Maggoteers-*.jar` → `E:\MCpaper\plugins\`，重启服。

**人工 smoke 清单：**

1. `/mgc join maggoteers` → 强怪波直到 zombie horse / strider → 1 tick 后有乘客。
2. 清波 → 休整 → 普通奖励 → 点选项 → **绿字「获得」**。
3. 选 `a1w_dmg10` 或力量之环 → 伤害/粒子可见。
4. 控制台无 apply 相关 stack trace。

- [ ] **Step 4: Commit**

```bash
git add docs/dev-log.md
git commit -m "docs: dev-log smoke round2 fixes"
```

---

## Plan self-review（已完成）

| Spec 条目 | Task |
|-----------|------|
| §1 expand passengers | Task 1 |
| §2.2 PickMenu UX | Task 5 |
| §2.3 draw before pay | Task 5 |
| §2.4 apply + game | Tasks 3–4 |
| §2.5 attribute | Task 2 |
| §3 test/deploy | Task 6 |

无 TBD；`MenuFacade.open` 参数变更仅在 RestMenu→PickMenu 一条链，已写明。

---

**Plan complete and saved to `docs/superpowers/plans/2026-07-25-smoke-round2.md`.**

**两种执行方式：**

1. **Subagent-Driven（推荐）** — 按 Task 1→6 分派子 agent，每 Task 后复查  
2. **Inline Execution** — 本会话按 Task 连续实现，Task 1/4/5 完成后可 pause 给你 smoke  

你更想用哪一种？回复 **1** 或 **2**（或直接说「开始实现」默认走 **2**）。
