# InfernalMobs 波次接入实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**目标：** 在不修改 MCZJUinfernalMobs 源码的前提下，让 `waves.yml` 能为主体、乘客和亡语生成物精确配置 IM 技能，并退役 Maggoteers 原有 `on:` 战斗词缀系统及 IM 死亡奖励。

**架构：** 新增纯配置值 `InfernalCfg`，沿现有 Config → RunPlan → MobFactory 数据流传递。`InfernalMobsBridge` 使用 IM 插件类加载器反射私有 `mobFactory`，只调用精确列表入口 `mechanizeWithAffixes`；成功注册的 UUID 由桥接层记录。死亡时先于 IM 的 `NORMAL` 监听器注销状态，从而阻止 IM 战利品、统计、消息和死亡技能。

**技术栈：** Java 25、Paper 26.2、Maven、JUnit 5、Bukkit PluginManager、Java Reflection、YAML

## 全局约束

- Paper API 固定为 `26.2.build.62-beta`，Java/Maven compiler release 固定为 25。
- MCZJUinfernalMobs 源码和构建产物不得修改；Maggoteers 也不得新增对 IM JAR 的编译期依赖。
- IM 仅通过 `plugin.yml` 的 `softdepend: InfernalMobs` 和运行时反射接入。
- 只调用 `mechanizeWithAffixes(LivingEntity, Location, int, List<String>)`；不得调用 IM 命令、自然生成入口或随机补全入口。
- `infernal.level` 仅接受 1–100；主体、乘客、亡语节点各自显式配置且互不继承。
- 禁用 `morph`、`mama`、`mounted`、`vexsummoner`、`ghost`。
- IM 缺失、签名不兼容、未知技能或单次调用失败时，保留普通且受 WaveEngine 追踪的实体。
- 本插件创建的 IM 怪物不得进入 IM 死亡奖励、统计、消息或死亡技能流程。
- 保留 Maggoteers 出生型/倍率词缀；删除旧 `on:` 战斗词缀及其全部默认波次引用，不自动迁移成 IM 技能。
- 当前工作树含大量既有未提交改动。执行时不得覆盖、回退或顺带暂存无关改动；只有用户明确要求提交且已核对 staged diff 时才执行计划中的提交步骤。

---

## 文件结构

**新增：**

- `src/main/java/io/mczju/maggoteers/config/InfernalCfg.java`：不可变 IM 等级与技能列表。
- `src/main/java/io/mczju/maggoteers/integration/InfernalMobsBridge.java`：技能过滤、反射调用、受管 UUID 和提前注销。
- `src/test/java/io/mczju/maggoteers/config/InfernalCfgTest.java`
- `src/test/java/io/mczju/maggoteers/config/MobYamlParserTest.java`
- `src/test/java/io/mczju/maggoteers/integration/InfernalMobsBridgeTest.java`
- `src/test/java/io/mczju/maggoteers/listener/MobDeathListenerTest.java`
- `src/test/java/io/mczju/maggoteers/config/RetiredAffixConfigTest.java`

**修改：**

- `src/main/java/io/mczju/maggoteers/config/{StepCfg,PassengerCfg,DeathSpawnCfg,MobYamlParser,WavesConfig,Affix,AffixService}.java`
- `src/main/java/io/mczju/maggoteers/wave/{SpawnStep,PassengerSpawn,DeathSpawn,MobSpawnProfile,WaveSpec,WaveEngine}.java`
- `src/main/java/io/mczju/maggoteers/plan/RunPlanner.java`
- `src/main/java/io/mczju/maggoteers/mob/MobFactory.java`
- `src/main/java/io/mczju/maggoteers/listener/MobDeathListener.java`
- `src/main/java/io/mczju/maggoteers/MaggoteersPlugin.java`
- `src/main/resources/{plugin.yml,affixes.yml,waves.yml}`
- `scripts/gen_waveplan_waves.py`
- `src/test/java/io/mczju/maggoteers/{plan/ComposeTest.java,plan/RunPlannerTest.java,wave/WaveSpecTest.java}`
- `CLAUDE.md`
- `.cursor/skills/maggoteers-plugin-config/{SKILL.md,reference.md}`
- `docs/dev-log.md`
- `docs/superpowers/specs/2026-07-26-affix-trigger-refactor-design.md`

**删除：**

- `src/main/java/io/mczju/maggoteers/config/AffixTrigger.java`
- `src/main/java/io/mczju/maggoteers/effect/AffixCombatService.java`
- `src/main/java/io/mczju/maggoteers/listener/CombatAffixListener.java`
- `src/test/java/io/mczju/maggoteers/config/AffixTriggerTest.java`
- `src/test/java/io/mczju/maggoteers/effect/AffixCombatServiceTest.java`

---

### Task 1：建立 `infernal` 配置模型并贯通 RunPlan

**Files:**
- Create: `src/main/java/io/mczju/maggoteers/config/InfernalCfg.java`
- Create: `src/test/java/io/mczju/maggoteers/config/InfernalCfgTest.java`
- Create: `src/test/java/io/mczju/maggoteers/config/MobYamlParserTest.java`
- Modify: `src/main/java/io/mczju/maggoteers/config/StepCfg.java`
- Modify: `src/main/java/io/mczju/maggoteers/config/PassengerCfg.java`
- Modify: `src/main/java/io/mczju/maggoteers/config/DeathSpawnCfg.java`
- Modify: `src/main/java/io/mczju/maggoteers/config/MobYamlParser.java`
- Modify: `src/main/java/io/mczju/maggoteers/config/WavesConfig.java`
- Modify: `src/main/java/io/mczju/maggoteers/wave/SpawnStep.java`
- Modify: `src/main/java/io/mczju/maggoteers/wave/PassengerSpawn.java`
- Modify: `src/main/java/io/mczju/maggoteers/wave/DeathSpawn.java`
- Modify: `src/main/java/io/mczju/maggoteers/wave/MobSpawnProfile.java`
- Modify: `src/main/java/io/mczju/maggoteers/wave/WaveSpec.java`
- Modify: `src/main/java/io/mczju/maggoteers/plan/RunPlanner.java`
- Modify: `src/test/java/io/mczju/maggoteers/plan/RunPlannerTest.java`
- Modify: `src/test/java/io/mczju/maggoteers/wave/WaveSpecTest.java`

**Interfaces:**
- Produces: `InfernalCfg(int level, List<String> affixes)`, `InfernalCfg.NONE`, `enabled()`
- Produces: `MobYamlParser.parseInfernal(Map<?, ?>): InfernalCfg`
- Produces: `infernal()` accessor on all config/runtime mob node records and `MobSpawnProfile`

- [ ] **Step 1: 写配置值的失败测试**

```java
package io.mczju.maggoteers.config;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class InfernalCfgTest {
    @Test void normalizesIdsAndCopiesInput() {
        var ids = new java.util.ArrayList<>(List.of(" Poisonous ", "SPRINT", "poisonous"));
        InfernalCfg cfg = new InfernalCfg(3, ids);
        ids.clear();
        assertEquals(3, cfg.level());
        assertEquals(List.of("poisonous", "sprint"), cfg.affixes());
        assertTrue(cfg.enabled());
    }

    @Test void noneIsDisabled() {
        assertFalse(InfernalCfg.NONE.enabled());
        assertEquals(1, InfernalCfg.NONE.level());
    }

    @Test void rejectsOutOfRangeLevels() {
        assertThrows(IllegalArgumentException.class, () -> new InfernalCfg(0, List.of("sprint")));
        assertThrows(IllegalArgumentException.class, () -> new InfernalCfg(101, List.of("sprint")));
    }
}
```

- [ ] **Step 2: 运行测试并确认因类型不存在而失败**

Run:

```powershell
$mvn = "E:/Intellij_Idea/IntelliJ IDEA 2025.3.2/plugins/maven/lib/maven3/bin/mvn.cmd"
& $mvn -Dtest=InfernalCfgTest test
```

Expected: FAIL，包含 `cannot find symbol: class InfernalCfg`。

- [ ] **Step 3: 实现不可变配置值**

```java
package io.mczju.maggoteers.config;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

public record InfernalCfg(int level, List<String> affixes) {
    public static final InfernalCfg NONE = new InfernalCfg(1, List.of());

    public InfernalCfg {
        if (level < 1 || level > 100) {
            throw new IllegalArgumentException("infernal.level 必须在 1..100，实际为 " + level);
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        if (affixes != null) {
            for (String id : affixes) {
                if (id == null || id.isBlank()) continue;
                normalized.add(id.trim().toLowerCase(Locale.ROOT));
            }
        }
        affixes = List.copyOf(normalized);
    }

    public boolean enabled() {
        return !affixes.isEmpty();
    }
}
```

- [ ] **Step 4: 写解析器的失败测试**

```java
package io.mczju.maggoteers.config;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class MobYamlParserTest {
    @Test void parsesInfernalBlock() {
        InfernalCfg cfg = MobYamlParser.parseInfernal(Map.of(
                "infernal", Map.of("level", 4, "affixes", List.of("poisonous", "sprint"))));
        assertEquals(4, cfg.level());
        assertEquals(List.of("poisonous", "sprint"), cfg.affixes());
    }

    @Test void missingBlockReturnsNone() {
        assertSame(InfernalCfg.NONE, MobYamlParser.parseInfernal(Map.of()));
    }

    @Test void invalidLevelFailsWithContext() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> MobYamlParser.parseInfernal(Map.of(
                        "infernal", Map.of("level", 0, "affixes", List.of("sprint")))));
        assertTrue(ex.getMessage().contains("infernal.level"));
    }
}
```

- [ ] **Step 5: 实现共享解析函数**

在 `MobYamlParser` 增加：

```java
public static InfernalCfg parseInfernal(Map<?, ?> m) {
    if (!(m.get("infernal") instanceof Map<?, ?> raw)) return InfernalCfg.NONE;
    int level = num(raw.get("level"), 1).intValue();
    List<String> ids = new ArrayList<>();
    if (raw.get("affixes") instanceof List<?> list) {
        for (Object value : list) ids.add(String.valueOf(value));
    }
    try {
        return new InfernalCfg(level, ids);
    } catch (IllegalArgumentException e) {
        throw new IllegalStateException("非法 infernal.level: " + level, e);
    }
}
```

在 `parsePassengersDepth` 和 `parseOnDeathDepth` 构造记录时传入 `parseInfernal(m)`；在 `WavesConfig.parseStrategy` 构造 `StepCfg` 时同样传入。

- [ ] **Step 6: 给配置记录增加非空 `infernal` 字段**

采用以下字段顺序，并让兼容构造器统一传 `InfernalCfg.NONE`：

```java
// StepCfg
public record StepCfg(String point, EntityType type, int count, CoeffCfg coeff,
                      int delaySec, List<String> affixes, InfernalCfg infernal,
                      List<MobEquipment> equipment, List<DeathSpawnCfg> onDeath,
                      List<PassengerCfg> passengers) {
    public StepCfg {
        affixes = affixes == null ? List.of() : List.copyOf(affixes);
        infernal = infernal == null ? InfernalCfg.NONE : infernal;
        equipment = equipment == null ? List.of() : List.copyOf(equipment);
        onDeath = onDeath == null ? List.of() : List.copyOf(onDeath);
        passengers = passengers == null ? List.of() : List.copyOf(passengers);
    }

    public StepCfg(String point, EntityType type, int count, CoeffCfg coeff,
                   int delaySec, List<String> affixes) {
        this(point, type, count, coeff, delaySec, affixes, InfernalCfg.NONE,
                List.of(), List.of(), List.of());
    }
}

// PassengerCfg
public record PassengerCfg(EntityType type, int count, CoeffCfg coeff,
                           List<String> affixes, InfernalCfg infernal,
                           List<MobEquipment> equipment, List<DeathSpawnCfg> onDeath,
                           List<PassengerCfg> passengers) {
    public PassengerCfg {
        affixes = affixes == null ? List.of() : List.copyOf(affixes);
        infernal = infernal == null ? InfernalCfg.NONE : infernal;
        equipment = equipment == null ? List.of() : List.copyOf(equipment);
        onDeath = onDeath == null ? List.of() : List.copyOf(onDeath);
        passengers = passengers == null ? List.of() : List.copyOf(passengers);
        if (count < 1) count = 1;
    }

    public PassengerCfg(EntityType type, int count, CoeffCfg coeff, List<String> affixes) {
        this(type, count, coeff, affixes, InfernalCfg.NONE,
                List.of(), List.of(), List.of());
    }
}

// DeathSpawnCfg
public record DeathSpawnCfg(EntityType type, int count, CoeffCfg coeff,
                            List<String> affixes, InfernalCfg infernal,
                            List<MobEquipment> equipment, List<DeathSpawnCfg> onDeath) {
    public DeathSpawnCfg {
        affixes = affixes == null ? List.of() : List.copyOf(affixes);
        infernal = infernal == null ? InfernalCfg.NONE : infernal;
        equipment = equipment == null ? List.of() : List.copyOf(equipment);
        onDeath = onDeath == null ? List.of() : List.copyOf(onDeath);
        if (count < 1) count = 1;
    }
}
```

- [ ] **Step 7: 给运行时记录增加 `infernal` 字段并确保展开时保留**

在 `SpawnStep`、`PassengerSpawn`、`DeathSpawn` 的 `potions` 后增加 `InfernalCfg infernal`，canonical constructor 将 null 转为 `InfernalCfg.NONE`。更新 `WaveSpec.expand()`：

```java
out.add(new SpawnStep(
        s.point(), s.type(), s.count(),
        s.hpMult(), s.dmgMult(), s.speedMult(), s.dropMult(),
        s.scaleMult(), s.followRangeMult(),
        timeline, s.affixes(), s.potions(), s.infernal(),
        s.equipment(), s.onDeath(), s.passengers()));
```

扩展 `MobSpawnProfile`：

```java
public record MobSpawnProfile(double dropMult, List<String> affixes,
                              InfernalCfg infernal, List<DeathSpawn> onDeath) {
    public MobSpawnProfile {
        affixes = affixes == null ? List.of() : List.copyOf(affixes);
        infernal = infernal == null ? InfernalCfg.NONE : infernal;
        onDeath = onDeath == null ? List.of() : List.copyOf(onDeath);
    }

    public static MobSpawnProfile fromStep(SpawnStep step) {
        return new MobSpawnProfile(step.dropMult(), step.affixes(), step.infernal(), step.onDeath());
    }
    public static MobSpawnProfile fromPassenger(PassengerSpawn spawn) {
        return new MobSpawnProfile(spawn.dropMult(), spawn.affixes(), spawn.infernal(), spawn.onDeath());
    }
    public static MobSpawnProfile fromDeathSpawn(DeathSpawn spawn) {
        return new MobSpawnProfile(spawn.dropMult(), spawn.affixes(), spawn.infernal(), spawn.onDeath());
    }
}
```

- [ ] **Step 8: 在 RunPlanner 中逐层复制配置**

三个构造点分别增加 `sc.infernal()`、`pc.infernal()`、`dc.infernal()`；不把 IM ID 传给 `AffixService.resolve`。例如：

```java
steps.add(new SpawnStep(
        Coords.resolve(origin, rel), sc.type(), resolvedCount,
        ms.hp(), ms.dmg(), ms.speed(), ms.drop(),
        ms.scale(), ms.followRange(),
        sc.delaySec() * 20,
        sc.affixes(),
        resolved.stream().filter(a -> !a.trigger().isCombatTriggered())
                .flatMap(a -> a.potions().stream()).toList(),
        sc.infernal(),
        sc.equipment(), onDeath, passengerSpawns));
```

本任务暂时保留 `AffixTrigger` 过滤；Task 5 再统一删除。

- [ ] **Step 9: 增加贯通断言并修复所有兼容构造器**

在 `RunPlannerTest` 构造一个含 `InfernalCfg(3, List.of("sprint"))` 的 `StepCfg`，断言规划后 `SpawnStep.infernal()` 相等；在 `WaveSpecTest` 断言 repeat 展开前后 `infernal` 不丢失。所有旧短构造器必须继续传 `NONE`，避免无关测试大面积改写。

- [ ] **Step 10: 运行相关测试**

Run:

```powershell
& $mvn -Dtest=InfernalCfgTest,MobYamlParserTest,RunPlannerTest,WaveSpecTest test
```

Expected: PASS；无 `InfernalCfg` 构造器或 accessor 编译错误。

- [ ] **Step 11: 条件提交**

仅在用户明确要求提交且 staged diff 只包含本任务文件时：

```powershell
git add src/main/java/io/mczju/maggoteers/config src/main/java/io/mczju/maggoteers/wave src/main/java/io/mczju/maggoteers/plan/RunPlanner.java src/test/java/io/mczju/maggoteers/config src/test/java/io/mczju/maggoteers/plan/RunPlannerTest.java src/test/java/io/mczju/maggoteers/wave/WaveSpecTest.java
git commit -m "feat: carry InfernalMobs config through wave plans"
```

---

### Task 2：实现精确且可降级的 IM 反射桥

**Files:**
- Create: `src/main/java/io/mczju/maggoteers/integration/InfernalMobsBridge.java`
- Create: `src/test/java/io/mczju/maggoteers/integration/InfernalMobsBridgeTest.java`
- Modify: `src/main/resources/plugin.yml`
- Modify: `src/main/java/io/mczju/maggoteers/MaggoteersPlugin.java`

**Interfaces:**
- Consumes: `InfernalCfg`
- Produces: `initialize(JavaPlugin)`, `mechanize(LivingEntity, InfernalCfg): boolean`
- Produces: `isManaged(UUID): boolean`, `unregisterManaged(UUID): boolean`, `shutdown()`
- Internal test seam: package-private `Backend` and `installForTesting(Backend, Logger)`

- [ ] **Step 1: 写技能过滤、精确参数和注销的失败测试**

```java
package io.mczju.maggoteers.integration;

import io.mczju.maggoteers.config.InfernalCfg;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.logging.Logger;
import static org.junit.jupiter.api.Assertions.*;

class InfernalMobsBridgeTest {
    @AfterEach void reset() { InfernalMobsBridge.shutdown(); }

    @Test void forwardsOnlyKnownAndSupportedIdsExactly() {
        UUID uuid = UUID.randomUUID();
        FakeBackend backend = new FakeBackend(Set.of("poisonous", "sprint", "ghost"));
        InfernalMobsBridge.installForTesting(backend, Logger.getAnonymousLogger());

        assertTrue(InfernalMobsBridge.mechanize(entity(uuid),
                new InfernalCfg(3, List.of("poisonous", "ghost", "missing", "sprint"))));

        assertEquals(3, backend.level);
        assertEquals(List.of("poisonous", "sprint"), backend.ids);
        assertTrue(InfernalMobsBridge.isManaged(uuid));
    }

    @Test void emptyFilteredListKeepsEntityOrdinary() {
        FakeBackend backend = new FakeBackend(Set.of("ghost"));
        InfernalMobsBridge.installForTesting(backend, Logger.getAnonymousLogger());
        assertFalse(InfernalMobsBridge.mechanize(entity(UUID.randomUUID()),
                new InfernalCfg(1, List.of("ghost", "missing"))));
        assertEquals(0, backend.calls);
    }

    @Test void unregisterIsIdempotent() {
        UUID uuid = UUID.randomUUID();
        FakeBackend backend = new FakeBackend(Set.of("sprint"));
        InfernalMobsBridge.installForTesting(backend, Logger.getAnonymousLogger());
        InfernalMobsBridge.mechanize(entity(uuid), new InfernalCfg(1, List.of("sprint")));
        assertTrue(InfernalMobsBridge.unregisterManaged(uuid));
        assertFalse(InfernalMobsBridge.unregisterManaged(uuid));
        assertEquals(List.of(uuid), backend.unregistered);
    }

    private static LivingEntity entity(UUID uuid) {
        return (LivingEntity) Proxy.newProxyInstance(
                LivingEntity.class.getClassLoader(), new Class<?>[]{LivingEntity.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getUniqueId" -> uuid;
                    case "getLocation" -> (Location) null;
                    case "isValid" -> true;
                    case "isDead" -> false;
                    default -> method.getReturnType().isPrimitive() ? primitiveDefault(method.getReturnType()) : null;
                });
    }

    private static Object primitiveDefault(Class<?> type) {
        if (type == boolean.class) return false;
        if (type == char.class) return '\0';
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0F;
        return 0D;
    }

    private static final class FakeBackend implements InfernalMobsBridge.Backend {
        final Set<String> known;
        final List<UUID> unregistered = new ArrayList<>();
        int calls;
        int level;
        List<String> ids = List.of();
        UUID registered;

        FakeBackend(Set<String> known) { this.known = known; }
        public boolean hasSkill(String id) { return known.contains(id); }
        public void mechanize(LivingEntity entity, int value, List<String> skillIds) {
            calls++;
            level = value;
            ids = List.copyOf(skillIds);
            registered = entity.getUniqueId();
        }
        public boolean isRegistered(UUID uuid) { return uuid.equals(registered); }
        public void unregister(UUID uuid) { unregistered.add(uuid); }
    }
}
```

- [ ] **Step 2: 运行测试并确认桥接类不存在**

Run:

```powershell
& $mvn -Dtest=InfernalMobsBridgeTest test
```

Expected: FAIL，包含 `cannot find symbol: InfernalMobsBridge`。

- [ ] **Step 3: 实现桥接层的公开行为和测试后端**

实现以下完整状态边界：

```java
public final class InfernalMobsBridge {
    static final Set<String> UNSUPPORTED = Set.of(
            "morph", "mama", "mounted", "vexsummoner", "ghost");
    private static final Set<UUID> MANAGED = ConcurrentHashMap.newKeySet();
    private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();
    private static volatile Backend backend;
    private static Logger logger = Logger.getLogger("Maggoteers");

    interface Backend {
        boolean hasSkill(String id) throws ReflectiveOperationException;
        void mechanize(LivingEntity entity, int level, List<String> ids)
                throws ReflectiveOperationException;
        boolean isRegistered(UUID uuid) throws ReflectiveOperationException;
        void unregister(UUID uuid) throws ReflectiveOperationException;
    }

    public static boolean mechanize(LivingEntity entity, InfernalCfg cfg) {
        Backend current = backend;
        if (entity == null || cfg == null || !cfg.enabled() || current == null) return false;
        List<String> accepted = new ArrayList<>();
        for (String id : cfg.affixes()) {
            if (UNSUPPORTED.contains(id)) {
                warnOnce("unsupported:" + id, "跳过不兼容 IM 技能: " + id);
                continue;
            }
            try {
                if (!current.hasSkill(id)) {
                    warnOnce("unknown:" + id, "跳过未知 IM 技能: " + id);
                    continue;
                }
            } catch (ReflectiveOperationException e) {
                disable("IM 技能注册表不可访问", e);
                return false;
            }
            accepted.add(id);
        }
        if (accepted.isEmpty()) return false;
        try {
            current.mechanize(entity, cfg.level(), List.copyOf(accepted));
            if (!current.isRegistered(entity.getUniqueId())) {
                warnOnce("not-registered", "IM 未注册机械化实体，按普通怪物继续");
                return false;
            }
            MANAGED.add(entity.getUniqueId());
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            warnOnce("invoke:" + e.getClass().getName(), "IM 机械化失败: " + e.getMessage());
            return false;
        }
    }

    public static boolean isManaged(UUID uuid) {
        return uuid != null && MANAGED.contains(uuid);
    }

    public static boolean unregisterManaged(UUID uuid) {
        if (uuid == null || !MANAGED.remove(uuid)) return false;
        Backend current = backend;
        if (current != null) {
            try {
                current.unregister(uuid);
            } catch (ReflectiveOperationException | RuntimeException e) {
                warnOnce("unregister:" + e.getClass().getName(), "IM 注销失败: " + e.getMessage());
            }
        }
        return true;
    }

    static void installForTesting(Backend value, Logger testLogger) {
        shutdown();
        backend = value;
        logger = testLogger;
    }

    public static void shutdown() {
        Backend current = backend;
        if (current != null) {
            for (UUID uuid : List.copyOf(MANAGED)) unregisterManaged(uuid);
        }
        MANAGED.clear();
        WARNED.clear();
        backend = null;
    }

    private static void warnOnce(String key, String message) {
        if (WARNED.add(key)) logger.warning(message);
    }

    private static void disable(String message, Exception cause) {
        backend = null;
        warnOnce("disabled", message + ": " + cause.getMessage());
    }

    private InfernalMobsBridge() {}
}
```

- [ ] **Step 4: 实现生产反射后端**

在同一文件中增加 `initialize(JavaPlugin)` 和私有 `ReflectiveBackend`。解析契约必须精确为：

```java
public static void initialize(JavaPlugin plugin) {
    logger = plugin.getLogger();
    Plugin im = Bukkit.getPluginManager().getPlugin("InfernalMobs");
    if (im == null || !im.isEnabled()) {
        warnOnce("missing", "InfernalMobs 未安装或未启用，infernal 配置将被跳过");
        return;
    }
    try {
        Field factoryField = im.getClass().getDeclaredField("mobFactory");
        factoryField.setAccessible(true);
        Object factory = factoryField.get(im);
        Method mechanize = factory.getClass().getMethod(
                "mechanizeWithAffixes", LivingEntity.class, Location.class, int.class, List.class);

        Object combat = im.getClass().getMethod("getCombatService").invoke(im);
        Method getState = combat.getClass().getMethod("getMobState", UUID.class);
        Method unregister = combat.getClass().getMethod("unregisterMob", UUID.class);

        ClassLoader loader = im.getClass().getClassLoader();
        Class<?> registry = Class.forName(
                "com.infernalmobs.registry.SkillRegistry", true, loader);
        Method has = registry.getMethod("has", String.class);

        backend = new ReflectiveBackend(factory, mechanize, combat, getState, unregister, has);
    } catch (ReflectiveOperationException | RuntimeException e) {
        disable("InfernalMobs 反射契约不兼容，本会话禁用桥接", e);
    }
}

private record ReflectiveBackend(
        Object factory, Method mechanize,
        Object combat, Method getState, Method unregister,
        Method hasSkill) implements Backend {
    public boolean hasSkill(String id) throws ReflectiveOperationException {
        return Boolean.TRUE.equals(hasSkill.invoke(null, id));
    }
    public void mechanize(LivingEntity entity, int level, List<String> ids)
            throws ReflectiveOperationException {
        mechanize.invoke(factory, entity, entity.getLocation(), level, ids);
    }
    public boolean isRegistered(UUID uuid) throws ReflectiveOperationException {
        return getState.invoke(combat, uuid) != null;
    }
    public void unregister(UUID uuid) throws ReflectiveOperationException {
        unregister.invoke(combat, uuid);
    }
}
```

捕获 `InvocationTargetException` 时，日志应优先使用 `getCause()`，但仍只警告且保留实体。

- [ ] **Step 5: 声明软依赖并初始化/关闭桥接**

`plugin.yml`：

```yaml
softdepend: [MCZJUItemCreator, InfernalMobs]
```

`MaggoteersPlugin.onEnable()` 在 `instance = this` 后调用：

```java
InfernalMobsBridge.initialize(this);
```

`onDisable()` 在其他运行任务停止后调用：

```java
InfernalMobsBridge.shutdown();
```

- [ ] **Step 6: 运行桥接测试和编译**

Run:

```powershell
& $mvn -Dtest=InfernalMobsBridgeTest test
```

Expected: PASS；`pom.xml` 不出现 IM 依赖坐标。

- [ ] **Step 7: 条件提交**

```powershell
git add src/main/java/io/mczju/maggoteers/integration/InfernalMobsBridge.java src/test/java/io/mczju/maggoteers/integration/InfernalMobsBridgeTest.java src/main/java/io/mczju/maggoteers/MaggoteersPlugin.java src/main/resources/plugin.yml
git commit -m "feat: add optional InfernalMobs reflection bridge"
```

---

### Task 3：在三类刷怪路径中应用 IM，并确保显式装备最后覆盖

**Files:**
- Modify: `src/main/java/io/mczju/maggoteers/mob/MobFactory.java`
- Modify: `src/main/java/io/mczju/maggoteers/wave/MobSpawnProfile.java`
- Modify: `src/main/java/io/mczju/maggoteers/wave/WaveEngine.java`
- Modify: `src/test/java/io/mczju/maggoteers/plan/RunPlannerTest.java`

**Interfaces:**
- Consumes: `InfernalMobsBridge.mechanize(LivingEntity, InfernalCfg)`
- Produces: 主体、乘客、亡语实体统一的顺序：原生属性/词缀 → IM → 显式装备 → WaveEngine 追踪

- [ ] **Step 1: 扩充贯通测试，覆盖三类节点互不继承**

在 `RunPlannerTest` 新增以下测试，复用该类已有的 `simpleDefs()`、`oneMapLib()`、`scaling()`、`affixes()`、`cfg()`：

```java
@Test
void carriesIndependentInfernalConfigForAllSpawnKinds() {
    PassengerCfg passenger = new PassengerCfg(
            EntityType.SKELETON, 1, new CoeffCfg(1, 1, 1), List.of(),
            new InfernalCfg(1, List.of("archer")),
            List.of(), List.of(), List.of());
    DeathSpawnCfg death = new DeathSpawnCfg(
            EntityType.SILVERFISH, 1, new CoeffCfg(1, 1, 1), List.of(),
            new InfernalCfg(2, List.of("berserk")), List.of(), List.of());
    StepCfg root = new StepCfg(
            "1", EntityType.ZOMBIE, 1, new CoeffCfg(1, 1, 1), 0, List.of(),
            new InfernalCfg(3, List.of("sprint")),
            List.of(), List.of(death), List.of(passenger));
    SpawnStrategyCfg im = new SpawnStrategyCfg("w_im", List.of(root), 1, List.of());

    Map<String, SpawnStrategyCfg> strategies = new HashMap<>(simpleDefs().strategies());
    strategies.put("w_im", im);
    Map<String, Map<String, List<WavesConfig.PoolEntry>>> pools = new HashMap<>();
    for (String act : List.of("act1", "act2", "act3")) {
        pools.put(act, Map.of(
                "weak", List.of(new WavesConfig.PoolEntry("w_im", 1)),
                "strong", List.of(new WavesConfig.PoolEntry("s_strong", 1)),
                "boss", List.of(new WavesConfig.PoolEntry("b_boss", 1))));
    }

    SpawnStep step = RunPlanner.plan(
            1L, 1, new WaveDefinitions(strategies, pools),
            oneMapLib(), scaling(), affixes(), cfg())
            .get(0).waves().get(0).steps().get(0);

    assertEquals(List.of("sprint"), step.infernal().affixes());
    assertEquals(List.of("archer"), step.passengers().get(0).infernal().affixes());
    assertEquals(List.of("berserk"), step.onDeath().get(0).infernal().affixes());
    assertNotEquals(step.infernal(), step.passengers().get(0).infernal());
    assertNotEquals(step.infernal(), step.onDeath().get(0).infernal());
}
```

- [ ] **Step 2: 运行测试并确认至少一个嵌套字段尚未贯通**

Run:

```powershell
& $mvn -Dtest=RunPlannerTest test
```

Expected: FAIL 于 passenger 或 death-spawn `infernal()` 断言。

- [ ] **Step 3: 在所有实体创建函数中插入桥接调用**

`spawnMount`、`spawnPassengerEntity`、`spawnDeathMob` 都使用同一顺序：

```java
applyMobStats(le, hpMult, dmgMult, speedMult, scaleMult, followRangeMult);
applyName(le, affixIds);
applyAffixPotions(le, affixIds, potions);
applyAffixVisuals(le, affixIds);
InfernalMobsBridge.mechanize(le, infernal);
applyEquipment(le, equipment);
```

具体参数分别来自 `step`、`ps`、`ds`。不得把 `applyEquipment` 留在桥接调用前，否则 IM 的 `armoured` 等技能可能覆盖波次配置。

- [ ] **Step 4: 让档案明确记录是否由桥接层管理**

`MobSpawnProfile` 已持有 `InfernalCfg`。`WaveEngine.trackSpawned` 不自行推断 IM 状态；死亡时同时检查：

```java
profile.infernal().enabled() && InfernalMobsBridge.isManaged(uuid)
```

这样未知/禁用技能全部被过滤后，普通实体不会被误判为 IM 实体。

- [ ] **Step 5: 更新调试构造和兼容构造器**

`MobFactory.spawnDebugMob` 的 fake `SpawnStep`、`WaveEngine.trackDebugMob` 的 `MobSpawnProfile` 均显式传 `InfernalCfg.NONE`。本任务不扩展 debug 命令参数，避免无关功能。

- [ ] **Step 6: 运行规划、展开和全量编译测试**

Run:

```powershell
& $mvn -Dtest=RunPlannerTest,WaveSpecTest,InfernalMobsBridgeTest test
```

Expected: PASS；所有 `SpawnStep`、`PassengerSpawn`、`DeathSpawn` 构造点编译通过。

- [ ] **Step 7: 条件提交**

```powershell
git add src/main/java/io/mczju/maggoteers/mob/MobFactory.java src/main/java/io/mczju/maggoteers/wave src/test/java/io/mczju/maggoteers/plan/RunPlannerTest.java
git commit -m "feat: mechanize configured wave mobs"
```

---

### Task 4：在 IM 死亡监听前注销，禁止全部 IM 死亡奖励

**Files:**
- Create: `src/test/java/io/mczju/maggoteers/listener/MobDeathListenerTest.java`
- Modify: `src/main/java/io/mczju/maggoteers/listener/MobDeathListener.java`
- Modify: `src/main/java/io/mczju/maggoteers/wave/WaveEngine.java`
- Modify: `src/main/java/io/mczju/maggoteers/integration/InfernalMobsBridge.java`
- Modify: `src/test/java/io/mczju/maggoteers/integration/InfernalMobsBridgeTest.java`

**Interfaces:**
- Consumes: `WaveEngine.isTracked(UUID)`, `InfernalMobsBridge.isManaged(UUID)`
- Produces: `MobDeathListener.beforeInfernalDeath(EntityDeathEvent)` at `LOWEST`
- Produces: `MobDeathListener.onDeath(EntityDeathEvent)` at `HIGHEST`

- [ ] **Step 1: 写事件优先级契约测试**

```java
package io.mczju.maggoteers.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDeathEvent;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Method;
import static org.junit.jupiter.api.Assertions.assertEquals;

class MobDeathListenerTest {
    @Test void unregisterHandlerRunsBeforeInfernalNormalListener() throws Exception {
        Method method = MobDeathListener.class.getDeclaredMethod(
                "beforeInfernalDeath", EntityDeathEvent.class);
        assertEquals(EventPriority.LOWEST,
                method.getAnnotation(EventHandler.class).priority());
    }

    @Test void finalDropCleanupRunsAfterInfernalNormalListener() throws Exception {
        Method method = MobDeathListener.class.getDeclaredMethod(
                "onDeath", EntityDeathEvent.class);
        assertEquals(EventPriority.HIGHEST,
                method.getAnnotation(EventHandler.class).priority());
    }
}
```

- [ ] **Step 2: 运行测试并确认 LOWEST handler 不存在**

Run:

```powershell
& $mvn -Dtest=MobDeathListenerTest test
```

Expected: FAIL，包含 `NoSuchMethodException: beforeInfernalDeath`。

- [ ] **Step 3: 拆分死亡监听器**

```java
@EventHandler(priority = EventPriority.LOWEST)
public void beforeInfernalDeath(EntityDeathEvent event) {
    UUID uuid = event.getEntity().getUniqueId();
    if (WaveEngine.isTracked(uuid) && InfernalMobsBridge.isManaged(uuid)) {
        InfernalMobsBridge.unregisterManaged(uuid);
    }
}

@EventHandler(priority = EventPriority.HIGHEST)
public void onDeath(EntityDeathEvent event) {
    if (WaveEngine.handleMobDeath(
            event.getEntity().getUniqueId(), event.getEntity().getLocation())) {
        event.getDrops().clear();
        event.setDroppedExp(0);
    }
}
```

`LOWEST` 只负责 IM 注销；`HIGHEST` 才执行 Maggoteers 亡语生成、清追踪和最终清空原版掉落。这样 IM 的 `NORMAL` handler 看不到 `MobState`，且后续插件加入的事件掉落仍会被 Maggoteers 清除。

- [ ] **Step 4: 覆盖非死亡移除路径，防止 IM 状态泄漏**

在 `WaveEngine.handleMobDeath` 开头、`clearTracking`、`killAllTracked`、`stop` 遍历 UUID 时调用：

```java
InfernalMobsBridge.unregisterManaged(uuid);
```

调用必须幂等；正常死亡已在 LOWEST 注销，后续调用应直接返回 false。

- [ ] **Step 5: 补充桥接测试**

在 `InfernalMobsBridgeTest` 增加：

```java
@Test void failedMechanizeIsNeverManagedOrUnregistered() {
    FakeBackend backend = new FakeBackend(Set.of("sprint"));
    backend.registerOnMechanize = false;
    InfernalMobsBridge.installForTesting(backend, Logger.getAnonymousLogger());
    UUID uuid = UUID.randomUUID();
    assertFalse(InfernalMobsBridge.mechanize(
            entity(uuid), new InfernalCfg(1, List.of("sprint"))));
    assertFalse(InfernalMobsBridge.isManaged(uuid));
    assertFalse(InfernalMobsBridge.unregisterManaged(uuid));
}
```

为 fake backend 增加 `registerOnMechanize` 开关，使 `isRegistered` 返回对应结果。

- [ ] **Step 6: 运行死亡与桥接测试**

Run:

```powershell
& $mvn -Dtest=MobDeathListenerTest,InfernalMobsBridgeTest test
```

Expected: PASS。

- [ ] **Step 7: 条件提交**

```powershell
git add src/main/java/io/mczju/maggoteers/listener/MobDeathListener.java src/main/java/io/mczju/maggoteers/wave/WaveEngine.java src/main/java/io/mczju/maggoteers/integration/InfernalMobsBridge.java src/test/java/io/mczju/maggoteers/listener/MobDeathListenerTest.java src/test/java/io/mczju/maggoteers/integration/InfernalMobsBridgeTest.java
git commit -m "fix: suppress InfernalMobs death rewards"
```

---

### Task 5：退役旧 `on:` 战斗词缀及默认波次引用

**Files:**
- Create: `src/test/java/io/mczju/maggoteers/config/RetiredAffixConfigTest.java`
- Modify: `src/main/java/io/mczju/maggoteers/config/Affix.java`
- Modify: `src/main/java/io/mczju/maggoteers/config/AffixService.java`
- Modify: `src/main/java/io/mczju/maggoteers/plan/RunPlanner.java`
- Modify: `src/main/java/io/mczju/maggoteers/mob/MobFactory.java`
- Modify: `src/main/java/io/mczju/maggoteers/wave/WaveEngine.java`
- Modify: `src/main/java/io/mczju/maggoteers/MaggoteersPlugin.java`
- Modify: `src/main/resources/affixes.yml`
- Modify: `src/main/resources/waves.yml`
- Modify: `scripts/gen_waveplan_waves.py`
- Modify: `src/test/java/io/mczju/maggoteers/plan/ComposeTest.java`
- Modify: `src/test/java/io/mczju/maggoteers/plan/RunPlannerTest.java`
- Delete: `src/main/java/io/mczju/maggoteers/config/AffixTrigger.java`
- Delete: `src/main/java/io/mczju/maggoteers/effect/AffixCombatService.java`
- Delete: `src/main/java/io/mczju/maggoteers/listener/CombatAffixListener.java`
- Delete: `src/test/java/io/mczju/maggoteers/config/AffixTriggerTest.java`
- Delete: `src/test/java/io/mczju/maggoteers/effect/AffixCombatServiceTest.java`

**Interfaces:**
- Produces: `Affix` 不再有 `trigger()`；所有剩余 potion 均为出生效果
- Removes: `WaveEngine.resolveCombatAffixes` 和监听器注册

- [ ] **Step 1: 写资源退役测试**

```java
package io.mczju.maggoteers.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class RetiredAffixConfigTest {
    private static final Set<String> RETIRED = Set.of(
            "toxic", "poisonous", "vampiric", "lifesteal", "sticky", "quicksand",
            "frostgrasp", "squid", "truck", "hidesuwa", "teleport", "cataclysm");

    @Test void affixesYamlHasNoCombatTriggerSchema() throws IOException {
        YamlConfiguration yaml = yaml("affixes.yml");
        ConfigurationSection affixes = yaml.getConfigurationSection("affixes");
        assertNotNull(affixes);
        assertTrue(java.util.Collections.disjoint(affixes.getKeys(false), RETIRED));
        for (String id : affixes.getKeys(false)) {
            assertFalse(yaml.contains("affixes." + id + ".on"), id);
        }
    }

    @Test void wavesYamlHasNoRetiredNativeAffixIds() throws IOException {
        YamlConfiguration yaml = yaml("waves.yml");
        ConfigurationSection strategies = yaml.getConfigurationSection("strategies");
        assertNotNull(strategies);
        List<String> found = new ArrayList<>();
        for (String id : strategies.getKeys(false)) {
            for (Map<?, ?> step : yaml.getMapList("strategies." + id + ".steps")) {
                collectNativeAffixes(step, found);
            }
        }
        assertTrue(java.util.Collections.disjoint(found, RETIRED), found.toString());
    }

    private static void collectNativeAffixes(Map<?, ?> node, List<String> found) {
        if (node.get("affixes") instanceof List<?> ids) {
            for (Object id : ids) found.add(String.valueOf(id));
        }
        collectChildren(node.get("passengers"), found);
        collectChildren(node.get("on_death"), found);
    }

    private static void collectChildren(Object raw, List<String> found) {
        if (!(raw instanceof List<?> children)) return;
        for (Object child : children) {
            if (child instanceof Map<?, ?> map) collectNativeAffixes(map, found);
        }
    }

    private static YamlConfiguration yaml(String name) throws IOException {
        try (var in = RetiredAffixConfigTest.class.getClassLoader().getResourceAsStream(name)) {
            assertNotNull(in, name);
            String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.loadFromString(text);
            return yaml;
        } catch (org.bukkit.configuration.InvalidConfigurationException e) {
            throw new IOException(name, e);
        }
    }
}
```

- [ ] **Step 2: 运行测试并确认列出旧 ID**

Run:

```powershell
& $mvn -Dtest=RetiredAffixConfigTest test
```

Expected: FAIL，首个失败至少包含 `toxic` 或 `on:`。

- [ ] **Step 3: 简化 Affix 模型和加载器**

`Affix` 改为：

```java
public record Affix(String id, double hp, double dmg, double speed, double drop,
                    double scale, double followRange,
                    List<PotionSpec> potions, String display) {
    public Affix(String id, double hp, double dmg, double speed, double drop,
                 List<PotionSpec> potions, String display) {
        this(id, hp, dmg, speed, drop, 1.0, 1.0, potions, display);
    }

    public Affix {
        if (hp <= 0) hp = 1;
        if (dmg <= 0) dmg = 1;
        if (speed <= 0) speed = 1;
        if (drop <= 0) drop = 1;
        if (scale <= 0) scale = 1;
        if (followRange <= 0) followRange = 1;
        potions = potions == null ? List.of() : List.copyOf(potions);
    }
}
```

`AffixService.parse` 删除 `on` 解析和异常分支，直接构造：

```java
return new Affix(id,
        s.getDouble("hp", 1.0), s.getDouble("dmg", 1.0),
        s.getDouble("speed", 1.0), s.getDouble("drop", 1.0),
        s.getDouble("scale", 1.0), s.getDouble("follow_range", 1.0),
        pots, s.getString("display", id));
```

同步修正 `ComposeTest`、`RunPlannerTest`、`StrongWaveRollTest` 的 `new Affix(...)`。

- [ ] **Step 4: 删除战斗触发代码路径**

删除列出的 3 个生产类和 2 个测试类。`MaggoteersPlugin` 删除 `CombatAffixListener` 注册。`WaveEngine` 删除：

- `entityAffixes`
- `resolveCombatAffixes`
- `affixesOf`
- `squadAffixes`
- 仅为上述方法存在的 imports

保留 `MobSpawnProfile.affixes`，因为名称、调试和档案仍使用原生出生型词缀。

- [ ] **Step 5: 让剩余药水无条件按出生效果处理**

`RunPlanner` 三处改为：

```java
resolved.stream().flatMap(a -> a.potions().stream()).toList()
```

`MobFactory.applyAffixPotions` 改为：

```java
for (String affixId : affixIds) {
    Affix affix = AffixService.getInstance().get(affixId);
    if (affix == null) continue;
    for (PotionSpec potion : affix.potions()) {
        PotionEffectType type = GameRegistries.potionEffect(potion.effect());
        if (type != null) {
            le.addPotionEffect(new PotionEffect(type, potion.dur(), potion.amp()));
        }
    }
}
```

删除所有关于“战斗触发药水不在出生时施加”的注释。

- [ ] **Step 6: 安全移除默认 YAML 中 12 个定义和引用**

从 `affixes.yml` 删除 12 个完整定义块，不能只删 `on:`，否则旧药水会错误变成出生效果。

`scripts/gen_waveplan_waves.py` 是 `waves.yml` 的生成源，必须先从生成器中的原生 `affixes: [...]` token 列表移除旧 ID，再执行：

```powershell
python scripts/gen_waveplan_waves.py
```

Expected: 输出 `Wrote .../src/main/resources/waves.yml`，随后结构化 JUnit guard 找不到任何旧原生 ID。不要只修改生成后的 `waves.yml`，否则下次生成会重新引入约 111 个旧引用。

机械清理生成器时只能处理字符串中的 `affixes: [...]` 列表并保留共存词缀，例如 `[sticky, infected]` 必须变成 `[infected]`。不得递归删除未来的 `infernal.affixes`；生成完成后以 `RetiredAffixConfigTest` 的结构化遍历结果为准。

- [ ] **Step 7: 运行退役测试和全量单测**

Run:

```powershell
& $mvn -Dtest=RetiredAffixConfigTest,ComposeTest,RunPlannerTest,StrongWaveRollTest test
& $mvn test
```

Expected: 两条命令均 PASS；源码搜索 `AffixTrigger|AffixCombatService|CombatAffixListener` 只允许出现在标记为历史的文档中。

- [ ] **Step 8: 条件提交**

```powershell
git add src/main/java/io/mczju/maggoteers src/main/resources/affixes.yml src/main/resources/waves.yml scripts/gen_waveplan_waves.py src/test/java/io/mczju/maggoteers
git commit -m "refactor: retire native combat affix triggers"
```

提交前必须用 `git diff --cached` 排除这些目录内原有但与本任务无关的改动；无法隔离时不要提交。

---

### Task 6：更新配置文档并执行完整构建部署验收

**Files:**
- Modify: `src/main/resources/waves.yml`
- Modify: `scripts/gen_waveplan_waves.py`
- Modify: `CLAUDE.md`
- Modify: `.cursor/skills/maggoteers-plugin-config/SKILL.md`
- Modify: `.cursor/skills/maggoteers-plugin-config/reference.md`
- Modify: `docs/dev-log.md`
- Modify: `docs/superpowers/specs/2026-07-26-affix-trigger-refactor-design.md`

**Interfaces:**
- Documents: `infernal.level`, `infernal.affixes`、独立配置/不继承、禁用 ID、死亡阶段抑制
- Documents: IM 可选反射依赖和测试服 IM cleanup 约束

- [ ] **Step 1: 在默认 waves.yml 顶部加入可复制示例**

在 `scripts/gen_waveplan_waves.py` 的 `HEADER` 中增加以下注释，再重新生成 `waves.yml`；不给现有波次自动添加 IM 技能：

```yaml
# 可选 IM 精确词缀（主体 / passengers / on_death 均可，各自独立不继承）：
# infernal:
#   level: 1
#   affixes: [poisonous, sprint]
# 禁用：morph, mama, mounted, vexsummoner, ghost
# IM 缺失、未知或禁用技能只 warning 并按普通怪物继续。
```

- [ ] **Step 2: 更新项目圣经和配置技能**

在 `CLAUDE.md`：

- 把“不引入 InfernalMobs”改为“可选运行时反射集成，不作为 Maven 依赖”。
- 在 waves schema、扩展点、构建依赖和风险章节记录 `infernal`。
- 明确本插件 IM 怪物死亡前注销，不触发 IM 掉落/统计/死亡技能。

在 `.cursor/skills/maggoteers-plugin-config/SKILL.md` 与 `reference.md`：

- 删除 `on:` 表格和“新增 AffixTrigger”的说明。
- 添加三种节点均支持的 `infernal` schema。
- 添加等级 1–100、无继承、禁用 ID 和显式装备优先级。

- [ ] **Step 3: 标记历史设计并追加开发日志**

`docs/superpowers/specs/2026-07-26-affix-trigger-refactor-design.md` 顶部状态改为：

```markdown
**Status:** Superseded by `2026-07-26-infernal-mobs-wave-integration-design.md`
```

`docs/dev-log.md` 追加日期、反射原因、降级策略、五个禁用技能、立即删除旧词缀且不迁移、提前注销 IM 死亡状态的决策。

- [ ] **Step 4: 运行全量测试**

Run:

```powershell
.cursor/skills/maggoteers-build-deploy/scripts/build-and-deploy.ps1 -SkipDeploy
```

Expected: Maven test 与 package 均成功，生成 `target/Maggoteers-0.1.0-SNAPSHOT.jar`。

- [ ] **Step 5: 部署到测试服**

先确认 `E:/MCpaper/plugins/InfernalMobs-*.jar` 或实际 IM JAR 已存在，再运行：

```powershell
.cursor/skills/maggoteers-build-deploy/scripts/build-and-deploy.ps1
```

Expected:

- JAR 覆盖到 `E:/MCpaper/plugins/Maggoteers-0.1.0-SNAPSHOT.jar`
- `config.yml`、`waves.yml`、`rewards.yml`、`affixes.yml` 同步到 `E:/MCpaper/plugins/Maggoteers/`
- 提醒重启 Paper，不使用 `/reload`

- [ ] **Step 6: 测试服 smoke 验收**

在一个临时测试 strategy 分别给主体、乘客、亡语生成物配置互不相同的受支持 IM 技能，然后：

1. `/mgc join maggoteers`
2. 确认三类实体只显示各自配置的技能，没有随机追加。
3. 配置一个未知技能和一个 `ghost`，确认实体仍生成且每类警告只出现一次。
4. 击杀实体，确认波次推进且不出现 IM 普通/特殊/保底物品、奖励广播、统计增长或死亡技能。
5. 配置 `armoured` 与显式 `equipment`，确认显式装备最终保留。
6. 临时移走 IM JAR 后重启，确认 Maggoteers 仍启用且同一波次生成普通怪物。
7. 恢复 IM JAR 和原测试配置并再次重启。

- [ ] **Step 7: 条件提交文档**

```powershell
git add CLAUDE.md .cursor/skills/maggoteers-plugin-config src/main/resources/waves.yml scripts/gen_waveplan_waves.py docs/dev-log.md docs/superpowers/specs/2026-07-26-affix-trigger-refactor-design.md
git commit -m "docs: document InfernalMobs wave configuration"
```

提交前同样排除这些文件中既有的无关改动。

---

## 最终验收清单

- [ ] `infernal` 在主体、乘客、亡语生成物中均能解析并贯通。
- [ ] 三种节点配置互不继承。
- [ ] 精确技能列表不被随机补全。
- [ ] 五个不兼容技能被跳过并去重警告。
- [ ] IM 缺失或反射失败时实体仍生成并受 WaveEngine 追踪。
- [ ] 显式装备覆盖 IM 自动装备。
- [ ] IM 死亡战利品、命令、广播、统计、消息和死亡技能均不触发。
- [ ] 旧 12 个 `on:` 词缀定义与全部默认波次引用已删除。
- [ ] 出生型、倍率、标记、免疫和掉落倍率词缀继续工作。
- [ ] `mvn test`、package 和测试服 smoke 均通过。
