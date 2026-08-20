# Maggoteers 1.1 — 计数器（含计时器）+ 怪物技能系统 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为玩家被动/魔法武器增加**计数器** trigger（`ON_COUNTER`），并彻底移除 InfernalMobs（IM）依赖、退役原生 affixes 词条系统，构建与玩家被动**对称**的可配置怪物技能系统（trigger + effect），同时让追踪投射物同时支持玩家与怪物侧（带目标选择）。

**Architecture:** 玩家侧复用现有 `EffectService`（PlayerState 为真相源）——新增一个 trigger（`ON_COUNTER` 计满），计数器作为"effect 输入 + trigger 中继"：已有 trigger（每秒 tick/攻击/受击/击杀/回合结束）驱动计数，计满只输出 `ON_COUNTER`；计满信号可再串联驱动下游计数器（防自环）。**计时器与计数器是同一机制**：计时器就是 `count: tick` 的计数器——每 20 game tick（1 秒）计一次，`amount: N` 即 N 秒计满触发，计满清零重新计（秒表语义）。怪物侧新建与玩家被动对称的 `mob.skill` 子系统：`MobSkillRegistry`（mob_skills.yml）→ `MobSkillService`（attach/detach/tick/事件）+ `MobEffectExecutor`（HP/属性/药水/召唤/传送）。waves 模型删 `affixes`/`infernal`/`potions` 三字段、加 `skills`；`AffixService`/`InfernalCfg`/`InfernalMobsBridge`/`affixes.yml` 整体删除；affixes.yml 的 12 个词缀迁移成 mob_skills.yml 里同名 SPAWN 技能。追踪投射物统一走共享 `ProjectileHomingService`，玩家 `SUMMON` 与怪物 `SUMMON` 共用。

**Tech Stack:** Paper 26.2 API、Maven、JDK 25（`/home/chalcanthite/.local/jdk-25`）、JUnit 5（纯单测，**无** MockBukkit/Mockito）。

## Global Constraints

1. **trigger 扩展是「加一」**：`Trigger` 只新增 `ON_COUNTER` 一个值。计数器计满**只输出 `ON_COUNTER`**（携带计满计数器的来源 id）。**不**存在"计满广播已有 trigger"的配置，也**没有**独立的 `ON_TIMER` trigger。
2. **计时器 = 计数器**：`count: tick` 的计数器就是计时器——每 20 game tick（1 秒，`EffectListener` 的 `ON_TICK_1S` 心跳）计一次，`amount: N` 表示 N 秒触发一次；计满清零继续计（秒表语义）。玩家被动"计时器"写法：`trigger: ON_COUNTER` + `params.counter: {id: t1, count: tick, amount: N}`。
3. **计数器 = 中继器**：计数输入（`CounterCountTrigger`）= `TICK`/`ATTACK`/`DAMAGE_TAKEN`/`KILL`/`WAVE_CLEAR` + **`ON_COUNTER`**（串联输入）。计满触发绑定该计数器的被动（`fireTrigger: ON_COUNTER` + `params.counter.id` 匹配），并驱动 `countTrigger: on_counter` 的其它计数器（**跳过自身，防自环**）。
4. **追踪投射物双端 + 目标选择**：homing 不只给怪物。玩家 `SUMMON` 与怪物 `SUMMON` 共用共享 `ProjectileHomingService`；目标选择由 `homing_target` 配置——玩家侧 `nearest_enemy`（最近本局怪）；怪物侧 `nearest_player` / `attack_target` / `damage_source` / `killer`。
5. **affixes 整体退役（修正 3）**：删除 `Affix`/`AffixService`/`affixes.yml`/`InfernalCfg`/`InfernalMobsBridge` 及其测试。waves 模型（SpawnStep/PassengerSpawn/DeathSpawn/StepCfg/PassengerCfg/DeathSpawnCfg）删除 `affixes`、`infernal`、`potions` 三个字段，新增 `skills`（技能 id 列表）。数值只由 `coeff × scaling` 决定（Compose 不再乘 affix）。waves.yml 的 `affixes:`/`infernal:` 块迁移为 `skills:`；`MobYamlParser.parseSkills` 兼容旧文件 fallback：`skills` → `affixes` → `infernal.affixes`。
6. **怪物技能对称**：`MobTrigger` = `ATTACK`（带 target）/`DAMAGE_TAKEN`（带 source）/`KILLED`（带 killer）/`SPAWN`/`TICK`（光环类）/`COUNTER`。`MobEffect` = `HEALTH`/`ATTRIBUTE`/`POTION`/`SUMMON`/`TELEPORT`，各自支持 self / target / area。带计数器的怪物**出生时**加载计数器、**死亡时**卸载；条件修饰符（HOLD_ITEM_PDC / PLAYER_IN_RADIUS / AND / OR / NOT）**以怪物为中心**判断。
7. **怪物不再有 `potions` 字段**：药水效果（含 affixes.yml 迁移出来的 spawn 药水）一律由 mob_skills.yml 技能在 `SPAWN` 时施加。waves.yml 目前无 `steps[].potions`，故删字段零配置损失。
8. **隐身词缀**：`invisible` 用 `MobSkillSpec.invisible: true`（→ `LivingEntity.setInvisible(true)` + 玻璃瓶头盔），**不用** INVISIBILITY 药水（苦力怕爆炸会把药水打成滞留云）。`MobSkillSpecParser` 允许 `effects` 为空（纯装饰技能如 `infected`），仅要求：`trigger: counter` 必须有 `counter:` 块、`trigger: tick` 的 `cooldown_sec > 0`。
9. **虚拟属性忽略**：`MobEffectSpec` 无独立 attr 组件——属性名走 `params.attr`（`EffectKeys.ATTR_NAME`）。虚拟属性名（如 MAGIC_DAMAGE）经 `GameRegistries.attribute` 解析为 null 时，`MobTempAttributeService` 直接跳过（不写 Bukkit）。
10. **构建命令**：`export JAVA_HOME=/home/chalcanthite/.local/jdk-25 && export PATH="$JAVA_HOME/bin:$PATH" && mvn -o -q test`。基线：**329 tests / 0 failures / 5 skipped**（2026-08-20）。
11. **中文交流**：本计划所有任务说明、代码注释遵循项目语言习惯（中文注释、英文标识符）。
12. **不改玩家被动白名单语义**：`RewardLoadValidator.ALLOWED_FIRE` 只**增加** `ON_COUNTER`，现有 trigger 校验不动。
13. **铁律**：定义唯一处——怪物技能只在 `mob_skills.yml`、波次策略只在 `waves.yml`；绝对坐标永不入配置。

## File Structure

**新增（玩家侧计数器）**
- `src/main/java/io/mczju/maggoteers/effect/CounterCountTrigger.java`
- `src/main/java/io/mczju/maggoteers/effect/CounterCondition.java`（sealed：`And/Or/Not/HoldItemPdc/PlayerInRadius`）
- `src/main/java/io/mczju/maggoteers/effect/CounterSpec.java`
- `src/main/java/io/mczju/maggoteers/effect/CounterState.java`
- `src/main/java/io/mczju/maggoteers/effect/CounterSpecParser.java`
- `src/main/java/io/mczju/maggoteers/effect/CounterService.java`
- `src/main/java/io/mczju/maggoteers/effect/ProjectileHomingService.java`

**新增（怪物技能）**
- `src/main/java/io/mczju/maggoteers/mob/MobTrigger.java`
- `src/main/java/io/mczju/maggoteers/mob/MobEffect.java`
- `src/main/java/io/mczju/maggoteers/mob/MobEffectSpec.java`
- `src/main/java/io/mczju/maggoteers/mob/MobSkillSpec.java`
- `src/main/java/io/mczju/maggoteers/mob/MobSkillSpecParser.java`
- `src/main/java/io/mczju/maggoteers/mob/MobSkillRegistry.java`
- `src/main/java/io/mczju/maggoteers/mob/MobCondition.java`（怪物为中心的条件：sealed `And/Or/Not/HoldItemPdc/PlayerInRadius`）
- `src/main/java/io/mczju/maggoteers/mob/MobSkillService.java`
- `src/main/java/io/mczju/maggoteers/mob/MobEffectExecutor.java`
- `src/main/java/io/mczju/maggoteers/mob/MobTempAttributeService.java`
- `src/main/java/io/mczju/maggoteers/mob/MobProjectileService.java`
- `src/main/java/io/mczju/maggoteers/mob/MobCounterRegistry.java`（怪物计数器加载/卸载/推进）
- `src/main/java/io/mczju/maggoteers/mob/TeleportSafety.java`
- `src/main/java/io/mczju/maggoteers/listener/MobSkillListener.java`
- `src/main/resources/mob_skills.yml`

**修改（玩家侧）**
- `effect/Trigger.java`（+ON_COUNTER）
- `effect/TriggerContext.java`（+counterId）
- `effect/EffectKeys.java`（+COUNTER_ID/COUNTER_SPEC；HOMING_TARGET 在 Task 4 加）
- `effect/EffectService.java`（apply 注册计数器 + fireCounter + dispatch 过滤）
- `effect/EffectListener.java`（TICK 计数接线：startTick 心跳）
- `effect/SummonParams.java` / `effect/SummonParamsParser.java` / `effect/SummonExecutor.java`（homing）
- `reward/RewardOption.java`（counter 解析）
- `reward/RewardLoadValidator.java`（ALLOWED_FIRE += ON_COUNTER；counter 校验）
- `state/PlayerState.java`（+counters()）
- `item/WeaponEffectValidator.java`（武器 trigger=ON_COUNTER 校验）

**修改（怪物侧 + 模型手术）**
- `wave/SpawnStep.java` `wave/PassengerSpawn.java` `wave/DeathSpawn.java`（删 affixes/infernal/potions，加 skills）
- `config/StepCfg.java` `config/PassengerCfg.java` `config/DeathSpawnCfg.java`（同上）
- `config/MobYamlParser.java`（parseSkills fallback）
- `config/WavesConfig.java`（parseStrategy 用 parseSkills）
- `mob/MobFactory.java`（删 affix 应用/infernal，configureSpawnedLiving 删参数）
- `plan/Compose.java`（删 affix 合成）
- `plan/RunPlanner.java`（删 AffixService 参数，透传 skills）
- `wave/WaveEngine.java`（删 InfernalMobsBridge，加 gameOfEntity/nearestTrackedMob + attach/detach 钩子）
- `listener/MobDeathListener.java`（删 IM 分支，加 detach）
- `command/MaggoteersCommand.java`（debug spawn 改 skills）
- `MaggoteersPlugin.java`（删 AffixService/IM 注册，加 MobSkillRegistry）

**删除**
- `config/Affix.java` `config/AffixService.java` `config/InfernalCfg.java` `integration/InfernalMobsBridge.java` `src/main/resources/affixes.yml`
- `test/config/InfernalCfgTest.java` `test/integration/InfernalMobsBridgeTest.java`

**测试**
- 新增：`test/effect/CounterSpecParserTest.java` `test/effect/CounterStateTest.java` `test/effect/CounterConditionTest.java` `test/effect/CounterServiceTest.java` `test/mob/MobSkillSpecParserTest.java` `test/mob/TeleportSafetyTest.java`
- 修改：`test/effect/TriggerContextTest.java` `test/effect/SummonExecutorTest.java` `test/effect/SummonParamsParserTest.java` `test/wave/WaveSpecTest.java` `test/mob/MountControllerResolverTest.java` `test/plan/RunPlannerTest.java` `test/plan/ComposeTest.java` `test/plan/StrongWaveRollTest.java` `test/config/MobYamlParserTest.java`

---

### Task 1: Trigger 扩展（ON_COUNTER）+ 上下文/键扩展

**Files:**
- Modify: `src/main/java/io/mczju/maggoteers/effect/Trigger.java`
- Modify: `src/main/java/io/mczju/maggoteers/effect/TriggerContext.java`
- Modify: `src/main/java/io/mczju/maggoteers/effect/EffectKeys.java`
- Test: `src/test/java/io/mczju/maggoteers/effect/TriggerContextTest.java`

**Interfaces:**
- Consumes: 现有 `Trigger` 8 值、`TriggerContext` 4 字段 record（`(Trigger fired, LivingEntity hitTarget, LivingEntity attacker, Location eventLocation)`）、`EffectKeys` 现有键。
- Produces: `Trigger.ON_COUNTER`；`TriggerContext.withCounter(String)` / `counterId()`；`EffectKeys.COUNTER_ID`（`EffectKey<String>`）。

- [ ] **Step 1: 写失败测试**

修改 `src/test/java/io/mczju/maggoteers/effect/TriggerContextTest.java`，追加用例：

```java
@Test
void counterIdSurvivesWithCounter() {
    TriggerContext base = TriggerContext.empty().withFired(Trigger.ON_COUNTER);
    TriggerContext c = base.withCounter("fire_every_5_attacks");
    assertEquals(Trigger.ON_COUNTER, c.fired());
    assertEquals("fire_every_5_attacks", c.counterId());
    // withHitTarget 保留 counterId（串联链路里战斗字段不丢来源）
    assertEquals("fire_every_5_attacks",
            c.withHitTarget(null).counterId());
}
```

- [ ] **Step 2: 运行确认失败**

Run: `export JAVA_HOME=/home/chalcanthite/.local/jdk-25 && export PATH="$JAVA_HOME/bin:$PATH" && mvn -o -q test -Dtest=TriggerContextTest`
Expected: FAIL（`Trigger` 无 `ON_COUNTER`、`TriggerContext` 无 `counterId()`）

- [ ] **Step 3: 实现**

`Trigger.java`：

```java
public enum Trigger {
    ON_WAVE_CLEAR, ON_ACT_ENTER,                 // 生命周期
    ON_KILL, ON_DAMAGE_DEALT, ON_DAMAGE_TAKEN,     // 战斗
    ON_REVIVE, ON_DEATH, ON_TICK_1S,               // 玩家状态
    ON_COUNTER                                     // 计数器计满（唯一新增；计时器=count:tick 计数器）
}
```

`TriggerContext.java`（在原 record 上加第 5 字段，保持既有 4 个方法签名不变）：

```java
public record TriggerContext(
        Trigger fired,
        LivingEntity hitTarget,
        LivingEntity attacker,
        Location eventLocation,
        String counterId
) {
    public TriggerContext(Trigger fired, LivingEntity hitTarget, LivingEntity attacker) {
        this(fired, hitTarget, attacker, null, null);
    }

    public static TriggerContext empty() {
        return new TriggerContext(null, null, null, null, null);
    }

    /** Event anchor (e.g. death site) with optional trigger tag. */
    public static TriggerContext atEvent(Trigger fired, Location location) {
        Location loc = location == null ? null : location.clone();
        return new TriggerContext(fired, null, null, loc, null);
    }

    public static Location resolveOrigin(Player player, TriggerContext ctx) {
        if (ctx != null && ctx.eventLocation() != null && ctx.eventLocation().getWorld() != null) {
            return ctx.eventLocation();
        }
        return player != null ? player.getLocation() : null;
    }

    public TriggerContext withHitTarget(LivingEntity hitTarget) {
        return new TriggerContext(fired, hitTarget, attacker, eventLocation, counterId);
    }

    public TriggerContext withFired(Trigger fired) {
        return new TriggerContext(fired, hitTarget, attacker, eventLocation, counterId);
    }

    public TriggerContext withCounter(String counterId) {
        return new TriggerContext(fired, hitTarget, attacker, eventLocation, counterId);
    }
}
```

`EffectKeys.java` 末尾新增：

```java
    /** 计数器 id（ON_COUNTER 信号的来源；绑定被动的 params.counter.id 与之匹配）。 */
    public static final EffectKey<String> COUNTER_ID = new EffectKey<>("counter_id");
    // COUNTER_SPEC 在 Task 2 引入（CounterSpec 类型定义后）
```

- [ ] **Step 4: 运行确认通过**

Run: `export JAVA_HOME=/home/chalcanthite/.local/jdk-25 && export PATH="$JAVA_HOME/bin:$PATH" && mvn -o -q test -Dtest=TriggerContextTest`
Expected: PASS

- [ ] **Step 5: 全量回归**

Run: `export JAVA_HOME=/home/chalcanthite/.local/jdk-25 && export PATH="$JAVA_HOME/bin:$PATH" && mvn -o -q test`
Expected: 仍为 329 tests / 0 failures / 5 skipped（TriggerContext 既有用例不受 4→5 字段影响）

- [ ] **Step 6: Commit**

```bash
git add src/main/java/io/mczju/maggoteers/effect/Trigger.java \
        src/main/java/io/mczju/maggoteers/effect/TriggerContext.java \
        src/main/java/io/mczju/maggoteers/effect/EffectKeys.java \
        src/test/java/io/mczju/maggoteers/effect/TriggerContextTest.java
git commit -m "feat(effect): add ON_COUNTER trigger + counterId context/key"
```

---

### Task 2: 计数器模型 + 解析（CounterCountTrigger / CounterCondition / CounterSpec / CounterState）

**Files:**
- Create: `src/main/java/io/mczju/maggoteers/effect/CounterCountTrigger.java`
- Create: `src/main/java/io/mczju/maggoteers/effect/CounterCondition.java`
- Create: `src/main/java/io/mczju/maggoteers/effect/CounterSpec.java`
- Create: `src/main/java/io/mczju/maggoteers/effect/CounterState.java`
- Create: `src/main/java/io/mczju/maggoteers/effect/CounterSpecParser.java`
- Modify: `src/main/java/io/mczju/maggoteers/effect/EffectKeys.java`（+COUNTER_SPEC）
- Modify: `src/main/java/io/mczju/maggoteers/state/PlayerState.java`（+counters()）
- Test: `src/test/java/io/mczju/maggoteers/effect/CounterSpecParserTest.java`
- Create: `src/test/java/io/mczju/maggoteers/effect/CounterStateTest.java`

**Interfaces:**
- Consumes: `Trigger.ON_COUNTER`（Task 1）。
- Produces: `CounterCountTrigger`（6 值）；`CounterCondition`（sealed：`And/Or/Not/HoldItemPdc/PlayerInRadius`，`evaluate(MaggoteersGame, Player)`）；`CounterSpec(id, countTrigger, amount, resetOn, condition)`（`resetOn: Trigger|null`，`triggeredBy(CounterCountTrigger)`）；`CounterState(spec)`（`progress()/increment()→boolean/reset()`）；`CounterSpecParser.parse(Object)→Optional<CounterSpec>`；`EffectKeys.COUNTER_SPEC`（`EffectKey<CounterSpec>`）；`PlayerState.counters()`。

- [ ] **Step 1: 写失败测试**

新建 `src/test/java/io/mczju/maggoteers/effect/CounterSpecParserTest.java`：

```java
package io.mczju.maggoteers.effect;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CounterSpecParserTest {

    @Test
    void parsesPlainCounter() {
        CounterSpec spec = CounterSpecParser.parse(Map.of(
                "id", "c1",
                "count", "attack",
                "amount", 5)).orElseThrow();
        assertEquals("c1", spec.id());
        assertEquals(CounterCountTrigger.ATTACK, spec.countTrigger());
        assertEquals(5, spec.amount());
        assertNull(spec.condition());
        assertNull(spec.resetOn());
    }

    @Test
    void timerIsTickCounter() {
        // 计时器 = count:tick 计数器：每 1 秒计一次，amount=30 → 30 秒触发
        CounterSpec spec = CounterSpecParser.parse(Map.of(
                "id", "t1",
                "count", "tick",
                "amount", 30)).orElseThrow();
        assertEquals(CounterCountTrigger.TICK, spec.countTrigger());
        assertEquals(30, spec.amount());
    }

    @Test
    void parsesCountOnCounterForRelay() {
        CounterSpec spec = CounterSpecParser.parse(Map.of(
                "id", "relay",
                "count", "on_counter",
                "amount", 2)).orElseThrow();
        assertEquals(CounterCountTrigger.ON_COUNTER, spec.countTrigger());
    }

    @Test
    void parsesResetTrigger() {
        CounterSpec spec = CounterSpecParser.parse(Map.of(
                "id", "c2",
                "count", "wave_clear",
                "amount", 1,
                "reset", "on_wave_clear")).orElseThrow();
        assertEquals(Trigger.ON_WAVE_CLEAR, spec.resetOn());
    }

    @Test
    void parsesConditionHoldItem() {
        CounterSpec spec = CounterSpecParser.parse(Map.of(
                "id", "c3",
                "count", "tick",
                "amount", 3,
                "condition", Map.of("hold_item_pdc", "maggoteers:wrench"))).orElseThrow();
        assertTrue(spec.condition() instanceof CounterCondition.HoldItemPdc h
                && h.pdcValue().equals("maggoteers:wrench"));
    }

    @Test
    void parsesCompoundCondition() {
        CounterSpec spec = CounterSpecParser.parse(Map.of(
                "id", "c4",
                "count", "kill",
                "amount", 2,
                "condition", Map.of(
                        "and", List.of(
                                Map.of("hold_item_pdc", "a"),
                                Map.of("not", Map.of("player_in_radius", 5.0)))))).orElseThrow();
        assertTrue(spec.condition() instanceof CounterCondition.And and
                && and.children().size() == 2
                && and.children().get(1) instanceof CounterCondition.Not);
    }

    @Test
    void missingIdRejected() {
        assertTrue(CounterSpecParser.parse(Map.of("count", "tick", "amount", 1)).isEmpty());
    }

    @Test
    void unknownCountTriggerRejected() {
        assertTrue(CounterSpecParser.parse(Map.of(
                "id", "x", "count", "bogus", "amount", 1)).isEmpty());
    }
}
```

新建 `src/test/java/io/mczju/maggoteers/effect/CounterStateTest.java`：

```java
package io.mczju.maggoteers.effect;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CounterStateTest {

    @Test
    void incrementsAndFiresAtAmount() {
        CounterState st = new CounterState(new CounterSpec("c", CounterCountTrigger.ATTACK, 3, null, null));
        assertFalse(st.increment());
        assertFalse(st.increment());
        assertTrue(st.increment());   // 3/3 → fire
        assertEquals(0, st.progress());   // 计满后清零（秒表/循环语义）
    }

    @Test
    void resetZeroes() {
        CounterState st = new CounterState(new CounterSpec("c", CounterCountTrigger.ATTACK, 3, null, null));
        st.increment();
        st.reset();
        assertEquals(0, st.progress());
    }

    @Test
    void triggeredByMatchesTrigger() {
        CounterSpec spec = new CounterSpec("c", CounterCountTrigger.ON_COUNTER, 2, null, null);
        assertTrue(spec.triggeredBy(CounterCountTrigger.ON_COUNTER));
        assertFalse(spec.triggeredBy(CounterCountTrigger.ATTACK));
    }
}
```

- [ ] **Step 2: 运行确认失败**

Run: `export JAVA_HOME=/home/chalcanthite/.local/jdk-25 && export PATH="$JAVA_HOME/bin:$PATH" && mvn -o -q test -Dtest=CounterSpecParserTest,CounterStateTest`
Expected: FAIL（类型未定义）

- [ ] **Step 3: 实现**

`CounterCountTrigger.java`：

```java
package io.mczju.maggoteers.effect;

/**
 * 计数器的计数输入（修正：计数器=中继器；计时器=count:tick 计数器）。
 * <p>TICK/ATTACK/DAMAGE_TAKEN/KILL/WAVE_CLEAR 为既有 trigger 驱动的输入；
 * ON_COUNTER 为<b>串联输入</b>——上游计数器计满后，其输出信号（ON_COUNTER）
 * 会驱动 countTrigger 为 ON_COUNTER 的其它计数器（跳过自身防自环）。
 */
public enum CounterCountTrigger {
    TICK, ATTACK, DAMAGE_TAKEN, KILL, WAVE_CLEAR, ON_COUNTER
}
```

`CounterCondition.java`：

```java
package io.mczju.maggoteers.effect;

import io.mczju.maggoteers.game.MaggoteersGame;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

/**
 * 计数条件修饰符（AND/OR/NOT 组合 + 叶子条件）。evaluate 以玩家为中心（玩家被动侧）。
 * <p>怪物侧的条件以怪物为中心判断，见 {@code MobCondition}（Task 4）。
 */
public sealed interface CounterCondition {

    boolean evaluate(MaggoteersGame game, Player player);

    /** 全部子条件为真。 */
    record And(List<CounterCondition> children) implements CounterCondition {
        public And {
            children = children == null ? List.of() : List.copyOf(children);
        }
        @Override public boolean evaluate(MaggoteersGame game, Player player) {
            for (CounterCondition c : children) {
                if (!c.evaluate(game, player)) {
                    return false;
                }
            }
            return true;
        }
    }

    /** 任一子条件为真。 */
    record Or(List<CounterCondition> children) implements CounterCondition {
        public Or {
            children = children == null ? List.of() : List.copyOf(children);
        }
        @Override public boolean evaluate(MaggoteersGame game, Player player) {
            for (CounterCondition c : children) {
                if (c.evaluate(game, player)) {
                    return true;
                }
            }
            return false;
        }
    }

    /** 子条件取反。 */
    record Not(CounterCondition child) implements CounterCondition {
        @Override public boolean evaluate(MaggoteersGame game, Player player) {
            return child != null && !child.evaluate(game, player);
        }
    }

    /** 手持物品 PDC {@code maggoteers:id} 匹配。 */
    record HoldItemPdc(String pdcValue) implements CounterCondition {
        @Override public boolean evaluate(MaggoteersGame game, Player player) {
            if (player == null) {
                return false;
            }
            ItemStack held = player.getInventory().getItemInMainHand();
            String id = held == null || held.getItemMeta() == null ? null
                    : held.getItemMeta().getPersistentDataContainer()
                            .get(io.mczju.maggoteers.item.RunItemTags.ID_KEY, PersistentDataType.STRING);
            return pdcValue != null && pdcValue.equals(id);
        }
    }

    /** 半径内存在<b>其它</b>玩家实体（以被评估玩家位置为中心）。 */
    record PlayerInRadius(double radius) implements CounterCondition {
        @Override public boolean evaluate(MaggoteersGame game, Player player) {
            if (player == null || game == null) {
                return false;
            }
            double r = Math.max(0.0, radius);
            for (var pe : game.getPlayers()) {
                Player other = pe.player();
                if (other == null || other.getUniqueId().equals(player.getUniqueId())) {
                    continue;
                }
                if (!other.getWorld().equals(player.getWorld())) {
                    continue;
                }
                if (other.getLocation().distanceSquared(player.getLocation()) <= r * r) {
                    return true;
                }
            }
            return false;
        }
    }
}
```

`CounterSpec.java`：

```java
package io.mczju.maggoteers.effect;

/**
 * 计数器规格（effect + trigger 中继器）。
 * <p>{@code countTrigger} 为输入；计满<b>只输出 {@code ON_COUNTER}</b>（由 CounterService
 * 对绑定该计数器 id 的被动 fire）。{@code resetOn} 为该 trigger 触发时清零（null=不自动清零；
 * 但计满即清零——秒表/循环语义）。{@code condition} 为该 trigger 计数时才评估（不满足则本次不计数）。
 */
public record CounterSpec(
        String id,
        CounterCountTrigger countTrigger,
        int amount,
        Trigger resetOn,
        CounterCondition condition
) {
    public CounterSpec {
        id = id == null ? "" : id.trim();
        amount = Math.max(1, amount);
    }

    public boolean triggeredBy(CounterCountTrigger t) {
        return countTrigger == t;
    }
}
```

`CounterState.java`：

```java
package io.mczju.maggoteers.effect;

/** 运行期计数器实例（出生/获取时加载，卸载/移除时丢弃）。 */
public final class CounterState {
    private final CounterSpec spec;
    private int progress;

    public CounterState(CounterSpec spec) {
        this.spec = spec;
        this.progress = 0;
    }

    public CounterSpec spec() { return spec; }
    public String id() { return spec.id(); }
    public int progress() { return progress; }

    /** 计满返回 true 并清零（循环语义）；否则 false。 */
    public boolean increment() {
        progress++;
        if (progress >= spec.amount()) {
            progress = 0;
            return true;
        }
        return false;
    }

    public void reset() { progress = 0; }
}
```

`CounterSpecParser.java`：

```java
package io.mczju.maggoteers.effect;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** 解析 {@code params.counter: {...}} 嵌套块（rewards.yml / items/*.yml）。 */
public final class CounterSpecParser {

    private CounterSpecParser() {}

    public static Optional<CounterSpec> parse(Object raw) {
        if (!(raw instanceof Map<?, ?> m)) {
            return Optional.empty();
        }
        String id = m.get("id") == null ? "" : String.valueOf(m.get("id")).trim();
        if (id.isBlank()) {
            return Optional.empty();
        }
        CounterCountTrigger trigger = enumOf(m.get("count"), CounterCountTrigger.class);
        if (trigger == null) {
            return Optional.empty();
        }
        int amount = m.get("amount") instanceof Number n ? n.intValue() : 1;
        Trigger reset = enumOf(m.get("reset"), Trigger.class);
        CounterCondition condition = parseCondition(m.get("condition"));
        return Optional.of(new CounterSpec(id, trigger, amount, reset, condition));
    }

    private static CounterCondition parseCondition(Object raw) {
        if (!(raw instanceof Map<?, ?> m)) {
            return null;
        }
        if (m.containsKey("and")) {
            return new CounterCondition.And(parseChildren(m.get("and")));
        }
        if (m.containsKey("or")) {
            return new CounterCondition.Or(parseChildren(m.get("or")));
        }
        if (m.containsKey("not")) {
            CounterCondition child = parseCondition(m.get("not"));
            return child == null ? null : new CounterCondition.Not(child);
        }
        if (m.containsKey("hold_item_pdc")) {
            return new CounterCondition.HoldItemPdc(String.valueOf(m.get("hold_item_pdc")));
        }
        if (m.containsKey("player_in_radius")) {
            double r = m.get("player_in_radius") instanceof Number n ? n.doubleValue() : 0.0;
            return new CounterCondition.PlayerInRadius(r);
        }
        return null;
    }

    private static List<CounterCondition> parseChildren(Object raw) {
        List<CounterCondition> out = new ArrayList<>();
        if (!(raw instanceof List<?> list)) {
            return out;
        }
        for (Object o : list) {
            CounterCondition c = parseCondition(o);
            if (c != null) {
                out.add(c);
            }
        }
        return out;
    }

    private static <T extends Enum<T>> T enumOf(Object raw, Class<T> type) {
        if (raw == null) {
            return null;
        }
        try {
            return Enum.valueOf(type, String.valueOf(raw).trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
```

`EffectKeys.java` 追加：

```java
    public static final EffectKey<CounterSpec> COUNTER_SPEC = new EffectKey<>("counter_spec");
```

`PlayerState.java` 加字段 + accessor（确保 `java.util.HashMap`/`java.util.Map` 已 import）：

```java
    private final Map<String, CounterState> counters = new HashMap<>();
    // 在 acquireUnique() accessor 附近：
    public Map<String, CounterState> counters() { return counters; }
```

- [ ] **Step 4: 运行确认通过**

Run: `export JAVA_HOME=/home/chalcanthite/.local/jdk-25 && export PATH="$JAVA_HOME/bin:$PATH" && mvn -o -q test -Dtest=CounterSpecParserTest,CounterStateTest`
Expected: PASS

- [ ] **Step 5: 全量回归 + Commit**

Run: `export JAVA_HOME=/home/chalcanthite/.local/jdk-25 && export PATH="$JAVA_HOME/bin:$PATH" && mvn -o -q test`
Expected: 329 tests / 0 failures / 5 skipped

```bash
git add src/main/java/io/mczju/maggoteers/effect/CounterCountTrigger.java \
        src/main/java/io/mczju/maggoteers/effect/CounterCondition.java \
        src/main/java/io/mczju/maggoteers/effect/CounterSpec.java \
        src/main/java/io/mczju/maggoteers/effect/CounterState.java \
        src/main/java/io/mczju/maggoteers/effect/CounterSpecParser.java \
        src/main/java/io/mczju/maggoteers/effect/EffectKeys.java \
        src/main/java/io/mczju/maggoteers/state/PlayerState.java \
        src/test/java/io/mczju/maggoteers/effect/CounterSpecParserTest.java \
        src/test/java/io/mczju/maggoteers/effect/CounterStateTest.java
git commit -m "feat(effect): counter model — count triggers, conditions, spec/state/parser"
```

---

### Task 3: 计数器运行时（CounterService + EffectService 接线 + 配置解析/校验 + 武器支持）

**Files:**
- Create: `src/main/java/io/mczju/maggoteers/effect/CounterService.java`
- Modify: `src/main/java/io/mczju/maggoteers/effect/EffectService.java`（apply 注册 / removeAllFor 清理 / processTriggerEffects 注销 / fireTrigger+firedPlayer 推进 / 新方法 fireCounter+dispatchCounterEffects+counterInput+toTrigger）
- Modify: `src/main/java/io/mczju/maggoteers/reward/RewardOption.java`（parseParams 加 `case "counter"`）
- Modify: `src/main/java/io/mczju/maggoteers/reward/RewardLoadValidator.java`（ALLOWED_FIRE += ON_COUNTER；ON_COUNTER 需 counter 校验）
- Modify: `src/main/java/io/mczju/maggoteers/effect/WeaponEffectValidator.java`（HELD_TRIGGERS += ON_COUNTER；counter 校验）
- Modify: `src/main/java/io/mczju/maggoteers/effect/WeaponHeldRegistry.java`（解析 counter 块）
- Test: `src/test/java/io/mczju/maggoteers/effect/CounterServiceTest.java`
- Create: `src/test/java/io/mczju/maggoteers/effect/CounterConditionTest.java`

**Interfaces:**
- Consumes: Task 1（`Trigger.ON_COUNTER`、`TriggerContext.counterId()`）、Task 2（`CounterSpec`/`CounterState`/`CounterCondition`/`CounterSpecParser`/`EffectKeys.COUNTER_SPEC`/`PlayerState.counters()`）。
- Produces: `CounterService.register(PlayerState, CounterSpec)` / `unregister(PlayerState, String)` / `clearAll(PlayerState)` / `resetMatching(PlayerState, Trigger)` / `advance(PlayerState, CounterCountTrigger, String sourceId, Predicate<CounterSpec>) → List<String>`；`EffectService.fireCounter(game, ps, p, t, sourceId, ctx)`。**触发器映射**：`ON_TICK_1S→TICK`、`ON_WAVE_CLEAR→WAVE_CLEAR`、`ON_KILL→KILL`、`ON_DAMAGE_DEALT→ATTACK`、`ON_DAMAGE_TAKEN→DAMAGE_TAKEN`（在 fireTrigger/fireTriggerPlayer 内部自动推进，**EffectListener 无需改动**）。

- [ ] **Step 1: 写失败测试**

新建 `src/test/java/io/mczju/maggoteers/effect/CounterServiceTest.java`：

```java
package io.mczju.maggoteers.effect;

import io.mczju.maggoteers.state.PlayerState;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

class CounterServiceTest {

    private static final Predicate<CounterSpec> ALL = spec -> true;

    private static CounterState counter(String id, CounterCountTrigger t, int amount) {
        return new CounterState(new CounterSpec(id, t, amount, null, null));
    }

    private static CounterState counter(String id, CounterCountTrigger t, int amount, Trigger resetOn) {
        return new CounterState(new CounterSpec(id, t, amount, resetOn, null));
    }

    private static PlayerState stateWith(CounterState... states) {
        PlayerState ps = new PlayerState(UUID.randomUUID(), 2);
        for (CounterState cs : states) {
            ps.counters().put(cs.id(), cs);
        }
        return ps;
    }

    @Test
    void firesAtAmountAndResets() {
        PlayerState ps = stateWith(counter("c", CounterCountTrigger.ATTACK, 3));
        assertEquals(List.of(), CounterService.advance(ps, CounterCountTrigger.ATTACK, null, ALL));
        assertEquals(List.of(), CounterService.advance(ps, CounterCountTrigger.ATTACK, null, ALL));
        assertEquals(List.of("c"), CounterService.advance(ps, CounterCountTrigger.ATTACK, null, ALL));
        assertEquals(0, ps.counters().get("c").progress());   // 计满清零（秒表循环语义）
    }

    @Test
    void timerIsTickCounter() {
        // 计时器 = count:tick：每秒 +1，amount=2 → 第 2、4、6…秒触发
        PlayerState ps = stateWith(counter("t", CounterCountTrigger.TICK, 2));
        assertEquals(List.of(), CounterService.advance(ps, CounterCountTrigger.TICK, null, ALL));
        assertEquals(List.of("t"), CounterService.advance(ps, CounterCountTrigger.TICK, null, ALL));
        assertEquals(List.of(), CounterService.advance(ps, CounterCountTrigger.TICK, null, ALL));
        assertEquals(List.of("t"), CounterService.advance(ps, CounterCountTrigger.TICK, null, ALL));
    }

    @Test
    void relayChainDrivesDownstream() {
        PlayerState ps = stateWith(
                counter("a", CounterCountTrigger.ATTACK, 1),
                counter("b", CounterCountTrigger.ON_COUNTER, 2),
                counter("c", CounterCountTrigger.ON_COUNTER, 1));
        // a 计满 → ON_COUNTER 信号 → b+1、c 计满
        List<String> fired = CounterService.advance(ps, CounterCountTrigger.ATTACK, null, ALL);
        assertTrue(fired.contains("a"));
        assertTrue(fired.contains("c"));
        assertFalse(fired.contains("b"));   // b 只 +1 未满
        // 下一次：a 又满、b 满
        fired = CounterService.advance(ps, CounterCountTrigger.ATTACK, null, ALL);
        assertTrue(fired.contains("a"));
        assertTrue(fired.contains("b"));
    }

    @Test
    void selfLoopSkipped() {
        PlayerState ps = stateWith(counter("a", CounterCountTrigger.ON_COUNTER, 1));
        // ON_COUNTER 信号来自 a 自身 → 排除 sourceId，不触发
        assertEquals(List.of(), CounterService.advance(ps, CounterCountTrigger.ON_COUNTER, "a", ALL));
    }

    @Test
    void gateFilters() {
        PlayerState ps = stateWith(counter("c", CounterCountTrigger.ATTACK, 1));
        assertEquals(List.of(), CounterService.advance(ps, CounterCountTrigger.ATTACK, null,
                spec -> false));
        assertEquals(0, ps.counters().get("c").progress());
    }

    @Test
    void resetMatchingZeroesOnMatchingResetOn() {
        PlayerState ps = stateWith(counter("c", CounterCountTrigger.TICK, 5, Trigger.ON_WAVE_CLEAR));
        ps.counters().get("c").increment();
        CounterService.resetMatching(ps, Trigger.ON_WAVE_CLEAR);
        assertEquals(0, ps.counters().get("c").progress());
        // resetOn 不匹配 → 不重置
        ps.counters().get("c").increment();
        CounterService.resetMatching(ps, Trigger.ON_ACT_ENTER);
        assertEquals(1, ps.counters().get("c").progress());
    }

    @Test
    void registerAndUnregister() {
        PlayerState ps = new PlayerState(UUID.randomUUID(), 2);
        CounterService.register(ps, new CounterSpec("c", CounterCountTrigger.KILL, 2, null, null));
        assertTrue(ps.counters().containsKey("c"));
        CounterService.unregister(ps, "c");
        assertFalse(ps.counters().containsKey("c"));
    }
}
```

新建 `src/test/java/io/mczju/maggoteers/effect/CounterConditionTest.java`：

```java
package io.mczju.maggoteers.effect;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CounterConditionTest {

    // 叶子在 (game,player)=(null,null) 恒 false；用 Not 构造 TRUE 测纯逻辑组合真值表
    private static final CounterCondition FALSE = new CounterCondition.HoldItemPdc("x");
    private static final CounterCondition TRUE = new CounterCondition.Not(FALSE);

    @Test
    void andRequiresAllTrue() {
        assertTrue(new CounterCondition.And(List.of(TRUE, TRUE)).evaluate(null, null));
        assertFalse(new CounterCondition.And(List.of(TRUE, FALSE)).evaluate(null, null));
    }

    @Test
    void orRequiresAnyTrue() {
        assertTrue(new CounterCondition.Or(List.of(FALSE, TRUE)).evaluate(null, null));
        assertFalse(new CounterCondition.Or(List.of(FALSE, FALSE)).evaluate(null, null));
    }

    @Test
    void notInverts() {
        assertTrue(TRUE.evaluate(null, null));
        assertFalse(FALSE.evaluate(null, null));
        assertFalse(new CounterCondition.Not(TRUE).evaluate(null, null));
    }

    @Test
    void parserRoundTripsNestedCondition() {
        CounterCondition c = CounterSpecParser.parse(Map.of(
                "id", "c", "count", "tick", "amount", 1,
                "condition", Map.of("or", List.of(
                        Map.of("not", Map.of("hold_item_pdc", "a")),
                        Map.of("player_in_radius", 3.0))))).orElseThrow().condition();
        assertTrue(c instanceof CounterCondition.Or);
        assertTrue(((CounterCondition.Or) c).children().get(0) instanceof CounterCondition.Not);
    }
}
```

- [ ] **Step 2: 运行确认失败**

Run: `export JAVA_HOME=/home/chalcanthite/.local/jdk-25 && export PATH="$JAVA_HOME/bin:$PATH" && mvn -o -q test -Dtest=CounterServiceTest,CounterConditionTest`
Expected: FAIL（`CounterService` 未定义）

- [ ] **Step 3: 实现 CounterService**

`src/main/java/io/mczju/maggoteers/effect/CounterService.java`：

```java
package io.mczju.maggoteers.effect;

import io.mczju.maggoteers.state.PlayerState;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * 计数器运行时（玩家侧）。纯逻辑部分（advance）与 Bukkit 解耦，便于单测。
 * <p>advance 语义：以 {@code t} 为输入信号，推进所有 countTrigger==t 且未被 sourceId 排除
 * 且通过 gate 的计数器；计满的 id 会作为 ON_COUNTER 信号继续驱动 countTrigger==ON_COUNTER
 * 的其它计数器（跳过自身防自环）。返回<b>全部</b>计满 id（含串联各级）。
 */
public final class CounterService {

    private CounterService() {}

    public static void register(PlayerState ps, CounterSpec spec) {
        if (ps == null || spec == null || spec.id().isBlank()) {
            return;
        }
        ps.counters().put(spec.id(), new CounterState(spec));
    }

    public static void unregister(PlayerState ps, String counterId) {
        if (ps == null || counterId == null) {
            return;
        }
        ps.counters().remove(counterId);
    }

    public static void clearAll(PlayerState ps) {
        if (ps != null) {
            ps.counters().clear();
        }
    }

    /** 清零修饰符：该 trigger 触发时，resetOn 匹配的计数器清零（本次不计数）。 */
    public static void resetMatching(PlayerState ps, Trigger firedTrigger) {
        if (ps == null || firedTrigger == null) {
            return;
        }
        for (CounterState cs : ps.counters().values()) {
            if (firedTrigger == cs.spec().resetOn()) {
                cs.reset();
            }
        }
    }

    /**
     * @param t        输入信号（外部 trigger 映射来的 CounterCountTrigger；串联在内部处理）
     * @param sourceId 初始来源 id（外部调用传 null；内联中继时传信号来源）
     * @param gate     条件门（玩家侧由 EffectService 构造 spec.condition().evaluate(game, player)）
     * @return 计满的计数器 id 列表（去重、含串联链）
     */
    public static List<String> advance(PlayerState ps, CounterCountTrigger t, String sourceId,
                                       Predicate<CounterSpec> gate) {
        if (ps == null || ps.counters().isEmpty()) {
            return List.of();
        }
        List<String> fired = new ArrayList<>();
        Set<String> processed = new HashSet<>();
        Deque<String> relayQueue = new ArrayDeque<>();
        collect(ps, t, sourceId, gate, fired, processed, relayQueue);
        while (!relayQueue.isEmpty()) {
            String sig = relayQueue.poll();
            collect(ps, CounterCountTrigger.ON_COUNTER, sig, gate, fired, processed, relayQueue);
        }
        return List.copyOf(fired);
    }

    private static void collect(PlayerState ps, CounterCountTrigger t, String excludeId,
                                Predicate<CounterSpec> gate, List<String> fired,
                                Set<String> processed, Deque<String> relayQueue) {
        for (CounterState cs : ps.counters().values()) {
            CounterSpec spec = cs.spec();
            if (!spec.triggeredBy(t)) {
                continue;
            }
            if (excludeId != null && excludeId.equals(cs.id())) {
                continue;                       // 防自环
            }
            if (processed.contains(cs.id())) {
                continue;
            }
            if (gate != null && !gate.test(spec)) {
                continue;
            }
            if (cs.increment()) {
                processed.add(cs.id());
                fired.add(cs.id());
                relayQueue.add(cs.id());        // 作为 ON_COUNTER 信号继续中继
            }
        }
    }
}
```

- [ ] **Step 4: 接入 EffectService**

`src/main/java/io/mczju/maggoteers/effect/EffectService.java` 四处改动：

**(a) `apply(Player, PlayerEffect, MaggoteersGame)`**——在 `if (mergedPe != null && mergedPe.effect() == Effect.BOUND_EQUIP) {...}` 分支之后追加：

```java
        if (mergedPe != null) {
            CounterSpec spec = mergedPe.params().get(EffectKeys.COUNTER_SPEC);
            if (spec != null) CounterService.register(st, spec);
        }
```

**(b) `removeAllFor(Player, MaggoteersGame)`**——`st.clearEffects();` 之后追加：

```java
            CounterService.clearAll(st);
```

**(c) `fireTriggerPlayer(MaggoteersGame, Player, Trigger, TriggerContext)`**——在 `if (fired != Trigger.ON_DEATH && fired != Trigger.ON_REVIVE && !ps.isAlive()) return;` 与 `processTriggerEffects(...)` 之间插入：

```java
            CounterCountTrigger ct = counterInput(fired);
            if (ct != null) {
                fireCounter(game, ps, p, ct, null, useCtx);
            }
```

**(d) `fireTrigger(MaggoteersGame, Trigger, int)`**——per-player 循环内 `processTriggerEffects(ps, p, game, fired, TriggerContext.empty());` 之前插入：

```java
                    CounterCountTrigger ct = counterInput(fired);
                    if (ct != null) {
                        fireCounter(game, ps, p, ct, null, TriggerContext.empty());
                    }
```

**新增方法**（放在 `fireTriggerPlayer` 之后、`processTriggerEffects` 之前；`counterInput`/`toTrigger`/`dispatchCounterEffects` 放文件内合适位置）：

```java
    /** 外部 trigger → 计数器输入信号（无对应输入返回 null）。 */
    private static CounterCountTrigger counterInput(Trigger t) {
        return switch (t) {
            case ON_TICK_1S -> CounterCountTrigger.TICK;
            case ON_WAVE_CLEAR -> CounterCountTrigger.WAVE_CLEAR;
            case ON_KILL -> CounterCountTrigger.KILL;
            case ON_DAMAGE_DEALT -> CounterCountTrigger.ATTACK;
            case ON_DAMAGE_TAKEN -> CounterCountTrigger.DAMAGE_TAKEN;
            default -> null;
        };
    }

    private static Trigger toTrigger(CounterCountTrigger t) {
        return switch (t) {
            case TICK -> Trigger.ON_TICK_1S;
            case WAVE_CLEAR -> Trigger.ON_WAVE_CLEAR;
            case KILL -> Trigger.ON_KILL;
            case ATTACK -> Trigger.ON_DAMAGE_DEALT;
            case DAMAGE_TAKEN -> Trigger.ON_DAMAGE_TAKEN;
            case ON_COUNTER -> null;
        };
    }

    /**
     * 推进计数器（含 resetOn 清零 + condition 门 + ON_COUNTER 串联），
     * 对每个计满 id dispatch fireTrigger==ON_COUNTER && params.counter.id==id 的被动。
     */
    public static void fireCounter(MaggoteersGame game, PlayerState ps, Player p,
                                   CounterCountTrigger t, String sourceId, TriggerContext ctx) {
        if (ps == null) {
            return;
        }
        CounterService.resetMatching(ps, toTrigger(t));
        List<String> firedIds = CounterService.advance(ps, t, sourceId,
                spec -> spec.condition() == null || spec.condition().evaluate(game, p));
        for (String id : firedIds) {
            TriggerContext c = (ctx != null ? ctx : TriggerContext.empty())
                    .withFired(Trigger.ON_COUNTER).withCounter(id);
            dispatchCounterEffects(ps, p, game, id, c);
        }
    }

    private static void dispatchCounterEffects(PlayerState ps, Player p, MaggoteersGame game,
                                               String counterId, TriggerContext ctx) {
        for (PlayerEffect e : new ArrayList<>(ps.effects())) {
            if (e.fireTrigger() != Trigger.ON_COUNTER) {
                continue;
            }
            if (!counterId.equals(e.params().get(EffectKeys.COUNTER_ID))) {
                continue;
            }
            executeEffect(p, e, game, ctx);
        }
    }
```

**附加改动（e）`processTriggerEffects`**——效果移除时注销其计数器。在 `TriggerFireOrder.run(...)` 调用之前，先记录 effects 里各 effect id → COUNTER_SPEC 的映射，removed 循环内对匹配项注销：

```java
        Map<String, CounterSpec> counterByEffectId = new java.util.HashMap<>();
        for (PlayerEffect e : ps.effects()) {
            CounterSpec cs = e.params().get(EffectKeys.COUNTER_SPEC);
            if (cs != null) {
                counterByEffectId.put(e.id(), cs);
            }
        }
        Set<String> touched = new HashSet<>();
        List<String> removed = TriggerFireOrder.run(...);
        if (!removed.isEmpty()) {
            resyncDerived(p, ps);
            for (String removedId : removed) {
                io.mczju.maggoteers.reward.CollectibleService.remove(p, removedId);
                BoundEquipService.remove(p, removedId);
                CounterSpec cs = counterByEffectId.get(removedId);
                if (cs != null) {
                    CounterService.unregister(ps, cs.id());
                }
            }
        }
```

- [ ] **Step 5: 配置解析 + 校验 + 武器支持**

`RewardOption.java` 的 `parseParams` switch 中 `case "potion"` 之后加：

```java
                case "counter" -> io.mczju.maggoteers.effect.CounterSpecParser.parse(v).ifPresent(spec -> {
                    ctx.put(EffectKeys.COUNTER_SPEC, spec);
                    ctx.put(EffectKeys.COUNTER_ID, spec.id());
                });
```

`RewardLoadValidator.java`：
- `ALLOWED_FIRE` 集合追加 `Trigger.ON_COUNTER`。
- `validateStatOption` 中 `if (fire != null && !ALLOWED_FIRE.contains(fire))` 检查之后加：

```java
        if (fire == Trigger.ON_COUNTER) {
            if (opt.params() == null || opt.params().get(EffectKeys.COUNTER_SPEC) == null) {
                return Optional.of("ON_COUNTER requires params.counter");
            }
        }
```

`WeaponEffectValidator.java`：
- `HELD_TRIGGERS` 追加 `Trigger.ON_COUNTER`。
- `validateHeld` 的 `if (fireTrigger != null)` 分支内、`HELD_TRIGGERS` 检查之后加：

```java
            if (fireTrigger == Trigger.ON_COUNTER) {
                if (params == null || params.get(EffectKeys.COUNTER_SPEC) == null) {
                    return Optional.of("ON_COUNTER held requires params.counter");
                }
            }
```

`WeaponHeldRegistry.java` 的 `parseEntry`：在 `params = MagicEffectParams.withDefaults(effect, params);` 之后加（解析 `params.counter` 块；`EffectParamsParser` 不认识该键）：

```java
        if (paramsRaw instanceof Map<?, ?> pm && pm.get("counter") != null) {
            CounterSpecParser.parse(pm.get("counter")).ifPresent(spec -> {
                params.put(EffectKeys.COUNTER_SPEC, spec);
                params.put(EffectKeys.COUNTER_ID, spec.id());
            });
        }
```

> ⚠️ **use_ability 不引入计数器**：魔法武器计数器走 `held_effects`（主手持有期间秒表语义）。`use_ability` 为右键即时/延期，无"获得即计时"语义，不在本计划范围内（保持 WeaponHeldRegistry/WeaponEffectValidator 单入口）。

- [ ] **Step 6: 运行确认通过 + 全量回归**

Run: `export JAVA_HOME=/home/chalcanthite/.local/jdk-25 && export PATH="$JAVA_HOME/bin:$PATH" && mvn -o -q test -Dtest=CounterServiceTest,CounterConditionTest`
Expected: PASS

Run: `export JAVA_HOME=/home/chalcanthite/.local/jdk-25 && export PATH="$JAVA_HOME/bin:$PATH" && mvn -o -q test`
Expected: 全部通过（新增 ~10 用例；既有 329 保持绿）

- [ ] **Step 7: Commit**

```bash
git add src/main/java/io/mczju/maggoteers/effect/CounterService.java \
        src/main/java/io/mczju/maggoteers/effect/EffectService.java \
        src/main/java/io/mczju/maggoteers/reward/RewardOption.java \
        src/main/java/io/mczju/maggoteers/reward/RewardLoadValidator.java \
        src/main/java/io/mczju/maggoteers/effect/WeaponEffectValidator.java \
        src/main/java/io/mczju/maggoteers/effect/WeaponHeldRegistry.java \
        src/test/java/io/mczju/maggoteers/effect/CounterServiceTest.java \
        src/test/java/io/mczju/maggoteers/effect/CounterConditionTest.java
git commit -m "feat(effect): counter runtime — CounterService + ON_COUNTER dispatch + config/weapon support"
```

---

### Task 4: 怪物技能模型 + mob_skills.yml（MobTrigger/MobEffect/MobEffectSpec/MobSkillSpec/MobCondition/MobSkillSpecParser/MobSkillRegistry）

**Files:**
- Create: `src/main/java/io/mczju/maggoteers/mob/MobTrigger.java`
- Create: `src/main/java/io/mczju/maggoteers/mob/MobEffect.java`
- Create: `src/main/java/io/mczju/maggoteers/mob/MobEffectSpec.java`
- Create: `src/main/java/io/mczju/maggoteers/mob/MobCounterSpec.java`
- Create: `src/main/java/io/mczju/maggoteers/mob/MobSkillSpec.java`
- Create: `src/main/java/io/mczju/maggoteers/mob/MobCondition.java`
- Create: `src/main/java/io/mczju/maggoteers/mob/MobSkillSpecParser.java`
- Create: `src/main/java/io/mczju/maggoteers/mob/MobSkillRegistry.java`
- Create: `src/main/resources/mob_skills.yml`
- Modify: `src/main/java/io/mczju/maggoteers/effect/EffectKeys.java`（**加 `HOMING_TARGET`**——本 Task 的 parser 引用它；Task 7 只消费）
- Test: `src/test/java/io/mczju/maggoteers/mob/MobSkillSpecParserTest.java`

**Interfaces:**
- Consumes: 玩家侧 `CounterSpec`/`CounterCondition` 无依赖（怪物独立模型）；`EffectKeys`（复用 EffectContext 存效果参数；**本 Task 向 EffectKeys 追加 `HOMING_TARGET`**）；`Effect` 无依赖。
- Produces: `MobTrigger`（6 值）；`MobEffect`（5 值）；`MobEffectSpec(MobEffect effect, EffectContext params)`；`MobCounterSpec(id, MobTrigger count, int amount, MobTrigger resetOn, MobCondition condition)`；`MobSkillSpec(id, MobTrigger trigger, MobCondition condition, boolean invisible, MobCounterSpec counter, int cooldownSec, List<MobEffectSpec> effects)`；`MobCondition.evaluate(MaggoteersGame, LivingEntity mob, LivingEntity target, LivingEntity source)`；`MobSkillSpecParser.parse(Map)→Optional<MobSkillSpec>`；`MobSkillRegistry.load(MaggoteersPlugin)` / `get(String id)→Optional<MobSkillSpec>` / `all()`。

- [ ] **Step 1: 写失败测试**

新建 `src/test/java/io/mczju/maggoteers/mob/MobSkillSpecParserTest.java`：

```java
package io.mczju.maggoteers.mob;

import io.mczju.maggoteers.effect.EffectKeys;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MobSkillSpecParserTest {

    @Test
    void parsesSpawnPotionSkill() {
        MobSkillSpec s = MobSkillSpecParser.parse(Map.of(
                "id", "plastic",
                "trigger", "spawn",
                "effects", List.of(Map.of(
                        "effect", "potion",
                        "target", "self",
                        "potion", "resistance",
                        "amp", 4,
                        "duration_ticks", 600)))).orElseThrow();
        assertEquals(MobTrigger.SPAWN, s.trigger());
        assertFalse(s.invisible());
        assertNull(s.counter());
        assertEquals(1, s.effects().size());
        MobEffectSpec es = s.effects().get(0);
        assertEquals(MobEffect.POTION, es.effect());
        assertEquals(4, es.params().getOrDefault(EffectKeys.AMP, 0));
    }

    @Test
    void parsesInvisibleFlag() {
        MobSkillSpec s = MobSkillSpecParser.parse(Map.of(
                "id", "invisible",
                "trigger", "spawn",
                "invisible", true)).orElseThrow();
        assertTrue(s.invisible());
        assertTrue(s.effects().isEmpty());   // 纯装饰允许空 effects
    }

    @Test
    void counterTriggerRequiresCounter() {
        assertTrue(MobSkillSpecParser.parse(Map.of(
                "id", "x",
                "trigger", "counter",
                "effects", List.of(Map.of("effect", "health", "target", "self", "amount", 2)))).isEmpty());
        assertTrue(MobSkillSpecParser.parse(Map.of(
                "id", "x",
                "trigger", "counter",
                "counter", Map.of("id", "c1", "count", "attack", "amount", 5),
                "effects", List.of(Map.of("effect", "health", "target", "self", "amount", 2)))).isPresent());
    }

    @Test
    void tickTriggerRequiresCooldown() {
        assertTrue(MobSkillSpecParser.parse(Map.of(
                "id", "aura",
                "trigger", "tick",
                "cooldown_sec", 2,
                "effects", List.of(Map.of(
                        "effect", "potion", "target", "self",
                        "potion", "regeneration", "amp", 0, "duration_ticks", 40)))).isPresent());
        assertTrue(MobSkillSpecParser.parse(Map.of(
                "id", "aura",
                "trigger", "tick",
                "effects", List.of(Map.of("effect", "health", "target", "self", "amount", 1)))).isEmpty());
    }

    @Test
    void parsesCounterSpec() {
        MobSkillSpec s = MobSkillSpecParser.parse(Map.of(
                "id", "x",
                "trigger", "counter",
                "counter", Map.of("id", "c1", "count", "damage_taken", "amount", 3,
                        "condition", Map.of("not", Map.of("player_in_radius", 4.0))),
                "cooldown_sec", 1,
                "effects", List.of(Map.of("effect", "teleport", "target", "self")))).orElseThrow();
        MobCounterSpec c = s.counter();
        assertNotNull(c);
        assertEquals(MobTrigger.DAMAGE_TAKEN, c.count());
        assertEquals(3, c.amount());
        assertTrue(c.condition() instanceof MobCondition.Not);
    }

    @Test
    void unknownTriggerRejected() {
        assertTrue(MobSkillSpecParser.parse(Map.of(
                "id", "x", "trigger", "bogus")).isEmpty());
    }

    @Test
    void mobConditionCombinators() {
        MobCondition.TRUE t = MobCondition.TRUE;   // 见实现
        MobCondition.FALSE f = MobCondition.FALSE;
        assertTrue(new MobCondition.And(List.of(t, t)).evaluate(null, null, null, null));
        assertFalse(new MobCondition.And(List.of(t, f)).evaluate(null, null, null, null));
        assertTrue(new MobCondition.Or(List.of(f, t)).evaluate(null, null, null, null));
        assertFalse(new MobCondition.Not(t).evaluate(null, null, null, null));
    }
}
```

> 测试引用 `MobCondition.TRUE`/`MobCondition.FALSE` 两个**常量**——实现里提供（恒真/恒假叶子），便于纯逻辑组合测试。

- [ ] **Step 2: 运行确认失败**

Run: `export JAVA_HOME=/home/chalcanthite/.local/jdk-25 && export PATH="$JAVA_HOME/bin:$PATH" && mvn -o -q test -Dtest=MobSkillSpecParserTest`
Expected: FAIL（类型未定义）

- [ ] **Step 3: 实现模型类**

`MobTrigger.java`：

```java
package io.mczju.maggoteers.mob;

/** 怪物技能触发类型（与玩家被动对称；计数器为独立 trigger）。 */
public enum MobTrigger {
    ATTACK,          // 攻击时（MobSkillService 注入 target）
    DAMAGE_TAKEN,    // 受击时（注入 source）
    KILLED,          // 被击杀时（注入 killer）
    SPAWN,           // 生成时（出生施加）
    TICK,            // 光环类：每 cooldown_sec 触发一次（tick 心跳）
    COUNTER          // 计数器计满（MobCounterRegistry 推进）
}
```

`MobEffect.java`：

```java
package io.mczju.maggoteers.mob;

/** 怪物技能效果（自身/目标/范围）。 */
public enum MobEffect {
    HEALTH,      // 对目标/自身增减血量（单体或半径范围）
    ATTRIBUTE,   // 对目标/自身属性修改（临时，可虚拟属性）
    POTION,      // 对目标/自身施加药水
    SUMMON,      // 召唤（含追踪投射物 homing）
    TELEPORT     // 传送（以自身或目标为锚点，防虚空/出图）
}
```

`MobEffectSpec.java`：

```java
package io.mczju.maggoteers.mob;

import io.mczju.maggoteers.effect.EffectContext;
import io.mczju.maggoteers.effect.EffectKeys;

/**
 * 一条怪物效果。参数复用玩家侧 {@link EffectContext}/{@link EffectKeys}：
 * <ul>
 *   <li>HEALTH: {@code amount}（正=治疗 负=伤害；单体）或 {@code radius}+{@code damage}（范围）</li>
 *   <li>ATTRIBUTE: {@code attr} + {@code op} + {@code value} + {@code duration_ticks}（>0 为临时）</li>
 *   <li>POTION: {@code potion} + {@code amp} + {@code duration_ticks}</li>
 *   <li>SUMMON: {@code entity} + {@code count} + {@code projectile}（含 homing）</li>
 *   <li>TELEPORT: {@code radius}（目标随机落点范围）；锚点由触发上下文决定</li>
 * </ul>
 */
public record MobEffectSpec(MobEffect effect, EffectContext params) {
    public MobEffectSpec {
        params = params == null ? new EffectContext() : params;
    }
}
```

`MobCounterSpec.java`：

```java
package io.mczju.maggoteers.mob;

/**
 * 怪物计数器规格（对称于玩家 CounterSpec，count 用 MobTrigger）。
 * <p>count 取 ATTACK/DAMAGE_TAKEN/KILLED/TICK；SPAWN 无意义（出生即计满）；
 * COUNTER 用于串联（上游计满信号驱动 count==COUNTER 的其它计数器，跳过自身）。
 */
public record MobCounterSpec(
        String id,
        MobTrigger count,
        int amount,
        MobTrigger resetOn,
        MobCondition condition
) {
    public MobCounterSpec {
        id = id == null ? "" : id.trim();
        amount = Math.max(1, amount);
    }

    public boolean triggeredBy(MobTrigger t) {
        return count == t;
    }
}
```

`MobSkillSpec.java`：

```java
package io.mczju.maggoteers.mob;

import java.util.List;

/**
 * 一条怪物技能（trigger + 可选条件 + 可选计数器 + 效果列表）。
 * <p>{@code invisible: true} 由 {@code MobSkillService} 在 SPAWN 时处理
 * （setInvisible + 玻璃瓶头盔，不用 INVISIBILITY 药水）。{@code effects} 允许为空（纯装饰）。
 */
public record MobSkillSpec(
        String id,
        MobTrigger trigger,
        MobCondition condition,
        boolean invisible,
        MobCounterSpec counter,
        int cooldownSec,
        List<MobEffectSpec> effects
) {
    public MobSkillSpec {
        effects = effects == null ? List.of() : List.copyOf(effects);
        cooldownSec = Math.max(0, cooldownSec);
    }
}
```

`MobCondition.java`（怪物为中心；evaluate 时 mob 为判断中心，target/source 为触发上下文）：

```java
package io.mczju.maggoteers.mob;

import io.mczju.maggoteers.game.MaggoteersGame;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

/**
 * 怪物技能条件修饰符（AND/OR/NOT + 叶子），<b>以怪物为中心</b>判断：
 * HoldItemPdc 看怪物手持；PlayerInRadius 以怪物位置为中心查玩家。
 */
public sealed interface MobCondition {

    boolean evaluate(MaggoteersGame game, LivingEntity mob, LivingEntity target, LivingEntity source);

    /** 恒真（纯逻辑组合测试用）。 */
    MobCondition TRUE = (g, m, t, s) -> true;
    /** 恒假（纯逻辑组合测试用）。 */
    MobCondition FALSE = (g, m, t, s) -> false;

    record And(List<MobCondition> children) implements MobCondition {
        public And {
            children = children == null ? List.of() : List.copyOf(children);
        }
        @Override public boolean evaluate(MaggoteersGame game, LivingEntity mob,
                                          LivingEntity target, LivingEntity source) {
            for (MobCondition c : children) {
                if (!c.evaluate(game, mob, target, source)) {
                    return false;
                }
            }
            return true;
        }
    }

    record Or(List<MobCondition> children) implements MobCondition {
        public Or {
            children = children == null ? List.of() : List.copyOf(children);
        }
        @Override public boolean evaluate(MaggoteersGame game, LivingEntity mob,
                                          LivingEntity target, LivingEntity source) {
            for (MobCondition c : children) {
                if (c.evaluate(game, mob, target, source)) {
                    return true;
                }
            }
            return false;
        }
    }

    record Not(MobCondition child) implements MobCondition {
        @Override public boolean evaluate(MaggoteersGame game, LivingEntity mob,
                                          LivingEntity target, LivingEntity source) {
            return child != null && !child.evaluate(game, mob, target, source);
        }
    }

    /** 怪物手持物品 PDC {@code maggoteers:id} 匹配。 */
    record HoldItemPdc(String pdcValue) implements MobCondition {
        @Override public boolean evaluate(MaggoteersGame game, LivingEntity mob,
                                          LivingEntity target, LivingEntity source) {
            if (mob == null) {
                return false;
            }
            ItemStack held = mob.getEquipment() == null ? null : mob.getEquipment().getItemInMainHand();
            String id = held == null || held.getItemMeta() == null ? null
                    : held.getItemMeta().getPersistentDataContainer()
                            .get(io.mczju.maggoteers.item.RunItemTags.ID_KEY, PersistentDataType.STRING);
            return pdcValue != null && pdcValue.equals(id);
        }
    }

    /** 怪物位置半径内存在存活玩家。 */
    record PlayerInRadius(double radius) implements MobCondition {
        @Override public boolean evaluate(MaggoteersGame game, LivingEntity mob,
                                          LivingEntity target, LivingEntity source) {
            if (mob == null || mob.getWorld() == null) {
                return false;
            }
            double r = Math.max(0.0, radius);
            double rsq = r * r;
            for (Player pl : mob.getWorld().getPlayers()) {
                if (!pl.isValid() || pl.isDead()) {
                    continue;
                }
                if (pl.getLocation().distanceSquared(mob.getLocation()) <= rsq) {
                    return true;
                }
            }
            return false;
        }
    }
}
```

`MobSkillSpecParser.java`：

```java
package io.mczju.maggoteers.mob;

import io.mczju.maggoteers.effect.EffectContext;
import io.mczju.maggoteers.effect.EffectKey;
import io.mczju.maggoteers.effect.EffectKeys;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** 解析 mob_skills.yml 的单条技能（Map→MobSkillSpec）。纯逻辑，可单测。 */
public final class MobSkillSpecParser {

    private MobSkillSpecParser() {}

    public static Optional<MobSkillSpec> parse(Object raw) {
        if (!(raw instanceof Map<?, ?> m)) {
            return Optional.empty();
        }
        String id = m.get("id") == null ? "" : String.valueOf(m.get("id")).trim();
        if (id.isBlank()) {
            return Optional.empty();
        }
        MobTrigger trigger = enumOf(m.get("trigger"), MobTrigger.class);
        if (trigger == null) {
            return Optional.empty();
        }
        if (trigger == MobTrigger.COUNTER && m.get("counter") == null) {
            return Optional.empty();                       // counter 触发必须有 counter 块
        }
        int cooldownSec = m.get("cooldown_sec") instanceof Number n ? n.intValue() : 0;
        if (trigger == MobTrigger.TICK && cooldownSec <= 0) {
            return Optional.empty();                       // 光环类必须有冷却，否则每 tick 触发
        }
        boolean invisible = Boolean.parseBoolean(String.valueOf(m.get("invisible")));
        MobCounterSpec counter = parseCounter(m.get("counter"));
        MobCondition condition = parseCondition(m.get("condition"));
        List<MobEffectSpec> effects = parseEffects(m.get("effects"));
        return Optional.of(new MobSkillSpec(id, trigger, condition, invisible, counter, cooldownSec, effects));
    }

    private static MobCounterSpec parseCounter(Object raw) {
        if (!(raw instanceof Map<?, ?> m)) {
            return null;
        }
        String id = m.get("id") == null ? "" : String.valueOf(m.get("id")).trim();
        if (id.isBlank()) {
            return null;
        }
        MobTrigger count = enumOf(m.get("count"), MobTrigger.class);
        if (count == null) {
            return null;
        }
        int amount = m.get("amount") instanceof Number n ? n.intValue() : 1;
        MobTrigger resetOn = enumOf(m.get("reset"), MobTrigger.class);
        MobCondition condition = parseCondition(m.get("condition"));
        return new MobCounterSpec(id, count, amount, resetOn, condition);
    }

    private static List<MobEffectSpec> parseEffects(Object raw) {
        List<MobEffectSpec> out = new ArrayList<>();
        if (!(raw instanceof List<?> list)) {
            return out;
        }
        for (Object o : list) {
            if (!(o instanceof Map<?, ?> em)) {
                continue;
            }
            MobEffect effect = enumOf(em.get("effect"), MobEffect.class);
            if (effect == null) {
                continue;
            }
            out.add(new MobEffectSpec(effect, parseEffectParams(effect, em)));
        }
        return List.copyOf(out);
    }

    private static EffectContext parseEffectParams(MobEffect effect, Map<?, ?> em) {
        EffectContext ctx = new EffectContext();
        // target 是 effects 条目的顶层键：self / target / area（存 EffectKeys.TARGETS）
        Object targetRaw = em.get("target");
        if (targetRaw != null) {
            ctx.put(EffectKeys.TARGETS, String.valueOf(targetRaw).toLowerCase(Locale.ROOT));
        }
        // 参数字段：优先 params 子块，回落顶层（mob_skills.yml 两种写法皆可）
        Map<?, ?> pm = em.get("params") instanceof Map<?, ?> p ? p : em;
        for (var e : pm.entrySet()) {
            String k = String.valueOf(e.getKey());
            if ("effect".equals(k) || "target".equals(k)) {
                continue;
            }
            Object v = e.getValue();
            switch (k) {
                case "amount", "damage" -> putNum(ctx, k.equals("damage")
                        ? EffectKeys.DAMAGE : EffectKeys.AMOUNT, v);
                case "radius" -> putNum(ctx, EffectKeys.RADIUS, v);
                case "attr" -> putAttr(ctx, v);
                case "op" -> ctx.put(EffectKeys.OP, String.valueOf(v).toUpperCase(Locale.ROOT));
                case "value" -> putNum(ctx, EffectKeys.VALUE, v);
                case "potion" -> ctx.put(EffectKeys.POTION_NAME, String.valueOf(v));
                case "amp" -> putInt(ctx, EffectKeys.AMP, v);
                case "duration_ticks" -> putInt(ctx, EffectKeys.DURATION_TICKS, v);
                case "entity" -> ctx.put(EffectKeys.ENTITY, String.valueOf(v));
                case "count" -> putInt(ctx, EffectKeys.COUNT, v);
                case "homing_target" -> ctx.put(EffectKeys.HOMING_TARGET, String.valueOf(v));
                case "projectile_speed" -> putNum(ctx, EffectKeys.PROJECTILE_SPEED, v);
                default -> { }
            }
        }
        return ctx;
    }

    private static void putNum(EffectContext ctx, EffectKey<Double> key, Object v) {
        if (v instanceof Number n) {
            ctx.put(key, n.doubleValue());
        }
    }

    private static void putInt(EffectContext ctx, EffectKey<Integer> key, Object v) {
        if (v instanceof Number n) {
            ctx.put(key, n.intValue());
        }
    }

    private static void putAttr(EffectContext ctx, Object v) {
        String name = String.valueOf(v);
        ctx.put(EffectKeys.ATTR_NAME, name);
    }

    private static MobCondition parseCondition(Object raw) {
        if (!(raw instanceof Map<?, ?> m)) {
            return null;
        }
        if (m.containsKey("and")) {
            return new MobCondition.And(parseChildren(m.get("and")));
        }
        if (m.containsKey("or")) {
            return new MobCondition.Or(parseChildren(m.get("or")));
        }
        if (m.containsKey("not")) {
            MobCondition child = parseCondition(m.get("not"));
            return child == null ? null : new MobCondition.Not(child);
        }
        if (m.containsKey("hold_item_pdc")) {
            return new MobCondition.HoldItemPdc(String.valueOf(m.get("hold_item_pdc")));
        }
        if (m.containsKey("player_in_radius")) {
            double r = m.get("player_in_radius") instanceof Number n ? n.doubleValue() : 0.0;
            return new MobCondition.PlayerInRadius(r);
        }
        return null;
    }

    private static List<MobCondition> parseChildren(Object raw) {
        List<MobCondition> out = new ArrayList<>();
        if (!(raw instanceof List<?> list)) {
            return out;
        }
        for (Object o : list) {
            MobCondition c = parseCondition(o);
            if (c != null) {
                out.add(c);
            }
        }
        return out;
    }

    private static <T extends Enum<T>> T enumOf(Object raw, Class<T> type) {
        if (raw == null) {
            return null;
        }
        try {
            return Enum.valueOf(type, String.valueOf(raw).trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
```

> `EffectKeys.HOMING_TARGET` 键**在本 Task 落实**（Files 已列 `Modify: EffectKeys.java`）。按既有键的写法追加：
>
> ```java
>     /** SUMMON 弹道 homing 目标预设：nearest_enemy | nearest_player | attack_target | damage_source | killer | none/缺省=不追踪。 */
>     public static final EffectKey<String> HOMING_TARGET =
>             new EffectKey<>(String.class, "homing_target");
> ```
>
> Task 7 只消费该键，**不再**重复定义。`parseEffectParams` 里 `case "homing_target"` 已引用它（见下），本 Task 实现该 parser 时该键必须在场。

`MobSkillRegistry.java`：

```java
package io.mczju.maggoteers.mob;

import io.mczju.maggoteers.MaggoteersPlugin;
import io.mczju.maggoteers.util.ItemYaml;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

/** 加载 mob_skills.yml（技能 id → MobSkillSpec）。定义唯一处：怪物技能只在 mob_skills.yml。 */
public final class MobSkillRegistry {

    private static final Logger LOG = Logger.getLogger("Maggoteers");
    private static final Map<String, MobSkillSpec> BY_ID = new HashMap<>();

    private MobSkillRegistry() {}

    public static void load(MaggoteersPlugin plugin) {
        BY_ID.clear();
        File file = new File(plugin.getDataFolder(), "mob_skills.yml");
        if (!file.isFile()) {
            plugin.saveResource("mob_skills.yml", false);
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection skills = yaml.getConfigurationSection("skills");
        if (skills == null) {
            LOG.warning("mob_skills.yml 缺 skills 段");
            return;
        }
        int loaded = 0;
        for (String key : skills.getKeys(false)) {
            Object raw = skills.get(key);
            if (raw instanceof Map<?, ?> m) {
                java.util.Map<Object, Object> copy = new java.util.HashMap<>(m);
                if (!copy.containsKey("id")) {
                    copy.put("id", key);
                }
                MobSkillSpecParser.parse(copy).ifPresentOrElse(
                        spec -> { BY_ID.put(key, spec); loaded++; },
                        () -> LOG.warning("mob_skills.yml 技能 " + key + " 解析失败，跳过"));
            } else if (raw instanceof ConfigurationSection sec) {
                // ConfigurationSection → Map
                Map<String, Object> flat = new HashMap<>();
                for (String k : sec.getKeys(false)) {
                    flat.put(k, sec.get(k));
                }
                if (!flat.containsKey("id")) {
                    flat.put("id", key);
                }
                MobSkillSpecParser.parse(flat).ifPresentOrElse(
                        spec -> { BY_ID.put(key, spec); loaded++; },
                        () -> LOG.warning("mob_skills.yml 技能 " + key + " 解析失败，跳过"));
            }
        }
        LOG.info("MobSkillRegistry: loaded " + loaded + " skills");
    }

    public static Optional<MobSkillSpec> get(String id) {
        return id == null ? Optional.empty() : Optional.ofNullable(BY_ID.get(id));
    }

    public static Map<String, MobSkillSpec> all() {
        return Map.copyOf(BY_ID);
    }
}
```

- [ ] **Step 4: 运行确认通过**

Run: `export JAVA_HOME=/home/chalcanthite/.local/jdk-25 && export PATH="$JAVA_HOME/bin:$PATH" && mvn -o -q test -Dtest=MobSkillSpecParserTest`
Expected: PASS

- [ ] **Step 5: 写 mob_skills.yml（迁移 affixes + IM 技能）**

`src/main/resources/mob_skills.yml`（`affixes.yml` 的 12 词缀 → 同名 SPAWN 技能；IM 引用的技能 id → 同名技能。**技能 id 与 waves.yml 引用零映射**）：

```yaml
skills:
  # ---- 原 affixes.yml 12 词缀迁移（trigger: spawn，出生时施加）----
  invisible:
    trigger: spawn
    invisible: true
  plastic:
    trigger: spawn
    effects:
      - { effect: potion, target: self, potion: resistance, amp: 4, duration_ticks: 600 }
  cloaked:
    trigger: spawn
    effects:
      - { effect: potion, target: self, potion: invisibility, amp: 0, duration_ticks: 600 }
  antifreeze:
    trigger: spawn
    effects:
      - { effect: potion, target: self, potion: slowness, amp: 255, duration_ticks: 0 }
  mechanical:
    trigger: spawn
    effects:
      - { effect: potion, target: self, potion: wither, amp: 255, duration_ticks: 0 }
  fireresistance:
    trigger: spawn
    effects:
      - { effect: potion, target: self, potion: fire_resistance, amp: 255, duration_ticks: 0 }
  withering:
    trigger: spawn
    effects:
      - { effect: potion, target: self, potion: wither, amp: 255, duration_ticks: 99999 }
  shield:
    trigger: spawn
    effects:
      - { effect: potion, target: self, potion: absorption, amp: 19, duration_ticks: 99999 }
  exoskeleton:
    trigger: spawn
    effects:
      - { effect: potion, target: self, potion: resistance, amp: 1, duration_ticks: 99999 }
  infected:
    trigger: spawn            # 纯装饰：无 effects
  blinding:
    trigger: spawn
    effects:
      - { effect: potion, target: self, potion: blindness, amp: 0, duration_ticks: 999999 }
  eternal_curse:
    trigger: spawn
    effects:
      - { effect: potion, target: self, potion: slowness, amp: 255, duration_ticks: 10 }

  # ---- IM 战斗技能迁移（waves.yml infernal.affixes 引用同名 id）----
  lifesteal:
    trigger: attack
    cooldown_sec: 1
    effects:
      - { effect: health, target: self, amount: 2 }
  weakness:
    trigger: attack
    cooldown_sec: 2
    effects:
      - { effect: potion, target: target, potion: weakness, amp: 0, duration_ticks: 100 }
  quicksand:
    trigger: damage_taken
    cooldown_sec: 2
    effects:
      - { effect: potion, target: target, potion: slowness, amp: 0, duration_ticks: 80 }
  poisonous:
    trigger: attack
    cooldown_sec: 3
    effects:
      - { effect: potion, target: target, potion: poison, amp: 0, duration_ticks: 100 }
  sprint:
    trigger: attack
    cooldown_sec: 4
    effects:
      - { effect: attribute, target: self, attr: movement_speed, op: multiply, value: 0.4, duration_ticks: 100 }
  berserk:
    trigger: damage_taken
    cooldown_sec: 5
    effects:
      - { effect: attribute, target: self, attr: attack_damage, op: multiply, value: 0.5, duration_ticks: 120 }
  archer:
    trigger: tick
    cooldown_sec: 6
    condition: { player_in_radius: 24 }
    effects:
      - { effect: summon, target: self, entity: arrow, count: 1, homing_target: nearest_player,
          projectile_speed: 1.6 }
  tosser:
    trigger: tick
    cooldown_sec: 7
    condition: { player_in_radius: 24 }
    effects:
      - { effect: summon, target: self, entity: tnt, count: 1, projectile_speed: 1.2 }
  storm:
    trigger: tick
    cooldown_sec: 8
    condition: { player_in_radius: 18 }
    effects:
      - { effect: health, target: area, radius: 6, damage: 6 }   # 风暴：目标区域伤害（等效闪电落点）
  molten:
    trigger: tick
    cooldown_sec: 7
    condition: { player_in_radius: 26 }
    effects:
      - { effect: summon, target: self, entity: fireball, count: 1, homing_target: nearest_player,
          projectile_speed: 1.4 }
  necromancer:
    trigger: tick
    cooldown_sec: 9
    condition: { player_in_radius: 20 }
    effects:
      - { effect: summon, target: area, radius: 8, entity: zombie, count: 2 }
  ghastly:
    trigger: tick
    cooldown_sec: 10
    condition: { player_in_radius: 28 }
    effects:
      - { effect: summon, target: area, radius: 10, entity: ghast, count: 1 }
```

> ⚠️ **迁移语义说明**：原 affixes.yml 的 `potions:` 全部是**施加给怪物自身**的（词缀强化层），故迁移为 `trigger: spawn + target: self`。IM 技能的 archer/molten 等"射击追踪"由 `homing_target: nearest_player` 在 Task 5 的 `MobProjectileService` 实现；`storm` 以区域伤害近似雷电（如需真实 lightning 可在 `MobEffectExecutor` 扩展，不阻塞本计划）。`sprint` 用 `op: multiply, value: 0.4` 为速度加成（乘算 +40%）。

- [ ] **Step 6: 全量回归 + Commit**

Run: `export JAVA_HOME=/home/chalcanthite/.local/jdk-25 && export PATH="$JAVA_HOME/bin:$PATH" && mvn -o -q test`
Expected: 全部通过（MobSkillRegistry 为纯静态加载，不影响既有测试；MobSkillSpecParserTest ~8 用例新增）

```bash
git add src/main/java/io/mczju/maggoteers/mob/MobTrigger.java \
        src/main/java/io/mczju/maggoteers/mob/MobEffect.java \
        src/main/java/io/mczju/maggoteers/mob/MobEffectSpec.java \
        src/main/java/io/mczju/maggoteers/mob/MobCounterSpec.java \
        src/main/java/io/mczju/maggoteers/mob/MobSkillSpec.java \
        src/main/java/io/mczju/maggoteers/mob/MobCondition.java \
        src/main/java/io/mczju/maggoteers/mob/MobSkillSpecParser.java \
        src/main/java/io/mczju/maggoteers/mob/MobSkillRegistry.java \
        src/main/resources/mob_skills.yml \
        src/test/java/io/mczju/maggoteers/mob/MobSkillSpecParserTest.java \
        src/main/java/io/mczju/maggoteers/effect/EffectKeys.java
git commit -m "feat(mob): mob skill model + parser + registry + mob_skills.yml (affix/IM migration)"
```

---

### Task 5: 模型手术（SpawnStep/配置模型 删 affix 换 skills，删 IM/Affix 体系）

> **本任务是纯机械重构**：数据模型把 `affixes/potions/infernal` 三个字段替换为 `skills`（技能 id 列表，透传不解析——解析在 Task 4 的 `MobSkillSpecParser`，执行在 Task 6）。`skills` 数据先就位，行为在 Task 6 接入。**中间态（Task 5 完成后、Task 6 完成前）**：waves.yml 里 affix/infernal 的效果暂时不生效（affixes.yml/IM 已删），skills 只是透传数据——两个 Task 应连续执行，不在中间态停留。
> `MobYamlParser.parseSkills` 做 **fallback**（`skills`→`affixes`→`infernal.affixes`）：迁移脚本（Task 8）执行前，旧 waves.yml 仍能加载，affix id 作为 skill id 直传（mob_skills.yml 已含同名技能，Task 4 迁移）。

**Files:**
- Modify: `src/main/java/io/mczju/maggoteers/wave/SpawnStep.java`（18→16 字段）
- Modify: `src/main/java/io/mczju/maggoteers/wave/PassengerSpawn.java`（15→13 字段）
- Modify: `src/main/java/io/mczju/maggoteers/wave/DeathSpawn.java`（14→12 字段）
- Modify: `src/main/java/io/mczju/maggoteers/wave/WaveSpec.java:60`（expand 复制用新 full ctor）
- Modify: `src/main/java/io/mczju/maggoteers/config/StepCfg.java`（13→12 字段）
- Modify: `src/main/java/io/mczju/maggoteers/config/PassengerCfg.java` / `config/DeathSpawnCfg.java`
- Modify: `src/main/java/io/mczju/maggoteers/config/MobYamlParser.java`（parseAffixes→parseSkills fallback，删 parseInfernal）
- Modify: `src/main/java/io/mczju/maggoteers/config/WavesConfig.java:68-77,94-103`（校验 + parseStrategy）
- Modify: `src/main/java/io/mczju/maggoteers/plan/Compose.java`（2-arg）
- Modify: `src/main/java/io/mczju/maggoteers/plan/RunPlanner.java`（plan 签名删 AffixService；skills 透传）
- Modify: `src/main/java/io/mczju/maggoteers/mob/MobFactory.java`（configureSpawnedLiving 删 3 参 + 删 applyAffix*/mechanize；spawnDebugMob 改 skillIds）
- Modify: `src/main/java/io/mczju/maggoteers/MaggoteersPlugin.java:42-43,92`
- Modify: `src/main/java/io/mczju/maggoteers/command/MaggoteersCommand.java:204-222`
- Modify: `src/main/java/io/mczju/maggoteers/listener/MobDeathListener.java`（删 beforeInfernalDeath）
- Modify: `src/main/java/io/mczju/maggoteers/wave/WaveEngine.java`（删 4 处 `InfernalMobsBridge.unregisterManaged` line 118/134/147/161 + import）
- Delete: `config/Affix.java`、`config/AffixService.java`、`config/InfernalCfg.java`、`integration/InfernalMobsBridge.java`、`src/main/resources/affixes.yml`、`config/InfernalCfgTest.java`、`integration/InfernalMobsBridgeTest.java`、`config/RetiredAffixConfigTest.java`
- Test: `plan/ComposeTest.java`、`plan/RunPlannerTest.java`、`plan/StrongWaveRollTest.java`、`config/MobYamlParserTest.java`、`wave/WaveSpecTest.java`、`mob/MountControllerResolverTest.java`、`listener/MobDeathListenerTest.java`

**Interfaces:**
- Consumes: `MobSkillRegistry`（Task 4，WavesConfig 校验用）；`MobSkillSpec`（Task 4）。
- Produces: 全部记录类新签名（下面各 Step 给出完整新代码）；`MobYamlParser.parseSkills(Map, String)→List<String>`（fallback）；`Compose.compose(CoeffCfg, Scaling.Scaling)→MobScale`（2-arg）；`RunPlanner.plan(long, int, WaveDefinitions, MapLibrary, ScalingConfig, RunConfig)→List<ActPlan>`（6 参，删 AffixService）；`MobFactory.spawnDebugMob(Location, EntityType, double, double, double, List<String> skillIds)`。
  - **SpawnStep 新 ctor 集（删 affixes/potions/infernal 加 skills，→16 字段）**：
    - A′ full 16：`(point,type,count,hp,dmg,speed,scale,follow,delay,skills,equipment,onDeath,passengers,repeat,name,bossBar)`
    - B′ 13：`(point,type,count,hp,dmg,speed,scale,follow,delay,skills,equipment,onDeath,passengers)`
    - C′ 14：`(point,type,count,hp,dmg,speed,scale,follow,delay,skills,equipment,onDeath,passengers,repeat)`
    - D′ 9：`(point,type,count,hp,dmg,speed,delay,skills,passengers)`
    - E′ 8：`(point,type,count,hp,dmg,speed,delay,skills)`
  - **PassengerSpawn 新 ctor（→13 字段）**：full 13 `(type,hp,dmg,speed,scale,follow,skills,equipment,onDeath,passengers,name,bossBar,controller)`；5 `(type,hp,dmg,speed,skills)`；10 `(type,hp,dmg,speed,scale,follow,skills,equipment,onDeath,passengers)`
  - **DeathSpawn 新 ctor（→12 字段）**：full 12 `(type,hp,dmg,speed,scale,follow,skills,equipment,passengers,onDeath,name,bossBar)`；10 `(type,hp,dmg,speed,scale,follow,skills,equipment,passengers,onDeath)`
  - **StepCfg 新 ctor（→12 字段）**：full 12 `(point,type,count,coeff,delaySec,skills,equipment,onDeath,passengers,repeat,name,bossBar)`；6 `(point,type,count,coeff,delaySec,skills)`；9 `(point,type,count,coeff,delaySec,skills,equipment,onDeath,passengers)`；10 `(point,type,count,coeff,delaySec,skills,equipment,onDeath,passengers,repeat)`
  - **PassengerCfg 新 ctor（→10 字段）**：full 10 `(type,count,coeff,skills,equipment,onDeath,passengers,name,bossBar,controller)`；4 `(type,count,coeff,skills)`；7 `(type,count,coeff,skills,equipment,onDeath,passengers)`
  - **DeathSpawnCfg 新 ctor（→9 字段）**：full 9 `(type,count,coeff,skills,equipment,passengers,onDeath,name,bossBar)`；7 `(type,count,coeff,skills,equipment,passengers,onDeath)`

- [ ] **Step 1: 更新测试（先行红）**

**1a. `ComposeTest.java`** — 删 `import io.mczju.maggoteers.config.Affix;`；`multipleAffixesMultiply` 整个删除；`coeffOnlyTimesScaling` 改为：

```java
    @Test
    void coeffOnlyTimesScaling() {
        Compose.MobScale m = Compose.compose(new CoeffCfg(2.0, 1.5, 1.0), SNAP);
        assertEquals(2.0 * 1.6, m.hp(), 1e-9);
        assertEquals(1.5 * 1.3, m.dmg(), 1e-9);
        assertEquals(1.0 * 1.0, m.speed(), 1e-9);
    }
```

**1b. `MobYamlParserTest.java`** — 删 3 个 Infernal 测试（`parsesInfernalBlock`/`missingBlockReturnsNone`/`invalidLevelFailsWithContext`），加 fallback 测试：

```java
    @Test
    void parsesSkillsList() {
        assertEquals(List.of("plastic", "berserk"),
                MobYamlParser.parseSkills(Map.of("skills", List.of("plastic", "berserk")), "t"));
    }

    @Test
    void fallsBackToAffixes() {
        assertEquals(List.of("armored"),
                MobYamlParser.parseSkills(Map.of("affixes", List.of("armored")), "t"));
    }

    @Test
    void fallsBackToInfernalAffixes() {
        assertEquals(List.of("poisonous", "sprint"),
                MobYamlParser.parseSkills(Map.of("infernal",
                        Map.of("level", 4, "affixes", List.of("poisonous", "sprint"))), "t"));
    }

    @Test
    void skillsWinsOverAffixes() {
        assertEquals(List.of("plastic"),
                MobYamlParser.parseSkills(Map.of("skills", List.of("plastic"),
                        "affixes", List.of("armored")), "t"));
    }
```

**1c. `WaveSpecTest.java`** — 改全部 SpawnStep 构造：

```java
    private static SpawnStep step(int delay) {
        return new SpawnStep(new Vec3(0, 64, 0), EntityType.ZOMBIE, 5,
                1.0, 1.0, 1.0, delay, List.of());
    }
```
（E′ 8-arg；其余测试方法不变）

`expandPreservesPassengers` 与 `expandPreservesPassengersAcrossRepeat`：

```java
        PassengerSpawn child = new PassengerSpawn(
                EntityType.SKELETON, 1.0, 1.0, 1.0, List.of());
        SpawnStep mount = new SpawnStep(
                new Vec3(0, 64, 0), EntityType.STRIDER, 1,
                1.0, 1.0, 1.0, 0, List.of(), List.of(child));
```
（child 用 5-arg；mount 用 D′ 9-arg；第二个测试把 EntityType 换成 ZOMBIE_HORSE）

`stepWithRepeat`/`atPoint`（C′ 14-arg，删 InfernalCfg.NONE）：

```java
    private static SpawnStep stepWithRepeat(int delayTicks, int stepRepeat) {
        return new SpawnStep(new Vec3(0, 64, 0), EntityType.CREEPER, 1,
                1.0, 1.0, 1.0, 1.0, 1.0,
                delayTicks, List.of(), List.of(), List.of(), List.of(), stepRepeat);
    }

    private static SpawnStep atPoint(Vec3 point, int delayTicks, int stepRepeat) {
        return new SpawnStep(point, EntityType.ZOMBIE, 1,
                1.0, 1.0, 1.0, 1.0, 1.0,
                delayTicks, List.of(), List.of(), List.of(), List.of(), stepRepeat);
    }
```

**1d. `MountControllerResolverTest.java`** — `node()` 改 full 13（删 InfernalCfg.NONE）+ 删 import：

```java
    private static PassengerSpawn node(boolean controller, PassengerSpawn... nested) {
        return new PassengerSpawn(EntityType.ZOMBIE, 1.0, 1.0, 1.0, 1.0, 1.0,
                List.of(), List.of(), List.of(), List.of(nested), null, false, controller);
    }
```
（删 `import io.mczju.maggoteers.config.InfernalCfg;`）

**1e. `RunPlannerTest.java`** — 删 `affixes()` helper（line 74-77）与相关 import；**所有** `RunPlanner.plan(...)` 调用删 `affixes()` 参数（line 88-89/109/116/139/147/155/173/202/223/255-257/290-292）；`simpleDefs()` 里 StepCfg 第 6 参从 `List.of("armored")` 改为 `List.of("armored")`（语义变 skills，id 同名）——**保持原样即可**（6-arg ctor 第 6 参类型仍 `List<String>`）；`bossWaveHasAffixComposed` 重写为 `bossWaveCarriesSkills`：

```java
    @Test
    void bossWaveCarriesSkills() {
        List<ActPlan> acts = RunPlanner.plan(1L, 1, simpleDefs(), oneMapLib(), scaling(), cfg());
        var bossStep = acts.get(0).waves().get(5).expand().get(0);
        assertEquals(8.0, bossStep.hpMult(), 1e-9);   // 8 × scaling.mobHp(1)=8，无 affix 乘法
        assertTrue(bossStep.skills().contains("armored"));
    }
```
`carriesIndependentInfernalConfigForAllSpawnKinds` 重写为 `carriesIndependentSkillsForAllSpawnKinds`：

```java
    @Test
    void carriesIndependentSkillsForAllSpawnKinds() {
        PassengerCfg passenger = new PassengerCfg(
                EntityType.SKELETON, 1, new CoeffCfg(1, 1, 1), List.of("archer"));
        DeathSpawnCfg death = new DeathSpawnCfg(
                EntityType.SILVERFISH, 1, new CoeffCfg(1, 1, 1), List.of("berserk"));
        StepCfg root = new StepCfg(
                "1", EntityType.ZOMBIE, 1, new CoeffCfg(1, 1, 1), 0, List.of("sprint"),
                List.of(), List.of(death), List.of(passenger));
        SpawnStrategyCfg im = new SpawnStrategyCfg("w_im", "w_im", List.of(root), 1, List.of());

        Map<String, SpawnStrategyCfg> strategies = new HashMap<>(simpleDefs().strategies());
        strategies.put("w_im", im);
        Map<String, Map<String, List<WavesConfig.PoolEntry>>> pools = new HashMap<>();
        for (String act : List.of("act1", "act2", "act3")) {
            pools.put(act, Map.of(
                    "weak", List.of(new WavesConfig.PoolEntry("w_im", 1)),
                    "strong", List.of(new WavesConfig.PoolEntry("s_a", 1)),
                    "boss", List.of(new WavesConfig.PoolEntry("b_boss", 1))));
        }

        SpawnStep step = RunPlanner.plan(
                1L, 1, new WaveDefinitions(strategies, pools), oneMapLib(), scaling(), cfg(1, 1))
                .get(0).waves().get(0).steps().get(0);

        assertEquals(List.of("sprint"), step.skills());
        assertEquals(List.of("archer"), step.passengers().get(0).skills());
        assertEquals(List.of("berserk"), step.onDeath().get(0).skills());
    }
```
`deathSpawnCarriesPassengers` 里 3 处 `new PassengerCfg(...)/new DeathSpawnCfg(...)/new StepCfg(...)` 的 `InfernalCfg.NONE` 参数删除（PassengerCfg 用 7-arg、DeathSpawnCfg 用 7-arg、StepCfg 用 9-arg，第 6 参 `List.of()` 为 skills）：

```java
        PassengerCfg rider = new PassengerCfg(
                EntityType.VINDICATOR, 1, new CoeffCfg(1, 1, 1), List.of());
        DeathSpawnCfg camel = new DeathSpawnCfg(
                EntityType.CAMEL, 1, new CoeffCfg(1, 1, 1), List.of());
        StepCfg root = new StepCfg(
                "1", EntityType.SLIME, 1, new CoeffCfg(1, 1, 1), 0, List.of(),
                List.of(), List.of(camel), List.of());
```

**1f. `StrongWaveRollTest.java`** — 删 `affixes()`/`loadBundledAffixes()` helper 与 `AffixService/Affix` import；所有 `plan(...)` 调用删 `affixes()`；`parseStrategy` 里 affix 解析改为调 `MobYamlParser.parseSkills`：

```java
            List<String> skills = MobYamlParser.parseSkills(m, id);
            steps.add(new StepCfg(point, type, count, new CoeffCfg(hp, dmg, spd), delay, skills));
```

**1g. `MobDeathListenerTest.java`** — 删 `unregisterHandlerRunsBeforeInfernalNormalListener` 测试（`beforeInfernalDeath` 方法被删）。保留 `finalDropCleanupRunsAfterInfernalNormalListener`。

- [ ] **Step 2: 运行确认失败（编译错即红）**

Run: `export JAVA_HOME=/home/chalcanthite/.local/jdk-25 && export PATH="$JAVA_HOME/bin:$PATH" && mvn -o -q test`
Expected: 编译错误（新 ctor 签名未实现 / Compose 仍 3-arg / AffixService 仍被引用）

- [ ] **Step 3: 改数据模型（record ctor 手术）**

**`SpawnStep.java`** 全量替换为：

```java
package io.mczju.maggoteers.wave;

import org.bukkit.entity.EntityType;

import java.util.List;

/**
 * 一条刷怪指令，数值已"完全解析"（coeff×scaling）。主线程生成直接套用，零随机/零查询。
 * <p>{@code skills} 为技能 id 列表（mob_skills.yml，Task 4/6 解析执行）。
 * <p>{@code delayTicks} 在 {@link WaveSpec} 未展开前为<strong>步前等待</strong>（秒×20）；经 {@link WaveSpec#expand()} 后为距本波开始的绝对 tick。
 * <p>{@code repeat} 为该 step 自身重复次数（未展开前）；{@link WaveSpec#expand()} 后恒为 1。
 */
public record SpawnStep(Vec3 point, EntityType type, int count,
                        double hpMult, double dmgMult, double speedMult,
                        double scaleMult, double followRangeMult,
                        int delayTicks, List<String> skills,
                        List<MobEquipment> equipment, List<DeathSpawn> onDeath,
                        List<PassengerSpawn> passengers, int repeat,
                        String name, boolean bossBar) {
    /** full 之外最常用：无 name/bossBar（repeat 1）。 */
    public SpawnStep(Vec3 point, EntityType type, int count,
                     double hpMult, double dmgMult, double speedMult,
                     double scaleMult, double followRangeMult,
                     int delayTicks, List<String> skills,
                     List<MobEquipment> equipment, List<DeathSpawn> onDeath,
                     List<PassengerSpawn> passengers) {
        this(point, type, count, hpMult, dmgMult, speedMult, scaleMult, followRangeMult,
                delayTicks, skills, equipment, onDeath, passengers, 1, null, false);
    }

    public SpawnStep(Vec3 point, EntityType type, int count,
                     double hpMult, double dmgMult, double speedMult,
                     double scaleMult, double followRangeMult,
                     int delayTicks, List<String> skills,
                     List<MobEquipment> equipment, List<DeathSpawn> onDeath,
                     List<PassengerSpawn> passengers, int repeat) {
        this(point, type, count, hpMult, dmgMult, speedMult, scaleMult, followRangeMult,
                delayTicks, skills, equipment, onDeath, passengers, repeat, null, false);
    }

    public SpawnStep(Vec3 point, EntityType type, int count,
                     double hpMult, double dmgMult, double speedMult,
                     int delayTicks, List<String> skills,
                     List<PassengerSpawn> passengers) {
        this(point, type, count, hpMult, dmgMult, speedMult, 1.0, 1.0,
                delayTicks, skills, List.of(), List.of(), passengers, 1, null, false);
    }

    public SpawnStep(Vec3 point, EntityType type, int count,
                     double hpMult, double dmgMult, double speedMult,
                     int delayTicks, List<String> skills) {
        this(point, type, count, hpMult, dmgMult, speedMult, delayTicks, skills, List.of());
    }

    public SpawnStep {
        skills = skills == null ? List.of() : List.copyOf(skills);
        equipment = equipment == null ? List.of() : List.copyOf(equipment);
        onDeath = onDeath == null ? List.of() : List.copyOf(onDeath);
        passengers = passengers == null ? List.of() : List.copyOf(passengers);
        repeat = Math.max(1, repeat);
        if (name != null) {
            name = name.trim();
            if (name.isEmpty()) name = null;
        }
    }
}
```

**`PassengerSpawn.java`** 全量替换（删 `import InfernalCfg`）：

```java
package io.mczju.maggoteers.wave;

import org.bukkit.entity.EntityType;

import java.util.List;

/** 已解析的乘客怪树节点（coeff×scaling）。skills 为 mob_skills.yml 技能 id 列表。 */
public record PassengerSpawn(EntityType type,
                             double hpMult, double dmgMult, double speedMult,
                             double scaleMult, double followRangeMult,
                             List<String> skills,
                             List<MobEquipment> equipment, List<DeathSpawn> onDeath,
                             List<PassengerSpawn> passengers,
                             String name, boolean bossBar, boolean controller) {
    public PassengerSpawn {
        skills = skills == null ? List.of() : List.copyOf(skills);
        equipment = equipment == null ? List.of() : List.copyOf(equipment);
        onDeath = onDeath == null ? List.of() : List.copyOf(onDeath);
        passengers = passengers == null ? List.of() : List.copyOf(passengers);
        if (name != null) {
            name = name.trim();
            if (name.isEmpty()) name = null;
        }
    }

    /** 无嵌套乘客（兼容旧构造）。 */
    public PassengerSpawn(EntityType type, double hpMult, double dmgMult, double speedMult,
                          List<String> skills) {
        this(type, hpMult, dmgMult, speedMult, 1.0, 1.0,
                skills, List.of(), List.of(), List.of(), null, false, false);
    }

    public PassengerSpawn(EntityType type,
                          double hpMult, double dmgMult, double speedMult,
                          double scaleMult, double followRangeMult,
                          List<String> skills,
                          List<MobEquipment> equipment, List<DeathSpawn> onDeath,
                          List<PassengerSpawn> passengers) {
        this(type, hpMult, dmgMult, speedMult, scaleMult, followRangeMult,
                skills, equipment, onDeath, passengers, null, false, false);
    }
}
```

**`DeathSpawn.java`** 全量替换（删 `import InfernalCfg`）：

```java
package io.mczju.maggoteers.wave;

import org.bukkit.entity.EntityType;

import java.util.List;

public record DeathSpawn(EntityType type,
                         double hpMult, double dmgMult, double speedMult,
                         double scaleMult, double followRangeMult,
                         List<String> skills,
                         List<MobEquipment> equipment, List<PassengerSpawn> passengers,
                         List<DeathSpawn> onDeath,
                         String name, boolean bossBar) {
    public DeathSpawn {
        skills = skills == null ? List.of() : List.copyOf(skills);
        equipment = equipment == null ? List.of() : List.copyOf(equipment);
        passengers = passengers == null ? List.of() : List.copyOf(passengers);
        onDeath = onDeath == null ? List.of() : List.copyOf(onDeath);
        if (name != null) {
            name = name.trim();
            if (name.isEmpty()) name = null;
        }
    }

    public DeathSpawn(EntityType type,
                      double hpMult, double dmgMult, double speedMult,
                      double scaleMult, double followRangeMult,
                      List<String> skills,
                      List<MobEquipment> equipment, List<PassengerSpawn> passengers,
                      List<DeathSpawn> onDeath) {
        this(type, hpMult, dmgMult, speedMult, scaleMult, followRangeMult,
                skills, equipment, passengers, onDeath, null, false);
    }
}
```

**`StepCfg.java`** 全量替换（删 `import InfernalCfg`）：

```java
package io.mczju.maggoteers.config;

import io.mczju.maggoteers.wave.MobEquipment;
import org.bukkit.entity.EntityType;

import java.util.List;

/** waves.yml 一条 step（未解析）。skills 为技能 id 列表（RunPlanner 直传）。 */
public record StepCfg(String point, EntityType type, int count, CoeffCfg coeff,
                      int delaySec, List<String> skills,
                      List<MobEquipment> equipment, List<DeathSpawnCfg> onDeath,
                      List<PassengerCfg> passengers, int repeat,
                      String name, boolean bossBar) {
    public StepCfg(String point, EntityType type, int count, CoeffCfg coeff,
                   int delaySec, List<String> skills) {
        this(point, type, count, coeff, delaySec, skills,
                List.of(), List.of(), List.of(), 1, null, false);
    }

    /** 兼容旧调用：无 step 级 repeat / name / boss_bar 时默认。 */
    public StepCfg(String point, EntityType type, int count, CoeffCfg coeff,
                   int delaySec, List<String> skills,
                   List<MobEquipment> equipment, List<DeathSpawnCfg> onDeath,
                   List<PassengerCfg> passengers) {
        this(point, type, count, coeff, delaySec, skills,
                equipment, onDeath, passengers, 1, null, false);
    }

    public StepCfg(String point, EntityType type, int count, CoeffCfg coeff,
                   int delaySec, List<String> skills,
                   List<MobEquipment> equipment, List<DeathSpawnCfg> onDeath,
                   List<PassengerCfg> passengers, int repeat) {
        this(point, type, count, coeff, delaySec, skills,
                equipment, onDeath, passengers, repeat, null, false);
    }

    public StepCfg {
        skills = skills == null ? List.of() : List.copyOf(skills);
        equipment = equipment == null ? List.of() : List.copyOf(equipment);
        onDeath = onDeath == null ? List.of() : List.copyOf(onDeath);
        passengers = passengers == null ? List.of() : List.copyOf(passengers);
        repeat = Math.max(1, repeat);
        if (name != null) {
            name = name.trim();
            if (name.isEmpty()) name = null;
        }
    }
}
```

**`PassengerCfg.java`** 全量替换（删 `import InfernalCfg`；affixes→skills）：

```java
package io.mczju.maggoteers.config;

import io.mczju.maggoteers.wave.MobEquipment;
import org.bukkit.entity.EntityType;

import java.util.List;

/** waves.yml step.passengers[] 条目（可嵌套 passengers / on_death）。skills 为技能 id 列表。 */
public record PassengerCfg(EntityType type, int count, CoeffCfg coeff, List<String> skills,
                           List<MobEquipment> equipment, List<DeathSpawnCfg> onDeath,
                           List<PassengerCfg> passengers,
                           String name, boolean bossBar, boolean controller) {
    public PassengerCfg {
        skills = skills == null ? List.of() : List.copyOf(skills);
        equipment = equipment == null ? List.of() : List.copyOf(equipment);
        onDeath = onDeath == null ? List.of() : List.copyOf(onDeath);
        passengers = passengers == null ? List.of() : List.copyOf(passengers);
        if (count < 1) count = 1;
        if (name != null) {
            name = name.trim();
            if (name.isEmpty()) name = null;
        }
    }

    /** 扁平条目（无嵌套）。 */
    public PassengerCfg(EntityType type, int count, CoeffCfg coeff, List<String> skills) {
        this(type, count, coeff, skills, List.of(), List.of(), List.of(), null, false, false);
    }

    /** 兼容旧 8 参构造。 */
    public PassengerCfg(EntityType type, int count, CoeffCfg coeff, List<String> skills,
                        List<MobEquipment> equipment, List<DeathSpawnCfg> onDeath,
                        List<PassengerCfg> passengers) {
        this(type, count, coeff, skills, equipment, onDeath, passengers, null, false, false);
    }
}
```

**`DeathSpawnCfg.java`** 全量替换（删 `import InfernalCfg`；affixes→skills）：

```java
package io.mczju.maggoteers.config;

import io.mczju.maggoteers.wave.MobEquipment;
import org.bukkit.entity.EntityType;

import java.util.List;

public record DeathSpawnCfg(EntityType type, int count, CoeffCfg coeff, List<String> skills,
                            List<MobEquipment> equipment, List<PassengerCfg> passengers,
                            List<DeathSpawnCfg> onDeath,
                            String name, boolean bossBar) {
    public DeathSpawnCfg {
        skills = skills == null ? List.of() : List.copyOf(skills);
        equipment = equipment == null ? List.of() : List.copyOf(equipment);
        passengers = passengers == null ? List.of() : List.copyOf(passengers);
        onDeath = onDeath == null ? List.of() : List.copyOf(onDeath);
        if (count < 1) count = 1;
        if (name != null) {
            name = name.trim();
            if (name.isEmpty()) name = null;
        }
    }

    /** 兼容旧 8 参构造。 */
    public DeathSpawnCfg(EntityType type, int count, CoeffCfg coeff, List<String> skills,
                         List<MobEquipment> equipment, List<PassengerCfg> passengers,
                         List<DeathSpawnCfg> onDeath) {
        this(type, count, coeff, skills, equipment, passengers, onDeath, null, false);
    }
}
```

- [ ] **Step 4: 改解析/数值层**

**`MobYamlParser.java`**：`parseAffixes` 替换为 `parseSkills`（fallback：skills→affixes→infernal.affixes），删除 `parseInfernal`：

```java
    /**
     * 技能 id 列表，fallback：{@code skills} → 旧 {@code affixes} → 旧 {@code infernal.affixes}。
     * <p>迁移（Task 8）前旧 waves.yml 的 affix/infernal 引用仍能加载，id 直传 skill id（mob_skills.yml 含同名）。
     */
    public static List<String> parseSkills(Map<?, ?> m, String context) {
        List<String> out = new ArrayList<>();
        if (m.get("skills") instanceof List<?> skills) {
            for (Object o : skills) out.add(String.valueOf(o));
            return out;
        }
        if (m.get("affixes") instanceof List<?> aff) {
            for (Object o : aff) out.add(String.valueOf(o));
            return out;
        }
        if (m.get("infernal") instanceof Map<?, ?> im && im.get("affixes") instanceof List<?> il) {
            for (Object o : il) out.add(String.valueOf(o));
        }
        return out;
    }
```
`MobNode` record（line 123-126）改为 `(EntityType type, int count, CoeffCfg coeff, List<String> skills, List<MobEquipment> equipment, List<PassengerCfg> passengers, List<DeathSpawnCfg> onDeath, String name, boolean bossBar, boolean controller)`；`parseMobNode`（line 132-133）删 affixes/infernal 两行，改 `List<String> skills = parseSkills(m, context);`；`parsePassengersDepth`/`parseOnDeathDepth`（line 152-153/167-168）里 `n.affixes(), n.infernal()` → `n.skills()`（各删一个参数）。

**`Compose.java`** 全量替换（2-arg，删 Affix）：

```java
package io.mczju.maggoteers.plan;

import io.mczju.maggoteers.config.CoeffCfg;
import io.mczju.maggoteers.config.ScalingConfig;

/**
 * 数值合成纯函数：最终倍率 = coeff × scaling（§7.1；affix 层已退役，改技能系统）。
 * 与 Bukkit 解耦，纯单测。
 */
public final class Compose {

    /** 一只怪的最终倍率（hp/dmg/speed/scale/follow_range）。 */
    public record MobScale(double hp, double dmg, double speed, double scale, double followRange) {}

    public static MobScale compose(CoeffCfg coeff, ScalingConfig.Scaling snap) {
        return new MobScale(
                coeff.hp() * snap.mobHp(), coeff.dmg() * snap.mobDamage(),
                coeff.speed() * snap.mobSpeed(),
                coeff.scale(), coeff.followRange());
    }

    private Compose() {}
}
```

- [ ] **Step 5: 改 RunPlanner（删 AffixService 参数，skills 透传）**

`src/main/java/io/mczju/maggoteers/plan/RunPlanner.java`：

```java
    /** 生产入口：onGameStart 异步线程调用。 */
    public static List<ActPlan> plan(long seed, int playerCount) {
        JavaPlugin plugin = MaggoteersPlugin.getInstance();
        return plan(seed, playerCount,
                WavesConfig.getInstance().definitions(),
                MapRepository.library(),
                ScalingConfig.getInstance(),
                loadRunConfig(plugin));
    }

    /** 纯函数：完全注入，不碰 Bukkit。 */
    public static List<ActPlan> plan(long seed, int playerCount, WaveDefinitions defs,
                                     MapLibrary maps, ScalingConfig scaling, RunConfig cfg) {
        SeededRng root = new SeededRng(seed);
        ScalingConfig.Scaling snap = scaling.scaleFor(playerCount);
        List<ActPlan> out = new ArrayList<>(3);
        for (int i = 0; i < ACTS.length; i++) {
            out.add(planAct(root, i, ACTS[i], defs, maps, snap, cfg));
        }
        return out;
    }

    private static ActPlan planAct(SeededRng root, int actIndex, String act, WaveDefinitions defs, MapLibrary maps,
                                   ScalingConfig.Scaling snap, RunConfig cfg) {
        List<MapEntry> avail = maps.maps(act);
        if (avail.isEmpty()) throw new IllegalStateException(act + " 无可用地图");
        SeededRng actRng = root.derive(1_000L + actIndex);
        MapEntry map = avail.get(actRng.nextInt(avail.size()));
        Vec3 origin = cfg.origin(act);

        List<WaveSpec> waves = new ArrayList<>();
        Set<String> usedWeak = new HashSet<>();
        for (int i = 0; i < cfg.weak(act); i++)
            waves.add(rollWave(root.derive(wavePickSalt(actIndex, "weak", i)), act, "weak",
                    map, origin, defs, snap, usedWeak, cfg.weak(act)));
        Set<String> usedStrong = new HashSet<>();
        for (int i = 0; i < cfg.strong(act); i++)
            waves.add(rollWave(root.derive(wavePickSalt(actIndex, "strong", i)), act, "strong",
                    map, origin, defs, snap, usedStrong, cfg.strong(act)));
        Set<String> usedBoss = new HashSet<>();
        waves.add(rollWave(root.derive(wavePickSalt(actIndex, "boss", 0)), act, "boss",
                map, origin, defs, snap, usedBoss, 1));

        return new ActPlan(map.mapId(), Coords.resolve(origin, map.points().playerSpawn()), waves);
    }

    private static WaveSpec rollWave(SeededRng rng, String act, String tier, MapEntry map, Vec3 origin,
                                     WaveDefinitions defs, ScalingConfig.Scaling snap,
                                     Set<String> used, int need) {
        // ...（原开头到 strat 校验不变）
        List<SpawnStep> steps = new ArrayList<>();
        for (StepCfg sc : ordered) {
            Vec3 rel = map.points().resolvePointOrBossFallback(sc.point());
            if (rel == null) throw new IllegalStateException(
                    "刷怪点 " + sc.point() + " 在 " + map.mapId() + "/points.yml 未定义"
                            + (map.points().point("boss") == null ? "且无 boss 可回退" : "且非 boss 点数≥9 不可回退")
                            + "（strategy=" + strat.id() + "，G3）");
            Compose.MobScale ms = Compose.compose(sc.coeff(), snap);
            int resolvedCount = ScalingConfig.rollCount(sc.count(), snap.mobCount(), countRng);
            List<PassengerSpawn> passengerSpawns = buildPassengerTrees(sc.passengers(), snap, countRng);
            List<DeathSpawn> onDeath = buildDeathSpawns(sc.onDeath(), snap, countRng);
            steps.add(new SpawnStep(
                    Coords.resolve(origin, rel), sc.type(), resolvedCount,
                    ms.hp(), ms.dmg(), ms.speed(),
                    ms.scale(), ms.followRange(),
                    sc.delaySec() * 20,
                    sc.skills(),
                    sc.equipment(),
                    onDeath,
                    passengerSpawns,
                    sc.repeat(),
                    sc.name(),
                    sc.bossBar()));
        }
        // ...（clearReward / return 不变）
    }
```
`buildPassengerTrees`/`buildDeathSpawns` 删 `AffixService affixes` 参数；内部删 `affixes.resolve(...)` 与 `pa/da.stream().flatMap(a -> a.potions().stream())`；`PassengerSpawn`/`DeathSpawn` 构造里 skills 传 `pc.skills()`/`dc.skills()`。删 `import io.mczju.maggoteers.config.AffixService;`（`import io.mczju.maggoteers.config.*;` 通配仍在，但 Affix 类删除后 `List<Affix>` 引用已不存在）。

- [ ] **Step 6: 改 MobFactory / 主类 / 命令 / 监听 / WaveEngine**

**`MobFactory.java`**：
- `configureSpawnedLiving`（line 89-102）删 `affixes/potions/infernal` 参数，删 `applyAffixPotions`/`applyAffixVisuals`/`InfernalMobsBridge.mechanize` 三行，删 `import InfernalCfg`/`InfernalMobsBridge`：

```java
    /** 刷怪后统一配置：防卸载 → 系数 → 装备 → 名字锁定 → 名字再断言 → 挂载限制。技能挂载由 WaveEngine.trackSpawned 做（Task 6）。 */
    private static void configureSpawnedLiving(LivingEntity le, double hpMult, double dmgMult, double speedMult,
                                               double scaleMult, double followRangeMult,
                                               List<MobEquipment> equipment, String name) {
        le.setRemoveWhenFarAway(false);
        applyMobStats(le, hpMult, dmgMult, speedMult, scaleMult, followRangeMult);
        applyEquipment(le, equipment);
        MobDisplayNames.lock(le, name);
        scheduleNameReassert(le);
        MountPassengerLimits.prepareMount(le);
    }
```
- `spawnDeathMob`/`spawnMount`/`spawnPassengerEntity` 三处 `configureSpawnedLiving(...)` 调用删 `ds.affixes(), ds.potions(), ds.infernal()` / `step.affixes(), step.potions(), step.infernal()` / `ps.affixes(), ps.potions(), ps.infernal()`。
- `spawnDebugMob`（line 179-187）改：

```java
    /** 调试用：在玩家位置生成带技能的怪。 */
    public static LivingEntity spawnDebugMob(Location loc, org.bukkit.entity.EntityType type,
                                             double hpMult, double dmgMult, double spdMult,
                                             List<String> skillIds) {
        SpawnStep fake = new SpawnStep(
                new Vec3(loc.getX(), loc.getY(), loc.getZ()),
                type, 1, hpMult, dmgMult, spdMult, 1.0, 1.0,
                0, skillIds, List.of(), List.of(), List.of());
        return spawnMount(loc, fake);
    }
```
- 删除 `applyAffixVisuals`（line 326-335）与 `applyAffixPotions`（line 337-356）两个方法。

**`MaggoteersPlugin.java`**：
- 删 `import io.mczju.maggoteers.integration.InfernalMobsBridge;` 与 `import io.mczju.maggoteers.config.AffixService;`
- line 42-43 两行替换为 `io.mczju.maggoteers.mob.MobSkillRegistry.load(this);`（置于 `WavesConfig.loadFromFile(this);` 之前）
- line 92 删 `InfernalMobsBridge.shutdown();`

**`MaggoteersCommand.java`** `case "spawnmob"`（line 204-222）改：

```java
            case "spawnmob" -> {
                if (game == null) { msg(p, "不在局内。"); return true; }
                if (args.length < 3) { msg(p, "用法：/maggoteers debug spawnmob <实体> [技能...]"); return true; }
                EntityType type;
                try {
                    type = io.mczju.maggoteers.util.GameRegistries.entityType(args[2]);
                } catch (Exception e) {
                    msg(p, "未知实体：" + args[2]);
                    return true;
                }
                List<String> skills = new ArrayList<>();
                for (int i = 3; i < args.length; i++) skills.add(args[i]);
                var snap = ScalingConfig.getInstance().scaleFor(game.getPlayers().size());
                var ms = Compose.compose(new io.mczju.maggoteers.config.CoeffCfg(1, 1, 1), snap);
                var le = MobFactory.spawnDebugMob(p.getLocation(), type, ms.hp(), ms.dmg(), ms.speed(), skills);
                if (le != null) WaveEngine.trackDebugMob(game, le);
                msg(p, le == null ? "生成失败。" : "已生成 " + type.name() + "（技能=" + skills + "）");
            }
```
（同时删 `import` 中 `AffixService` 引用——确认 `MaggoteersCommand` 顶部无其他 `AffixService` 使用后删除该 import；`Compose.compose` 现在 2-arg。）

**`MobDeathListener.java`**：
- 删 `import io.mczju.maggoteers.integration.InfernalMobsBridge;`
- 删 `beforeInfernalDeath` 方法（line 38-45）与其 javadoc。

**`WaveEngine.java`**：
- 删 `import io.mczju.maggoteers.integration.InfernalMobsBridge;`
- 删 4 处 `InfernalMobsBridge.unregisterManaged(uuid);`（line 118 `clearTracking`、line 134 `killAllTracked`、line 147 `stop`、line 161 `handleMobDeath`）。

**删除文件**（`git rm`）：`config/Affix.java`、`config/AffixService.java`、`config/InfernalCfg.java`、`integration/InfernalMobsBridge.java`、`src/main/resources/affixes.yml`、`config/InfernalCfgTest.java`、`integration/InfernalMobsBridgeTest.java`、`config/RetiredAffixConfigTest.java`（Task 8 会建新的技能 schema 校验测试）。

- [ ] **Step 7: 运行确认通过**

Run: `export JAVA_HOME=/home/chalcanthite/.local/jdk-25 && export PATH="$JAVA_HOME/bin:$PATH" && mvn -o -q test`
Expected: 全绿。注意：`WavesConfig` 现在校验 skills 引用 `MobSkillRegistry`——若 `waves.yml` 里 affix id（如 `armored`）在 mob_skills.yml 已定义则无警告；未定义的仅 severe 日志（不 fail）。`RetiredAffixConfigTest` 已删，`affixes.yml` 已删，资源加载测试无残留。

- [ ] **Step 8: Commit**

```bash
git add -A
git commit -m "refactor(mob): drop affix/IM systems, swap SpawnStep/Passenger/Death models to skills"
```

---

### Task 6: 怪物技能执行核心（MobSkillService/MobEffectExecutor/MobTempAttributeService/MobCounterRegistry/MobSkillListener）

> 消费 Task 5 的 `SpawnStep.skills()`。**SUMMON/TELEPORT 两种效果在本 Task 的 `MobEffectExecutor` 走 default 跳过（Task 7 补）**；Health/Attribute/Potion 三种全量实现。怪物技能事件：攻击（target）、受击（source）、被击杀（killer）、生成（attach 时）、tick（每秒心跳）、计数器（MobCounterRegistry 驱动）。

**Files:**
- Create: `src/main/java/io/mczju/maggoteers/mob/MobSkillService.java`
- Create: `src/main/java/io/mczju/maggoteers/mob/MobEffectExecutor.java`
- Create: `src/main/java/io/mczju/maggoteers/mob/MobTempAttributeService.java`
- Create: `src/main/java/io/mczju/maggoteers/mob/MobCounterRegistry.java`
- Create: `src/main/java/io/mczju/maggoteers/listener/MobSkillListener.java`
- Create: `src/test/java/io/mczju/maggoteers/mob/MobCounterRegistryTest.java`
- Modify: `src/main/java/io/mczju/maggoteers/wave/MobSpawnProfile.java`（加 skills 字段）
- Modify: `src/main/java/io/mczju/maggoteers/wave/WaveEngine.java`（trackSpawned attach + 4 处 detach + gameOfEntity）
- Modify: `src/main/java/io/mczju/maggoteers/mob/MobFactory.java`（spawnDebugMob 内 attach）
- Modify: `src/main/java/io/mczju/maggoteers/MaggoteersPlugin.java`（register MobSkillListener + MobSkillService.start）

**Interfaces:**
- Consumes: `MobSkillSpec`/`MobSkillRegistry`/`MobCounterSpec`/`MobCondition`/`MobEffectSpec`/`MobTrigger`（Task 4）；`EffectContext`/`EffectKeys`；`WaveEngine`（Task 5 形态）；`SpawnStep.skills()`（Task 5）。
- Produces:
  - `MobSkillService.attach(LivingEntity, List<String>)`（幂等；SPAWN 立即执行 + invisible + 计数器加载）
  - `MobSkillService.detach(UUID)`
  - `MobSkillService.fire(MaggoteersGame, LivingEntity, MobTrigger, LivingEntity target, LivingEntity source)`
  - `MobSkillService.start(MaggoteersPlugin)`（每秒心跳，TICK 技能按 cooldown 触发）
  - `MobEffectExecutor.execute(LivingEntity mob, MobEffectSpec, LivingEntity target, LivingEntity source)`（HEALTH/ATTRIBUTE/POTION 实现，SUMMON/TELEPORT default 跳过）
  - `MobTempAttributeService.apply(LivingEntity, EffectContext, int durationTicks)` / `clearAll(UUID)`
  - `MobCounterRegistry.load/unload/signal` + 静态纯逻辑 `advanceCount(MobCounterSpec, MobTrigger, int)→int` / `fullAt(MobCounterSpec, MobTrigger, int)→boolean`
  - `WaveEngine.gameOfEntity(UUID)→AbstractGame`

- [ ] **Step 1: 写失败测试（MobCounterRegistryTest，纯逻辑）**

`src/test/java/io/mczju/maggoteers/mob/MobCounterRegistryTest.java`：

```java
package io.mczju.maggoteers.mob;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MobCounterRegistryTest {

    private static MobCounterSpec counter(MobTrigger count, int amount) {
        return new MobCounterSpec("c", count, amount, null, null);
    }

    @Test
    void advanceCountIgnoresNonMatchingInput() {
        MobCounterSpec spec = counter(MobTrigger.ATTACK, 3);
        assertEquals(2, MobCounterRegistry.advanceCount(spec, MobTrigger.ATTACK, 1));
        assertEquals(1, MobCounterRegistry.advanceCount(spec, MobTrigger.DAMAGE_TAKEN, 1));
    }

    @Test
    void advanceCountWrapsToZeroAtFull() {
        MobCounterSpec spec = counter(MobTrigger.ATTACK, 3);
        assertEquals(0, MobCounterRegistry.advanceCount(spec, MobTrigger.ATTACK, 2));
    }

    @Test
    void fullAtDetectsTriggerMoment() {
        MobCounterSpec spec = counter(MobTrigger.TICK, 5);
        assertFalse(MobCounterRegistry.fullAt(spec, MobTrigger.TICK, 3));
        assertTrue(MobCounterRegistry.fullAt(spec, MobTrigger.TICK, 4));
        assertFalse(MobCounterRegistry.fullAt(spec, MobTrigger.DAMAGE_TAKEN, 4));
    }

    @Test
    void counterStateIncrementsAndReportsFull() {
        MobCounterSpec spec = counter(MobTrigger.TICK, 3);
        MobCounterRegistry.MobCounterState st =
                new MobCounterRegistry.MobCounterState(spec, 0);
        st = st.increment().increment();
        assertFalse(st.full());
        st = st.increment();
        assertTrue(st.full());
        assertEquals(3, st.current());
    }
}
```

- [ ] **Step 2: 运行确认失败**

Run: `export JAVA_HOME=/home/chalcanthite/.local/jdk-25 && export PATH="$JAVA_HOME/bin:$PATH" && mvn -o -q test -Dtest=MobCounterRegistryTest`
Expected: FAIL（MobCounterRegistry 未定义）

- [ ] **Step 3: 实现 MobCounterRegistry + MobTempAttributeService**

**`MobCounterRegistry.java`**：

```java
package io.mczju.maggoteers.mob;

import io.mczju.maggoteers.game.MaggoteersGame;
import io.mczju.maggoteers.wave.WaveEngine;
import org.bukkit.entity.LivingEntity;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 怪物计数器生命周期：出生加载、死亡卸载；输入信号推进，计满触发 COUNTER 技能。
 * <p>与玩家 CounterService 对称但独立：count 用 {@link MobTrigger}，条件以怪物为中心。
 * <p>串联语义：本 tick 计满的 id 作为 {@code COUNTER} 信号继续驱动 count==COUNTER 的其它计数器
 *（防自环：跳过自身 id），用队列迭代直到没有新计满。
 */
public final class MobCounterRegistry {

    private static final Map<UUID, Map<String, MobCounterState>> BY_ENTITY = new ConcurrentHashMap<>();

    /** 计数器运行态。current 从 0 起，每次匹配输入 +1，计满清零循环。 */
    public record MobCounterState(MobCounterSpec spec, int current) {
        public MobCounterState {
            if (current < 0) current = 0;
        }
        public MobCounterState increment() { return new MobCounterState(spec, current + 1); }
        public boolean full() { return current >= spec.amount(); }
    }

    /** 纯逻辑：输入信号 input 时该计数器应得的下一计数；计满→0（触发）。非匹配输入→原值。 */
    static int advanceCount(MobCounterSpec spec, MobTrigger input, int current) {
        if (spec == null || !spec.triggeredBy(input)) return current;
        int next = current + 1;
        return next >= spec.amount() ? 0 : next;
    }

    /** 纯逻辑：输入信号 input 是否使该计数器计满（决定是否触发）。 */
    static boolean fullAt(MobCounterSpec spec, MobTrigger input, int current) {
        if (spec == null || !spec.triggeredBy(input)) return false;
        return current + 1 >= spec.amount();
    }

    public static void load(LivingEntity le, List<MobSkillSpec> specs) {
        if (le == null || specs == null || specs.isEmpty()) return;
        Map<String, MobCounterState> counters = new HashMap<>();
        for (MobSkillSpec s : specs) {
            if (s.counter() != null) {
                counters.put(s.counter().id(), new MobCounterState(s.counter(), 0));
            }
        }
        if (!counters.isEmpty()) {
            BY_ENTITY.put(le.getUniqueId(), counters);
        }
    }

    public static void unload(UUID uuid) {
        BY_ENTITY.remove(uuid);
    }

    /**
     * 输入信号推进本怪计数器；返回本 tick 最终应触发的 counter id 列表（含串联驱动）。
     * 条件不通过、count!=input 的计数器不推进。
     */
    public static List<String> signal(LivingEntity mob, MobTrigger t,
                                      LivingEntity target, LivingEntity source) {
        List<String> fired = new ArrayList<>();
        if (mob == null) return fired;
        Map<String, MobCounterState> counters = BY_ENTITY.get(mob.getUniqueId());
        if (counters == null || counters.isEmpty()) return fired;

        Deque<String> queue = new ArrayDeque<>();
        // 初筛：count==t（非 COUNTER 信号，避免自环）且计满者入队
        for (var e : counters.entrySet()) {
            MobCounterState st = e.getValue();
            if (!st.spec().triggeredBy(t) || t == MobTrigger.COUNTER) continue;
            if (!passesCondition(mob, st.spec(), target, source)) continue;
            e.setValue(new MobCounterState(st.spec(), advanceCount(st.spec(), t, st.current())));
            if (fullAt(st.spec(), t, st.current())) {
                queue.add(st.spec().id());
            }
        }
        // 串联：COUNTER 信号驱动其它计数器（跳过自身）
        while (!queue.isEmpty()) {
            String cid = queue.poll();
            fired.add(cid);
            for (var e : counters.entrySet()) {
                MobCounterState st = e.getValue();
                if (!st.spec().triggeredBy(MobTrigger.COUNTER) || cid.equals(st.spec().id())) continue;
                if (!passesCondition(mob, st.spec(), target, source)) continue;
                e.setValue(new MobCounterState(st.spec(),
                        advanceCount(st.spec(), MobTrigger.COUNTER, st.current())));
                if (fullAt(st.spec(), MobTrigger.COUNTER, st.current())) {
                    queue.add(st.spec().id());
                }
            }
        }
        return fired;
    }

    private static boolean passesCondition(LivingEntity mob, MobCounterSpec spec,
                                           LivingEntity target, LivingEntity source) {
        if (spec.condition() == null) return true;
        AbstractGame game = WaveEngine.gameOfEntity(mob.getUniqueId());
        return game instanceof MaggoteersGame mg
                && spec.condition().evaluate(mg, mob, target, source);
    }

    private MobCounterRegistry() {}
}
```

> 引入 `org.bukkit.entity.Entity` 类型 `AbstractGame`（`com.github.mczjuops.mczjugamecore.game.AbstractGame`）——`passesCondition` 用了它，需 `import com.github.mczjuops.mczjugamecore.game.AbstractGame;`。

**`MobTempAttributeService.java`**：

```java
package io.mczju.maggoteers.mob;

import io.mczju.maggoteers.MaggoteersPlugin;
import io.mczju.maggoteers.effect.EffectContext;
import io.mczju.maggoteers.effect.EffectKeys;
import io.mczju.maggoteers.util.GameRegistries;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.EquipmentSlotGroup;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 怪物临时属性修改：到期自动移除、死亡/卸载统一清除（防残留 modifier）。
 * <p>op 映射：FLAT→ADD_NUMBER；PERCENT/MULTIPLY→ADD_SCALAR（+value 倍率，与 mob_skills.yml sprint 语义一致）。
 */
public final class MobTempAttributeService {

    private static final Map<UUID, List<NamespacedKey>> ACTIVE = new ConcurrentHashMap<>();

    private MobTempAttributeService() {}

    public static void apply(LivingEntity le, EffectContext p, int durationTicks) {
        if (le == null || p == null || durationTicks <= 0) return;
        String attrName = p.get(EffectKeys.ATTR_NAME);
        if (attrName == null || attrName.isBlank()) return;
        Attribute attr = GameRegistries.attribute(attrName);
        if (attr == null) return;
        AttributeInstance inst = le.getAttribute(attr);
        if (inst == null) return;
        double value = p.getOrDefault(EffectKeys.VALUE, 0.0);
        String opRaw = p.getOrDefault(EffectKeys.OP, "FLAT");
        NamespacedKey key = new NamespacedKey(MaggoteersPlugin.getInstance(),
                "mtmp_" + attrName.toLowerCase(Locale.ROOT) + "_" + UUID.randomUUID().toString().substring(0, 8));
        inst.addTransientModifier(new AttributeModifier(
                key, value, operation(opRaw), EquipmentSlotGroup.ANY));
        ACTIVE.computeIfAbsent(le.getUniqueId(), u -> new ArrayList<>()).add(key);

        int ticks = Math.max(1, durationTicks);
        Bukkit.getScheduler().runTaskLater(MaggoteersPlugin.getInstance(), () -> {
            AttributeInstance ai = le.getAttribute(attr);
            if (ai != null) ai.removeModifier(key);
            List<NamespacedKey> list = ACTIVE.get(le.getUniqueId());
            if (list != null) list.remove(key);
        }, ticks);
    }

    public static void clearAll(UUID uuid) {
        List<NamespacedKey> list = ACTIVE.remove(uuid);
        if (list == null || list.isEmpty()) return;
        Entity raw = Bukkit.getEntity(uuid);
        if (!(raw instanceof LivingEntity le)) return;
        for (NamespacedKey key : list) {
            for (Attribute attr : Attribute.values()) {
                AttributeInstance ai = le.getAttribute(attr);
                if (ai != null) ai.removeModifier(key);
            }
        }
    }

    private static AttributeModifier.Operation operation(String raw) {
        if (raw == null) return AttributeModifier.Operation.ADD_NUMBER;
        return switch (raw.trim().toUpperCase(Locale.ROOT)) {
            case "PERCENT", "MULTIPLY" -> AttributeModifier.Operation.ADD_SCALAR;
            default -> AttributeModifier.Operation.ADD_NUMBER;
        };
    }
}
```

> ⚠️ **op 对齐**：若项目玩家侧 `ADD_ATTRIBUTE` 的 MULTIPLY 实际映射为 `AttributeModifier.Operation.MULTIPLY_SCALAR_1`（见 `effect/AttributeModifierKeys.java` / `EffectService.executeMagicEffect`），此处改同值保持一致——执行前先 grep `Operation.` 确认。

- [ ] **Step 4: 实现 MobEffectExecutor（HEALTH/ATTRIBUTE/POTION）**

**`MobEffectExecutor.java`**：

```java
package io.mczju.maggoteers.mob;

import io.mczju.maggoteers.effect.EffectContext;
import io.mczju.maggoteers.effect.EffectKeys;
import io.mczju.maggoteers.util.GameRegistries;
import io.mczju.maggoteers.wave.WaveEngine;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.List;
import java.util.Locale;

/**
 * 怪物技能效果执行。SUMMON/TELEPORT 由 Task 7 补（当前 default 跳过）。
 * <p>target 键（EffectKeys.TARGETS）：self（默认，作用于怪自身）| target（触发上下文目标/伤害来源落点）| area（以怪为中心 radius 内敌对）。
 */
public final class MobEffectExecutor {

    private MobEffectExecutor() {}

    public static void execute(LivingEntity mob, MobEffectSpec es,
                               LivingEntity target, LivingEntity source) {
        if (mob == null || es == null) return;
        switch (es.effect()) {
            case HEALTH -> applyHealth(mob, es, target);
            case ATTRIBUTE -> applyAttribute(mob, es, target);
            case POTION -> applyPotion(mob, es, target);
            default -> { }   // SUMMON / TELEPORT：Task 7
        }
    }

    private static void applyHealth(LivingEntity mob, MobEffectSpec es, LivingEntity target) {
        EffectContext p = es.params();
        Double amount = p.get(EffectKeys.AMOUNT);
        Double damage = p.get(EffectKeys.DAMAGE);
        Double radius = p.get(EffectKeys.RADIUS);
        if (isArea(p) && radius != null && radius > 0) {
            for (LivingEntity le : nearbyHostiles(mob, radius)) {
                applySingle(le, amount, damage);
            }
            return;
        }
        applySingle(resolveTarget(mob, target, p), amount, damage);
    }

    private static void applySingle(LivingEntity victim, Double amount, Double damage) {
        if (victim == null || victim.isDead()) return;
        if (amount != null) {
            if (amount > 0) victim.setHealth(Math.min(victim.getMaxHealth(), victim.getHealth() + amount));
            else victim.damage(-amount, victim);
        } else if (damage != null && damage > 0) {
            victim.damage(damage, victim);
        }
    }

    private static void applyAttribute(LivingEntity mob, MobEffectSpec es, LivingEntity target) {
        EffectContext p = es.params();
        LivingEntity victim = resolveTarget(mob, target, p);
        if (victim == null || victim.isDead()) return;
        int dur = p.getOrDefault(EffectKeys.DURATION_TICKS, 0);
        if (dur <= 0) return;   // 常驻属性不在本效果做（spawn 属性由 coeff 承担）
        MobTempAttributeService.apply(victim, p, dur);
    }

    private static void applyPotion(LivingEntity mob, MobEffectSpec es, LivingEntity target) {
        EffectContext p = es.params();
        LivingEntity victim = resolveTarget(mob, target, p);
        if (victim == null || victim.isDead()) return;
        PotionEffectType type = p.get(EffectKeys.POTION);
        if (type == null) {
            String name = p.get(EffectKeys.POTION_NAME);
            if (name != null) type = GameRegistries.potionEffect(name);
        }
        if (type == null) return;
        int amp = p.getOrDefault(EffectKeys.AMP, 0);
        int dur = p.getOrDefault(EffectKeys.DURATION_TICKS, 0);
        if (dur <= 0) dur = 20 * 60 * 60;   // 旧 affix dur 0 语义=常驻；用 1 小时近似
        victim.addPotionEffect(new PotionEffect(type, dur, amp));
    }

    private static boolean isArea(EffectContext p) {
        String t = p.get(EffectKeys.TARGETS);
        return t != null && "area".equalsIgnoreCase(t.trim());
    }

    /** self→mob；target→触发目标（null 回落 mob）；area→mob（以 mob 为圆心）。 */
    private static LivingEntity resolveTarget(LivingEntity mob, LivingEntity target, EffectContext p) {
        String t = p.get(EffectKeys.TARGETS);
        if (t == null) return mob;
        String norm = t.trim().toLowerCase(Locale.ROOT);
        if ("target".equals(norm)) return target != null && !target.isDead() ? target : mob;
        return mob;
    }

    /** 以 mob 为中心半径内敌对实体（非玩家 + WaveEngine 追踪怪；含 mob 自身命中规则由调用方决定）。 */
    static List<LivingEntity> nearbyHostiles(LivingEntity mob, double radius) {
        List<LivingEntity> out = new java.util.ArrayList<>();
        if (mob.getWorld() == null) return out;
        for (Entity en : mob.getWorld().getNearbyEntities(mob.getLocation(), radius, radius, radius)) {
            if (!(en instanceof LivingEntity le) || en instanceof Player) continue;
            if (en.equals(mob)) continue;
            if (!WaveEngine.isTracked(le.getUniqueId())) continue;
            out.add(le);
        }
        return out;
    }
}
```

> `nearbyHostiles` 供 Task 7 的 SUMMON area 落点 / TELEPORT 目标判定复用（`static` 包内可见）。

- [ ] **Step 5: 实现 MobSkillService（attach/detach/fire/tick）**

**`MobSkillService.java`**：

```java
package io.mczju.maggoteers.mob;

import com.github.mczjuops.mczjugamecore.game.AbstractGame;
import io.mczju.maggoteers.MaggoteersPlugin;
import io.mczju.maggoteers.game.MaggoteersGame;
import io.mczju.maggoteers.wave.WaveEngine;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 怪物技能运行时：attach（生成）→ 事件分发（攻击/受击/被击杀/tick/计数器）→ detach（死亡/清波）。
 * <p>技能 id 解析失败（mob_skills.yml 缺失）时静默跳过；SPAWN 技能在 attach 立即执行。
 */
public final class MobSkillService {

    private static final Map<UUID, List<MobSkillSpec>> BY_ENTITY = new ConcurrentHashMap<>();
    private static final Map<UUID, Map<String, Long>> LAST_FIRE = new ConcurrentHashMap<>();

    private MobSkillService() {}

    public static void start(MaggoteersPlugin plugin) {
        Bukkit.getScheduler().runTaskTimer(plugin, MobSkillService::tickAll, 20L, 20L);
    }

    /** 生成时挂载：技能表 + 计数器 + 隐身 + SPAWN 技能。幂等（重复 attach 覆盖）。 */
    public static void attach(LivingEntity le, List<String> skillIds) {
        if (le == null || skillIds == null || skillIds.isEmpty()) return;
        List<MobSkillSpec> specs = new ArrayList<>();
        for (String id : skillIds) {
            MobSkillRegistry.get(id).ifPresent(specs::add);
        }
        if (specs.isEmpty()) return;
        UUID uuid = le.getUniqueId();
        BY_ENTITY.put(uuid, List.copyOf(specs));
        MobCounterRegistry.load(le, specs);
        MaggoteersGame game = gameOf(le);
        for (MobSkillSpec spec : specs) {
            if (spec.invisible()) applyInvisible(le);
            if (spec.trigger() == MobTrigger.SPAWN) {
                execute(game, le, spec, null, null);
            }
        }
    }

    public static void detach(UUID uuid) {
        BY_ENTITY.remove(uuid);
        LAST_FIRE.remove(uuid);
        MobCounterRegistry.unload(uuid);
        MobTempAttributeService.clearAll(uuid);
    }

    public static List<MobSkillSpec> skillsOf(UUID uuid) {
        List<MobSkillSpec> s = BY_ENTITY.get(uuid);
        return s == null ? List.of() : s;
    }

    /** 事件分发：先推进计数器（计满 id 触发对应 COUNTER 技能），再匹配普通 trigger。 */
    public static void fire(MaggoteersGame game, LivingEntity mob, MobTrigger t,
                            LivingEntity target, LivingEntity source) {
        if (mob == null) return;
        List<MobSkillSpec> specs = BY_ENTITY.get(mob.getUniqueId());
        if (specs == null || specs.isEmpty()) return;

        for (String cid : MobCounterRegistry.signal(mob, t, target, source)) {
            for (MobSkillSpec spec : specs) {
                if (spec.trigger() == MobTrigger.COUNTER && spec.counter() != null
                        && spec.counter().id().equals(cid)) {
                    execute(game, mob, spec, target, source);
                }
            }
        }
        if (t == MobTrigger.COUNTER) return;

        for (MobSkillSpec spec : specs) {
            if (spec.trigger() != t || spec.trigger() == MobTrigger.SPAWN) continue;
            if (spec.condition() != null && !spec.condition().evaluate(game, mob, target, source)) continue;
            execute(game, mob, spec, target, source);
        }
    }

    private static void execute(MaggoteersGame game, LivingEntity mob, MobSkillSpec spec,
                                LivingEntity target, LivingEntity source) {
        for (MobEffectSpec es : spec.effects()) {
            MobEffectExecutor.execute(mob, es, target, source);
        }
    }

    /** 每秒心跳：TICK 光环技能按 cooldown_sec 触发；失效实体 detach。 */
    private static void tickAll() {
        for (var entry : BY_ENTITY.entrySet()) {
            UUID uuid = entry.getKey();
            Entity raw = Bukkit.getEntity(uuid);
            if (!(raw instanceof LivingEntity le) || le.isDead() || !le.isValid()) {
                detach(uuid);
                continue;
            }
            MaggoteersGame game = gameOf(le);
            if (game == null) continue;
            long now = System.currentTimeMillis();
            for (MobSkillSpec spec : entry.getValue()) {
                if (spec.trigger() != MobTrigger.TICK) continue;
                if (!cooldownElapsed(uuid, spec.id(), now, spec.cooldownSec())) continue;
                if (spec.condition() != null && !spec.condition().evaluate(game, le, null, null)) continue;
                execute(game, le, spec, null, null);
                markFired(uuid, spec.id(), now);
            }
        }
    }

    private static boolean cooldownElapsed(UUID uuid, String skillId, long now, int cooldownSec) {
        if (cooldownSec <= 0) return true;
        Map<String, Long> bySkill = LAST_FIRE.get(uuid);
        Long last = bySkill == null ? null : bySkill.get(skillId);
        return last == null || now - last >= cooldownSec * 1000L;
    }

    private static void markFired(UUID uuid, String skillId, long now) {
        LAST_FIRE.computeIfAbsent(uuid, u -> new HashMap<>()).put(skillId, now);
    }

    private static void applyInvisible(LivingEntity le) {
        le.setInvisible(true);
        EntityEquipment eq = le.getEquipment();
        if (eq == null) return;
        ItemStack bottle = new ItemStack(Material.GLASS_BOTTLE);
        eq.setHelmet(bottle);
        eq.setHelmetDropChance(0f);
    }

    private static MaggoteersGame gameOf(LivingEntity le) {
        AbstractGame g = WaveEngine.gameOfEntity(le.getUniqueId());
        return g instanceof MaggoteersGame mg ? mg : null;
    }
}
```

- [ ] **Step 6: 实现 MobSkillListener + 接入 WaveEngine / MobFactory / 主类**

**`MobSkillListener.java`**：

```java
package io.mczju.maggoteers.listener;

import com.github.mczjuops.mczjugamecore.game.AbstractGame;
import io.mczju.maggoteers.game.MaggoteersGame;
import io.mczju.maggoteers.mob.MobSkillService;
import io.mczju.maggoteers.mob.MobTrigger;
import io.mczju.maggoteers.wave.WaveEngine;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;

import java.util.UUID;

/**
 * 怪物技能事件入口：攻击（附带目标）、受击（附带伤害来源）、被击杀（附带击杀者）。
 * <p>LOWEST 优先：先于 MobDeathListener.HIGHEST 的 handleMobDeath（untrack）触发，保证 KILLED 能反查技能表。
 */
public class MobSkillListener implements Listener {

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCombat(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof LivingEntity victim)) return;
        if (e.getDamager() instanceof LivingEntity attacker) {
            MaggoteersGame victimGame = gameOf(victim.getUniqueId());
            if (victimGame != null) {
                MobSkillService.fire(victimGame, victim, MobTrigger.DAMAGE_TAKEN, null, attacker);
            }
            MaggoteersGame attackGame = gameOf(attacker.getUniqueId());
            if (attackGame != null) {
                MobSkillService.fire(attackGame, attacker, MobTrigger.ATTACK, victim, null);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onKilled(EntityDeathEvent e) {
        if (!(e.getEntity() instanceof LivingEntity dead)) return;
        MaggoteersGame game = gameOf(dead.getUniqueId());
        if (game == null) return;
        LivingEntity killer = dead.getKiller();
        MobSkillService.fire(game, dead, MobTrigger.KILLED, null, killer);
    }

    private static MaggoteersGame gameOf(UUID uuid) {
        AbstractGame g = WaveEngine.gameOfEntity(uuid);
        return g instanceof MaggoteersGame mg ? mg : null;
    }
}
```

**`MobSpawnProfile.java`** 全量替换（加 skills）：

```java
package io.mczju.maggoteers.wave;

import java.util.List;

/** 运行时怪物档案：技能 id 列表 + 死亡召唤列表 + 是否挂 Boss 血条。 */
public record MobSpawnProfile(List<String> skills, List<DeathSpawn> onDeath, boolean bossBar) {
    public MobSpawnProfile {
        skills = skills == null ? List.of() : List.copyOf(skills);
        onDeath = onDeath == null ? List.of() : List.copyOf(onDeath);
    }

    public MobSpawnProfile(List<DeathSpawn> onDeath) {
        this(List.of(), onDeath, false);
    }

    public static MobSpawnProfile fromStep(SpawnStep step) {
        return new MobSpawnProfile(step.skills(), step.onDeath(), step.bossBar());
    }

    public static MobSpawnProfile fromPassenger(PassengerSpawn spawn) {
        return new MobSpawnProfile(spawn.skills(), spawn.onDeath(), spawn.bossBar());
    }

    public static MobSpawnProfile fromDeathSpawn(DeathSpawn spawn) {
        return new MobSpawnProfile(spawn.skills(), spawn.onDeath(), spawn.bossBar());
    }
}
```

**`WaveEngine.java`**：
- 顶部 `import io.mczju.maggoteers.mob.MobSkillService;`
- `trackSpawned`（line 82）在 `BY_ENTITY.put(uuid, rt);` 后加 `MobSkillService.attach(le, profile.skills());`
- 新增方法：

```java
    /** UUID → 本局（供 MobSkillListener/MobSkillService 反查）。 */
    public static AbstractGame gameOfEntity(UUID uuid) {
        WaveRuntime rt = BY_ENTITY.get(uuid);
        return rt == null ? null : rt.game;
    }
```
- 4 处加 `MobSkillService.detach(uuid);`：`clearTracking`（原 line 118 处）、`killAllTracked`（原 line 134 处）、`stop`（原 line 147 处）、`handleMobDeath`（原 line 161 处）——Task 5 已删 InfernalMobsBridge 行，在这些空位补 detach。

**`MobFactory.java`**：`spawnDebugMob` 末尾加：

```java
        LivingEntity le = spawnMount(loc, fake);
        if (le != null) MobSkillService.attach(le, skillIds);
        return le;
```

**`MaggoteersPlugin.java`**：
- `registerEvents(new MobDeathListener(), this);` 后加 `getServer().getPluginManager().registerEvents(new io.mczju.maggoteers.listener.MobSkillListener(), this);`
- onEnable 中加 `io.mczju.maggoteers.mob.MobSkillService.start(this);`（与 `GameplayTickListener.start()` 并列）

- [ ] **Step 7: 运行确认通过**

Run: `export JAVA_HOME=/home/chalcanthite/.local/jdk-25 && export PATH="$JAVA_HOME/bin:$PATH" && mvn -o -q test`
Expected: 全绿（新增 MobCounterRegistryTest 4 用例；既有测试不受影响——WaveEngine/MobSpawnProfile 改动是加法）

- [ ] **Step 8: 手动验证（测试服）**

1. `/maggoteers debug spawnmob ZOMBIE lifesteal` → 打怪应回血（ATTACK + HEALTH self）。
2. `/maggoteers debug spawnmob ZOMBIE invisible` → 隐身 + 玻璃瓶头盔。
3. `/maggoteers debug spawnmob ZOMBIE sprint` → 打它后它加速（DAMAGE_TAKEN + ATTRIBUTE self）。
4. `/maggoteers debug spawnmob ZOMBIE quicksand` → 打它后自己中缓慢（DAMAGE_TAKEN + POTION target）。
5. 波次里出现带 `infernal: {affixes: [lifesteal]}` 的旧 waves.yml 节点：parseSkills fallback → 技能生效。

- [ ] **Step 9: Commit**

```bash
git add -A
git commit -m "feat(mob): mob skill runtime (service/executor/temp-attr/counter/listener) + wave hooks"
```

---

### Task 7: Homing 双端 + 怪物 SUMMON/TELEPORT + 玩家 homing

> 让弹道能追踪目标：**玩家发射**（魔法武器 SUMMON projectile + homing）追踪**最近的追踪怪**；**怪物发射**（mob_skills.yml SUMMON projectile + homing）追踪**最近的玩家**。补全 Task 6 跳过的 SUMMON/TELEPORT 两种怪物效果。参考 IM 原「追踪箭/火球/凋零头」能力，但纯原版实现（`ProjectileHomingService` 每 tick 转向），不再依赖 IM。

**Files:**
- Create: `src/main/java/io/mczju/maggoteers/effect/ProjectileHomingService.java`（**effect 包**——File Structure 声明如此；玩家/怪物共享）
- Create: `src/main/java/io/mczju/maggoteers/mob/MobProjectileService.java`
- Create: `src/main/java/io/mczju/maggoteers/mob/TeleportSafety.java`
- Modify: `src/main/java/io/mczju/maggoteers/wave/WaveEngine.java`（加 `nearestTrackedMob(Location, MaggoteersGame, double)`）
- Modify: `src/main/java/io/mczju/maggoteers/effect/SummonParams.java`（record 加 `homingTarget` 组件）
- Modify: `src/main/java/io/mczju/maggoteers/effect/SummonParamsParser.java`（parseProjectile 读 homing_target）
- Modify: `src/main/java/io/mczju/maggoteers/effect/SummonExecutor.java`（applyProjectile 登记 homing）
- Modify: `src/main/java/io/mczju/maggoteers/mob/MobEffectExecutor.java`（SUMMON/TELEPORT 从 default 跳转补全）
- Modify: `src/main/java/io/mczju/maggoteers/MaggoteersPlugin.java`（`ProjectileHomingService.start(this)`）
- Test: `src/test/java/io/mczju/maggoteers/effect/SummonParamsParserTest.java`（新用例）
- Test: `src/test/java/io/mczju/maggoteers/mob/TeleportSafetyTest.java`（新建；File Structure 已声明）

**Interfaces:**
- Consumes: `EffectKeys.HOMING_TARGET`（String，**Task 4 落实**，本 Task 只消费不重复定义）；`SummonParams`（含新组件）；`WaveEngine.gameOfEntity` / `RUNTIMES` / `livingMobs`（Task 6）；`MobEffectSpec`/`MobEffectExecutor`（Task 4/6）。
- Produces:
  - `ProjectileHomingService.home(MaggoteersGame, Projectile, LivingEntity initialTarget, String homingTarget)` / `start(MaggoteersPlugin)`（每 tick 转向，目标死则回落最近目标）
  - `MobProjectileService.shoot(LivingEntity, MaggoteersGame, SummonParams.ProjectileParams, String homingTarget, LivingEntity initialTarget) → Projectile`
  - `TeleportSafety.safeTarget(LivingEntity anchor, double offsetY) → Location|null`；`TeleportSafety.outsideMap(double x, double z) → boolean`（纯逻辑，可测）；`TeleportSafety.isSolidGround(Material) → boolean`（纯逻辑，可测）
  - `WaveEngine.nearestTrackedMob(Location, MaggoteersGame, double) → LivingEntity|null`
  - `SummonParams` 新组件 `String homingTarget()`；`SummonExecutor` 据此登记 homing
- **homing_target 值集合（与 Global Constraints §4 一致）**：`nearest_enemy`（玩家发射追最近本局怪）| `nearest_player`（怪发射追最近玩家）| `attack_target` / `damage_source` / `killer`（怪物弹道追触发上下文目标，initialTarget 传入；目标死则回落最近玩家）| `none`/缺省 = 不追踪。

- [ ] **Step 1: 写失败测试（SummonParamsParserTest 新用例 + TeleportSafetyTest 新文件）**

在 `src/test/java/io/mczju/maggoteers/effect/SummonParamsParserTest.java` 追加：

```java
    @Test
    void parsesProjectileHomingTarget() {
        Map<String, Object> raw = new HashMap<>();
        raw.put("effect", Effect.SUMMON);
        Map<String, Object> params = new HashMap<>();
        params.put("projectile", true);
        params.put("projectile_speed", 1.2);
        params.put("homing_target", "nearest_enemy");
        raw.put("params", params);
        EffectContext ctx = new EffectContext();
        ctx.put(EffectKeys.PROJECTILE_SPEED, 1.2);
        ctx.put(EffectKeys.HOMING_TARGET, "nearest_enemy");
        assertTrue(SummonParamsParser.parseProjectile(ctx).isPresent());
        assertEquals(1.2, SummonParamsParser.parseProjectile(ctx).get().speed(), 1e-9);
        // homing_target 存进 params；SummonParams 组件由 parse() 透传
        assertEquals("nearest_enemy", ctx.get(EffectKeys.HOMING_TARGET));
    }

    @Test
    void homingTargetOptionalWithoutProjectile() {
        EffectContext ctx = new EffectContext();
        assertFalse(ctx.has(EffectKeys.HOMING_TARGET));
    }
```

新建 `src/test/java/io/mczju/maggoteers/mob/TeleportSafetyTest.java`（只测纯逻辑，无 Bukkit mock）：

```java
package io.mczju.maggoteers.mob;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TeleportSafetyTest {

    @Test
    void outsideMapBoundary() {
        assertFalse(TeleportSafety.outsideMap(100, 0));
        assertFalse(TeleportSafety.outsideMap(0, 159));
        assertTrue(TeleportSafety.outsideMap(0, 200));
        assertTrue(TeleportSafety.outsideMap(-300, 0));
    }

    @Test
    void solidGroundKinds() {
        assertTrue(TeleportSafety.isSolidGround(Material.STONE));
        assertTrue(TeleportSafety.isSolidGround(Material.DIRT));
        assertFalse(TeleportSafety.isSolidGround(Material.AIR));
        assertFalse(TeleportSafety.isSolidGround(Material.BEDROCK));
        assertFalse(TeleportSafety.isSolidGround(Material.WATER));
    }
}
```

> 依赖 `TeleportSafety.outsideMap(double, double)` / `isSolidGround(Material)` 两个 **package-private 静态纯逻辑方法**——Step 4 的 TeleportSafety 实现里提供。

- [ ] **Step 2: 运行确认失败**

Run: `export JAVA_HOME=/home/chalcanthite/.local/jdk-25 && export PATH="$JAVA_HOME/bin:$PATH" && mvn -o -q test -Dtest=SummonParamsParserTest,TeleportSafetyTest`
Expected: FAIL（SummonParams 无 homingTarget 组件 / parseProjectile 不识别 / TeleportSafety 无 outsideMap/isSolidGround）

- [ ] **Step 3: SummonParams + SummonParamsParser（EffectKeys 只消费）**

> `EffectKeys.HOMING_TARGET` 已在 **Task 4** 落实（`MobSkillSpecParser.parseEffectParams` 的 `case "homing_target"` 引用它）。本 Task **不再**往 `EffectKeys.java` 加键——若实现时 Task 4 尚未编译该键（Task 按序执行不会发生），回到 Task 4 补定义，不要在本 Task 重复加。

**`SummonParams.java`** 全量替换（record 加 `homingTarget` 组件）：

```java
package io.mczju.maggoteers.effect;

import org.bukkit.entity.EntityType;

import java.util.List;

/** SUMMON 效果参数：实体/锚点/数量/偏移/清理/时长/属性/驯服/友伤/弹道/homing。 */
public record SummonParams(
        EntityType entityType,
        String anchor,
        int count,
        double offsetY,
        int cleanup,
        int durationSec,
        List<AttributeModifierSpec> attributes,
        boolean tamed,
        boolean friendlyFire,
        boolean projectile,
        String homingTarget
) {
    public SummonParams {
        if (attributes == null) attributes = List.of();
        if (homingTarget == null || homingTarget.isBlank()) homingTarget = null;
    }

    public record ProjectileParams(double speed) {}

    /** 保留既有调用：无 homing。 */
    public SummonParams(EntityType entityType, String anchor, int count, double offsetY,
                        int cleanup, int durationSec, List<AttributeModifierSpec> attributes,
                        boolean tamed, boolean friendlyFire, boolean projectile) {
        this(entityType, anchor, count, offsetY, cleanup, durationSec,
                attributes, tamed, friendlyFire, projectile, null);
    }
}
```

**`SummonParamsParser.java`** 修改 `parseProjectile`：

```java
    public static Optional<SummonParams.ProjectileParams> parseProjectile(EffectContext sec) {
        if (!Boolean.TRUE.equals(sec.get(EffectKeys.PROJECTILE))) {
            return Optional.empty();
        }
        double speed = sec.getOrDefault(EffectKeys.PROJECTILE_SPEED, 1.0);
        return Optional.of(new SummonParams.ProjectileParams(speed));
    }
```

`parse(...)` 里构造 `SummonParams` 处加 homingTarget（先读 `sec.get(EffectKeys.HOMING_TARGET)`）：

```java
    SummonParams sp = new SummonParams(
            type, anchor, count, offsetY, cleanup, durationSec, attrs, tamed, friendlyFire, projectile,
            sec.get(EffectKeys.HOMING_TARGET));
```
> 若既有 `parse` 内部先 `parseProjectile` 再拼 SummonParams，保持现有结构，只把最后一个参数补上；`validate(...)` 对 homing 无额外约束（缺省即不追踪）。

- [ ] **Step 4: 实现 ProjectileHomingService + MobProjectileService + TeleportSafety**

**`ProjectileHomingService.java`**（玩家+怪物共享）：

```java
package io.mczju.maggoteers.effect;

import com.github.mczjuops.mczjugamecore.game.AbstractGame;
import io.mczju.maggoteers.MaggoteersPlugin;
import io.mczju.maggoteers.game.MaggoteersGame;
import io.mczju.maggoteers.wave.WaveEngine;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.util.Vector;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 弹道 homing：每 tick 把速度重设为目标方向（平滑转向）。
 * <p>homing_target 预设（与 {@link EffectKeys#HOMING_TARGET} 一致）：
 * nearest_enemy（追最近本局怪）| nearest_player（追最近对局玩家）|
 * attack_target / damage_source / killer（追 initialTarget，目标死则回落最近玩家）| 缺省=不追踪。
 * <p>目标死亡/失效/对局结束 → 停止转向（实体保持惯性飞行）。
 */
public final class ProjectileHomingService {

    private static final Map<UUID, HomingEntry> ACTIVE = new ConcurrentHashMap<>();
    private static final double TURN_FACTOR = 0.35;   // 每 tick 目标方向权重（越小越平滑）

    private record HomingEntry(MaggoteersGame game, String target, LivingEntity initial, double speed) {}

    private ProjectileHomingService() {}

    public static void start(MaggoteersPlugin plugin) {
        Bukkit.getScheduler().runTaskTimer(plugin, ProjectileHomingService::homeAll, 1L, 1L);
    }

    /** initialTarget 仅 attack_target/damage_source/killer 预设时使用（可 null）。 */
    public static void home(MaggoteersGame game, Projectile proj,
                            LivingEntity initialTarget, String homingTarget) {
        if (proj == null || game == null) return;
        String t = homingTarget == null ? null : homingTarget.trim().toLowerCase(Locale.ROOT);
        if (t == null || t.isEmpty() || "none".equals(t)) return;
        ACTIVE.put(proj.getUniqueId(),
                new HomingEntry(game, t, initialTarget, proj.getVelocity().length()));
    }

    public static void stop(UUID projectileUuid) {
        ACTIVE.remove(projectileUuid);
    }

    private static void homeAll() {
        if (ACTIVE.isEmpty()) return;
        for (var it = ACTIVE.entrySet().iterator(); it.hasNext(); ) {
            var e = it.next();
            UUID pid = e.getKey();
            HomingEntry entry = e.getValue();
            Entity raw = Bukkit.getEntity(pid);
            if (!(raw instanceof Projectile proj) || !proj.isValid() || proj.isDead()) {
                it.remove();
                continue;
            }
            AbstractGame now = WaveEngine.gameOfEntity(pid);
            if (!(now instanceof MaggoteersGame mg) || !mg.equals(entry.game())) {
                it.remove();
                continue;
            }
            LivingEntity target = resolve(proj.getLocation(), entry);
            if (target == null || target.isDead() || !target.isValid()) {
                continue;   // 无目标则保持当前速度（不再转向）
            }
            Vector dir = target.getLocation().toVector()
                    .subtract(proj.getLocation().toVector()).normalize();
            Vector cur = proj.getVelocity();
            Vector next = cur.multiply(1 - TURN_FACTOR)
                    .add(dir.multiply(TURN_FACTOR * entry.speed()))
                    .normalize().multiply(entry.speed());
            proj.setVelocity(next);
        }
    }

    private static LivingEntity resolve(Location loc, HomingEntry entry) {
        String t = entry.target();
        if ("attack_target".equals(t) || "damage_source".equals(t) || "killer".equals(t)) {
            LivingEntity init = entry.initial();
            if (init != null && !init.isDead() && init.isValid()) return init;
            return nearestPlayer(loc, entry.game());   // 上下文目标已死：回落追最近玩家
        }
        if ("nearest_player".equals(t)) {
            return nearestPlayer(loc, entry.game());
        }
        return WaveEngine.nearestTrackedMob(loc, entry.game(), 64.0);   // nearest_enemy（默认）
    }

    private static LivingEntity nearestPlayer(Location loc, MaggoteersGame game) {
        LivingEntity best = null;
        double bestDist = Double.MAX_VALUE;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!p.isValid() || p.isDead()) continue;
            if (!game.getPlayers().contains(p.getUniqueId())) continue;
            double d = p.getLocation().distanceSquared(loc);
            if (d < bestDist) {
                bestDist = d;
                best = p;
            }
        }
        return best;
    }
}
```

**`MobProjectileService.java`**（怪物 SUMMON projectile 出口）：

```java
package io.mczju.maggoteers.mob;

import io.mczju.maggoteers.effect.ProjectileHomingService;
import io.mczju.maggoteers.effect.SummonParams;
import io.mczju.maggoteers.game.MaggoteersGame;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Snowball;
import org.bukkit.util.Vector;

import java.util.Locale;

/** 怪物侧弹道发射：可选 homing（缺省追最近玩家，或按 homingTarget 预设 + initialTarget）。 */
public final class MobProjectileService {

    private MobProjectileService() {}

    /** initialTarget：attack_target/damage_source/killer 预设的触发上下文目标（可 null）。 */
    public static Projectile shoot(LivingEntity shooter, MaggoteersGame game,
                                   SummonParams.ProjectileParams pp,
                                   String homingTarget, LivingEntity initialTarget) {
        if (shooter == null || pp == null) return null;
        double speed = Math.max(0.1, pp.speed());
        Projectile proj = shooter.launchProjectile(Snowball.class, forward(shooter, speed));
        if (proj != null) {
            proj.setShooter(shooter);
            String t = homingTarget == null ? null : homingTarget.trim().toLowerCase(Locale.ROOT);
            ProjectileHomingService.home(game, proj, initialTarget,
                    t == null ? "nearest_player" : t);
        }
        return proj;
    }

    private static Vector forward(LivingEntity shooter, double speed) {
        return shooter.getLocation().getDirection().normalize().multiply(speed);
    }
}
```

**`TeleportSafety.java`**：

```java
package io.mczju.maggoteers.mob;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;

/** 传送安全：候选落点避开虚空与地图外（结构边界外）。找不到安全点返回 null（调用方不传）。 */
public final class TeleportSafety {

    /** 结构外判定半径（自建虚空世界无 WorldBorder；用原点 ± 该值近似地图边界）。 */
    private static final double MAP_RADIUS = 160.0;
    private static final double VOID_FLOOR = -32.0;   // 低于此且脚下无方块 = 虚空

    private TeleportSafety() {}

    /** 纯逻辑（可测）：绝对坐标距层原点 (0,0) 超出 MAP_RADIUS 即视为出图。 */
    static boolean outsideMap(double x, double z) {
        return Math.hypot(x, z) > MAP_RADIUS;
    }

    /** 纯逻辑（可测）：可站立地面判定。 */
    static boolean isSolidGround(Material t) {
        return t != null && t.isSolid() && t != Material.BEDROCK;
    }

    /** 在 anchor 上方 offsetY 找安全点：向下扫描找实心地面上的空位；越界/虚空则 null。 */
    public static Location safeTarget(LivingEntity anchor, double offsetY) {
        if (anchor == null || anchor.getWorld() == null) return null;
        Location base = anchor.getLocation().clone().add(0, offsetY, 0);
        if (outsideMap(base.getX(), base.getZ())) return null;
        // 向下扫 6 格：找"脚下实心 + 自身方块可站立"
        for (int dy = 0; dy >= -6; dy--) {
            Location cand = base.clone().add(0, dy, 0);
            Block foot = cand.getBlock();
            Block below = cand.clone().add(0, -1, 0).getBlock();
            if (foot.isLiquid() || foot.getType().isAir()) {
                if (isSolidGround(below.getType()) && cand.getY() > VOID_FLOOR) {
                    return cand.clone().add(0.5, 0, 0.5);
                }
            }
        }
        return null;
    }
}
```
> `outsideMap` 的原点 (0,0) 是层原点近似（结构往 +X/+Z 展开、负向通常虚空）；执行时可按 `MapPoints`（`config.yml act_origins`）把该层原点传进来替换常量 0，语义不变。

- [ ] **Step 5: WaveEngine.nearestTrackedMob**

**`WaveEngine.java`** 追加方法（与 `gameOfEntity` 并列）：

```java
    /** 以 loc 为圆心、radius 内最近的本局追踪怪（供 homing 目标选择）。 */
    public static LivingEntity nearestTrackedMob(Location loc, MaggoteersGame game, double radius) {
        if (loc == null || game == null || loc.getWorld() == null) return null;
        WaveRuntime rt = RUNTIMES.get(game);
        if (rt == null) return null;
        LivingEntity best = null;
        double bestSq = radius * radius;
        for (UUID uuid : rt.livingMobs) {
            Entity raw = Bukkit.getEntity(uuid);
            if (!(raw instanceof LivingEntity le) || le.isDead() || !le.isValid()) continue;
            double d = le.getLocation().distanceSquared(loc);
            if (d < bestSq) {
                bestSq = d;
                best = le;
            }
        }
        return best;
    }
```
> 顶部已有 `import io.mczju.maggoteers.game.MaggoteersGame;`（Task 6 gameOfEntity 时确认；若缺则补）。

- [ ] **Step 6: 玩家侧 SummonExecutor.applyProjectile 登记 homing**

**`SummonExecutor.java`** 的 `applyProjectile`，在 `setVelocity` 之后补：

```java
            proj.setVelocity(vel);
            if (params.homingTarget() != null) {
                ProjectileHomingService.home(game, proj, null, params.homingTarget());
            }
```
> `ProjectileHomingService` 在 `effect` 包（`SummonExecutor` 同包，无需 import）。玩家右键发射无触发上下文目标 → initialTarget 传 `null`；homingTarget 由配置给（玩家侧用 `nearest_enemy`）。若 `applyProjectile` 现签名无 `game` 参数，从既有调用链传入（`spawn(...)` 已有 `MaggoteersGame`）。

- [ ] **Step 7: 怪物 SUMMON/TELEPORT 效果**

**`MobEffectExecutor.java`** 的 `execute` switch 改为：

```java
        switch (es.effect()) {
            case HEALTH -> applyHealth(mob, es, target);
            case ATTRIBUTE -> applyAttribute(mob, es, target);
            case POTION -> applyPotion(mob, es, target);
            case SUMMON -> applySummon(mob, es, target, source);
            case TELEPORT -> applyTeleport(mob, es, target);
            default -> { }
        }
```

新增两个 private static 方法：

```java
    private static void applySummon(LivingEntity mob, MobEffectSpec es,
                                    LivingEntity target, LivingEntity source) {
        EffectContext p = es.params();
        EntityType type = p.get(EffectKeys.ENTITY_TYPE);
        if (type == null) {
            String name = p.get(EffectKeys.ENTITY_NAME);
            if (name != null) type = GameRegistries.entityType(name);
        }
        if (type == null) return;
        MaggoteersGame game = gameOf(mob.getUniqueId());
        if (game == null) return;
        int count = Math.max(1, p.getOrDefault(EffectKeys.COUNT, 1));
        double offsetY = p.getOrDefault(EffectKeys.OFFSET_Y, 0.0);
        Boolean projectile = p.get(EffectKeys.PROJECTILE);
        if (Boolean.TRUE.equals(projectile)) {
            double speed = p.getOrDefault(EffectKeys.PROJECTILE_SPEED, 1.0);
            String homing = p.get(EffectKeys.HOMING_TARGET);
            String h = homing == null ? "" : homing.trim().toLowerCase(java.util.Locale.ROOT);
            // initialTarget 按 homing 预设选触发上下文：attack_target→target；damage_source/killer→source
            LivingEntity init = null;
            if ("attack_target".equals(h)) init = target;
            else if ("damage_source".equals(h) || "killer".equals(h)) init = source;
            for (int i = 0; i < count; i++) {
                MobProjectileService.shoot(mob, game,
                        new SummonParams.ProjectileParams(speed), homing, init);
            }
            return;
        }
        for (int i = 0; i < count; i++) {
            Location at = mob.getLocation().clone().add(0, offsetY + 0.8 * i, 0);
            LivingEntity spawned = (LivingEntity) mob.getWorld().spawnEntity(at, type);
            spawned.setTarget(target instanceof org.bukkit.entity.Mob m ? m : null);
        }
    }
```

    private static void applyTeleport(LivingEntity mob, MobEffectSpec es, LivingEntity target) {
        EffectContext p = es.params();
        String t = p.get(EffectKeys.TARGETS);
        boolean toTarget = t != null && "target".equalsIgnoreCase(t.trim());
        LivingEntity anchor = toTarget && target != null ? target : mob;
        double offsetY = p.getOrDefault(EffectKeys.OFFSET_Y, 2.0);
        Location dest = TeleportSafety.safeTarget(anchor, offsetY);
        if (dest != null) mob.teleport(dest);
    }

    private static MaggoteersGame gameOf(UUID uuid) {
        AbstractGame g = WaveEngine.gameOfEntity(uuid);
        return g instanceof MaggoteersGame mg ? mg : null;
    }
```

顶部 imports 补：`io.mczju.maggoteers.effect.SummonParams`、`com.github.mczjuops.mczjugamecore.game.AbstractGame`、`io.mczju.maggoteers.game.MaggoteersGame`、`org.bukkit.entity.EntityType`、`org.bukkit.Location`、`org.bukkit.entity.Mob`、`java.util.UUID`。

- [ ] **Step 8: MaggoteersPlugin 启动 ProjectileHomingService**

**`MaggoteersPlugin.java`** 中 `MobSkillService.start(this);` 旁加：

```java
        io.mczju.maggoteers.effect.ProjectileHomingService.start(this);
```

- [ ] **Step 9: 运行确认通过**

Run: `export JAVA_HOME=/home/chalcanthite/.local/jdk-25 && export PATH="$JAVA_HOME/bin:$PATH" && mvn -o -q test`
Expected: 全绿（SummonParamsParserTest 新增 2 用例 + TeleportSafetyTest 2 用例；SummonParams 加组件——既有 `new SummonParams(...)` 因保留 11 参旧 ctor 不破）

- [ ] **Step 10: 手动验证（测试服）**

1. `mob_skills.yml` 给某怪加 `summon`（`projectile: true` + `homing_target: nearest_player`）→ 怪喷出的弹道转弯追最近的玩家。
2. 魔法武器 SUMMON projectile + `params: {homing_target: nearest_enemy}` → 弹道追最近的追踪怪。
3. 给怪加 `summon`（`projectile: true` + `homing_target: damage_source`，`trigger: damage_taken`）→ 被打时喷弹道反身追打你的人。
4. 给怪加 `teleport` 效果（DAMAGE_TAKEN 触发）→ 被打时传送到目标身边，且不会掉进虚空。
5. 把怪引到世界边缘（原点 ±160 外）再触发 TELEPORT → 不传送（保持原位）。

- [ ] **Step 11: Commit**

```bash
git add -A
git commit -m "feat(mob): homing projectiles (player+mob) + summon/teleport mob effects + teleport safety"
```

---

### Task 8: 迁移 waves.yml（affixes/infernal → skills）+ schema 测试 + 文档同步

> 用一次性脚本把 `waves.yml` 全部旧词缀写法迁移到新技能写法，并加 schema 测试**防止回潮**（未来任何 commit 再引入 `affixes:`/`infernal:` 都会红）。最后同步 dev-log 与 CLAUDE.md。

**Files:**
- Create: `scripts/migrate_mob_skills.py`（迁移脚本，PyYAML）
- Create: `src/test/java/io/mczju/maggoteers/config/WaveSkillSchemaTest.java`
- Modify: `src/main/resources/waves.yml`（迁移后：无 affixes/infernal；注释更新）
- Modify: `src/main/resources/mob_skills.yml`（补齐 waves.yml 引用的全部 skill id）
- Modify: `docs/dev-log.md`（顶部追加 2026-08-20 条目）
- Modify: `CLAUDE.md`（§7.4 waves.yml 草案、§7.5 affixes.yml、§14 扩展点表、§18 风险 去 IM 引用）
- Delete: `src/main/resources/affixes.yml`（Task 5 已删 AffixService；残留资源一并删）

**Interfaces:**
- Consumes: `mob_skills.yml` 顶层 `skills:`（Task 4）；`waves.yml` `strategies[].steps[].skills`（Task 5 schema）。
- Produces:
  - `WaveSkillSchemaTest` 两条断言：waves.yml 无 `affixes`/`infernal` 字符串；waves.yml 每个 `skills` 引用 id 都定义于 mob_skills.yml `skills:`。
  - 迁移后的 `waves.yml`（纯 skills 写法）。

- [ ] **Step 1: 写失败测试（WaveSkillSchemaTest）**

**`src/test/java/io/mczju/maggoteers/config/WaveSkillSchemaTest.java`**：

```java
package io.mczju.maggoteers.config;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/** 1.1 怪物技能系统迁移后的 waves.yml schema 校验。 */
class WaveSkillSchemaTest {

    private static final Pattern FLOW_ID = Pattern.compile("skills\\s*:\\s*\\[([^\\]]*)\\]");
    private static final Pattern BLOCK_ID = Pattern.compile("^[ \\t]*-[ \\t]*([A-Za-z0-9_]+)[ \\t]*$", Pattern.MULTILINE);

    @Test
    void wavesYamlHasNoLegacyAffixOrInfernalKeys() throws Exception {
        String text = resourceText("waves.yml");
        assertFalse(text.contains("affixes"), "legacy affixes key still present in waves.yml");
        assertFalse(text.contains("infernal"), "legacy infernal key still present in waves.yml");
    }

    @Test
    void everyWavesSkillIdIsDefinedInMobSkillsYaml() throws Exception {
        String wavesText = resourceText("waves.yml");
        String skillsText = resourceText("mob_skills.yml");
        assertTrue(skillsText.contains("skills:"), "mob_skills.yml missing top-level skills:");

        // 收集 waves.yml 全部 skills 引用 id（flow + block 两种写法）
        Set<String> used = collectSkillIds(wavesText);
        // 收集 mob_skills.yml 顶层 skills 定义 id（缩进键，非 - id 行）
        Set<String> defined = collectDefinedSkillIds(skillsText);

        List<String> missing = used.stream().filter(id -> !defined.contains(id)).distinct().toList();
        assertTrue(missing.isEmpty(), () -> "waves.yml references undefined skills: " + missing);
    }

    /** waves.yml 引用侧：收集 flow（skills: [a, b]）与 block（- a）两种写法的 id。 */
    private static Set<String> collectSkillIds(String yamlText) {
        List<String> ids = new ArrayList<>();
        Matcher flow = FLOW_ID.matcher(yamlText);
        while (flow.find()) {
            for (String part : flow.group(1).split(",")) {
                String s = part.trim();
                if (!s.isEmpty()) ids.add(s);
            }
        }
        // block 写法：仅统计位于 skills: 下缩进的 - id 行
        boolean inSkillsBlock = false;
        for (String line : yamlText.split("\n")) {
            String t = line.trim();
            if (t.startsWith("skills:")) {
                inSkillsBlock = true;
                continue;
            }
            if (inSkillsBlock) {
                Matcher m = BLOCK_ID.matcher(t);
                if (m.matches()) {
                    ids.add(m.group(1));
                } else if (!t.startsWith("-")) {
                    inSkillsBlock = false;   // 离开 skills 块
                }
            }
        }
        return Set.copyOf(ids);
    }

    /** mob_skills.yml 定义侧：解析顶层 skills: 下第一层缩进键名（定义是 id: 键，不是 - id）。 */
    private static Set<String> collectDefinedSkillIds(String yamlText) {
        List<String> ids = new ArrayList<>();
        boolean inTop = false;
        for (String line : yamlText.split("\n")) {
            String t = line.trim();
            if (t.startsWith("skills:")) { inTop = true; continue; }
            if (!inTop) continue;
            if (t.isEmpty() || t.startsWith("#") || t.startsWith("-")) continue;
            if (!line.startsWith(" ") && t.contains(":")) { break; }   // 离开 skills 顶层块
            String id = t.substring(0, t.indexOf(':')).trim();
            if (!id.isEmpty()) ids.add(id);
        }
        return Set.copyOf(ids);
    }

    private static String resourceText(String name) throws Exception {
        InputStream in = WaveSkillSchemaTest.class.getClassLoader().getResourceAsStream(name);
        assertNotNull(in, name);
        return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
}
```

> 若 Task 4 的 mob_skills.yml 顶层结构不是 `skills:`（例如直接顶层 id），按实际结构调整 `collectDefinedSkillIds`——**以 Task 4 定义为准**。

- [ ] **Step 2: 运行确认失败**

Run: `export JAVA_HOME=/home/chalcanthite/.local/jdk-25 && export PATH="$JAVA_HOME/bin:$PATH" && mvn -o -q test -Dtest=WaveSkillSchemaTest`
Expected: FAIL（waves.yml 含 `affixes:` / `infernal:`）

- [ ] **Step 3: 写迁移脚本 migrate_mob_skills.py**

**`scripts/migrate_mob_skills.py`**：

```python
#!/usr/bin/env python3
"""迁移 waves.yml：affixes/infernal → skills（怪物技能系统 1.1）。

规则（作用于 step / passengers / on_death 任意层级 Map）：
  - 键重命名：affixes: [...] → skills: [...]
  - infernal 块删除；其 affixes 并入同 Map 的 skills（无 affixes 则整个丢弃）
  - 同一 Map 既有 affixes 又有 infernal.affixes → 合并去重为一个 skills 列表

副作用：PyYAML dump 会丢失注释与手写格式。waves.yml 头部注释含 IM 说明，
本就要移除（Task 8 Step 5 会用新注释重写头部），接受。

用法：python3 scripts/migrate_mob_skills.py
"""
from __future__ import annotations

from pathlib import Path

import yaml

ROOT = Path(__file__).resolve().parents[1]
WAVES = ROOT / "src/main/resources/waves.yml"


def transform(node):
    """递归：Map 内 affixes→skills；infernal 展开并入 skills。返回新结构。"""
    if isinstance(node, dict):
        out = {}
        skills = None
        for k, v in node.items():
            if k == "affixes":
                if isinstance(v, list):
                    skills = list(v)
                continue
            if k == "infernal":
                if isinstance(v, dict) and isinstance(v.get("affixes"), list):
                    merged = list(v["affixes"])
                    if skills:
                        merged = skills + merged
                    skills = merged
                continue
            out[k] = transform(v)
        if skills is not None:
            existing = out.get("skills")
            if existing is not None and isinstance(existing, list):
                for s in skills:
                    if s not in existing:
                        existing.append(s)
            else:
                out["skills"] = skills
        return out
    if isinstance(node, list):
        return [transform(x) for x in node]
    return node


def main() -> None:
    if not WAVES.exists():
        raise SystemExit("missing " + str(WAVES))
    with WAVES.open(encoding="utf-8") as f:
        data = yaml.safe_load(f) or {}
    migrated = transform(data)
    with WAVES.open("w", encoding="utf-8") as f:
        yaml.safe_dump(migrated, f, sort_keys=False, allow_unicode=True)
    print("migrated " + str(WAVES))


if __name__ == "__main__":
    main()
```

- [ ] **Step 4: 运行迁移脚本**

Run: `python3 scripts/migrate_mob_skills.py`
Expected: `migrated .../src/main/resources/waves.yml`；随后 `grep -n "affixes\|infernal" src/main/resources/waves.yml` 无输出。

- [ ] **Step 5: 重写 waves.yml 头部注释（去 IM，介绍技能系统）**

迁移后头部前 12 行（原 IM 注释 + 说明）替换为：

```yaml
# waveplan implementation (2026-07-26). Single source for strategies and pools.
# delay = step pre-wait seconds; coeff may include hp/dmg/speed/scale/follow_range
# delay = 相对【同 point】上一刷怪时刻的等待秒数（该点尚无事件则相对本轮起点）；不同 point 时间轴独立、可并行
# strategy.repeat = 整份 steps 再跑一轮（每轮各 point 时钟归零，轮起点=上一轮最晚时刻）
# strategy.关卡名称 = Chinese display name (chat begin tip + scoreboard)
# steps[].repeat = 单条 step 在同 point 时间轴上按 delay 再刷几次（默认 1）
# skills: mob_skills.yml 的怪物技能 id 列表（trigger+effect 全可配置；1.1 起替代旧 affixes/infernal）
```

- [ ] **Step 6: 补齐 mob_skills.yml 未定义的引用 id**

Run: `python3 - <<'PY'
import re
from pathlib import Path
w = (Path("src/main/resources/waves.yml")).read_text(encoding="utf-8")
used = set(re.findall(r"-?\s*([A-Za-z0-9_]+)\s*(?:[,}\]]|\n)", w))
import yaml
skills = yaml.safe_load(Path("src/main/resources/mob_skills.yml").read_text(encoding="utf-8"))
defined = set((skills.get("skills") or {}).keys()) if isinstance(skills.get("skills"), dict) else set(skills.keys())
# 只取出现在 skills: 上下文里的引用（精确性以 grep -A2 "skills:" 人工复核）
print("defined:", sorted(defined))
print("need check:", sorted(x for x in used if x in ("plastic","infected","quicksand","archer","tosser","invisible","cloaked","lifesteal","mechanical","antifreeze","fireresistance","sprint","armored","berserk","necromancer","flame")))
PY`

Expected: `need check` 列出的 id 全部出现在 `defined`。缺失的 id 在 `mob_skills.yml` 按 Task 4 模板补一条（trigger/effect 参照 Task 4 已定义的同语义旧 affix——plastic=生成时抗性提升、cloaked=隐身、archer=攻击时给目标缓慢、tosser=攻击时击退等；若旧 affix 在 affixes.yml 有定义则迁移其数值语义）。

> ⚠️ **内容增删零代码**：mob_skills.yml 缺失的 id 必须补定义，否则 `WaveSkillSchemaTest.everyWavesSkillIdIsDefinedInMobSkillsYaml` 红且运行时该怪无技能。删除 `affixes.yml` 前先对照——Task 5 已删 Affix/AffixService，本 Step 把 waves.yml 引用的旧 affix 语义（affixes.yml 里 hp/dmg/potion 数值）落成 mob_skills.yml 技能条目。

- [ ] **Step 7: 删除 affixes.yml + 运行全量测试**

```bash
rm src/main/resources/affixes.yml
export JAVA_HOME=/home/chalcanthite/.local/jdk-25 && export PATH="$JAVA_HOME/bin:$PATH" && mvn -o -q test
```
Expected: 全绿（WaveSkillSchemaTest 2 用例通过；Task 5 已删 RetiredAffixConfigTest，无残留引用 affixes.yml 的测试）

- [ ] **Step 8: 手动验证（测试服）**

1. 清一波有 `skills: [infected]` 的僵尸 → 生成时按 mob_skills.yml 施加技能（如生成时加抗性/移速）。
2. `waves.yml` 迁移后首启无 parse 错误（`MobYamlParser` 走 parseSkills fallback 已不需要——迁移后无 affixes/infernal，直接读 skills）。
3. `/maggoteers debug spawnmob ZOMBIE plastic` → 生成带技能。

- [ ] **Step 9: 追加 dev-log（顶部，日期倒序）**

在 `docs/dev-log.md` 第 8 行 `## 2026-08-17` 之前插入：

```markdown
## 2026-08-20 — 怪物技能系统 1.1：计数器 + 计时器合一 + 怪物技能（plan）

按 `docs/superpowers/plans/2026-08-20-maggoteers-1.1-counters-mob-skills.md` 完成 1.1 三部分：

1. **计数器（玩家侧）**：`CounterService` + `CounterSpec` + `CounterCondition`（HOLD_ITEM_PDC / PLAYER_IN_RADIUS，AND/OR/NOT 组合）；新 trigger `ON_COUNTER`。
2. **计时器并入计数器**：计时器 = `count: tick` 的计数器（每秒 1 次，`amount: N` = N 秒触发一次，清零循环）。**删除独立 ON_TIMER / tickTimers 状态机**——同一套 `ON_COUNTER`。
3. **怪物技能系统**：移除 InfernalMobs 依赖与旧 affixes 系统；`mob_skills.yml` 可配置 `trigger（attack/damage_taken/killed/spawn/tick/counter）+ effect（health/attribute/potion/summon/teleport）`；怪物计数器出生加载、死亡卸载；条件以怪物为中心。waves.yml 已迁移（affixes/infernal → skills）。

**决策与原因**
- 计数器与计时器同构 → 只保留 `ON_COUNTER`（避免双 trigger 状态机）。
- 怪物技能链路复用玩家 CounterCondition 范式但以怪物为中心判断。
- homing 弹道纯原版每 tick 转向（不依赖 IM 追踪能力）。
- 迁移脚本用 PyYAML dump（丢注释），waves.yml 头部注释顺带去 IM。

**遗留 / 待实现期核实**
- mob_skills.yml 旧 affix 数值语义迁移后的平衡性测试。
- TELEPORT 的 `outsideMap` 常量半径（160）在正式地图尺寸确认后调整。
- ItemCreator 版本 / jitpack 坐标（沿用前代）。
```

- [ ] **Step 10: 同步 CLAUDE.md（去 IM 引用，指向技能系统）**

逐项修改：
- **§7.4 waves.yml 草案**：`steps:` 示例中 `affixes: [armored]` → `skills: [armored]`；删除 `# 可选 IM 技能（...）` 注释块与 `infernal:` 示例，替换为 `# skills: 引用 mob_skills.yml 的怪物技能 id（可配置 trigger+effect）`。
- **§7.5 affixes.yml**：整节替换为 mob_skills.yml 小节（`skills:` 顶层；`trigger/effect/params/cooldown_sec/condition/counter` schema 简述 + 一句"见 mob_skills.yml 全文与 plan"）。
- **§14 扩展点表**：`新词缀（原生 hp/dmg/速度/掉落/体型）` 行 → `新怪物技能（trigger+effect）`：`mob_skills.yml` 加条目，否；删 `IM 战斗技能` 行；`新触发类型`/`新 Effect 类型` 行补怪物侧同理。
- **§18 风险**：删 `InfernalMobs 反射（IM1）` 条，改为 `怪物技能运行时` 条（attach/detach 泄漏、homing 每 tick 开销、TeleportSafety 边界）。
- **§2.3/§7.2**：`infernal: 块` 相关描述删除；`MobFactory` 中 "mechanize" 描述改技能 attach。
- **§7.6 config.yml**：`mob_attributes` 保留；删 `debug` 里 IM 相关说明（若存在）。
- **§16.1 部署清单**：`若使用 infernal: 块：测试服 plugins/ 有 InfernalMobs JAR` 行 → `waves.yml 引用 mob_skills.yml 技能 id，无需 IM JAR`。

> 用 `grep -rn "InfernalMobs\|infernal\|affix" CLAUDE.md` 逐个核对清理；保留 `docs/spec/` 历史设计文档不动（历史记录）。

- [ ] **Step 11: Commit**

```bash
git add -A
git commit -m "refactor(mob): migrate waves.yml to mob skills system + schema guard + docs"
```
