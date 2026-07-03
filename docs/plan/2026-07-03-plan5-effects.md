# The Maggoteers — Plan 5: 效果系统（Trigger/Effect/到期/堆叠）Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 建成 CLAUDE.md §10 的"触发→分发→效果"引擎：`Trigger`/`Effect`/`PlayerEffect` 模型 + `EffectService`（apply/resync/expire/fireTrigger + 五种 Effect 执行 + D5 唯一 `NamespacedKey`）+ 触发监听器（战斗/生命周期/玩家状态/ON_TICK_1S 心跳），`PlayerState` 成为效果真相源、复活后 `resync` 重施加。**本 Plan 只造引擎 + 接监听器**；奖励（`RewardOption` reshape + `RewardService` rewire）、净化 D1、物品 CD D2、`ON_INTERACT` 留 Plan 6（物品/菜单侧）。

**Architecture:** `effect/` 包：`Trigger`(enum)、`Effect`(enum)、`EffectKey<T>`+`EffectContext`(类型化参数)、`Stack`(enum)、`PlayerEffect`(生命周期模型)、`EffectService`(静态：apply/resync/remove/fireTrigger + 纯逻辑 `EffectStacker.merge`/`sweepExpiry`)、`EffectListener`(战斗+tick 监听)。**纯逻辑（堆叠合并、到期电荷递减、recurring 生成）与 Bukkit 应用分层**——前者全单测，后者（属性/药水施加、监听器）编译 + 手测。`PlayerState`(Plan 4) 扩 `List<PlayerEffect>`。fire-points 接入既有：`WaveScheduler.onWaveCleared/enterAct`、`DeathStrategy.onPlayerDeath`、`MaggoteersGame.cleanupRun`、新 `ON_TICK_1S` 心跳。

**Tech Stack:** Paper 1.21.7 API，Java 21，Maven，MCZJUGameCore 1.0.5，JUnit 5（纯逻辑单测）。

## Global Constraints

- **前置条件**：Plan 1–4 DoD 已满足（`e1cc5ab`+ 上层提交），`PlayerState`/`PlayerStateManager`、`WaveScheduler`(onWaveCleared/enterAct/start/stop)、`MaggoteersDeathStrategy`、`MaggoteersGame.cleanupRun` 均就位。
- **核心原则（§10）**：`PlayerState` 是效果真相源，Bukkit 属性/药水是派生视图；限时 = **事件到期**（trigger 驱动），非挂钟计时；效果**跨死亡复活存活**（存 PlayerState，复活后 resync）。
- **D5（属性多层叠加唯一 key）**：`stack: ADD` 的常驻型，每层 `AttributeModifier` 必须用唯一 `NamespacedKey`（"效果id + level + 随机/序号"），1.21 起同 key 重复添加抛异常。
- **D7（触发型被动默认 IGNORE）**：重复拿到同一条触发型被动不叠加/不刷新，除非显式声明 `ADD`/`REFRESH`。
- **D1（净化 0s 255 级抵消）/ D2（物品 CD）/ `ON_INTERACT`** → **Plan 6**（物品侧）。本 Plan 的 `Effect.ADD_POTION` 只做"施加药水"，不做"免疫抵消"。
- **JDK 21 + Maven 在 PATH**，`mvn -q test` 可跑；Bukkit 运行时（属性/监听/药水）手测。
- 铁律①②③ 继承。

---

## 本 Plan 范围与衔接

- **Plan 5 做**：`Trigger`/`Effect`/`EffectKey`/`EffectContext`/`Stack`/`PlayerEffect` 模型；`PlayerState` 扩效果列表；`EffectService`（apply/resync/remove/fireTrigger/expire/recurring + 5 Effect 执行 + D5）；`EffectListener`（战斗 + ON_TICK_1S 心跳）；接入 fire-points（ON_WAVE_CLEAR/ON_ACT_ENTER/ON_GAME_END/ON_REVIVE/ON_DEATH/ON_KILL/ON_DAMAGE_DEALT/ON_DAMAGE_TAKEN）。
- **Plan 5 不做（留 Plan 6）**：
  - `RewardOption` 加 `trigger/effect/params/stack` + `RewardService.apply` 改走 `EffectService`（**效果引擎的输入源**在 Plan 6，所以本 Plan 引擎先靠单测 + 编译验证；Plan 6 接通后才有真实效果流入）。
  - 净化 D1（`ADD_POTION` 0s-255 抵消 + damage-cancel fallback）、物品 CD D2（`cooldown_sec` 时间戳表）、`ON_INTERACT` 触发。
  - `acquiredUnique` 集（Plan 6）。

---

## Task 1: 模型 — Trigger / Effect / EffectKey / EffectContext / Stack（纯模型 + 单测）

**Files:**
- Create: `src/main/java/io/mczju/maggoteers/effect/Trigger.java`
- Create: `src/main/java/io/mczju/maggoteers/effect/Effect.java`
- Create: `src/main/java/io/mczju/maggoteers/effect/Stack.java`
- Create: `src/main/java/io/mczju/maggoteers/effect/EffectKey.java`
- Create: `src/main/java/io/mczju/maggoteers/effect/EffectContext.java`
- Create: `src/test/java/io/mczju/maggoteers/effect/EffectContextTest.java`

**Interfaces:**
- Produces:
  - `Trigger` enum：`ON_WAVE_CLEAR, ON_ACT_ENTER, ON_GAME_END, ON_KILL, ON_DAMAGE_DEALT, ON_DAMAGE_TAKEN, ON_REVIVE, ON_DEATH, ON_TICK_1S, ON_INTERACT`。
  - `Effect` enum：`ADD_ATTRIBUTE, ADD_POTION, HEAL, DAMAGE_AREA, GRANT_REVIVE`。
  - `Stack` enum：`ADD, UPGRADE_LEVEL, REFRESH, REPLACE, IGNORE`。
  - `EffectKey<T>`（类型化参数键）、`EffectContext`（`put/get/getOrDefault/has`）。

- [ ] **Step 1: 写 `Trigger` / `Effect` / `Stack`**

```java
package io.mczju.maggoteers.effect;

/** 触发目录（§10.1，v1 全部）。监听器在各 fire-point 调 {@link EffectService#fireTrigger}。 */
public enum Trigger {
    ON_WAVE_CLEAR, ON_ACT_ENTER, ON_GAME_END,     // 生命周期
    ON_KILL, ON_DAMAGE_DEALT, ON_DAMAGE_TAKEN,     // 战斗
    ON_REVIVE, ON_DEATH, ON_TICK_1S,               // 玩家状态
    ON_INTERACT                                     // 物品交互（Plan 6 接）
}
```

```java
package io.mczju.maggoteers.effect;

/** Effect 目录（§10.2，5 种）。 */
public enum Effect {
    ADD_ATTRIBUTE,   // params: attr, op(PERCENT|FLAT), value
    ADD_POTION,      // params: potion, amp, duration_ticks(0=常驻)
    HEAL,            // params: amount
    DAMAGE_AREA,     // params: radius, damage
    GRANT_REVIVE     // params: count
}
```

```java
package io.mczju.maggoteers.effect;

/** 堆叠/合并策略（§10.3）。 */
public enum Stack { ADD, UPGRADE_LEVEL, REFRESH, REPLACE, IGNORE }
```

- [ ] **Step 2: 写 `EffectKey<T>` + `EffectContext`**

```java
package io.mczju.maggoteers.effect;

/** 类型化参数键（仿 BuffKey），保证 {@link EffectContext} 取值类型安全。 */
public final class EffectKey<T> {
    private final String name;
    public EffectKey(String name) { this.name = name; }
    public String name() { return name; }
    @Override public String toString() { return name; }
}
```

```java
package io.mczju.maggoteers.effect;

import java.util.HashMap;
import java.util.Map;

/** Effect 的类型化参数容器。put/get 链式。 */
public final class EffectContext {
    private final Map<EffectKey<?>, Object> data = new HashMap<>();

    public <T> EffectContext put(EffectKey<T> key, T val) { data.put(key, val); return this; }

    @SuppressWarnings("unchecked")
    public <T> T get(EffectKey<T> key) { return (T) data.get(key); }

    @SuppressWarnings("unchecked")
    public <T> T getOrDefault(EffectKey<T> key, T def) {
        Object v = data.get(key);
        return v == null ? def : (T) v;
    }

    public boolean has(EffectKey<?> key) { return data.containsKey(key); }
}
```

- [ ] **Step 3: 写失败测试 `EffectContextTest`**

```java
package io.mczju.maggoteers.effect;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EffectContextTest {
    static final EffectKey<String> NAME = new EffectKey<>("name");
    static final EffectKey<Integer> LVL = new EffectKey<>("lvl");

    @Test
    void putAndGetIsTyped() {
        EffectContext ctx = new EffectContext().put(NAME, "lifesteal").put(LVL, 2);
        assertEquals("lifesteal", ctx.get(NAME));
        assertEquals(2, ctx.get(LVL));
    }

    @Test
    void missingKeyReturnsNullOrDefault() {
        EffectContext ctx = new EffectContext();
        assertNull(ctx.get(NAME));
        assertEquals("x", ctx.getOrDefault(NAME, "x"));
    }

    @Test
    void hasReportsPresence() {
        EffectContext ctx = new EffectContext().put(LVL, 1);
        assertTrue(ctx.has(LVL));
        assertFalse(ctx.has(NAME));
    }
}
```

- [ ] **Step 4: 构建并跑测试**

Run: `mvn -q test`
Expected: `BUILD SUCCESS`，`EffectContextTest` 通过（既有测试仍绿）。

- [ ] **Step 5: 提交**

```bash
git add src/main/java/io/mczju/maggoteers/effect/Trigger.java \
        src/main/java/io/mczju/maggoteers/effect/Effect.java \
        src/main/java/io/mczju/maggoteers/effect/Stack.java \
        src/main/java/io/mczju/maggoteers/effect/EffectKey.java \
        src/main/java/io/mczju/maggoteers/effect/EffectContext.java \
        src/test/java/io/mczju/maggoteers/effect/EffectContextTest.java
git commit -m "feat(plan5): Trigger/Effect/Stack/EffectKey/EffectContext 模型"
```

---

## Task 2: PlayerEffect + 标准参数键 + 堆叠/到期纯逻辑 + 单测

**Files:**
- Create: `src/main/java/io/mczju/maggoteers/effect/EffectKeys.java`
- Create: `src/main/java/io/mczju/maggoteers/effect/PlayerEffect.java`
- Create: `src/main/java/io/mczju/maggoteers/effect/EffectStacker.java`
- Create: `src/test/java/io/mczju/maggoteers/effect/EffectStackerTest.java`

**Interfaces:**
- Produces:
  - `EffectKeys`：标准参数键常量（`ATTR`/`OP`/`VALUE`/`POTION`/`AMP`/`DURATION_TICKS`/`AMOUNT`/`RADIUS`/`DAMAGE`/`COUNT`）。
  - `PlayerEffect`：`{id, effect, params, fireTrigger(可空), expiryTrigger(可空), expiryCharges(-1=首次即移除), recurringIntervalSec, recurringSpawn, stack, upgradeMax, level, cooldownSec}` + 构造器 + getters/setters（`expiryCharges`/`level` 可变）。
  - `EffectStacker.merge(List<PlayerEffect> existing, PlayerEffect incoming): List<PlayerEffect>` —— 按 `incoming.stack` 合并（纯函数）。
  - `EffectStacker.sweepExpiry(List<PlayerEffect> list, Trigger fired): int` —— 扫到期、扣电荷、移除，返回移除数（纯函数）。

- [ ] **Step 1: 写 `EffectKeys` 标准键**

```java
package io.mczju.maggoteers.effect;

import org.bukkit.attribute.Attribute;
import org.bukkit.potion.PotionEffectType;

/** Effect 参数的标准键（§10.2 各 Effect 的 params 契约）。 */
public final class EffectKeys {
    public static final EffectKey<Attribute> ATTR = new EffectKey<>("attr");
    public static final EffectKey<String> OP = new EffectKey<>("op");           // "PERCENT" | "FLAT"
    public static final EffectKey<Double> VALUE = new EffectKey<>("value");
    public static final EffectKey<PotionEffectType> POTION = new EffectKey<>("potion");
    public static final EffectKey<Integer> AMP = new EffectKey<>("amp");
    public static final EffectKey<Integer> DURATION_TICKS = new EffectKey<>("duration_ticks"); // 0=常驻
    public static final EffectKey<Double> AMOUNT = new EffectKey<>("amount");   // HEAL
    public static final EffectKey<Double> RADIUS = new EffectKey<>("radius");   // DAMAGE_AREA
    public static final EffectKey<Double> DAMAGE = new EffectKey<>("damage");   // DAMAGE_AREA
    public static final EffectKey<Integer> COUNT = new EffectKey<>("count");    // GRANT_REVIVE
    private EffectKeys() {}
}
```

- [ ] **Step 2: 写 `PlayerEffect`**

```java
package io.mczju.maggoteers.effect;

/**
 * 一条效果实例（§10.3）。生命周期=事件到期（trigger 驱动）。
 *
 * <ul>
 *   <li>{@code fireTrigger=null} → 常驻型（apply 时把属性/药水写进派生视图）。
 *   <li>{@code fireTrigger≠null} → 触发型（该 trigger 触发时执行 effect）。</li>
 *   <li>{@code expiryTrigger=null} → 整局有效；非空 → 该 trigger 触发时按 {@code expiryCharges} 扣除/移除。</li>
 *   <li>{@code expiryCharges=-1} → 首次 expiryTrigger 触发即移除；>0 → 倒计到 0 移除。</li>
 *   <li>{@code recurringIntervalSec>0} → 由 ON_TICK_1S 累计，每间生成 {@code recurringSpawn} 子效果。</li>
 * </ul>
 */
public final class PlayerEffect {
    private final String id;
    private final Effect effect;
    private final EffectContext params;
    private final Trigger fireTrigger;
    private final Trigger expiryTrigger;
    private int expiryCharges;
    private final int recurringIntervalSec;
    private final PlayerEffect recurringSpawn;
    private final Stack stack;
    private final int upgradeMax;
    private int level;
    private final int cooldownSec;

    public PlayerEffect(String id, Effect effect, EffectContext params, Trigger fireTrigger,
                        Trigger expiryTrigger, int expiryCharges,
                        int recurringIntervalSec, PlayerEffect recurringSpawn,
                        Stack stack, int upgradeMax, int cooldownSec) {
        this.id = id; this.effect = effect; this.params = params; this.fireTrigger = fireTrigger;
        this.expiryTrigger = expiryTrigger; this.expiryCharges = expiryCharges;
        this.recurringIntervalSec = recurringIntervalSec; this.recurringSpawn = recurringSpawn;
        this.stack = stack; this.upgradeMax = upgradeMax; this.level = 1; this.cooldownSec = cooldownSec;
    }

    // 只读
    public String id() { return id; }
    public Effect effect() { return effect; }
    public EffectContext params() { return params; }
    public Trigger fireTrigger() { return fireTrigger; }
    public Trigger expiryTrigger() { return expiryTrigger; }
    public int expiryCharges() { return expiryCharges; }
    public int recurringIntervalSec() { return recurringIntervalSec; }
    public PlayerEffect recurringSpawn() { return recurringSpawn; }
    public Stack stack() { return stack; }
    public int upgradeMax() { return upgradeMax; }
    public int cooldownSec() { return cooldownSec; }

    // 可变（堆叠/到期用）
    public int level() { return level; }
    public void setLevel(int l) { this.level = l; }
    public void setExpiryCharges(int c) { this.expiryCharges = c; }

    public boolean isPermanent() { return fireTrigger == null; }  // 常驻型
}
```

- [ ] **Step 3: 写 `EffectStacker`（纯函数：merge + sweepExpiry）**

```java
package io.mczju.maggoteers.effect;

import java.util.ArrayList;
import java.util.List;

/**
 * 效果堆叠/到期纯逻辑（与 Bukkit 解耦，全单测）。
 * <p>由 {@link EffectService} 在 apply（merge）与 fireTrigger（sweepExpiry）时调用。
 */
public final class EffectStacker {

    /**
     * 把 incoming 合并进 existing 列表（按 incoming.stack 策略）。返回新列表。
     * <ul>
     *   <li>IGNORE：同 id 已存在 → 不变。</li>
     *   <li>REPLACE：移除旧、加新。</li>
     *   <li>REFRESH：旧效果 expiryCharges 重置为 incoming 的（续期）。</li>
     *   <li>ADD：直接追加（多层叠加）。</li>
     *   <li>UPGRADE_LEVEL：旧效果 level++（封顶 upgradeMax）；满则 IGNORE。</li>
     * </ul>
     * D7：触发型被动（fireTrigger≠null）默认 stack=IGNORE 时，重复不叠加。
     */
    public static List<PlayerEffect> merge(List<PlayerEffect> existing, PlayerEffect incoming) {
        List<PlayerEffect> out = new ArrayList<>(existing);
        PlayerEffect same = out.stream().filter(e -> e.id().equals(incoming.id())).findFirst().orElse(null);
        if (same == null) { out.add(incoming); return out; }

        switch (incoming.stack()) {
            case IGNORE:    return out;                                   // 已存在，忽略
            case REPLACE:   out.remove(same); out.add(incoming); return out;
            case REFRESH:   same.setExpiryCharges(incoming.expiryCharges()); return out;
            case ADD:       out.add(incoming); return out;
            case UPGRADE_LEVEL:
                if (same.level() < incoming.upgradeMax()) same.setLevel(same.level() + 1);
                return out;                                               // 满级则 IGNORE
            default:        return out;
        }
    }

    /**
     * 扫描列表：对 expiryTrigger==fired 的效果扣电荷/移除。返回移除条数（原地修改 list）。
     */
    public static int sweepExpiry(List<PlayerEffect> list, Trigger fired) {
        int removed = 0;
        var it = list.iterator();
        while (it.hasNext()) {
            PlayerEffect e = it.next();
            if (e.expiryTrigger() == fired) {
                if (e.expiryCharges() == -1) { it.remove(); removed++; }
                else if (e.expiryCharges() > 0) {
                    e.setExpiryCharges(e.expiryCharges() - 1);
                    if (e.expiryCharges() == 0) { it.remove(); removed++; }
                }
            }
        }
        return removed;
    }

    private EffectStacker() {}
}
```

- [ ] **Step 4: 写 `EffectStackerTest`**

```java
package io.mczju.maggoteers.effect;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class EffectStackerTest {

    private static PlayerEffect perm(String id, Stack s) {
        return new PlayerEffect(id, Effect.ADD_ATTRIBUTE, new EffectContext().put(EffectKeys.OP, "FLAT"),
                null, null, 0, 0, null, s, 4, 0);
    }
    private static PlayerEffect timed(String id, int charges, Trigger expiry) {
        return new PlayerEffect(id, Effect.HEAL, new EffectContext().put(EffectKeys.AMOUNT, 2.0),
                Trigger.ON_KILL, expiry, charges, 0, null, Stack.IGNORE, 0, 0);
    }

    @Test
    void addNewAppends() {
        List<PlayerEffect> out = EffectStacker.merge(List.of(), perm("a", Stack.ADD));
        assertEquals(1, out.size());
    }

    @Test
    void ignoreKeepsExisting() {
        List<PlayerEffect> out = EffectStacker.merge(List.of(perm("a", Stack.IGNORE)), perm("a", Stack.IGNORE));
        assertEquals(1, out.size());
    }

    @Test
    void replaceSwaps() {
        PlayerEffect old = perm("a", Stack.ADD);
        List<PlayerEffect> out = EffectStacker.merge(List.of(old), perm("a", Stack.REPLACE));
        assertEquals(1, out.size());
        assertNotSame(old, out.get(0));
    }

    @Test
    void addStacksMultiple() {
        List<PlayerEffect> out = EffectStacker.merge(List.of(perm("a", Stack.ADD)), perm("a", Stack.ADD));
        assertEquals(2, out.size());
    }

    @Test
    void upgradeLevelIncrementsToMax() {
        PlayerEffect base = perm("a", Stack.UPGRADE_LEVEL);   // upgradeMax=4
        List<PlayerEffect> out = List.of(base);
        out = EffectStacker.merge(out, perm("a", Stack.UPGRADE_LEVEL));
        assertEquals(2, out.get(0).level());
        out = EffectStacker.merge(out, perm("a", Stack.UPGRADE_LEVEL));
        out = EffectStacker.merge(out, perm("a", Stack.UPGRADE_LEVEL));
        assertEquals(4, out.get(0).level());                  // 封顶
        out = EffectStacker.merge(out, perm("a", Stack.UPGRADE_LEVEL));
        assertEquals(4, out.get(0).level());                  // 满级 IGNORE
    }

    @Test
    void refreshResetsCharges() {
        PlayerEffect t = timed("ls", 3, Trigger.ON_WAVE_CLEAR);
        t.setExpiryCharges(1);
        PlayerEffect refresh = timed("ls", 3, Trigger.ON_WAVE_CLEAR);
        List<PlayerEffect> out = EffectStacker.merge(List.of(t), refresh);
        assertEquals(3, out.get(0).expiryCharges());
    }

    @Test
    void sweepRemovesOnFirstFireWhenChargesMinusOne() {
        PlayerEffect t = timed("burst", -1, Trigger.ON_DAMAGE_DEALT);
        List<PlayerEffect> list = new java.util.ArrayList<>(List.of(t));
        assertEquals(1, EffectStacker.sweepExpiry(list, Trigger.ON_DAMAGE_DEALT));
        assertTrue(list.isEmpty());
    }

    @Test
    void sweepCountsDownPositiveCharges() {
        PlayerEffect t = timed("berry", 2, Trigger.ON_KILL);
        List<PlayerEffect> list = new java.util.ArrayList<>(List.of(t));
        assertEquals(0, EffectStacker.sweepExpiry(list, Trigger.ON_KILL));  // 2→1，未移除
        assertEquals(1, list.get(0).expiryCharges());
        assertEquals(1, EffectStacker.sweepExpiry(list, Trigger.ON_KILL));  // 1→0，移除
        assertTrue(list.isEmpty());
    }

    @Test
    void sweepOnlyMatchesFiredTrigger() {
        PlayerEffect t = timed("x", -1, Trigger.ON_WAVE_CLEAR);
        List<PlayerEffect> list = new java.util.ArrayList<>(List.of(t));
        assertEquals(0, EffectStacker.sweepExpiry(list, Trigger.ON_KILL));  // 不同 trigger，不动
        assertEquals(1, list.size());
    }
}
```

- [ ] **Step 5: 构建并跑测试**

Run: `mvn -q test`
Expected: `BUILD SUCCESS`，`EffectStackerTest`（9 例）通过。

- [ ] **Step 6: 提交**

```bash
git add src/main/java/io/mczju/maggoteers/effect/EffectKeys.java \
        src/main/java/io/mczju/maggoteers/effect/PlayerEffect.java \
        src/main/java/io/mczju/maggoteers/effect/EffectStacker.java \
        src/test/java/io/mczju/maggoteers/effect/EffectStackerTest.java
git commit -m "feat(plan5): PlayerEffect + EffectStacker(merge/sweepExpiry 纯逻辑)"
```

---

## Task 3: PlayerState 扩效果列表 + EffectService（apply/resync/remove + D5 唯一 key）

**Files:**
- Modify: `src/main/java/io/mczju/maggoteers/state/PlayerState.java`（加 `effects` 列表 + 访问方法）
- Create: `src/main/java/io/mczju/maggoteers/effect/EffectService.java`

**Interfaces:**
- Consumes: `PlayerEffect`、`EffectStacker`、`EffectKeys`、`PlayerState`、`MaggoteersPlugin.getInstance()`（NamespacedKey）。
- Produces:
  - `PlayerState.effects()` / `addEffect(PlayerEffect)` / `removeEffect(id)` / `clearEffects()`。
  - `EffectService.apply(Player p, PlayerEffect e)`：merge 进 PlayerState；常驻型立即施加派生视图（属性/药水），每层 `AttributeModifier` 用唯一 `NamespacedKey`（D5）。
  - `EffectService.resync(Player p)`：复活后把所有常驻型效果重新施加（死亡清掉的药水被补回）。
  - `EffectService.removeAll(Player p)`：对局结束清理。

- [ ] **Step 1: `PlayerState` 加效果列表**

把 `PlayerState.java` 的字段区与方法区扩展（保留 Plan 4 的 reviveCount/alive 逻辑不变）。在 `private boolean alive;` 后加：

```java
    private final java.util.List<io.mczju.maggoteers.effect.PlayerEffect> effects = new java.util.ArrayList<>();
```

在 `markDown()` 后加访问方法：

```java
    public java.util.List<io.mczju.maggoteers.effect.PlayerEffect> effects() { return effects; }
    public void addEffect(io.mczju.maggoteers.effect.PlayerEffect e) { effects.add(e); }
    public void removeEffect(String id) { effects.removeIf(e -> e.id().equals(id)); }
    public void clearEffects() { effects.clear(); }
```

（无需新 import——用全限定名；或顶部 `import io.mczju.maggoteers.effect.PlayerEffect;` + `import java.util.List;` 改用短名。任选其一，保持一致。）

- [ ] **Step 2: 写 `EffectService`（apply/resync/removeAll；D5 唯一 key；fireTrigger/execute 在 Task 4 补）**

```java
package io.mczju.maggoteers.effect;

import io.mczju.maggoteers.MaggoteersPlugin;
import io.mczju.maggoteers.state.PlayerState;
import io.mczju.maggoteers.state.PlayerStateManager;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

/**
 * 效果引擎服务（§10）。PlayerState 为真相源，Bukkit 属性/药水为派生视图。
 * <p>D5：常驻 ADD_ATTRIBUTE 每层 AttributeModifier 用唯一 NamespacedKey（id + level + 序号），
 * 1.21 起同 key 重复添加会抛异常。
 */
public final class EffectService {
    private static final Logger LOG = Logger.getLogger("Maggoteers");
    private static final AtomicLong KEY_SEQ = new AtomicLong();   // 保证 key 唯一

    /** 应用一条效果：merge 进 PlayerState；常驻型立即施加派生视图。 */
    public static void apply(Player p, PlayerEffect incoming) {
        PlayerState st = PlayerStateManager.get(currentGame(p), p.getUniqueId());
        if (st == null) { LOG.warning("EffectService.apply: 无 PlayerState " + p.getName()); return; }
        // merge（堆叠/合并）
        var merged = EffectStacker.merge(st.effects(), incoming);
        st.effects().clear();
        st.effects().addAll(merged);
        // 常驻型 → 立即施加派生视图
        if (incoming.isPermanent()) {
            applyDerived(p, incoming);
        }
    }

    /** 复活后重施加：把 PlayerState 所有常驻型重新写回 Bukkit（死亡清掉的药水补回）。 */
    public static void resync(Player p) {
        PlayerState st = PlayerStateManager.get(currentGame(p), p.getUniqueId());
        if (st == null) return;
        for (PlayerEffect e : new ArrayList<>(st.effects())) {
            if (e.isPermanent()) applyDerived(p, e);
        }
    }

    /** 对局结束：清 PlayerState 效果 + 移除本插件加的属性修改器/药水（粗粒度：清插件 key 的 modifier + 清药水）。 */
    public static void removeAll(Player p) {
        PlayerState st = PlayerStateManager.get(currentGame(p), p.getUniqueId());
        if (st == null) return;
        // 移除插件命名空间下的所有 AttributeModifier
        for (Attribute attr : new Attribute[]{Attribute.GENERIC_MAX_HEALTH, Attribute.GENERIC_ATTACK_DAMAGE,
                Attribute.GENERIC_MOVEMENT_SPEED, Attribute.GENERIC_ATTACK_SPEED}) {
            AttributeInstance inst = p.getAttribute(attr);
            if (inst == null) continue;
            for (AttributeModifier m : new ArrayList<>(inst.getModifiers())) {
                if (m.getKey().namespace().equals(MaggoteersPlugin.getInstance().getName().toLowerCase())) {
                    inst.removeModifier(m);
                }
            }
        }
        st.clearEffects();
    }

    // —— 派生视图施加（常驻型）——
    private static void applyDerived(Player p, PlayerEffect e) {
        switch (e.effect()) {
            case ADD_ATTRIBUTE -> applyAttribute(p, e);
            case ADD_POTION    -> applyPotionPermanent(p, e);
            default -> { /* HEAL/DAMAGE_AREA/GRANT_REVIVE 不是常驻型，触发时执行（Task 4） */ }
        }
    }

    private static void applyAttribute(Player p, PlayerEffect e) {
        Attribute attr = e.params().get(EffectKeys.ATTR);
        if (attr == null) return;
        AttributeInstance inst = p.getAttribute(attr);
        if (inst == null) return;
        String op = e.params().getOrDefault(EffectKeys.OP, "FLAT");
        double value = e.params().getOrDefault(EffectKeys.VALUE, 0.0);
        AttributeModifier.Operation operation = "PERCENT".equalsIgnoreCase(op)
                ? AttributeModifier.Operation.MULTIPLY_SCALAR_1
                : AttributeModifier.Operation.ADD_NUMBER;
        // D5：唯一 key = id + level + 自增序号
        NamespacedKey key = new NamespacedKey(MaggoteersPlugin.getInstance(),
                e.id() + "_" + e.level() + "_" + KEY_SEQ.incrementAndGet());
        double amount = "PERCENT".equalsIgnoreCase(op) ? value : value;
        inst.addModifier(new AttributeModifier(key, amount, operation));
    }

    private static void applyPotionPermanent(Player p, PlayerEffect e) {
        PotionEffectType type = e.params().get(EffectKeys.POTION);
        if (type == null) return;
        int amp = e.params().getOrDefault(EffectKeys.AMP, 0);
        // 常驻药水：用足够长时长模拟 infinite（定期由 ON_TICK_1S 刷新，见 Task 5）；这里先施加长时长
        p.addPotionEffect(new PotionEffect(type, 20 * 30, amp, false, false, true));
    }

    /** 由 Task 5 监听器持有当前对局引用；这里临时用玩家所在游戏反查。 */
    private static io.mczju.maggoteers.game.MaggoteersGame currentGame(Player p) {
        var pe = new com.github.mczjuops.mczjugamecore.player.PlayerExt(p);
        return pe.isInGame() ? (io.mczju.maggoteers.game.MaggoteersGame) pe.getGame() : null;
    }

    private EffectService() {}
}
```

> `applyDerived` 的 `HEAL/DAMAGE_AREA/GRANT_REVIVE` 分支在常驻型下不做事——它们本就是触发型（`fireTrigger≠null`），由 Task 4 的 `fireTrigger` 执行。`applyAttribute` 的 `MULTIPLY_SCALAR_1` 对应 PERCENT（×(1+value)）；`ADD_NUMBER` 对应 FLAT。1.21 的 `Attribute` 枚举名是 `GENERIC_MAX_HEALTH` 等（带 `GENERIC_` 前缀）；若编译报 `MAX_HEALTH`，改全限定 `org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH`。**先按 `GENERIC_*` 编译；若 Paper 1.21.7 已去前缀，改无前缀。** Task 5 监听器会持有 `AbstractGame`，`currentGame` 在 Plan 6 接 RewardService 后可改成显式传 game 参数（本 Plan 暂用 `PlayerExt` 反查）。

- [ ] **Step 3: 构建确认编译通过**

Run: `mvn -q -DskipTests compile`
Expected: `BUILD SUCCESS`。若 `Attribute.GENERIC_*` 报错 → 改无前缀 `Attribute.MAX_HEALTH` 等（Paper 1.21.7 实际枚举名以编译器为准）。

> 本 Task 无新单测（apply/resync 操作 Bukkit Player，无法 headless 测；堆叠纯逻辑已在 Task 2 覆盖）。`PlayerStateTest` 仍应绿。

- [ ] **Step 4: 提交**

```bash
git add src/main/java/io/mczju/maggoteers/state/PlayerState.java \
        src/main/java/io/mczju/maggoteers/effect/EffectService.java
git commit -m "feat(plan5): PlayerState 扩效果列表 + EffectService apply/resync(D5 唯一key)"
```

---

## Task 4: EffectService.fireTrigger（到期扫描 + 五种 Effect 执行）

**Files:**
- Modify: `src/main/java/io/mczju/maggoteers/effect/EffectService.java`（加 `fireTrigger` + `executeEffect` + recurring）

**Interfaces:**
- Consumes: `AbstractGame`、`Player`、`Trigger`、`EffectStacker.sweepExpiry`。
- Produces:
  - `EffectService.fireTrigger(AbstractGame game, Trigger fired, Consumer<Player> eachPlayer)`：对该局每个冒险模式玩家，扫到期（`sweepExpiry`）、执行 fireTrigger==fired 的触发型效果、累计 recurring（由 ON_TICK_1S 调用，带秒数）。
  - `EffectService.fireTrigger(AbstractGame, Trigger)`：便捷重载（无 recurring 计时）。
  - `executeEffect(Player, PlayerEffect)`：执行 HEAL/ADD_POTION(限时)/DAMAGE_AREA/GRANT_REVIVE/ADD_ATTRIBUTE(触发型一次性)。

- [ ] **Step 1: 在 `EffectService` 加 `fireTrigger` + `executeEffect`**

在 `EffectService` 类内（`removeAll` 之后、`applyDerived` 之前）加：

```java
    /**
     * 对局内某 trigger 触发：对每个冒险模式玩家扫到期 + 执行 fireTrigger==fired 的触发型效果。
     * @param game        当前对局
     * @param fired       触发的 trigger
     * @param tickSeconds 仅 ON_TICK_1S 传>0（用于 recurring 累计）；其余传 0
     */
    public static void fireTrigger(io.mczju.maggoteers.game.MaggoteersGame game, Trigger fired, int tickSeconds) {
        if (game == null) return;
        for (var pe : game.getPlayers()) {
            org.bukkit.entity.Player p = pe.player();
            var ps = io.mczju.maggoteers.state.PlayerStateManager.get(game, p.getUniqueId());
            if (ps == null || !ps.isAlive()) continue;
            // 1) 到期扫描
            EffectStacker.sweepExpiry(ps.effects(), fired);
            // 2) 执行触发型（fireTrigger==fired）
            for (PlayerEffect e : new java.util.ArrayList<>(ps.effects())) {
                if (e.fireTrigger() == fired) executeEffect(p, e, game);
                // 3) recurring（仅 ON_TICK_1S 累计秒）
                if (fired == Trigger.ON_TICK_1S && tickSeconds > 0 && e.recurringIntervalSec() > 0
                        && e.recurringSpawn() != null
                        && tickSeconds % e.recurringIntervalSec() == 0) {
                    apply(p, e.recurringSpawn());
                }
            }
        }
    }

    /** 便捷重载（无 recurring 计时）。 */
    public static void fireTrigger(io.mczju.maggoteers.game.MaggoteersGame game, Trigger fired) {
        fireTrigger(game, fired, 0);
    }

    /** 执行一条效果（触发型在 fireTrigger 时调；常驻型在 apply 时已施加）。 */
    private static void executeEffect(Player p, PlayerEffect e,
                                      io.mczju.maggoteers.game.MaggoteersGame game) {
        switch (e.effect()) {
            case HEAL -> {
                double amount = e.params().getOrDefault(EffectKeys.AMOUNT, 0.0);
                var hp = p.getAttribute(Attribute.GENERIC_MAX_HEALTH);
                double max = hp != null ? hp.getValue() : 20.0;
                p.setHealth(Math.min(max, p.getHealth() + amount));
            }
            case ADD_POTION -> {   // 限时药水（duration_ticks>0）；常驻药水在 applyDerived 施加
                PotionEffectType type = e.params().get(EffectKeys.POTION);
                if (type == null) return;
                int amp = e.params().getOrDefault(EffectKeys.AMP, 0);
                int dur = e.params().getOrDefault(EffectKeys.DURATION_TICKS, 0);
                if (dur > 0) p.addPotionEffect(new PotionEffect(type, dur, amp, false, true));
            }
            case DAMAGE_AREA -> {
                double radius = e.params().getOrDefault(EffectKeys.RADIUS, 3.0);
                double dmg = e.params().getOrDefault(EffectKeys.DAMAGE, 0.0);
                org.bukkit.World w = p.getWorld();
                for (org.bukkit.entity.Entity en : w.getNearbyEntities(p.getLocation(), radius, radius, radius)) {
                    if (en instanceof org.bukkit.entity.LivingEntity le && en != p) {
                        le.damage(dmg, p);
                    }
                }
            }
            case GRANT_REVIVE -> {
                int count = e.params().getOrDefault(EffectKeys.COUNT, 1);
                io.mczju.maggoteers.state.PlayerStateManager.addReviveCount(game, p.getUniqueId(), count);
            }
            case ADD_ATTRIBUTE -> {
                // 触发型一次性 ADD_ATTRIBUTE（少见；按常驻同样施加，靠 expiry 控制去留）
                applyAttribute(p, e);
            }
        }
    }
```

补 import（EffectService 顶部）：
```java
import io.mczju.maggoteers.effect.Trigger;   // 同包，可省
```
（`Trigger`/`PlayerEffect`/`EffectStacker` 与 EffectService 同包，无需 import。）

- [ ] **Step 2: 构建确认编译通过**

Run: `mvn -q -DskipTests compile`
Expected: `BUILD SUCCESS`（`Attribute.GENERIC_*` 同 Task 3 Step 3 的命名处理）。

- [ ] **Step 3: 提交**

```bash
git add src/main/java/io/mczju/maggoteers/effect/EffectService.java
git commit -m "feat(plan5): EffectService.fireTrigger 到期扫描+5种Effect执行+recurring"
```

---

## Task 5: 触发监听器（战斗 + ON_TICK_1S 心跳）+ 接入生命周期 fire-points

**Files:**
- Create: `src/main/java/io/mczju/maggoteers/effect/EffectListener.java`
- Modify: `src/main/java/io/mczju/maggoteers/wave/WaveScheduler.java`（`onWaveCleared` 加 `fireTrigger(ON_WAVE_CLEAR)`；`enterAct` 末尾加 `fireTrigger(ON_ACT_ENTER)`）
- Modify: `src/main/java/io/mczju/maggoteers/game/MaggoteersDeathStrategy.java`（复活分支加 `fireTrigger(ON_REVIVE)` + `resync`；观察者分支加 `fireTrigger(ON_DEATH)`）
- Modify: `src/main/java/io/mczju/maggoteers/game/MaggoteersGame.java`（`cleanupRun` 加 `fireTrigger(ON_GAME_END)` + 心跳启动/停止）
- Modify: `src/main/java/io/mczju/maggoteers/MaggoteersPlugin.java`（注册 `EffectListener`）

**Interfaces:**
- Consumes: `EffectService.fireTrigger`、`PlayerExt`（判断 isInGame + 取 game）。
- Produces: 战斗 trigger（ON_KILL/ON_DAMAGE_DEALT/ON_DAMAGE_TAKEN）+ ON_TICK_1S 心跳（每秒 fire ON_TICK_1S）；生命周期 fire-points 接入。

- [ ] **Step 1: 写 `EffectListener`（战斗监听 + ON_TICK_1S 心跳任务）**

```java
package io.mczju.maggoteers.effect;

import com.github.mczjuops.mczjugamecore.game.AbstractGame;
import com.github.mczjuops.mczjugamecore.player.PlayerExt;
import io.mczju.maggoteers.MaggoteersPlugin;
import io.mczju.maggoteers.game.MaggoteersGame;
import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.scheduler.BukkitTask;

/** 战斗 trigger 监听 + ON_TICK_1S 心跳。lifecycle/player trigger 在各 fire-point 直接调 EffectService。 */
public final class EffectListener implements Listener {

    private static BukkitTask tickTask;

    /** 启动 ON_TICK_1S 心跳（每秒对每个运行中的 MaggoteersGame fire ON_TICK_1S）。 */
    public static void startTick() {
        stopTick();
        tickTask = Bukkit.getScheduler().runTaskTimer(MaggoteersPlugin.getInstance(), () -> {
            for (var game : com.github.mczjuops.mczjugamecore.MCZJUGameCore.getGameManager().getRunningGames()) {
                if (game instanceof MaggoteersGame mg) {
                    EffectService.fireTrigger(mg, Trigger.ON_TICK_1S, 1);
                }
            }
        }, 20L, 20L);   // 1s
    }

    public static void stopTick() {
        if (tickTask != null) { tickTask.cancel(); tickTask = null; }
    }

    /** 玩家击杀实体 → ON_KILL（玩家方）。 */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onKill(EntityDeathEvent e) {
        Player killer = e.getEntity().getKiller();
        if (killer == null) return;
        MaggoteersGame g = gameOf(killer);
        if (g != null) EffectService.fireTrigger(g, Trigger.ON_KILL);
    }

    /** 玩家造成伤害 → ON_DAMAGE_DEALT。 */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamageDealt(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player p)) return;
        MaggoteersGame g = gameOf(p);
        if (g != null) EffectService.fireTrigger(g, Trigger.ON_DAMAGE_DEALT);
    }

    /** 玩家受击 → ON_DAMAGE_TAKEN。 */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamageTaken(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        MaggoteersGame g = gameOf(p);
        if (g != null) EffectService.fireTrigger(g, Trigger.ON_DAMAGE_TAKEN);
    }

    private static MaggoteersGame gameOf(Player p) {
        PlayerExt pe = new PlayerExt(p);
        if (!pe.isInGame()) return null;
        AbstractGame g = pe.getGame();
        return g instanceof MaggoteersGame mg ? mg : null;
    }
}
```

> `getGameManager().getRunningGames()` 的确切方法名以 MGC 1.0.5 为准；若不存在，改为遍历 `Bukkit.getOnlinePlayers()` 里 `PlayerExt.isInGame(MaggoteersGame.class)` 的玩家所属 game 去重（fallback 写法见 Step 5 注）。**先按 `getRunningGames()` 编译；若报错用 fallback。**

- [ ] **Step 2: `WaveScheduler` 接 ON_WAVE_CLEAR + ON_ACT_ENTER**

在 `onWaveCleared(AbstractGame game, Cursor c)` 方法体末尾（`grantClearRewards` 与广播之后）加：

```java
        io.mczju.maggoteers.effect.EffectService.fireTrigger((MaggoteersGame) game, io.mczju.maggoteers.effect.Trigger.ON_WAVE_CLEAR);
```

在 `enterAct(AbstractGame game, Cursor c, int actIndex)` 方法体末尾（`beginWave(game, c, 0)` 之前）加：

```java
        io.mczju.maggoteers.effect.EffectService.fireTrigger((MaggoteersGame) game, io.mczju.maggoteers.effect.Trigger.ON_ACT_ENTER);
```

- [ ] **Step 3: `MaggoteersDeathStrategy` 接 ON_REVIVE（+ resync）+ ON_DEATH**

在 `onPlayerDeath` 的 `if (st.tryAutoRevive())` 分支（`healAndInvuln(p)` 之后）加：

```java
            io.mczju.maggoteers.effect.EffectService.fireTrigger((io.mczju.maggoteers.game.MaggoteersGame) game, io.mczju.maggoteers.effect.Trigger.ON_REVIVE);
            io.mczju.maggoteers.effect.EffectService.resync(p);   // 复活后补回死亡清掉的常驻药水/属性
```

在观察者分支（`p.setGameMode(GameMode.SPECTATOR)` 之后）加：

```java
            io.mczju.maggoteers.effect.EffectService.fireTrigger((io.mczju.maggoteers.game.MaggoteersGame) game, io.mczju.maggoteers.effect.Trigger.ON_DEATH);
```

- [ ] **Step 4: `MaggoteersGame.cleanupRun` 接 ON_GAME_END + 启/停心跳**

在 `cleanupRun()` 方法体**开头**加：

```java
        io.mczju.maggoteers.effect.EffectService.fireTrigger(this, io.mczju.maggoteers.effect.Trigger.ON_GAME_END);
        // 清每位玩家的效果派生视图（属性 modifier / 药水）
        for (var pe : getPlayers()) io.mczju.maggoteers.effect.EffectService.removeAll(pe.player());
        io.mczju.maggoteers.effect.EffectListener.stopTick();
```

并在 `onGameStart`（实际开波的方法 `beginClassSelect` 或 `startWaves` 末尾——以"波次即将开始"处为准）加启动心跳：
```java
        io.mczju.maggoteers.effect.EffectListener.startTick();
```
> 若不确定注入点：放在 `onAllClassesChosen()`（全员选完职业、`WaveScheduler.start` 之前）最稳——此时玩家已进图、波次将开。

- [ ] **Step 5: `MaggoteersPlugin` 注册 `EffectListener` + 启动心跳**

在 `onEnable` 的 `registerEvents(new ItemInteractRouter(), this);` 之后加：

```java
        getServer().getPluginManager().registerEvents(new io.mczju.maggoteers.effect.EffectListener(), this);
```

在 `onDisable()` 加（防重开服残留）：
```java
        io.mczju.maggoteers.effect.EffectListener.stopTick();
```

> `getRunningGames()` fallback（若 Step 1 编译报错）：把 `startTick` 的循环体换成
> ```java
>     java.util.IdentityHashMap<io.mczju.maggoteers.game.MaggoteersGame,Boolean> seen = new java.util.IdentityHashMap<>();
>     for (org.bukkit.entity.Player p : Bukkit.getOnlinePlayers()) {
>         var pe = new com.github.mczjuops.mczjugamecore.player.PlayerExt(p);
>         if (pe.isInGame() && pe.getGame() instanceof io.mczju.maggoteers.game.MaggoteersGame mg)
>             seen.put(mg, Boolean.TRUE);
>     }
>     seen.keySet().forEach(mg -> EffectService.fireTrigger(mg, Trigger.ON_TICK_1S, 1));
> ```

- [ ] **Step 6: 构建确认编译通过**

Run: `mvn -q -DskipTests compile`
Expected: `BUILD SUCCESS`（`getRunningGames` 或 fallback 二选一编译过；`Attribute.GENERIC_*` 命名按 Task 3 处理）。

- [ ] **Step 7: 提交**

```bash
git add src/main/java/io/mczju/maggoteers/effect/EffectListener.java \
        src/main/java/io/mczju/maggoteers/wave/WaveScheduler.java \
        src/main/java/io/mczju/maggoteers/game/MaggoteersDeathStrategy.java \
        src/main/java/io/mczju/maggoteers/game/MaggoteersGame.java \
        src/main/java/io/mczju/maggoteers/MaggoteersPlugin.java
git commit -m "feat(plan5): 战斗/ON_TICK_1S 监听 + 接入 lifecycle fire-points"
```

---

## Task 6: 集成自检 + 手测清单

**Files:** 无新代码（构建 + 文档）。

- [ ] **Step 1: 全量构建 + 单测**

Run: `mvn -q test`
Expected: `BUILD SUCCESS`，所有测试绿（新增 `EffectContextTest`/`EffectStackerTest`；既有 9 个测试文件仍绿）。

- [ ] **Step 2: 打包**

Run: `mvn -q -DskipTests package`
Expected: 产 `target/Maggoteers-0.1.0-SNAPSHOT.jar`。

- [ ] **Step 3: dev-log 记录**

在 `docs/dev-log.md` 顶部追加一条（日期 + 做了什么 + 决策 + 遗留：D1/D2/ON_INTERACT 留 Plan 6；引擎输入源（RewardOption reshape）留 Plan 6）。

- [ ] **Step 4: 手测清单（部署后在真服执行；本 Plan 引擎无输入，需 Plan 6 接通奖励才能见效果——此处先验证"不崩 + 监听器就位"）**

部署 jar 到测试服（同目录有 MGC + ItemCreator）。Expected：
1. 启动无异常；`/mgc join maggoteers` 进局正常（引擎不干扰核心闭环）。
2. 控制台无 `EffectService` / `EffectListener` 相关异常。
3. 打怪/受击/清波/复活/跨层时，`fireTrigger` 被调（无 NPE；`getPlayers()` 遍历正常）。
4. 通关/失败 → `cleanupRun` 的 `ON_GAME_END` + `removeAll` 不抛异常，玩家离开后属性/药水已清（用 `/effect` 查玩家无残留 `maggoteers` 命名空间的 modifier）。

> 真正"看到效果"（嗜血回血、+10% 伤害、抗性升级等）需 **Plan 6** 把 `RewardOption` 接到 `EffectService.apply`。本 Plan 只保证引擎 + 监听器就位、不崩。

- [ ] **Step 5: 提交**

```bash
git add docs/dev-log.md
git commit -m "docs(plan5): 效果系统引擎完成记录 + 手测清单"
```

---

## Plan 5 验收标准（Definition of Done）

- [ ] `mvn package` 产 jar，`mvn test` 绿（新增 `EffectContextTest`/`EffectStackerTest`，纯逻辑覆盖堆叠/到期/merge）。
- [ ] `effect/` 包齐全：`Trigger/Effect/Stack/EffectKey/EffectContext/EffectKeys/PlayerEffect/EffectStacker/EffectService/EffectListener`。
- [ ] `PlayerState` 持 `List<PlayerEffect>`；`EffectService.apply/resync/removeAll/fireTrigger` 就位；D5 唯一 `NamespacedKey`。
- [ ] fire-points 全接：`WaveScheduler`(ON_WAVE_CLEAR/ON_ACT_ENTER)、`DeathStrategy`(ON_REVIVE+resync/ON_DEATH)、`MaggoteersGame.cleanupRun`(ON_GAME_END+removeAll+stopTick)、`EffectListener`(ON_KILL/ON_DAMAGE_DEALT/ON_DAMAGE_TAKEN/ON_TICK_1S)。
- [ ] 部署后核心闭环不受影响（进局/打怪/通关/失败不崩），监听器无异常。
- [ ] `docs/dev-log.md` 追加 Plan 5 完成条目。

> **已知遗留（Plan 6 处理）**：
> - `RewardOption` reshape（加 trigger/effect/params/stack/icon/requires_unlock/unlock_cost/unique）+ `RewardService.apply` 改走 `EffectService` —— **效果引擎的真实输入**。
> - D1 净化（`ADD_POTION` 0s-255 抵消 + damage-cancel fallback）。
> - D2 物品 CD（`cooldown_sec` 按 PDC-id 时间戳表）+ `ON_INTERACT` 触发（`ItemInteractRouter` 接入）。
> - 右键空气打不开菜单（`ItemInteractRouter` 的 `PlayerInteractEvent` 缺陷）。
> - `currentGame(p)` 反查可改为显式传 game 参数。
