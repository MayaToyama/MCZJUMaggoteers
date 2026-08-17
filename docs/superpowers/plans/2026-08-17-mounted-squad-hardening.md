# Mounted Squad Hardening（骑乘小队加固）Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 加固骑乘怪物生产与运行逻辑——squad 表按对局隔离 + 清理统一、controller 显式声明、小队驱动参数配置化、复刻原版马/骆驼冲刺与僵尸骑手举矛冲锋。

**Architecture:** 沿用静态工具类模式（WaveEngine/WaveScheduler/MobFactory 同款）。`MountControllerResolver` 拆出纯函数 `controllerCandidates`（无 Bukkit 依赖，可单测）＋薄运行时。`MountedSquadRegistry` 从静态全局表改为按 `AbstractGame` 键控。`MountedSquadAiService` 每 tick 读一次 `config.yml` 的 `mob_attributes.*` 并驱动冲刺坐骑。

**Tech Stack:** Java 25、Paper 26.2 API、Maven、JUnit 5（Jupiter，无 Mockito）。

## Global Constraints

- **内容 YAML 零改动**：`waves.yml` / `rewards.yml` 不动；`config.yml` 仅在 `mob_attributes:` 下新增 `squad_target_range` / `squad_move_speed` / `squad_charge_speed` 三键（getDouble 带默认值，旧服缺键不崩）。
- **铁律③**：不维护 EntityType 白名单——冲刺检测用运行时 `root instanceof AbstractHorse`（族分类，覆盖 Camel/CAMEL_HUSK 及未来子类）。**例外**：座位上限 `directPassengerLimit(EntityType)` 显式列 Camel 族 `CAMEL || CAMEL_HUSK → 2`（未来新增 Camel 族类型须同步进分支与单测，见 spec §8）。
- **座位语义**：`directPassengerLimit` 生产用 `carrier.getType()`，纯可单测；`CAMEL_HUSK` 是 Camel 子类，座位须随族给 2。
- **单测**：JUnit 5，无 Mockito，纯逻辑（不碰 Bukkit `Registry`/世界）。`MountedSquadRegistry`/`WaveEngine`/`AiService` 的 Bukkit 交互靠编译 + 测试服验证，不单测。
- **构建**：JDK 25 + Maven。本机 WSL 跑单测：`export JAVA_HOME=/home/chalcanthite/.local/jdk-25`（若未装见 Task 0）；Windows 侧用 IntelliJ Maven（JDK 25）同命令。
- **`controller` 字段透传**：`PassengerSpawn` / `PassengerCfg` 的**所有构造**都要带 `controller`，改漏一处编译挂（见 Task 1）。
- **R2 清理**：`clearTracking` 与 `stop` **各自**调用 `MountedSquadRegistry.removeGame(game)`（不假设 stop 复用 clearTracking，见 spec §1.2）。

---

## 文件结构

| 文件 | 职责 | 改动 |
|---|---|---|
| `wave/PassengerSpawn.java` | 已解析乘客树节点 | +`controller` 字段（记录末位） |
| `config/PassengerCfg.java` | YAML 乘客节点 | +`controller` 字段（记录末位） |
| `config/MobYamlParser.java` | YAML→Cfg | +`parseController` + MobNode + 递归计数 warning |
| `plan/RunPlanner.java` | 剧本预生成 | 透传 `controller` |
| `mob/MountControllerResolver.java` | controller 选择 | 拆纯 `controllerCandidates` + 薄运行时 |
| `mob/MountedSquadRegistry.java` | squad 表 | 按 game 键控 + `removeGame` |
| `mob/MountedSquadAiService.java` | 小队驱动 | 只遍历本局 + 配置参数 + 冲刺驱动 |
| `mob/MobFactory.java` | 刷怪 + 挂载 | `game` 参数透传 + L1 注释 |
| `mob/MountPassengerLimits.java` | 座位/驯服 | `directPassengerLimit(EntityType)` + 去 `setTamed` |
| `wave/WaveEngine.java` | 波执行/清理 | 两处 `removeGame` + `stop` 删逐根 + L3 注释 |
| `resources/config.yml` | 配置 | +3 个 `mob_attributes.*` 键 |
| `test/.../MountControllerResolverTest.java` | 新增 | `controllerCandidates` 纯函数覆盖 |
| `test/.../MountPassengerLimitsTest.java` | 新增 | 座位上限覆盖 |
| `test/.../MobYamlParserTest.java` | 扩展 | `parseController` 覆盖 |

---

## Task 0：构建工具链就绪（一次性）

本机 WSL 需要 Linux JDK 25 才能跑 `mvn test`（pom `maven.compiler.release=25`；现有 `~/.local/jdk-21` 编译不了）。

- [ ] **Step 1: 确认 JDK 25 存在**

```bash
ls /home/chalcanthite/.local/jdk-25/bin/java 2>/dev/null && echo READY
```

若 READY，跳过本任务。若空，先检查是否有**后台下载在进行**（会话可能已发起）：

```bash
ls /home/chalcanthite/.local/jdk25.tar.gz 2>/dev/null && echo "后台下载中——轮询 Step 1 的 READY 直到就绪，勿重复下载" || echo "无进行中下载，继续 Step 2"
```

有后台下载则轮询等待；无则继续：

- [ ] **Step 2: 下载安装 Temurin JDK 25**（~200MB，允许 8 分钟）

```bash
cd /home/chalcanthite/.local && \
curl -sL --retry 2 "https://github.com/adoptium/temurin25-binaries/releases/download/jdk-25.0.4%2B7/OpenJDK25U-jdk_x64_linux_hotspot_25.0.4_7.tar.gz" -o jdk25.tar.gz && \
tar -xzf jdk25.tar.gz && mv jdk-25.0.4+7 jdk-25 && rm jdk25.tar.gz && \
/home/chalcanthite/.local/jdk-25/bin/java -version 2>&1 | head -2
```

Expected: `openjdk version "25.0.4" ...`

- [ ] **Step 3: 验证 mvn 工具链**

```bash
cd /mnt/e/Intellij_Idea/plugins/MCPlugin && \
export JAVA_HOME=/home/chalcanthite/.local/jdk-25 && \
timeout 300 mvn -q test-compile 2>&1 | tail -5
```

Expected: 无错误（编译通过）。后续所有测试命令都用 `export JAVA_HOME=/home/chalcanthite/.local/jdk-25` 前缀。

---

## Task 1：`controller` 字段贯穿配置→剧本（R3 数据面）

**Files:**
- Modify: `src/main/java/io/mczju/maggoteers/wave/PassengerSpawn.java`
- Modify: `src/main/java/io/mczju/maggoteers/config/PassengerCfg.java`
- Modify: `src/main/java/io/mczju/maggoteers/config/MobYamlParser.java`
- Modify: `src/main/java/io/mczju/maggoteers/plan/RunPlanner.java:153`
- Test: `src/test/java/io/mczju/maggoteers/config/MobYamlParserTest.java`

**Interfaces:**
- Produces: `PassengerSpawn(..., boolean bossBar, boolean controller)`（末位新增）、`PassengerCfg(..., boolean bossBar, boolean controller)`、`MobYamlParser.parseController(Map,String)`、`PassengerCfg.controller()`
- Consumes: 无（本任务只加数据面，不动逻辑）

- [ ] **Step 1: 写失败测试**（`parseController` 纯函数——省略=false、布尔原样、非布尔硬失败）

追加到 `src/test/java/io/mczju/maggoteers/config/MobYamlParserTest.java`（类体内）：

```java
    @Test
    void parsesControllerBoolean() {
        assertTrue(MobYamlParser.parseController(Map.of("controller", true), "t"));
        assertFalse(MobYamlParser.parseController(Map.of("controller", false), "t"));
        assertFalse(MobYamlParser.parseController(Map.of(), "t"));
    }

    @Test
    void controllerStringFails() {
        assertThrows(IllegalStateException.class,
                () -> MobYamlParser.parseController(Map.of("controller", "true"), "t"));
    }
```

- [ ] **Step 2: 运行测试确认失败**

```bash
cd /mnt/e/Intellij_Idea/plugins/MCPlugin && export JAVA_HOME=/home/chalcanthite/.local/jdk-25 && timeout 300 mvn -q test -Dtest=MobYamlParserTest 2>&1 | tail -15
```

Expected: `parsesControllerBoolean` 编译失败（`parseController` 不存在）。

- [ ] **Step 3: PassengerSpawn 加 `controller` 字段**

`src/main/java/io/mczju/maggoteers/wave/PassengerSpawn.java` 记录声明末位加 `boolean controller`，两个兼容构造委托时末位补 `false`：

```java
public record PassengerSpawn(EntityType type,
                             double hpMult, double dmgMult, double speedMult,
                             double scaleMult, double followRangeMult,
                             List<String> affixes, List<PotionSpec> potions,
                             InfernalCfg infernal,
                             List<MobEquipment> equipment, List<DeathSpawn> onDeath,
                             List<PassengerSpawn> passengers,
                             String name, boolean bossBar, boolean controller) {
```

`PassengerSpawn` 的 6 参兼容构造委托行末 `null, false` 改 `null, false, false`；12 参兼容构造委托行末 `passengers, null, false` 改 `passengers, null, false, false`。（compact 构造体不变——boolean 无需归一化。）

- [ ] **Step 4: PassengerCfg 加 `controller` 字段**

`src/main/java/io/mczju/maggoteers/config/PassengerCfg.java` 记录声明末位加 `boolean controller`，两个兼容构造委托时末位补 `false`：

```java
public record PassengerCfg(EntityType type, int count, CoeffCfg coeff, List<String> affixes,
                           InfernalCfg infernal,
                           List<MobEquipment> equipment, List<DeathSpawnCfg> onDeath,
                           List<PassengerCfg> passengers,
                           String name, boolean bossBar, boolean controller) {
```

- [ ] **Step 5: MobYamlParser 解析 `controller` + 多处声明 warning**

`src/main/java/io/mczju/maggoteers/config/MobYamlParser.java`：

1) 文件头加 `import java.util.logging.Logger;`。
2) `parseBossBar` 后新增（复刻其硬失败风格）：

```java
    /** Opt-in 骑乘控制器。Omitted → false. Non-boolean → hard-fail. */
    public static boolean parseController(Map<?, ?> m, String context) {
        if (!m.containsKey("controller") || m.get("controller") == null) return false;
        Object raw = m.get("controller");
        if (!(raw instanceof Boolean b)) {
            throw new IllegalStateException("controller must be boolean (" + context + ")");
        }
        return b;
    }
```

3) `MobNode` 记录末位加 `boolean controller`；`parseMobNode` 内加 `boolean controller = parseController(m, context + "/" + type.name());` 并传入 `MobNode(...)` 末位。
4) `parsePassengersDepth` 的 `new PassengerCfg(...)` 末位 `n.name(), n.bossBar()` 改 `n.name(), n.bossBar(), n.controller()`。`parseOnDeathDepth` 的 `new DeathSpawnCfg(...)` **不改**（DeathSpawnCfg 无 controller 字段；其 `passengers()` 列表里的 PassengerCfg 已带 controller）。
5) 两个公开入口加递归计数 warning：

```java
    public static List<PassengerCfg> parsePassengers(Object raw) {
        List<PassengerCfg> out = parsePassengersDepth(raw, 1, "step");
        warnOnMultipleControllers(out, "step");
        return out;
    }

    public static List<DeathSpawnCfg> parseOnDeath(Object raw) {
        List<DeathSpawnCfg> out = parseOnDeathDepth(raw, 1, "on_death");
        for (DeathSpawnCfg dc : out) warnOnMultipleControllers(dc.passengers(), "on_death");
        return out;
    }

    private static void warnOnMultipleControllers(List<PassengerCfg> nodes, String context) {
        int n = countControllers(nodes);
        if (n > 1) {
            Logger.getLogger("Maggoteers")
                    .warning(context + " 乘客树声明了 " + n + " 个 controller（首个生效）");
        }
    }

    private static int countControllers(List<PassengerCfg> nodes) {
        int n = 0;
        for (PassengerCfg pc : nodes) {
            if (pc.controller()) n++;
            n += countControllers(pc.passengers());
        }
        return n;
    }
```

- [ ] **Step 6: RunPlanner 透传 `controller`**

`src/main/java/io/mczju/maggoteers/plan/RunPlanner.java` `buildPassengerTrees` 内 `new PassengerSpawn(...)` 末位 `pc.name(), pc.bossBar()` 改 `pc.name(), pc.bossBar(), pc.controller()`。

- [ ] **Step 7: 运行全部测试确认通过**

```bash
cd /mnt/e/Intellij_Idea/plugins/MCPlugin && export JAVA_HOME=/home/chalcanthite/.local/jdk-25 && timeout 400 mvn -q test 2>&1 | tail -15
```

Expected: `BUILD SUCCESS`（含既有 WaveSpecTest/RunPlannerTest——它们用兼容构造，不受字段新增影响）。

- [ ] **Step 8: Commit**

```bash
git add src/main/java/io/mczju/maggoteers/wave/PassengerSpawn.java \
        src/main/java/io/mczju/maggoteers/config/PassengerCfg.java \
        src/main/java/io/mczju/maggoteers/config/MobYamlParser.java \
        src/main/java/io/mczju/maggoteers/plan/RunPlanner.java \
        src/test/java/io/mczju/maggoteers/config/MobYamlParserTest.java && \
git commit -m "feat(mob): add passenger controller flag through config→plan records"
```

---

## Task 2：`controllerCandidates` 纯候选链 + 薄运行时（R3 逻辑面）

**Files:**
- Modify: `src/main/java/io/mczju/maggoteers/mob/MountControllerResolver.java`（整体重写）
- Create: `src/test/java/io/mczju/maggoteers/mob/MountControllerResolverTest.java`

**Interfaces:**
- Consumes: `PassengerSpawn.controller()`（Task 1）
- Produces: `static List<List<Integer>> controllerCandidates(List<PassengerSpawn> spec, boolean rootIsCamel)`（包私有，可单测）、`resolve(...)` 签名不变

- [ ] **Step 1: 写失败测试**

创建 `src/test/java/io/mczju/maggoteers/mob/MountControllerResolverTest.java`：

```java
package io.mczju.maggoteers.mob;

import io.mczju.maggoteers.config.InfernalCfg;
import io.mczju.maggoteers.wave.PassengerSpawn;
import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MountControllerResolverTest {

    private static PassengerSpawn node(boolean controller, PassengerSpawn... nested) {
        return new PassengerSpawn(EntityType.ZOMBIE, 1.0, 1.0, 1.0, 1.0, 1.0,
                List.of(), List.of(), InfernalCfg.NONE, List.of(), List.of(),
                List.of(nested), null, false, controller);
    }

    @Test
    void emptySpecYieldsNoCandidates() {
        assertEquals(List.of(), MountControllerResolver.controllerCandidates(List.of(), false));
    }

    @Test
    void nonCamelTakesDeepestLeaf() {
        var spec = List.of(node(false, node(false, node(false))));
        assertEquals(List.of(List.of(0, 0, 0)),
                MountControllerResolver.controllerCandidates(spec, false));
    }

    @Test
    void camelDoubleLeafPrefersFirstSeat() {
        var spec = List.of(node(false), node(false));
        assertEquals(List.of(List.of(0)),
                MountControllerResolver.controllerCandidates(spec, true));
    }

    @Test
    void camelNestedPrefersFirstSubtreeEvenIfShallower() {
        // 前座子树浅、后座深：规则 2.5 仍先前座子树，整树最深兜底
        var spec = List.of(node(false, node(false)), node(false, node(false, node(false))));
        assertEquals(List.of(List.of(0, 0), List.of(1, 0, 0)),
                MountControllerResolver.controllerCandidates(spec, true));
    }

    @Test
    void nonCamelDeepestAcrossAllSubtrees() {
        var spec = List.of(node(false, node(false)), node(false, node(false, node(false))));
        assertEquals(List.of(List.of(1, 0, 0)),
                MountControllerResolver.controllerCandidates(spec, false));
    }

    @Test
    void explicitControllerBecomesFirstCandidate() {
        var spec = List.of(node(true), node(false, node(false, node(false))));
        assertEquals(List.of(List.of(0), List.of(1, 0, 0)),
                MountControllerResolver.controllerCandidates(spec, false));
    }

    @Test
    void multipleExplicitControllersInDfsOrder() {
        var spec = List.of(node(true, node(true)), node(false, node(false)));
        assertEquals(List.of(List.of(0), List.of(0, 0)),
                MountControllerResolver.controllerCandidates(spec, false));
    }

    @Test
    void explicitControllerOverridesCamelDoubleLeaf() {
        var spec = List.of(node(false), node(true));
        assertEquals(List.of(List.of(1), List.of(0)),
                MountControllerResolver.controllerCandidates(spec, true));
    }

    @Test
    void defaultFallbackDedupedAgainstExplicit() {
        // 显式 [0]，默认回落整树最深 [0,0] → 保留
        var spec = List.of(node(true, node(false)));
        assertEquals(List.of(List.of(0), List.of(0, 0)),
                MountControllerResolver.controllerCandidates(spec, false));
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

```bash
cd /mnt/e/Intellij_Idea/plugins/MCPlugin && export JAVA_HOME=/home/chalcanthite/.local/jdk-25 && timeout 300 mvn -q test -Dtest=MountControllerResolverTest 2>&1 | tail -15
```

Expected: 编译失败（`controllerCandidates` 不存在）。

- [ ] **Step 3: 重写 MountControllerResolver**

整体替换 `src/main/java/io/mczju/maggoteers/mob/MountControllerResolver.java`：

```java
package io.mczju.maggoteers.mob;

import io.mczju.maggoteers.wave.PassengerSpawn;
import org.bukkit.entity.Camel;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;

import java.util.ArrayList;
import java.util.List;

/** 骑乘小队 AI 控制实体（§1.7 spec + controllerCandidates 纯候选链）。 */
public final class MountControllerResolver {

    private MountControllerResolver() {}

    /** 运行时：按候选链取第一个可用 Mob；全不可用 → root。签名不变。 */
    public static LivingEntity resolve(LivingEntity root,
                                       List<LivingEntity> directPassengersInOrder,
                                       List<PassengerSpawn> passengerSpec) {
        if (passengerSpec == null || passengerSpec.isEmpty()) return root;
        boolean rootIsCamel = root instanceof Camel;
        for (List<Integer> path : controllerCandidates(passengerSpec, rootIsCamel)) {
            LivingEntity e = walkPath(root, directPassengersInOrder, passengerSpec, path);
            if (e instanceof Mob m && m.isValid() && !m.isDead()) return m;
        }
        return root;
    }

    /**
     * 纯候选链（无 Bukkit 依赖，可单测）。
     * 候选 = 显式段（DFS 序全部 controller()==true 路径）+ 默认回落段（2/2.5/3，去重）。
     */
    static List<List<Integer>> controllerCandidates(List<PassengerSpawn> spec, boolean rootIsCamel) {
        List<List<Integer>> out = new ArrayList<>();
        collectExplicit(spec, List.of(), out);
        for (List<Integer> path : defaultChain(spec, rootIsCamel)) {
            if (!out.contains(path)) out.add(path);
        }
        return out;
    }

    private static void collectExplicit(List<PassengerSpawn> nodes, List<Integer> prefix,
                                        List<List<Integer>> out) {
        for (int i = 0; i < nodes.size(); i++) {
            PassengerSpawn n = nodes.get(i);
            List<Integer> path = new ArrayList<>(prefix);
            path.add(i);
            if (n.controller()) out.add(path);
            if (!n.passengers().isEmpty()) collectExplicit(n.passengers(), path, out);
        }
    }

    /** 默认回落链：camel 双叶→[0]；camel 嵌套→[前座子树最深, 整树最深]；非 camel→整树最深。 */
    private static List<List<Integer>> defaultChain(List<PassengerSpawn> spec, boolean rootIsCamel) {
        List<List<Integer>> out = new ArrayList<>();
        if (rootIsCamel) {
            if (spec.size() == 2
                    && spec.get(0).passengers().isEmpty()
                    && spec.get(1).passengers().isEmpty()) {
                out.add(List.of(0));          // 规则 2 双叶短路
                return out;
            }
            if (!spec.isEmpty()) {            // 规则 2.5：先前座子树，再整树
                List<Integer> firstSub = deepestLeafPath(spec.get(0), List.of(0));
                out.add(firstSub);
                List<Integer> whole = deepestLeafInList(spec, List.of());
                if (!whole.equals(firstSub)) out.add(whole);
                return out;
            }
            return out;
        }
        if (!spec.isEmpty()) {                // 规则 3
            out.add(deepestLeafInList(spec, List.of()));
        }
        return out;
    }

    /** 单个节点子树的最深叶路径；tie 取 DFS 序较后者（对齐现代码 deepestMob 的 >=）。 */
    private static List<Integer> deepestLeafPath(PassengerSpawn node, List<Integer> prefix) {
        List<Integer> best = prefix;
        int bestDepth = prefix.size();
        List<PassengerSpawn> nested = node.passengers();
        for (int i = 0; i < nested.size(); i++) {
            List<Integer> childPath = new ArrayList<>(prefix);
            childPath.add(i);
            List<Integer> cand = deepestLeafPath(nested.get(i), childPath);
            if (cand.size() >= bestDepth) {
                best = cand;
                bestDepth = cand.size();
            }
        }
        return best;
    }

    /** 多个子树整树最深叶路径；tie 取 DFS 序较前者（对齐现代码 collectDepth+max 的 first）。 */
    private static List<Integer> deepestLeafInList(List<PassengerSpawn> spec, List<Integer> prefix) {
        List<Integer> best = null;
        int bestDepth = -1;
        for (int i = 0; i < spec.size(); i++) {
            List<Integer> childPath = new ArrayList<>(prefix);
            childPath.add(i);
            List<Integer> cand = deepestLeafPath(spec.get(i), childPath);
            if (best == null || cand.size() > bestDepth) {
                best = cand;
                bestDepth = cand.size();
            }
        }
        return best;
    }

    /** 沿候选路径取实体（spec 树索引对齐实体树；越界/缺失 → null，落到下一候选/root）。 */
    private static LivingEntity walkPath(LivingEntity root, List<LivingEntity> direct,
                                         List<PassengerSpawn> spec, List<Integer> path) {
        List<LivingEntity> children = direct;
        for (int i = 0; i < path.size(); i++) {
            int idx = path.get(i);
            if (idx >= children.size()) return null;
            LivingEntity cur = children.get(idx);
            if (i == path.size() - 1) return cur;
            children = cur.getPassengers().stream()
                    .filter(LivingEntity.class::isInstance)
                    .map(LivingEntity.class::cast)
                    .toList();
        }
        return null;
    }
}
```

- [ ] **Step 4: 运行测试确认通过**

```bash
cd /mnt/e/Intellij_Idea/plugins/MCPlugin && export JAVA_HOME=/home/chalcanthite/.local/jdk-25 && timeout 300 mvn -q test -Dtest=MountControllerResolverTest 2>&1 | tail -15
```

Expected: `BUILD SUCCESS`，9 个用例全绿。

- [ ] **Step 5: Commit**

```bash
git add src/main/java/io/mczju/maggoteers/mob/MountControllerResolver.java \
        src/test/java/io/mczju/maggoteers/mob/MountControllerResolverTest.java && \
git commit -m "refactor(mob): pure controllerCandidates with explicit controller + camel fallback"
```

---

## Task 3：squad 表按对局作用域（R1）

**Files:**
- Modify: `src/main/java/io/mczju/maggoteers/mob/MountedSquadRegistry.java`（整体重写）
- Modify: `src/main/java/io/mczju/maggoteers/mob/MobFactory.java:42-84,127-138`
- Modify: `src/main/java/io/mczju/maggoteers/mob/MountedSquadAiService.java:23-43`
- Modify: `src/main/java/io/mczju/maggoteers/wave/WaveEngine.java:77-80,148,168,185-188`

**Interfaces:**
- Consumes: `AbstractGame`（MGC）
- Produces: `MountedSquadRegistry.register(game, root, controller)`、`removeByRoot(game, uuid)`、`squadsFor(game)`、`removeGame(game)`、`MobFactory.spawnStepGroup(game, baseLoc, step, track)`、`MobFactory.mountPassengersDelayed(game, mount, passengers, track)`

- [ ] **Step 1: 重写 MountedSquadRegistry（按 game 键控）**

整体替换 `src/main/java/io/mczju/maggoteers/mob/MountedSquadRegistry.java`：

```java
package io.mczju.maggoteers.mob;

import com.github.mczjuops.mczjugamecore.game.AbstractGame;
import org.bukkit.entity.LivingEntity;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 骑乘小队表：按对局作用域隔离（R1）；清理统一（R2）。全部主线程访问。 */
public final class MountedSquadRegistry {

    public record Squad(AbstractGame game, UUID rootUuid, UUID controllerUuid) {}

    private static final Map<AbstractGame, Map<UUID, Squad>> BY_GAME = new IdentityHashMap<>();

    private MountedSquadRegistry() {}

    public static void register(AbstractGame game, LivingEntity root, LivingEntity controller) {
        if (game == null || root == null || controller == null) return;
        BY_GAME.computeIfAbsent(game, k -> new java.util.HashMap<>())
                .put(root.getUniqueId(), new Squad(game, root.getUniqueId(), controller.getUniqueId()));
    }

    public static Squad removeByRoot(AbstractGame game, UUID rootUuid) {
        if (game == null || rootUuid == null) return null;
        Map<UUID, Squad> inner = BY_GAME.get(game);
        return inner == null ? null : inner.remove(rootUuid);
    }

    /** 本局全部 squad 快照（供 AiService 遍历）。 */
    public static List<Squad> squadsFor(AbstractGame game) {
        Map<UUID, Squad> inner = BY_GAME.get(game);
        return inner == null ? List.of() : new ArrayList<>(inner.values());
    }

    /** 整局清空（stop / clearTracking 调用，R2）。 */
    public static void removeGame(AbstractGame game) {
        if (game != null) BY_GAME.remove(game);
    }
}
```

- [ ] **Step 2: 更新调用点（编译对齐）**

`WaveEngine.java`：
- `spawnStep`（第 79 行）：`MobFactory.spawnStepGroup(game, base, step, ...)`（加 `game` 首参）。
- `handleMobDeath`（第 168 行）：`MountedSquadRegistry.removeByRoot(rt.game, uuid);`
- `stop`（第 148 行）：`MountedSquadRegistry.removeByRoot(game, uuid);`（Task 4 将整行删除）
- `spawnOnDeath`（第 186 行）：`MobFactory.mountPassengersDelayed(rt.game, le, ds.passengers(), ...)`（加 `game` 首参）。

`MobFactory.java`：
- `spawnStepGroup` 签名加 `AbstractGame game` 首参；延迟任务内 `MountedSquadRegistry.register(game, mount, controller);`
- `mountPassengersDelayed` 签名加 `AbstractGame game` 首参；`MountedSquadRegistry.register(game, mount, controller);`
- 文件头补 `import com.github.mczjuops.mczjugamecore.game.AbstractGame;`
- `mountPassengerTree` 的 `addPassenger` 失败分支加 L1 注释：

```java
            if (!carrier.addPassenger(child)) {
                // L1：child 已 remove 但仍被 livingMobs 追踪，由 5-tick 扫描回收；孙乘客随 child 移除弹飞成散怪
                LOG.warning("addPassenger 失败：" + carrier.getType() + " <- " + child.getType());
                child.remove();
                continue;
            }
```

`MountedSquadAiService.java`：
- `tick` 内 `MountedSquadRegistry.snapshot().values()` 改 `MountedSquadRegistry.squadsFor(game)`；
- 两处 `removeByRoot(squad.rootUuid())` 改 `removeByRoot(game, squad.rootUuid())`。

- [ ] **Step 3: 编译确认**

```bash
cd /mnt/e/Intellij_Idea/plugins/MCPlugin && export JAVA_HOME=/home/chalcanthite/.local/jdk-25 && timeout 400 mvn -q test-compile 2>&1 | tail -10
```

Expected: `BUILD SUCCESS`（无编译错误；若漏改调用点会在此暴露）。

- [ ] **Step 4: 跑既有单测**

```bash
cd /mnt/e/Intellij_Idea/plugins/MCPlugin && export JAVA_HOME=/home/chalcanthite/.local/jdk-25 && timeout 400 mvn -q test 2>&1 | tail -10
```

Expected: `BUILD SUCCESS`。

- [ ] **Step 5: Commit**

```bash
git add src/main/java/io/mczju/maggoteers/mob/MountedSquadRegistry.java \
        src/main/java/io/mczju/maggoteers/mob/MobFactory.java \
        src/main/java/io/mczju/maggoteers/mob/MountedSquadAiService.java \
        src/main/java/io/mczju/maggoteers/wave/WaveEngine.java && \
git commit -m "refactor(mob): game-scope MountedSquadRegistry (R1)"
```

---

## Task 4：清理统一（R2）+ 文档注释（L1/L3 收尾）

**Files:**
- Modify: `src/main/java/io/mczju/maggoteers/wave/WaveEngine.java:114-136,139-175`

**Interfaces:**
- Consumes: `MountedSquadRegistry.removeGame(game)`（Task 3）
- Produces: 无（行为修正）

- [ ] **Step 1: clearTracking 加 removeGame**

`clearTracking(game)` 末尾（`clearBossBars(rt);` 后）追加：

```java
        MountedSquadRegistry.removeGame(game);
```

- [ ] **Step 2: stop 改统一清理**

`stop(game)`：删除循环内 `MountedSquadRegistry.removeByRoot(game, uuid);` 一行；方法末尾（`PENDING_SPLIT_CANCEL.clear();` 后）追加：

```java
        MountedSquadRegistry.removeGame(game);
```

- [ ] **Step 3: handleMobDeath 加 L3 注释**

`handleMobDeath` 的 `MountedSquadRegistry.removeByRoot(rt.game, uuid);` 上一行加注释：

```java
        // L3：坐骑死 → squad 移除；其乘客随根实体死亡/删除弹飞，成独立追踪怪继续战斗（原版索敌）
        MountedSquadRegistry.removeByRoot(rt.game, uuid);
```

- [ ] **Step 4: 编译 + 跑既有单测**

```bash
cd /mnt/e/Intellij_Idea/plugins/MCPlugin && export JAVA_HOME=/home/chalcanthite/.local/jdk-25 && timeout 400 mvn -q test 2>&1 | tail -10
```

Expected: `BUILD SUCCESS`。

- [ ] **Step 5: Commit**

```bash
git add src/main/java/io/mczju/maggoteers/wave/WaveEngine.java && \
git commit -m "fix(mob): unified squad cleanup in clearTracking/stop (R2)"
```

---

## Task 5：座位上限改 EntityType 生产实现 + 去驯服（R4 座位面）

**Files:**
- Modify: `src/main/java/io/mczju/maggoteers/mob/MountPassengerLimits.java`
- Create: `src/test/java/io/mczju/maggoteers/mob/MountPassengerLimitsTest.java`

**Interfaces:**
- Consumes: `EntityType.CAMEL_HUSK`（已确认存在 paper-api 26.2）
- Produces: `directPassengerLimit(EntityType)`、`directPassengerLimit(LivingEntity)`（null→0）

- [ ] **Step 1: 写失败测试**

创建 `src/test/java/io/mczju/maggoteers/mob/MountPassengerLimitsTest.java`：

```java
package io.mczju.maggoteers.mob;

import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MountPassengerLimitsTest {

    @Test
    void camelFamilySeatLimitIsTwo() {
        assertEquals(2, MountPassengerLimits.directPassengerLimit(EntityType.CAMEL));
        assertEquals(2, MountPassengerLimits.directPassengerLimit(EntityType.CAMEL_HUSK));
    }

    @Test
    void horsesAndOthersSeatLimitIsOne() {
        assertEquals(1, MountPassengerLimits.directPassengerLimit(EntityType.HORSE));
        assertEquals(1, MountPassengerLimits.directPassengerLimit(EntityType.ZOMBIE_HORSE));
        assertEquals(1, MountPassengerLimits.directPassengerLimit(EntityType.STRIDER));
        assertEquals(1, MountPassengerLimits.directPassengerLimit(EntityType.ZOMBIE));
    }

    @Test
    void nullCarrierSeatLimitIsZero() {
        assertEquals(0, MountPassengerLimits.directPassengerLimit((LivingEntity) null));
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

```bash
cd /mnt/e/Intellij_Idea/plugins/MCPlugin && export JAVA_HOME=/home/chalcanthite/.local/jdk-25 && timeout 300 mvn -q test -Dtest=MountPassengerLimitsTest 2>&1 | tail -15
```

Expected: 编译失败（`directPassengerLimit(EntityType)` 不存在）。

- [ ] **Step 3: 改 MountPassengerLimits**

`src/main/java/io/mczju/maggoteers/mob/MountPassengerLimits.java`：
- 删 `import org.bukkit.entity.Camel;`、`import org.bukkit.entity.Strider;`；加 `import org.bukkit.entity.EntityType;`
- 座位上限改纯 EntityType 生产实现；`prepareMount` 删 `horse.setTamed(true);`（保留 `setAdult()`，加注释）：

```java
    public static int directPassengerLimit(EntityType type) {
        // Camel 族座位 2（CAMEL_HUSK 是 Camel 子类，须随族）；其余含全部 AbstractHorse 子类/Strider 默认 1。
        // 未来新增 Camel 族实体类型须同步加入本分支与 MountPassengerLimitsTest（spec §8）。
        if (type == EntityType.CAMEL || type == EntityType.CAMEL_HUSK) return 2;
        return 1;
    }

    public static int directPassengerLimit(LivingEntity carrier) {
        return carrier == null ? 0 : directPassengerLimit(carrier.getType());
    }

    public static void prepareMount(LivingEntity le) {
        if (le instanceof Mob m) {
            m.setAware(true);
            le.setRemoveWhenFarAway(false);
        }
        if (le instanceof AbstractHorse horse) {
            horse.setAdult();
            // 不再 setTamed(true)：原版僵尸骑士/骆驼队为未驯服态；车辆由 AiService moveTo 驱动（R4）
        }
        if (le instanceof Slime slime && !(le instanceof MagmaCube)) {
            if (slime.getSize() < 2) slime.setSize(2);
        }
        if (le instanceof MagmaCube cube && cube.getSize() < 2) {
            cube.setSize(2);
        }
    }
```

- [ ] **Step 4: 运行测试确认通过**

```bash
cd /mnt/e/Intellij_Idea/plugins/MCPlugin && export JAVA_HOME=/home/chalcanthite/.local/jdk-25 && timeout 300 mvn -q test -Dtest=MountPassengerLimitsTest 2>&1 | tail -10
```

Expected: `BUILD SUCCESS`，3 用例全绿。

- [ ] **Step 5: Commit**

```bash
git add src/main/java/io/mczju/maggoteers/mob/MountPassengerLimits.java \
        src/test/java/io/mczju/maggoteers/mob/MountPassengerLimitsTest.java && \
git commit -m "fix(mob): camel-family seat limit via EntityType + untamed mounts (R4)"
```

---

## Task 6：小队驱动配置化 + 冲刺坐骑（M1 + R4 移动面）

**Files:**
- Modify: `src/main/java/io/mczju/maggoteers/mob/MountedSquadAiService.java`
- Modify: `src/main/resources/config.yml`

**Interfaces:**
- Consumes: `MountedSquadRegistry.squadsFor/removeByRoot(game,…)`（Task 3）、`config.yml` 新键
- Produces: `mob_attributes.squad_target_range` / `squad_move_speed` / `squad_charge_speed`

- [ ] **Step 1: config.yml 加三键**

`src/main/resources/config.yml` 的 `mob_attributes:` 块追加（保留既有 `scale` / `follow_range`）：

```yaml
mob_attributes:
  scale: 1.0
  follow_range: 10.0
  squad_target_range: 32.0
  squad_move_speed: 1.0
  squad_charge_speed: 4.0
```

- [ ] **Step 2: 重写 MountedSquadAiService**

整体替换 `src/main/java/io/mczju/maggoteers/mob/MountedSquadAiService.java`：

```java
package io.mczju.maggoteers.mob;

import com.github.mczjuops.mczjugamecore.game.AbstractGame;
import io.mczju.maggoteers.MaggoteersPlugin;
import io.mczju.maggoteers.game.MaggoteersGame;
import io.mczju.maggoteers.state.PlayerStateManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;

import java.util.UUID;

/** 骑乘小队：controller 选目标，root 位移；马/骆驼以冲刺速度驱动（R4）。 */
public final class MountedSquadAiService {

    private MountedSquadAiService() {}

    public static void tick(AbstractGame game) {
        if (!(game instanceof MaggoteersGame)) return;
        var cfg = MaggoteersPlugin.getInstance().getConfig();
        double targetRange = cfg.getDouble("mob_attributes.squad_target_range", 32.0);
        double moveSpeed = cfg.getDouble("mob_attributes.squad_move_speed", 1.0);
        double chargeSpeed = cfg.getDouble("mob_attributes.squad_charge_speed", 4.0);
        for (MountedSquadRegistry.Squad squad : MountedSquadRegistry.squadsFor(game)) {
            var rootEnt = Bukkit.getEntity(squad.rootUuid());
            var ctrlEnt = Bukkit.getEntity(squad.controllerUuid());
            if (!(rootEnt instanceof LivingEntity root) || root.isDead() || !root.isValid()) {
                MountedSquadRegistry.removeByRoot(game, squad.rootUuid());
                continue;
            }
            if (!(ctrlEnt instanceof Mob controller) || controller.isDead() || !controller.isValid()) {
                MountedSquadRegistry.removeByRoot(game, squad.rootUuid());
                continue;
            }
            Player target = nearestParticipant(game, root.getLocation(), targetRange);
            if (target == null) continue;
            controller.setTarget(target);
            if (root instanceof Mob rootMob) {
                boolean sprint = root instanceof AbstractHorse;   // 族分类，不维护 EntityType 白名单（铁律③）
                if (sprint) root.setSprinting(true);
                // setSprinting 对马可能只生效动画；真正提速靠 moveTo 倍率（R4）
                rootMob.getPathfinder().moveTo(target, sprint ? chargeSpeed : moveSpeed);
            }
        }
    }

    private static Player nearestParticipant(AbstractGame game, Location from, double range) {
        Player best = null;
        double bestD = range * range;
        if (!(game instanceof MaggoteersGame)) return null;
        for (UUID uuid : PlayerStateManager.uuidsInGame(game)) {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null || !p.isOnline()) continue;
            if (!PlayerStateManager.isRunParticipant(game, p)) continue;
            if (!p.getWorld().equals(from.getWorld())) continue;
            double d = p.getLocation().distanceSquared(from);
            if (d <= bestD) {
                bestD = d;
                best = p;
            }
        }
        return best;
    }
}
```

- [ ] **Step 3: 编译 + 跑全部单测**

```bash
cd /mnt/e/Intellij_Idea/plugins/MCPlugin && export JAVA_HOME=/home/chalcanthite/.local/jdk-25 && timeout 400 mvn -q test 2>&1 | tail -10
```

Expected: `BUILD SUCCESS`。

- [ ] **Step 4: Commit**

```bash
git add src/main/java/io/mczju/maggoteers/mob/MountedSquadAiService.java \
        src/main/resources/config.yml && \
git commit -m "feat(mob): configurable squad drive + sprint mounts (M1+R4)"
```

---

## 收尾：全量验证 + 移交

- [ ] **Step 1: 全量单测**

```bash
cd /mnt/e/Intellij_Idea/plugins/MCPlugin && export JAVA_HOME=/home/chalcanthite/.local/jdk-25 && timeout 500 mvn -q test 2>&1 | tail -15
```

Expected: `BUILD SUCCESS`（既有全部测试 + 本计划 3 个新增/扩展测试类全绿）。

- [ ] **Step 2: 全量构建**

```bash
cd /mnt/e/Intellij_Idea/plugins/MCPlugin && export JAVA_HOME=/home/chalcanthite/.local/jdk-25 && timeout 500 mvn -q package -DskipTests 2>&1 | tail -10
```

Expected: `BUILD SUCCESS`，产出 `target/Maggoteers-<ver>.jar`。

- [ ] **Step 3: 自检清单（对照 spec）**

- §1.2 R2：`clearTracking` 与 `stop` **各自**调 `removeGame`；`stop` 无逐根 `removeByRoot`。
- §2 R3：`PassengerSpawn`/`PassengerCfg` 所有构造带 `controller`；`controllerCandidates` 显式段→默认回落段（2/2.5/3）去重；`parseController` 非布尔硬失败；>1 声明 warning。
- §3 M1：`squad_target_range`/`squad_move_speed`/`squad_charge_speed` 配置化，替换魔法数。
- §5.2 R4：`instanceof AbstractHorse` 冲刺检测；`setSprinting` + `moveTo(chargeSpeed)`；**无 root setTarget**（保留 §1.7）。
- §5.4：`prepareMount` 无 `setTamed`。
- §5.5：`directPassengerLimit(EntityType)` CAMEL/CAMEL_HUSK→2。
- §7 文件清单全覆盖；不触碰 `InfernalMobsBridge`/`MobDeathListener`/`SummonExecutor`/`WaveSpec`。

- [ ] **Step 4: 测试服验证（Must/Stretch，spec §6）**

部署 `target/Maggoteers-<ver>.jar`（`build-and-deploy.ps1`），测试服同时生成：
1. `w2_endless_spear`（`ZOMBIE_HORSE` 根 + 持 `mob_iron_spear` 的 `ZOMBIE` 乘客）；
2. `s1_desert_four_camels`（`CAMEL_HUSK` 根 + HUSK/PARCHED 双乘客）。

Must：① 车辆位移明显提速（对比踱步）；② controller 举矛姿态；③ `squad_charge_speed` 可调档校准默认 4.0。
Stretch：④ 贴脸挥矛伤害——失败记 follow-up（自定义近战驱动），不挡本计划。

- [ ] **Step 5: dev-log 追加一条**（日期 + 做了什么 + 决策原因 + 遗留问题），提交收尾 commit。

---

## Self-Review 记录

- **Spec 覆盖**：R1→Task 3、R2→Task 4、R3 数据面→Task 1 + 逻辑面→Task 2、M1→Task 6、R4 座位→Task 5 + 移动→Task 6、L1→Task 3 注释、L3→Task 4 注释、L4→Task 2/5 新增单测。§6 验证在收尾 Step 4。无缺口。
- **类型一致性**：`controllerCandidates` 返回 `List<List<Integer>>` 在两任务一致；`directPassengerLimit(EntityType)` / `(LivingEntity)` 双签名一致；`spawnStepGroup`/`mountPassengersDelayed` 首参 `AbstractGame game` 在 Task 3 定义并在 Task 3 内所有调用点统一。
- **无占位**：每步含完整代码 + 精确命令 + 期望输出。
