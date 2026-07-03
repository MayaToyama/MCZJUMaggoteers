# The Maggoteers — Plan 3: RunPlanner + 缩放 + 词缀 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把波次/数值生成从 Plan 2 的同步 `SimplePlanner`（coeff-only、count 固定、plain-Random）升级为**异步、种子化、确定性的 `RunPlanner`**：在非主线程纯计算 `coeff × affix × scaling` 最终倍率、count-roll（D6 走种子 RNG）、加权 roll 波次（含地图专属波次并入），产出同一份 `List<ActPlan>` 契约交给 `WaveScheduler`（**WaveEngine/Scheduler 零改动**）。战斗中零随机、零池查询、可重放。本 Plan 重在**纯逻辑单元测试**（不依赖 Bukkit）。

**Architecture:** 核心是把"配置读取"与"规划计算"解耦——`WaveDefinitions`（纯数据 record）由 `WavesConfig` 填充、`MapLibrary`（纯数据）由 `MapRepository` 填充，`ScalingConfig` 产出 `Scaling` 快照；`RunPlanner.plan(seed, playerCount, defs, maps, scaling)` 是**纯函数**（不碰 Bukkit，全单测）。`SeededRng` 封装确定性随机（同 seed → 同序列）；`Compose` 纯函数做 `coeff × affix × scaling`；count-roll 的补 1 概率走 `SeededRng`（D6 保确定/可重放）。`MaggoteersGame` 改为 `onGameStart` 异步跑 RunPlanner、就绪门闩等 `planReady` 后开波。

**Tech Stack:** Paper 1.21.7 API，Java 21，Maven，MCZJUGameCore 1.0.5，JUnit 5（**本 Plan 主战场：纯逻辑单测**）。

## Global Constraints

- **前置条件**：Plan 2 DoD 已满足——`ActPlan`/`WaveSpec`/`SpawnStep`/`Vec3` 模型、`WaveEngine`/`WaveScheduler`、`MapRepository`、`WavesConfig`、`SimplePlanner`、`MobFactory`、`StructurePaster` 均就位且三层闭环可玩。本 Plan 用 `RunPlanner` **替换** `SimplePlanner`，并让 `SpawnStep` 数值"完全解析"。
- **E8（异步不持 World）**：RunPlanner 在异步线程跑，只产出纯坐标 `Vec3`（已在 Plan 2 模型满足），主线程绑定 `World` 时才构造 `Location`。
- **D6（count-roll 走种子 RNG）**：`mob_count` 缩放 = `floor + 概率补 1`，补 1 的随机**必须**经 `SeededRng`，保证同 seed 可重放。
- **G3（刷怪点校验）**：`waves.yml` `steps[].point` 用到的每个编号必须在该图 `points.yml` 有定义；RunPlanner 解析时校验，缺失即抛错指到具体 strategy（启动/开局 fail-fast）。
- **铁律①②③** 继承：绝对坐标不入配置（RunPlanner 叠 `act_origins`）；定义唯一处（`affixes.yml`/`waves.yml`）；内容增删零代码。
- **本机无 JDK/Maven**：构建/部署由人执行；Claude 侧只写文件。

---

## 本 Plan 范围与衔接

- **Plan 3 做**：`SeededRng`、`Affix`/`AffixService`、`Compose`、`ScalingConfig`（数组 + count-roll）、`WaveDefinitions`/`MapLibrary` 纯数据、`RunPlanner`（异步种子化 + 专属波次并入 + G3 校验）、接入 `MaggoteersGame`（异步 + 就绪门闩）。
- **Plan 3 不做**：
  - `dropMult` 落地（货币掉落）→ Plan 6 `CurrencyService`（SpawnStep 已带 `dropMult` 字段，Plan 3 计算好，Plan 6 消费）。
  - 词缀药水对玩家生效（`on: hit-player`）→ Plan 5 效果系统（SpawnStep 已带 `potions`，Plan 3 解析进字段）。
  - NBT 异步预读（优化项，非必需）→ 后续打磨。

---

## Task 1: SeededRng（确定性随机）+ 单测

**Files:**
- Create: `src/main/java/io/mczju/maggoteers/plan/SeededRng.java`
- Create: `src/test/java/io/mczju/maggoteers/plan/SeededRngTest.java`

**Interfaces:**
- Produces: `SeededRng(long seed)`；`int nextInt(int bound)`、`double nextDouble()`、`int weightedIndex(double[] weights)`、`SeededRng derive(long salt)`、`long seed()`。
  - 契约：**同 seed → 同调用序列**；`derive(salt)` 产独立子序列（`new SeededRng(seed ^ salt)`），供各层/各池独立且稳定。

- [ ] **Step 1: 写 `SeededRng`**

```java
package io.mczju.maggoteers.plan;

import java.util.Random;

/**
 * 确定性随机：包装 {@link Random}，同 seed 同序列，供 RunPlanner 种子化预生成（可重放/可调试）。
 * <p>{@link #derive(long)} 用 {@code seed ^ salt} 派生独立子序列，让各层/各池 roll 互不干扰且稳定。
 */
public final class SeededRng {
    private final long seed;
    private final Random random;

    public SeededRng(long seed) {
        this.seed = seed;
        this.random = new Random(seed);
    }

    public long seed() { return seed; }

    public int nextInt(int bound) { return random.nextInt(bound); }

    public double nextDouble() { return random.nextDouble(); }

    /** 加权随机挑选索引（weights 全 0 或空 → 0）。 */
    public int weightedIndex(double[] weights) {
        double total = 0;
        for (double w : weights) total += Math.max(0, w);
        if (total <= 0) return 0;
        double r = random.nextDouble() * total;
        double acc = 0;
        for (int i = 0; i < weights.length; i++) {
            acc += Math.max(0, weights[i]);
            if (r <= acc) return i;
        }
        return weights.length - 1;
    }

    /** 派生子序列：seed ^ salt（保证不同 salt 得到不同独立流，同 salt 可重放）。 */
    public SeededRng derive(long salt) { return new SeededRng(seed ^ salt); }
}
```

- [ ] **Step 2: 写 `SeededRngTest`**

```java
package io.mczju.maggoteers.plan;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SeededRngTest {
    @Test
    void sameSeedSameSequence() {
        SeededRng a = new SeededRng(12345L);
        SeededRng b = new SeededRng(12345L);
        for (int i = 0; i < 100; i++) assertEquals(a.nextInt(1000), b.nextInt(1000));
        assertEquals(a.nextDouble(), b.nextDouble());
    }

    @Test
    void differentSeedDifferentSequence() {
        SeededRng a = new SeededRng(1L);
        SeededRng b = new SeededRng(2L);
        boolean diff = false;
        for (int i = 0; i < 20; i++) if (a.nextInt(1000) != b.nextInt(1000)) diff = true;
        assertTrue(diff);
    }

    @Test
    void weightedIndexRespectsWeights() {
        SeededRng rng = new SeededRng(42L);
        int[] counts = new int[3];
        for (int i = 0; i < 3000; i++) counts[rng.weightedIndex(new double[]{1, 3, 1})]++;
        // index 1 权重 3/5，应明显最多；index 0 与 2 权重相等，应接近
        assertTrue(counts[1] > counts[0] && counts[1] > counts[2]);
        assertEquals(counts[0], counts[2], 120); // 容差
    }

    @Test
    void weightedIndexAllZeroReturnsZero() {
        assertEquals(0, new SeededRng(1L).weightedIndex(new double[]{0, 0, 0}));
    }

    @Test
    void deriveIsReproducibleAndIndependent() {
        SeededRng root = new SeededRng(99L);
        SeededRng c1 = root.derive(1L);
        SeededRng c1again = new SeededRng(99L).derive(1L);
        assertEquals(c1.nextInt(500), c1again.nextInt(500));   // 同 salt 可重放
        // 不同 salt 序列不同（极大概率）
        assertNotEquals(root.derive(1L).nextInt(500), root.derive(2L).nextInt(500));
    }
}
```

- [ ] **Step 3: 构建并跑测试**

Run: `mvn -q test`
Expected: `BUILD SUCCESS`，`SeededRngTest` 通过。

- [ ] **Step 4: 提交**

```bash
git add src/main/java/io/mczju/maggoteers/plan/SeededRng.java \
        src/test/java/io/mczju/maggoteers/plan/SeededRngTest.java
git commit -m "feat(plan3): SeededRng 确定性随机 + 加权/派生子序列"
```

---

## Task 2: Affix 模型 + AffixService + Compose 纯函数 + 单测

**Files:**
- Create: `src/main/resources/affixes.yml`（默认样例）
- Create: `src/main/java/io/mczju/maggoteers/config/Affix.java`
- Create: `src/main/java/io/mczju/maggoteers/config/AffixService.java`
- Create: `src/main/java/io/mczju/maggoteers/plan/Compose.java`
- Create: `src/test/java/io/mczju/maggoteers/plan/ComposeTest.java`

**Interfaces:**
- Produces:
  - `Affix{id, hp, dmg, speed, drop, List<PotionSpec> potions, on, display}`（缺省 hp/dmg/speed/drop=1.0）。
  - `AffixService.load(JavaPlugin)` / `getInstance()` / `Affix get(String id)`。
  - `Compose.compose(CoeffCfg coeff, List<Affix> affixes, ScalingConfig.Scaling snap): MobScale{hp,dmg,speed,drop}` —— 纯函数，`coeff × Πaffix × scaling`。

- [ ] **Step 1: 写默认 `affixes.yml`**

```yaml
# 词缀定义唯一处（铁律②）。缺省倍率=1.0（即不写=不影响）。display 用 MiniMessage。
affixes:
  armored: { hp: 2.0, display: "<aqua>装甲" }
  berserk: { dmg: 1.5, speed: 1.2, display: "<red>狂暴" }
  toxic:
    potions: [ {effect: POISON, amp: 1, dur: 200} ]
    on: hit-player
    display: "<green>毒素"
  greedy:  { drop: 1.5, display: "<gold>贪婪" }
```

- [ ] **Step 2: 写 `Affix`**

```java
package io.mczju.maggoteers.config;

import io.mczju.maggoteers.wave.PotionSpec;
import java.util.List;

/**
 * 词缀：挂在原版实体上的强化层（纯配置）。缺省倍率 1.0。
 * @param hp/dmg/speed/drop 倍率（与 coeff、scaling 连乘）
 * @param potions           附带药水（如毒素：攻击玩家时上毒；Plan 5 落地）
 * @param on                potions 触发时机（hit-player 等；Plan 5 用）
 * @param display           头顶/名字 MiniMessage
 */
public record Affix(String id, double hp, double dmg, double speed, double drop,
                    List<PotionSpec> potions, String on, String display) {
    public Affix {
        if (hp <= 0) hp = 1; if (dmg <= 0) dmg = 1; if (speed <= 0) speed = 1; if (drop <= 0) drop = 1;
        potions = potions == null ? List.of() : List.copyOf(potions);
    }
}
```

- [ ] **Step 3: 写 `AffixService`（加载 affixes.yml）**

```java
package io.mczju.maggoteers.config;

import io.mczju.maggoteers.wave.PotionSpec;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.*;

/** 加载并缓存 affixes.yml。onEnable 调一次。 */
public final class AffixService {
    private static AffixService INSTANCE;
    private final Map<String, Affix> affixes = new HashMap<>();

    public static void load(JavaPlugin plugin) {
        plugin.saveResource("affixes.yml", false);
        File f = new File(plugin.getDataFolder(), "affixes.yml");
        var cfg = YamlConfiguration.loadConfiguration(f);
        INSTANCE = new AffixService();
        var sec = cfg.getConfigurationSection("affixes");
        if (sec != null) {
            for (String id : sec.getKeys(false)) {
                INSTANCE.affixes.put(id, parse(id, sec.getConfigurationSection(id)));
            }
        }
        plugin.getLogger().info("AffixService 已加载 " + INSTANCE.affixes.size() + " 词缀。");
    }

    @SuppressWarnings("unchecked")
    private static Affix parse(String id, ConfigurationSection s) {
        if (s == null) return new Affix(id, 1, 1, 1, 1, List.of(), null, id);
        List<PotionSpec> pots = new ArrayList<>();
        for (var m : s.getMapList("potions")) {
            pots.add(new PotionSpec((String) m.get("effect"),
                    ((Number) m.getOrDefault("amp", 0)).intValue(),
                    ((Number) m.getOrDefault("dur", 0)).intValue()));
        }
        return new Affix(id,
                s.getDouble("hp", 1.0), s.getDouble("dmg", 1.0),
                s.getDouble("speed", 1.0), s.getDouble("drop", 1.0),
                pots, s.getString("on"), s.getString("display", id));
    }

    public static AffixService getInstance() {
        if (INSTANCE == null) throw new IllegalStateException("AffixService 未加载！");
        return INSTANCE;
    }

    public Affix get(String id) { return affixes.get(id); }

    /** 批量解析（缺失的 id 抛错，便于 G3-style fail-fast）。 */
    public List<Affix> resolve(List<String> ids) {
        List<Affix> out = new ArrayList<>();
        for (String id : ids) {
            Affix a = affixes.get(id);
            if (a == null) throw new IllegalStateException("词缀未定义: " + id);
            out.add(a);
        }
        return out;
    }

    private AffixService() {}
}
```

- [ ] **Step 4: 写 `Compose`（纯函数，需先有 `ScalingConfig.Scaling`——在 Task 3 定义；这里先放占位引用，Task 3 补齐后编译）**

> 为避免前后向依赖导致编译断裂，**先在 Task 3 创建 `ScalingConfig.Scaling`**，再回此 Step 编译。实际执行顺序：Task 2 Step 4 写 `Compose`（引用 `ScalingConfig.Scaling`）→ Task 3 写 `ScalingConfig` → Task 2 Step 5 才能编译跑测试。**两 Task 一起编译**。

Create `src/main/java/io/mczju/maggoteers/plan/Compose.java`：
```java
package io.mczju.maggoteers.plan;

import io.mczju.maggoteers.config.Affix;
import io.mczju.maggoteers.config.CoeffCfg;
import io.mczju.maggoteers.config.ScalingConfig;

import java.util.List;

/**
 * 数值合成纯函数：最终倍率 = coeff × Π(affix) × scaling（三层叠加，§7.1）。
 * 与 Bukkit 解耦，纯单测。
 */
public final class Compose {

    /** 一只怪的最终倍率四元组（hp/dmg/speed/drop）。 */
    public record MobScale(double hp, double dmg, double speed, double drop) {}

    public static MobScale compose(CoeffCfg coeff, List<Affix> affixes, ScalingConfig.Scaling snap) {
        double hp = coeff.hp(), dmg = coeff.dmg(), speed = coeff.speed(), drop = 1.0;
        for (Affix a : affixes) {
            hp *= a.hp();
            dmg *= a.dmg();
            speed *= a.speed();
            drop *= a.drop();
        }
        return new MobScale(hp * snap.mobHp(), dmg * snap.mobDamage(), speed * snap.mobSpeed(), drop * snap.currencyDrop());
    }

    private Compose() {}
}
```

- [ ] **Step 5: 写 `ComposeTest`（Task 3 完成后一起跑）**

```java
package io.mczju.maggoteers.plan;

import io.mczju.maggoteers.config.Affix;
import io.mczju.maggoteers.config.CoeffCfg;
import io.mczju.maggoteers.config.ScalingConfig;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ComposeTest {
    private static final ScalingConfig.Scaling SNAP = new ScalingConfig.Scaling(1.6, 1.3, 1.0, 1.8, 1.2);

    @Test
    void coeffOnlyTimesScaling() {
        Compose.MobScale m = Compose.compose(new CoeffCfg(2.0, 1.5, 1.0), List.of(), SNAP);
        assertEquals(2.0 * 1.6, m.hp(), 1e-9);
        assertEquals(1.5 * 1.3, m.dmg(), 1e-9);
        assertEquals(1.0 * 1.0, m.speed(), 1e-9);
        assertEquals(1.0 * 1.8, m.drop(), 1e-9);
    }

    @Test
    void multipleAffixesMultiply() {
        Affix armored = new Affix("armored", 2.0, 1, 1, 1, List.of(), null, "x");
        Affix berserk = new Affix("berserk", 1, 1.5, 1.2, 1, List.of(), null, "x");
        Compose.MobScale m = Compose.compose(new CoeffCfg(1.0, 1.0, 1.0), List.of(armored, berserk), SNAP);
        assertEquals(2.0 * 1.6, m.hp(), 1e-9);
        assertEquals(1.5 * 1.3, m.dmg(), 1e-9);
        assertEquals(1.2 * 1.0, m.speed(), 1e-9);
    }

    @Test
    void dropScalesWithGreedy() {
        Affix greedy = new Affix("greedy", 1, 1, 1, 1.5, List.of(), null, "x");
        Compose.MobScale m = Compose.compose(new CoeffCfg(1.0, 1.0, 1.0), List.of(greedy), SNAP);
        assertEquals(1.5 * 1.8, m.drop(), 1e-9);
    }
}
```

> 不在此 Step 提交（依赖 Task 3 的 `ScalingConfig`）。Task 3 末尾一起提交 Task 2 + Task 3。

---

## Task 3: ScalingConfig（人数缩放数组 + count-roll）+ 单测 + config.yml

**Files:**
- Modify: `src/main/resources/config.yml`（加 `scaling` 段）
- Create: `src/main/java/io/mczju/maggoteers/config/ScalingConfig.java`
- Create: `src/test/java/io/mczju/maggoteers/config/ScalingConfigTest.java`

**Interfaces:**
- Produces:
  - `ScalingConfig.Scaling{mobHp, mobDamage, mobSpeed, currencyDrop, mobCount}`（某人数下的快照）。
  - `ScalingConfig.load(JavaPlugin)` / `getInstance()` / `Scaling scaleFor(int playerCount)`（1–4，越界 clamp）。
  - `ScalingConfig.rollCount(int base, double mobCountMult, SeededRng rng): int` —— `floor(base×m) + (rng补1)`（D6）。

- [ ] **Step 1: 在 `config.yml` 追加 `scaling` 段**

把 `config.yml` 末尾的 Plan 2 内容之后追加（如已有 `scaling` 注释占位则替换该注释）：

```yaml
scaling:
  mob_hp:        [1.0, 1.3, 1.6, 2.0]   # 1–4 人
  mob_damage:    [1.0, 1.15, 1.3, 1.5]
  mob_count:     [1.0, 1.0, 1.2, 1.5]   # 向下取整 + 概率补 1（走种子 RNG，D6）
  currency_drop: [1.0, 1.4, 1.8, 2.2]
```

- [ ] **Step 2: 写 `ScalingConfig`**

```java
package io.mczju.maggoteers.config;

import io.mczju.maggoteers.plan.SeededRng;
import org.bukkit.plugin.java.JavaPlugin;

/** 人数缩放配置（借鉴前代 VampireSurvivor ScalingConfig）。onEnable 调一次。 */
public final class ScalingConfig {

    /** 某人数下的缩放快照。 */
    public record Scaling(double mobHp, double mobDamage, double mobSpeed, double currencyDrop, double mobCount) {}

    private static ScalingConfig INSTANCE;
    private final double[] mobHp, mobDamage, mobSpeed, currencyDrop, mobCount;

    public static void load(JavaPlugin plugin) {
        INSTANCE = new ScalingConfig(
                read(plugin, "scaling.mob_hp",       new double[]{1.0, 1.3, 1.6, 2.0}),
                read(plugin, "scaling.mob_damage",   new double[]{1.0, 1.15, 1.3, 1.5}),
                read(plugin, "scaling.mob_speed",    new double[]{1.0, 1.0, 1.0, 1.0}),
                read(plugin, "scaling.currency_drop",new double[]{1.0, 1.4, 1.8, 2.2}),
                read(plugin, "scaling.mob_count",    new double[]{1.0, 1.0, 1.2, 1.5}));
        plugin.getLogger().info("ScalingConfig 已加载。");
    }

    private static double[] read(JavaPlugin plugin, String path, double[] def) {
        var list = plugin.getConfig().getDoubleList(path);
        return list.isEmpty() ? def : list.stream().mapToDouble(Double::doubleValue).toArray();
    }

    public static ScalingConfig getInstance() {
        if (INSTANCE == null) throw new IllegalStateException("ScalingConfig 未加载！");
        return INSTANCE;
    }

    /** 1–4 人，越界 clamp 到端点。 */
    public Scaling scaleFor(int playerCount) {
        return new Scaling(
                at(mobHp, playerCount), at(mobDamage, playerCount), at(mobSpeed, playerCount),
                at(currencyDrop, playerCount), at(mobCount, playerCount));
    }

    private static double at(double[] arr, int playerCount) {
        int idx = Math.max(0, Math.min(playerCount - 1, arr.length - 1));
        return arr[idx];
    }

    /**
     * count-roll（D6）：{@code floor(base × m) + (rng.nextDouble() < frac ? 1 : 0)}。
     * 补 1 的随机走 {@link SeededRng}，保证同 seed 可重放。
     */
    public static int rollCount(int base, double mobCountMult, SeededRng rng) {
        double scaled = base * mobCountMult;
        int floor = (int) Math.floor(scaled);
        double frac = scaled - floor;
        return Math.max(0, floor + (rng.nextDouble() < frac ? 1 : 0));
    }

    private ScalingConfig(double[] mobHp, double[] mobDamage, double[] mobSpeed,
                          double[] currencyDrop, double[] mobCount) {
        this.mobHp = mobHp; this.mobDamage = mobDamage; this.mobSpeed = mobSpeed;
        this.currencyDrop = currencyDrop; this.mobCount = mobCount;
    }
}
```

- [ ] **Step 3: 写 `ScalingConfigTest`（纯：用构造 + `at`/`rollCount`，绕过 Bukkit 单例）**

> `scaleFor`/`at` 依赖单例加载（Bukkit）。为可测，**给 `ScalingConfig` 加包级测试构造器与 `scaleFor` 直驱**：把 `at` 改 `static`（已是），单测直接调 `ScalingConfig.rollCount`（static）+ 用 `Scaling` record 验证 `Compose`。`scaleFor` 的 clamp 逻辑用 `at` 的等价纯函数另测：

补一个纯 clamp 工具到测试：直接测 `rollCount`（核心 D6 逻辑）即可，clamp 由集成测试覆盖。`ScalingConfigTest`：

```java
package io.mczju.maggoteers.config;

import io.mczju.maggoteers.plan.SeededRng;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ScalingConfigTest {
    @Test
    void rollCountFloorNoFraction() {
        // base=5, m=1.0 → 5.0 → floor 5, frac 0 → 永远 5
        SeededRng rng = new SeededRng(1L);
        assertEquals(5, ScalingConfig.rollCount(5, 1.0, rng));
        assertEquals(5, ScalingConfig.rollCount(5, 1.0, new SeededRng(2L)));
    }

    @Test
    void rollCountFloorAlwaysWhenFracZeroFromIntegerMult() {
        // base=5, m=2.0 → 10.0 → 10
        assertEquals(10, ScalingConfig.rollCount(5, 2.0, new SeededRng(7L)));
    }

    @Test
    void rollCountFractionalSupplementIsSeeded() {
        // base=5, m=1.2 → 6.0 → frac 0 → 6（5*1.2=6.0 整数）
        assertEquals(6, ScalingConfig.rollCount(5, 1.2, new SeededRng(7L)));
        // base=5, m=1.3 → 6.5 → floor 6, frac 0.5 → 一半概率补到 7
        int sevens = 0;
        for (int s = 0; s < 2000; s++) {
            if (ScalingConfig.rollCount(5, 1.3, new SeededRng(s)) == 7) sevens++;
        }
        // ~1000 ± 容差
        assertTrue(sevens > 850 && sevens < 1150, "sevens=" + sevens);
    }

    @Test
    void rollCountDeterministic() {
        // 同 seed 同结果（D6 可重放）
        assertEquals(ScalingConfig.rollCount(5, 1.3, new SeededRng(555L)),
                     ScalingConfig.rollCount(5, 1.3, new SeededRng(555L)));
    }

    @Test
    void rollCountNeverNegative() {
        assertEquals(0, ScalingConfig.rollCount(0, 1.5, new SeededRng(1L)));
    }
}
```

- [ ] **Step 4: 构建 + 跑 Task 2 + Task 3 测试**

Run: `mvn -q test`
Expected: `BUILD SUCCESS`；`SeededRngTest`、`ComposeTest`、`ScalingConfigTest` 全绿。

- [ ] **Step 5: 在 `MaggoteersPlugin.onEnable` 加载 `ScalingConfig` + `AffixService`**

在 `MapRepository.load(this);` 之后追加：
```java
        ScalingConfig.load(this);
        AffixService.load(this);
```
补 import：
```java
import io.mczju.maggoteers.config.AffixService;
import io.mczju.maggoteers.config.ScalingConfig;
```

- [ ] **Step 6: 提交 Task 2 + Task 3**

```bash
git add src/main/resources/affixes.yml src/main/resources/config.yml \
        src/main/java/io/mczju/maggoteers/config/Affix.java \
        src/main/java/io/mczju/maggoteers/config/AffixService.java \
        src/main/java/io/mczju/maggoteers/plan/Compose.java \
        src/main/java/io/mczju/maggoteers/config/ScalingConfig.java \
        src/main/java/io/mczju/maggoteers/MaggoteersPlugin.java \
        src/test/java/io/mczju/maggoteers/plan/ComposeTest.java \
        src/test/java/io/mczju/maggoteers/config/ScalingConfigTest.java
git commit -m "feat(plan3): Affix/AffixService + Compose纯函数 + ScalingConfig(count-roll D6)"
```

---

## Task 4: WaveDefinitions/MapLibrary 纯数据 + StepCfg 词缀 + SpawnStep.dropMult + RunPlanner（纯规划）+ 单测

**Files:**
- Modify: `src/main/java/io/mczju/maggoteers/config/StepCfg.java`（加 `List<String> affixes`）
- Modify: `src/main/java/io/mczju/maggoteers/config/WavesConfig.java`（解析 `affixes` + 暴露 `WaveDefinitions`）
- Create: `src/main/java/io/mczju/maggoteers/config/WaveDefinitions.java`
- Create: `src/main/java/io/mczju/maggoteers/world/MapLibrary.java`
- Modify: `src/main/java/io/mczju/maggoteers/world/MapRepository.java`（暴露 `MapLibrary` + MapEntry 加 `specialWaves`）
- Modify: `src/main/java/io/mczju/maggoteers/wave/SpawnStep.java`（加 `double dropMult`）
- Create: `src/main/java/io/mczju/maggoteers/plan/RunPlanner.java`
- Create: `src/test/java/io/mczju/maggoteers/plan/RunPlannerTest.java`

**Interfaces:**
- Produces:
  - `WaveDefinitions{Map<String,SpawnStrategyCfg> strategies; Map<String,Map<String,List<PoolEntry>>> pools}`（纯数据）。
  - `MapLibrary{Map<String,List<MapEntry>> byAct}`（纯数据；`MapEntry` 已是 record）。
  - `SpawnStep` 增加 `double dropMult`（其余字段不变；构造点全更新）。
  - `RunPlanner.plan(long seed, int playerCount): List<ActPlan>`（生产入口，读单例）。
  - `RunPlanner.plan(long seed, int playerCount, WaveDefinitions, MapLibrary, ScalingConfig): List<ActPlan>`（**纯函数，单测主战场**）。

- [ ] **Step 1: `StepCfg` 加 `affixes`**

整文件替换 `StepCfg.java`：
```java
package io.mczju.maggoteers.config;

import org.bukkit.entity.EntityType;
import java.util.List;

/** waves.yml 一条 step（未解析）。affixes 为词缀 id 列表（Plan 3 起 RunPlanner 解析）。 */
public record StepCfg(String point, EntityType type, int count, CoeffCfg coeff,
                      int delaySec, List<String> affixes) {
    public StepCfg {
        affixes = affixes == null ? List.of() : List.copyOf(affixes);
    }
}
```

> Plan 2 的 `SimplePlanner.rollWave` 构造 `StepCfg` 处会因新增字段编译失败——**Plan 3 本 Task 末尾删除 `SimplePlanner`**（被 RunPlanner 取代），故不影响最终编译。过渡期忽略 SimplePlanner。

- [ ] **Step 2: 写 `WaveDefinitions` 纯数据**

```java
package io.mczju.maggoteers.config;

import io.mczju.maggoteers.config.WavesConfig.PoolEntry;
import java.util.List;
import java.util.Map;

/** waves.yml 的纯数据视图（strategies + pools），供 RunPlanner 纯函数消费、单测构造。 */
public record WaveDefinitions(
        Map<String, SpawnStrategyCfg> strategies,
        Map<String, Map<String, List<PoolEntry>>> pools) {
    public WaveDefinitions {
        strategies = Map.copyOf(strategies);
        pools = Map.copyOf(pools);
    }

    public SpawnStrategyCfg strategy(String id) { return strategies.get(id); }
    public List<PoolEntry> pool(String act, String tier) {
        return pools.getOrDefault(act, Map.of()).getOrDefault(tier, List.of());
    }
}
```

- [ ] **Step 3: `WavesConfig` 解析 `affixes` + 暴露 `WaveDefinitions`**

在 `WavesConfig.parseStrategy` 的 step 解析里加 affixes；并在类里加 `definitions()`：

`parseStrategy` 里构造 `StepCfg` 处，把：
```java
            steps.add(new StepCfg(point, type, count, new CoeffCfg(hp, dmg, spd), delay));
```
改为：
```java
            List<String> affixes = new ArrayList<>();
            Object affRaw = m.get("affixes");
            if (affRaw instanceof List<?> al) for (Object o : al) affixes.add(String.valueOf(o));
            steps.add(new StepCfg(point, type, count, new CoeffCfg(hp, dmg, spd), delay, affixes));
```
并新增方法（放在 `getPool` 之后）：
```java
    public WaveDefinitions definitions() {
        return new WaveDefinitions(strategies, pools);
    }
```
确认 `WavesConfig` 顶部有 `import java.util.Map;`（已有）。

- [ ] **Step 4: `MapEntry` 加 `specialWaves` + `MapLibrary` + `MapRepository.library()`**

先在 `MapRepository` 里把 `MapEntry` record 加字段：
```java
public record MapEntry(String mapId, MapPoints points, Set<String> nbtFiles, File dir,
                       Map<String, List<WavesConfig.PoolEntry>> specialWaves) {
    public boolean hasNbt(String quad) { return nbtFiles.contains(quad + ".nbt"); }
}
```
> `specialWaves`：`tier(weak/strong/boss) → 池条目列表`（来自该图 `special_waves.yml`，Task 5 填充）。本 Task 先置空 map。

在 `MapRepository.load` 构造 `MapEntry` 处改为：
```java
entries.add(new MapEntry(mapDir.getName(), points, nbts, mapDir, parseSpecialWaves(mapDir)));
```
并加解析方法（本 Task 先返回空，Task 5 实装）：
```java
    private static Map<String, List<WavesConfig.PoolEntry>> parseSpecialWaves(File mapDir) {
        return Map.of();   // Task 5 实装 special_waves.yml
    }
```
补 import `import io.mczju.maggoteers.config.WavesConfig;` 与 `import java.util.Map;`（确认 `WavesConfig` 可见）。

Create `MapLibrary.java`：
```java
package io.mczju.maggoteers.world;

import io.mczju.maggoteers.world.MapRepository.MapEntry;
import java.util.List;
import java.util.Map;

/** 地图库纯数据视图（act → 该层所有地图条目），供 RunPlanner 纯函数消费。 */
public record MapLibrary(Map<String, List<MapEntry>> byAct) {
    public List<MapEntry> maps(String act) { return byAct.getOrDefault(act, List.of()); }
}
```

在 `MapRepository` 加方法：
```java
    public static MapLibrary library() {
        return new MapLibrary(BY_ACT);
    }
```
（`BY_ACT` 是 `Map<String, List<MapEntry>>`，构造 `MapLibrary` 直接包一层；注意 `BY_ACT` 用 `HashMap` 可变，`MapLibrary` record 不 copy——为纯数据快照，调用时机固定在开局，可接受。若需防御性拷贝，改 `Map.copyOf`，但 `MapEntry` 含 `File dir` 仍可拷。保持简单：不 copy。）

- [ ] **Step 5: `SpawnStep` 加 `dropMult`**

整文件替换 `SpawnStep.java`：
```java
package io.mczju.maggoteers.wave;

import org.bukkit.entity.EntityType;
import java.util.List;

/**
 * 一条刷怪指令，数值已"完全解析"（coeff×affix×scaling）。主线程生成直接套用，零随机/零查询。
 *
 * @param point      绝对坐标
 * @param type       原版实体
 * @param count      解析后只数（Plan 3 已 count-roll）
 * @param hpMult     最终血量倍率
 * @param dmgMult    最终攻击倍率
 * @param speedMult  最终速度倍率
 * @param dropMult   最终掉落倍率（Plan 6 CurrencyService 消费）
 * @param delayTicks 距本波开始延迟
 * @param affixes    词缀 id（头顶显示）
 * @param potions    词缀药水（Plan 5 生效）
 */
public record SpawnStep(Vec3 point, EntityType type, int count,
                        double hpMult, double dmgMult, double speedMult, double dropMult,
                        int delayTicks, List<String> affixes, List<PotionSpec> potions) {
    public SpawnStep {
        affixes = affixes == null ? List.of() : List.copyOf(affixes);
        potions = potions == null ? List.of() : List.copyOf(potions);
    }
}
```

> 这会破坏 Plan 2 的 `WaveSpecTest`（构造 SpawnStep 缺 dropMult）与 `MobFactory`/`WaveEngine`（不构造，只用 hp/dmg/speed，不受影响）。更新 `WaveSpecTest`：把两处 `new SpawnStep(new Vec3(0,64,0), EntityType.ZOMBIE, 5, 1.0,1.0,1.0, delay, List.of(), List.of())` 改为 `new SpawnStep(new Vec3(0,64,0), EntityType.ZOMBIE, 5, 1.0,1.0,1.0, 1.0, delay, List.of(), List.of())`（在 `speedMult` 后插 `1.0`）。

- [ ] **Step 6: 写 `RunConfig` 纯数据（层原点 + 每层波数快照）**

Create `src/main/java/io/mczju/maggoteers/plan/RunConfig.java`：
```java
package io.mczju.maggoteers.plan;

import io.mczju.maggoteers.wave.Vec3;
import java.util.Map;

/**
 * RunPlanner 的纯配置快照：层原点（来自 act_origins）+ 每层 weak/strong 波数（来自 waves_per_act）。
 * <p>生产由 {@link RunPlanner#loadRunConfig} 从 config.yml 一次性读出；单测直接构造。
 */
public record RunConfig(Map<String, Vec3> origins, Map<String, int[]> wavesPerAct) {
    public Vec3 origin(String act) { return origins.getOrDefault(act, new Vec3(0, 64, 0)); }
    public int weak(String act)    { return wavesPerAct.getOrDefault(act, new int[]{3, 2})[0]; }
    public int strong(String act)  { return wavesPerAct.getOrDefault(act, new int[]{3, 2})[1]; }
}
```

- [ ] **Step 7: 给 `AffixService` 与 `ScalingConfig` 加包级测试工厂（绕过 Bukkit 单例）**

在 `AffixService` 中：把字段 `private final Map<String, Affix> affixes` 改为 `final Map<String, Affix> affixes`（去 `private`，包可见），并在 `getInstance()` 后加：
```java
    /** 测试用：直接构造（绕过 Bukkit 文件加载）。 */
    static AffixService forTesting(java.util.Map<String, Affix> data) {
        AffixService s = new AffixService();
        s.affixes.putAll(data);
        return s;
    }
```

在 `ScalingConfig` 中（`at(...)` 方法后）加：
```java
    /** 测试用：直接构造（绕过 Bukkit）。 */
    static ScalingConfig forTesting(double[] hp, double[] dmg, double[] spd, double[] drop, double[] count) {
        return new ScalingConfig(hp, dmg, spd, drop, count);
    }
```

- [ ] **Step 8: 写 `RunPlanner`（生产入口 + 完全注入的纯函数，一步到位）**

Create `src/main/java/io/mczju/maggoteers/plan/RunPlanner.java`：
```java
package io.mczju.maggoteers.plan;

import io.mczju.maggoteers.MaggoteersPlugin;
import io.mczju.maggoteers.config.*;
import io.mczju.maggoteers.util.Coords;
import io.mczju.maggoteers.wave.*;
import io.mczju.maggoteers.world.MapLibrary;
import io.mczju.maggoteers.world.MapRepository;
import io.mczju.maggoteers.world.MapRepository.MapEntry;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

/**
 * 异步种子化预生成（§6）。同 seed + 同 playerCount → 同 {@code List<ActPlan>}（可重放）。
 * <p>{@link #plan(long, int, WaveDefinitions, MapLibrary, ScalingConfig, AffixService, RunConfig)}
 * 是**完全注入的纯函数**（不碰 Bukkit），全单测；
 * 生产入口 {@link #plan(long, int)} 读单例 + 构造 {@link RunConfig}。
 */
public final class RunPlanner {

    private static final String[] ACTS = {"act1", "act2", "act3"};

    /** 生产入口：onGameStart 异步线程调用。 */
    public static List<ActPlan> plan(long seed, int playerCount) {
        JavaPlugin plugin = MaggoteersPlugin.getInstance();
        return plan(seed, playerCount,
                WavesConfig.getInstance().definitions(),
                MapRepository.library(),
                ScalingConfig.getInstance(),
                AffixService.getInstance(),
                loadRunConfig(plugin));
    }

    /** 纯函数：完全注入，不碰 Bukkit。 */
    public static List<ActPlan> plan(long seed, int playerCount, WaveDefinitions defs,
                                     MapLibrary maps, ScalingConfig scaling,
                                     AffixService affixes, RunConfig cfg) {
        SeededRng root = new SeededRng(seed);
        ScalingConfig.Scaling snap = scaling.scaleFor(playerCount);
        List<ActPlan> out = new ArrayList<>(3);
        for (int i = 0; i < ACTS.length; i++) {
            out.add(planAct(root.derive(i), ACTS[i], defs, maps, snap, affixes, cfg));
        }
        return out;
    }

    private static ActPlan planAct(SeededRng actRng, String act, WaveDefinitions defs, MapLibrary maps,
                                   ScalingConfig.Scaling snap, AffixService affixes, RunConfig cfg) {
        List<MapEntry> avail = maps.maps(act);
        if (avail.isEmpty()) throw new IllegalStateException(act + " 无可用地图");
        MapEntry map = avail.get(actRng.nextInt(avail.size()));
        Vec3 origin = cfg.origin(act);

        List<WaveSpec> waves = new ArrayList<>();
        for (int i = 0; i < cfg.weak(act); i++)
            waves.add(rollWave(actRng.derive(100 + i), act, "weak",   map, origin, defs, snap, affixes));
        for (int i = 0; i < cfg.strong(act); i++)
            waves.add(rollWave(actRng.derive(200 + i), act, "strong", map, origin, defs, snap, affixes));
        waves.add(rollWave(actRng.derive(300), act, "boss", map, origin, defs, snap, affixes));

        return new ActPlan(map.mapId(), Coords.resolve(origin, map.points().playerSpawn()), waves);
    }

    private static WaveSpec rollWave(SeededRng rng, String act, String tier, MapEntry map, Vec3 origin,
                                     WaveDefinitions defs, ScalingConfig.Scaling snap, AffixService affixes) {
        // 合并地图专属波次（Task 5 填 specialWaves）
        List<WavesConfig.PoolEntry> base = new ArrayList<>(defs.pool(act, tier));
        base.addAll(map.specialWaves().getOrDefault(tier, List.of()));
        if (base.isEmpty()) throw new IllegalStateException(act + "/" + tier + " 池为空");

        double[] weights = base.stream().mapToDouble(WavesConfig.PoolEntry::weight).toArray();
        SpawnStrategyCfg strat = defs.strategy(base.get(rng.weightedIndex(weights)).strategy());
        if (strat == null) throw new IllegalStateException("strategy 未定义: " + base);

        List<StepCfg> ordered = new ArrayList<>(strat.steps());
        ordered.sort(Comparator.comparingInt(StepCfg::delaySec));

        List<SpawnStep> steps = new ArrayList<>();
        for (StepCfg sc : ordered) {
            Vec3 rel = map.points().point(sc.point());
            if (rel == null) throw new IllegalStateException(
                    "刷怪点 " + sc.point() + " 在 " + map.mapId() + "/points.yml 未定义（strategy=" + strat.id() + "，G3）");
            List<Affix> resolved = affixes.resolve(sc.affixes());              // 缺词缀 id 在此抛错
            Compose.MobScale ms = Compose.compose(sc.coeff(), resolved, snap); // coeff×affix×scaling
            int resolvedCount = ScalingConfig.rollCount(sc.count(), snap.mobCount(), rng); // D6 种子 roll
            steps.add(new SpawnStep(
                    Coords.resolve(origin, rel), sc.type(), resolvedCount,
                    ms.hp(), ms.dmg(), ms.speed(), ms.drop(),
                    sc.delaySec() * 20,
                    sc.affixes(),
                    resolved.stream().flatMap(a -> a.potions().stream()).toList()));
        }
        List<RewardItem> rewards = strat.clearReward().stream()
                .map(r -> new RewardItem(r.item(), r.amount())).toList();
        return new WaveSpec(steps, strat.repeat(), rewards);
    }

    /** 从 config.yml 读层原点 + 每层波数，构造纯 {@link RunConfig}。 */
    public static RunConfig loadRunConfig(JavaPlugin plugin) {
        Map<String, Vec3> origins = new HashMap<>();
        Map<String, int[]> wpa = new HashMap<>();
        for (String act : ACTS) {
            origins.put(act, io.mczju.maggoteers.util.Origins.read(
                    plugin.getConfig().getIntegerList("act_origins." + act), 64));
            wpa.put(act, new int[]{
                    plugin.getConfig().getInt("waves_per_act." + act + ".weak", 3),
                    plugin.getConfig().getInt("waves_per_act." + act + ".strong", 2)});
        }
        return new RunConfig(origins, wpa);
    }

    private RunPlanner() {}
}
```

- [ ] **Step 9: 写 `RunPlannerTest`（纯函数：确定性 + G3 + 波数 + count 合理性）**

```java
package io.mczju.maggoteers.plan;

import io.mczju.maggoteers.config.*;
import io.mczju.maggoteers.wave.Vec3;
import io.mczju.maggoteers.world.MapLibrary;
import io.mczju.maggoteers.world.MapRepository.MapEntry;
import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class RunPlannerTest {

    private WaveDefinitions simpleDefs() {
        SpawnStrategyCfg swarm = new SpawnStrategyCfg("w_swarm",
                List.of(new StepCfg("1", EntityType.ZOMBIE, 5, new CoeffCfg(1,1,1), 0, List.of())), 1,
                List.of(new RewardItemCfg("cur", 2)));
        SpawnStrategyCfg boss = new SpawnStrategyCfg("b_boss",
                List.of(new StepCfg("boss", EntityType.IRON_GOLEM, 1, new CoeffCfg(8,1.5,1), 0, List.of("armored"))),
                1, List.of(new RewardItemCfg("cur", 1)));
        Map<String, SpawnStrategyCfg> strat = new HashMap<>();
        strat.put("w_swarm", swarm);
        strat.put("s_strong", swarm);
        strat.put("b_boss", boss);
        Map<String, Map<String, List<WavesConfig.PoolEntry>>> pools = new HashMap<>();
        for (String act : List.of("act1","act2","act3")) {
            Map<String, List<WavesConfig.PoolEntry>> t = new HashMap<>();
            t.put("weak",   List.of(new WavesConfig.PoolEntry("w_swarm", 3)));
            t.put("strong", List.of(new WavesConfig.PoolEntry("s_strong", 2)));
            t.put("boss",   List.of(new WavesConfig.PoolEntry("b_boss", 1)));
            pools.put(act, t);
        }
        return new WaveDefinitions(strat, pools);
    }

    private MapLibrary oneMapLib() {
        MapPoints pts = new MapPoints(new Vec3(0.5,65,0.5), Map.of(
                "1", new Vec3(8,65,8), "boss", new Vec3(0,65,0)));
        MapEntry entry = new MapEntry("m1", pts, Set.of(), null, Map.of());
        Map<String, List<MapEntry>> byAct = new HashMap<>();
        for (String act : List.of("act1","act2","act3")) byAct.put(act, List.of(entry));
        return new MapLibrary(byAct);
    }

    private RunConfig cfg() {
        Map<String,Vec3> o = new HashMap<>();
        o.put("act1", new Vec3(0,64,0)); o.put("act2", new Vec3(1024,64,0)); o.put("act3", new Vec3(2048,64,0));
        Map<String,int[]> w = new HashMap<>();
        for (String act : List.of("act1","act2","act3")) w.put(act, new int[]{3,2});
        return new RunConfig(o, w);
    }

    private ScalingConfig scaling() {
        // 绕过单例：用反射或包级构造？ScalingConfig 构造私有 → 加包级测试工厂
        return ScalingConfig.forTesting(
                new double[]{1,1.3,1.6,2.0}, new double[]{1,1.15,1.3,1.5},
                new double[]{1,1,1,1}, new double[]{1,1.4,1.8,2.2}, new double[]{1,1,1.2,1.5});
    }

    private AffixService affixes() {
        Affix armored = new Affix("armored", 2,1,1,1, List.of(), null, "装甲");
        return AffixService.forTesting(Map.of("armored", armored));
    }

    @Test
    void sameSeedProducesSamePlan() {
        List<ActPlan> a = RunPlanner.plan(999L, 2, simpleDefs(), oneMapLib(), scaling(), affixes(), cfg());
        List<ActPlan> b = RunPlanner.plan(999L, 2, simpleDefs(), oneMapLib(), scaling(), affixes(), cfg());
        assertEquals(a.size(), b.size());
        for (int i = 0; i < a.size(); i++) {
            assertEquals(a.get(i).waves().size(), b.get(i).waves().size());
            // 逐波逐 step 比对（Vec3/count/mult 全相等）
            for (int w = 0; w < a.get(i).waves().size(); w++) {
                var wa = a.get(i).waves().get(w).expand();
                var wb = b.get(i).waves().get(w).expand();
                assertEquals(wa.size(), wb.size());
                for (int s = 0; s < wa.size(); s++) {
                    assertEquals(wa.get(s).count(), wb.get(s).count());
                    assertEquals(wa.get(s).hpMult(), wb.get(s).hpMult(), 1e-9);
                    assertEquals(wa.get(s).point(), wb.get(s).point());
                }
            }
        }
    }

    @Test
    void waveCountIsMPlusNPlusOne() {
        // weak=3, strong=2 → 3+2+1=6 波/层 × 3 层
        List<ActPlan> acts = RunPlanner.plan(1L, 1, simpleDefs(), oneMapLib(), scaling(), affixes(), cfg());
        acts.forEach(a -> assertEquals(6, a.waves().size()));
        assertEquals(3, acts.size());
    }

    @Test
    void bossWaveHasAffixComposed() {
        List<ActPlan> acts = RunPlanner.plan(1L, 1, simpleDefs(), oneMapLib(), scaling(), affixes(), cfg());
        var bossStep = acts.get(0).waves().get(5).expand().get(0);  // 最后一波=boss
        // coeff.hp=8 × armored.hp=2 × scaling.mobHp(1p)=1 → 16
        assertEquals(16.0, bossStep.hpMult(), 1e-9);
        assertTrue(bossStep.affixes().contains("armored"));
    }

    @Test
    void absoluteCoordsShiftByActOrigin() {
        List<ActPlan> acts = RunPlanner.plan(1L, 1, simpleDefs(), oneMapLib(), scaling(), affixes(), cfg());
        // act2 origin (1024,64,0) + point "1" (8,65,8) = (1032,129,8)
        var act2Step0 = acts.get(1).waves().get(0).expand().get(0);
        assertEquals(1032.0, act2Step0.point().x(), 1e-9);
    }

    @Test
    void g3MissingSpawnPointThrows() {
        // points.yml 无 "9"，但 strategy 用 "9"
        SpawnStrategyCfg bad = new SpawnStrategyCfg("bad",
                List.of(new StepCfg("9", EntityType.ZOMBIE, 1, new CoeffCfg(1,1,1), 0, List.of())), 1, List.of());
        Map<String, SpawnStrategyCfg> strat = new HashMap<>(simpleDefs().strategies());
        strat.put("bad", bad);
        WaveDefinitions defs = new WaveDefinitions(strat, simpleDefs().pools());
        // 把 weak 池指向 bad
        Map<String, Map<String, List<WavesConfig.PoolEntry>>> pools = new HashMap<>();
        for (String act : List.of("act1","act2","act3"))
            pools.put(act, Map.of("weak", List.of(new WavesConfig.PoolEntry("bad", 99)),
                    "strong", List.of(new WavesConfig.PoolEntry("s_strong",1)),
                    "boss", List.of(new WavesConfig.PoolEntry("b_boss",1))));
        assertThrows(IllegalStateException.class,
                () -> RunPlanner.plan(1L, 1, defs, oneMapLib(), scaling(), affixes(), cfg()));
    }
}
```

> 需给 `ScalingConfig` 加包级测试工厂（放 `at` 后）：
> ```java
>     /** 测试用：直接构造（绕过 Bukkit）。 */
>     static ScalingConfig forTesting(double[] hp, double[] dmg, double[] spd, double[] drop, double[] count) {
>         return new ScalingConfig(hp, dmg, spd, drop, count);
>     }
> ```
> （私有构造已存在，包级 factory 同包可调。）

- [ ] **Step 10: 删除 `SimplePlanner` + 更新 `WaveSpecTest`（SpawnStep dropMult）**

- 删除文件：`src/main/java/io/mczju/maggoteers/plan/SimplePlanner.java`、`src/test/java/io/mczju/maggoteers/util/OriginsTest.java`（保留 `Origins.java`——RunPlanner 用）。
  > `OriginsTest` 仍可保留（测 `Origins.parse`），不删；它不依赖 SimplePlanner。**只删 `SimplePlanner.java`。**
- 修改 `WaveSpecTest`：两处 SpawnStep 构造在 `speedMult` 后加 `1.0`（dropMult），如 Step 5 所述。

- [ ] **Step 11: 构建并跑全部测试**

Run: `mvn -q test`
Expected: `BUILD SUCCESS`；`SeededRngTest`/`ComposeTest`/`ScalingConfigTest`/`RunPlannerTest`/`CoordsTest`/`WaveSpecTest`/`OriginsTest` 全绿。

- [ ] **Step 12: 提交**

```bash
git add -A
git commit -m "feat(plan3): RunPlanner 种子化纯规划(coeff×affix×scaling+count-roll+G3)"
```

---

## Task 5: special_waves.yml（地图专属波次并入）+ G3 启动校验

**Files:**
- Modify: `src/main/java/io/mczju/maggoteers/world/MapRepository.java`（`parseSpecialWaves` 实装）
- Create: `src/main/resources/maps/act1/ruined_keep/special_waves.yml`（样例）
- Modify: `src/test/java/io/mczju/maggoteers/plan/RunPlannerTest.java`（加专属波次并入测试）

**Interfaces:**
- Produces: `MapEntry.specialWaves` 由 `special_waves.yml` 填充；RunPlanner 已并入（Task 4 `rollWave` 已 `base.addAll(map.specialWaves()...)`）。

- [ ] **Step 1: 实装 `MapRepository.parseSpecialWaves`**

把 Task 4 的占位方法替换为：

```java
    @SuppressWarnings("unchecked")
    private static Map<String, List<WavesConfig.PoolEntry>> parseSpecialWaves(File mapDir) {
        File f = new File(mapDir, "special_waves.yml");
        if (!f.isFile()) return Map.of();
        var cfg = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(f);
        Map<String, List<WavesConfig.PoolEntry>>> out = new HashMap<>();
        for (String tier : List.of("weak", "strong", "boss")) {
            var list = cfg.getMapList(tier);
            if (list.isEmpty()) continue;
            List<WavesConfig.PoolEntry> entries = new ArrayList<>();
            for (var m : list) {
                entries.add(new WavesConfig.PoolEntry((String) m.get("strategy"),
                        ((Number) m.getOrDefault("weight", 1)).doubleValue()));
            }
            out.put(tier, entries);
        }
        return out;
    }
```
确认 import `java.util.HashMap`（已有 `java.util.*`）。

- [ ] **Step 2: 写默认样例 `special_waves.yml`（act1，演示专属波次，权重更高）**

`src/main/resources/maps/act1/ruined_keep/special_waves.yml`：
```yaml
# 本图专属波次：引用 waves.yml 的 strategy id + 池 + 权重（设计要求权重更高）。
# 抽到本图时并入 act1 对应池。
strong:
  - { strategy: s_creeper_rush, weight: 5 }   # 比默认 strong 权重 2 高
```

- [ ] **Step 3: 在 `MapRepository.load` 释放样例 special_waves.yml**

在 Task 2 Step 1（Plan 2）的 `for (String act ...)` 释放 `points.yml` 之后追加释放 special_waves（仅 act1 有样例）：
```java
            if (act.equals("act1")) plugin.saveResource("maps/act1/ruined_keep/special_waves.yml", false);
```

- [ ] **Step 4: 加测试 `RunPlannerTest.specialWavesMergedIntoPool`**

在 `RunPlannerTest` 加：
```java
    @Test
    void specialWavesMergedIntoPool() {
        // act1 的 map 带专属 strong 波 s_special（高权重），应能被 roll 到
        SpawnStrategyCfg special = new SpawnStrategyCfg("s_special",
                List.of(new StepCfg("1", EntityType.SKELETON, 3, new CoeffCfg(1,1,1), 0, List.of())), 1, List.of());
        Map<String, SpawnStrategyCfg> strat = new HashMap<>(simpleDefs().strategies());
        strat.put("s_special", special);
        WaveDefinitions defs = new WaveDefinitions(strat, simpleDefs().pools());

        MapEntry entry = new MapEntry("m1",
                new MapPoints(new Vec3(0.5,65,0.5), Map.of("1", new Vec3(8,65,8), "boss", new Vec3(0,65,0))),
                Set.of(), null, Map.of("strong", List.of(new WavesConfig.PoolEntry("s_special", 999))));
        Map<String, List<MapEntry>> byAct = new HashMap<>();
        byAct.put("act1", List.of(entry));
        byAct.put("act2", oneMapLib().maps("act2"));
        byAct.put("act3", oneMapLib().maps("act3"));

        // 跑多次，act1 的 strong 波应几乎全是 s_special
        int specialHits = 0;
        for (int s = 0; s < 50; s++) {
            List<ActPlan> acts = RunPlanner.plan(s, 1, defs, new MapLibrary(byAct), scaling(), affixes(), cfg());
            // act1 的 strong 波 = 第 4、5 波（index 3,4）
            if (acts.get(0).waves().get(3).expand().get(0).type() == EntityType.SKELETON) specialHits++;
        }
        assertTrue(specialHits > 45, "specialHits=" + specialHits);
    }
```

- [ ] **Step 5: 构建并跑测试**

Run: `mvn -q test`
Expected: `BUILD SUCCESS`，新增 `specialWavesMergedIntoPool` 通过。

- [ ] **Step 6: 提交**

```bash
git add src/main/java/io/mczju/maggoteers/world/MapRepository.java \
        src/main/resources/maps/act1/ruined_keep/special_waves.yml \
        src/test/java/io/mczju/maggoteers/plan/RunPlannerTest.java
git commit -m "feat(plan3): special_waves.yml 地图专属波次并入 + 单测"
```

---

## Task 6: 接入 MaggoteersGame（异步 RunPlanner + 就绪门闩）+ WorldService seed

**Files:**
- Modify: `src/main/java/io/mczju/maggoteers/world/WorldService.java`（`create` 记录 seed + `getSeed`）
- Modify: `src/main/java/io/mczju/maggoteers/game/MaggoteersGame.java`（异步 RunPlanner + planReady 真实置位）

**Interfaces:**
- Consumes: `RunPlanner.plan(seed, playerCount)`、`WorldService.create/get/getSeed`、`WaveScheduler.start`。
- Produces: 开局 = 建世界 → 异步 RunPlanner（planReady=false）→ 就绪门闩等 planReady → `WaveScheduler.start`。

- [ ] **Step 1: `WorldService.create` 记录 seed + 暴露 `getSeed`**

在 `WorldService` 的静态字段区加：
```java
    private static final Map<AbstractGame, Long> SEEDS = new IdentityHashMap<>();
```
在 `create(...)` 里 `seed` 计算之后、`worlds.put(game, w)` 之前加：
```java
        SEEDS.put(game, seed);
```
新增方法（`get` 附近）：
```java
    public static long getSeed(AbstractGame game) {
        Long s = SEEDS.get(game);
        return s == null ? 0L : s;
    }
```
在 `cleanup(...)` 里 `worlds.remove(game)` 旁加 `SEEDS.remove(game);`。

> Plan 1 的 `create` 用 `ThreadLocalRandom.current().nextLong()` 生成 seed；保留。若需可重放调试，可日后改由命令注入固定 seed（Plan 8）。

- [ ] **Step 2: `MaggoteersGame` 改为异步 RunPlanner + 就绪门闩**

整文件替换 `MaggoteersGame.java`：

```java
package io.mczju.maggoteers.game;

import com.github.mczjuops.mczjugamecore.game.AbstractGame;
import com.github.mczjuops.mczjugamecore.game.GameMeta;
import com.github.mczjuops.mczjugamecore.game.strategy.wait.DefaultGameWaitStrategy;
import com.github.mczjuops.mczjugamecore.game.strategy.wait.GameWaitStrategy;
import io.mczju.maggoteers.MaggoteersPlugin;
import io.mczju.maggoteers.plan.ActPlan;
import io.mczju.maggoteers.plan.RunPlanner;
import io.mczju.maggoteers.wave.WaveScheduler;
import io.mczju.maggoteers.world.WorldService;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;

/**
 * 本局游戏。Plan 3：onGameStart 建世界 → 异步 RunPlanner（planReady=false）→ 就绪门闩等就绪 → 开波。
 * <p>§6：RunPlanner 在非主线程纯计算，主线程只消费；§2.3 G4：建世界唯一在 onGameStart 门闩内主线程。
 */
public class MaggoteersGame extends AbstractGame {

    private volatile boolean planReady = false;
    private volatile List<ActPlan> plannedActs;
    private volatile String planError;

    @Override public String getId() { return "maggoteers"; }

    @Override
    public GameMeta getGameMeta() {
        return GameMeta.builder()
                .displayName("<gold>卫戍协议")
                .icon(Material.NETHERITE_SWORD)
                .author("<green>MCZJU")
                .description(List.of("<aqua>4人合作 PvE · 波数防守 + Roguelike"))
                .build();
    }

    @Override
    public GameWaitStrategy getGameWaitStrategy() {
        int min = MaggoteersPlugin.getInstance().getConfig().getInt("wait.min_players", 1);
        return new DefaultGameWaitStrategy(this, 4, min);
    }

    @Override
    protected boolean onGameInit() {
        planReady = false;   // 等待 onGameStart 异步 RunPlanner 置位
        return true;
    }

    @Override
    protected void onGameStart() {
        runWhenReady(this::startInWorld);
    }

    /** 主线程：建世界 → 异步跑 RunPlanner → （门闩二段）开波。 */
    private void startInWorld() {
        World w = WorldService.create(this);
        if (w == null) { sender().error("世界创建失败，对局终止。"); return; }
        final long seed = WorldService.getSeed(this);
        final int players = getPlayers().size();
        sender().info("<gray>正在生成本局剧本（异步）…");

        Bukkit.getScheduler().runTaskAsynchronously(MaggoteersPlugin.getInstance(), () -> {
            try {
                plannedActs = RunPlanner.plan(seed, players);   // 纯计算，非主线程
            } catch (Exception e) {
                planError = e.getMessage();
                MaggoteersPlugin.getInstance().getLogger().severe("RunPlanner 失败：" + e.getMessage());
            } finally {
                planReady = true;   // 门闩放行（成功/失败都放行，由 startWaves 判错）
            }
        });
        // 第二段门闩：等 planReady 后再开波
        new BukkitRunnable() {
            int ticks = 0;
            @Override public void run() {
                if (planReady) {
                    cancel();
                    startWaves();
                } else if (++ticks > 20 * 30) {
                    cancel();
                    sender().warn("剧本生成超时(30s)，终止。");
                }
            }
        }.runTaskTimer(MaggoteersPlugin.getInstance(), 1L, 1L);
    }

    private void startWaves() {
        if (plannedActs == null) {
            sender().error("剧本生成失败" + (planError != null ? "：" + planError : "") + "，对局终止。");
            return;
        }
        WaveScheduler.start(this, plannedActs);
        sender().info("<green>卫戍协议 开局！三层共 "
                + plannedActs.stream().mapToInt(a -> a.waves().size()).sum() + " 波。");
    }

    @Override protected void onGameCancel() { WaveScheduler.stop(this); WorldService.cleanup(this); }
    @Override protected void onGameAbort()  { WaveScheduler.stop(this); WorldService.cleanup(this); }
    @Override protected void onGameEnd()    { WaveScheduler.stop(this); WorldService.cleanup(this); }

    /** 就绪门闩（Plan 1 一段：等世界/剧本首轮就绪）。Plan 3 下 planReady 已在 startInWorld 内异步置位。 */
    private void runWhenReady(Runnable action) {
        new BukkitRunnable() {
            @Override public void run() { cancel(); action.run(); }   // 世界在 action 内建（主线程）
        }.runTask(MaggoteersPlugin.getInstance());
    }
}
```

> `runWhenReady` 在 Plan 3 退化为立即执行（世界创建在 `startInWorld` 主线程同步完成，无需轮询）；异步等待的是剧本，由第二段门闩负责。

- [ ] **Step 3: 构建 + 部署**

Run: `mvn -q -DskipTests package`
Expected: `BUILD SUCCESS`。部署 jar，重启。

- [ ] **Step 4: 端到端 in-game 验证（手动，确认缩放/词缀生效）**

1. `/mgc join maggoteers`（**单人**）。Expected：`正在生成本局剧本（异步）…` → 片刻后 `卫戍协议 开局！`。
2. 查 boss 波（iron_golem，affix `armored`）：血量应为 `20 × coeff.hp(8) × armored.hp(2) × scaling.mobHp(1人=1.0) = 320`。用 `/maggoteers debug`（Plan 8）或直接看击杀耗时——本 Plan 用 `/minecraft:data get entity @e[type=iron_golem,limit=1] Health` 粗验 ≥ 300。
3. 改 `waves.yml` 给某 weak step 加 `affixes: [berserk]`，reload 重开。Expected：该僵尸速度 × 1.2、伤害 × 1.5（berserk）。
4. **确定性验证**：暂无固定 seed 命令（Plan 8 加）；本 Plan 通过单测 `sameSeedProducesSamePlan` 保证。运维可在 `WorldService.create` 临时改 `ThreadLocalRandom` 为固定值复现。
5. 三层闭环 + VICTORY 仍正常（继承 Plan 2）；`/mgc leave` 清世界。

- [ ] **Step 5: 提交**

```bash
git add src/main/java/io/mczju/maggoteers/world/WorldService.java \
        src/main/java/io/mczju/maggoteers/game/MaggoteersGame.java
git commit -m "feat(plan3): MaggoteersGame 异步 RunPlanner + 就绪门闩接入"
```

---

## Plan 3 验收标准（Definition of Done）

- [ ] `mvn package` 产 jar，`mvn test` 绿（**重点**：`SeededRngTest`/`ComposeTest`/`ScalingConfigTest`/`RunPlannerTest`，含确定性、G3、专属波次并入）。
- [ ] 开局异步生成剧本（控制台 `正在生成本局剧本（异步）…` → `卫戍协议 开局！`），主线程不卡顿。
- [ ] Boss/精英怪血量 = `coeff × affix × scaling`（实测 iron_golem armored ≈ 320hp @1人）。
- [ ] 词缀 visibly 生效（berserk 速度/伤害、armored 血量）。
- [ ] `special_waves.yml` 的专属波次能被 roll 到（高权重）。
- [ ] G3：删掉某 `points.yml` 的点编号 → 开局 `RunPlanner 失败：刷怪点 X 未定义`，对局不进。
- [ ] `docs/dev-log.md` 追加 Plan 3 完成条目（含"playerCount 开局锁定 → RunPlanner 在 onGameStart 异步"的决策记录）。

> **已知遗留（后续 Plan 处理）**：
> - `dropMult`（贪婪词缀/掉落倍率）已计算但未消费 → Plan 6 `CurrencyService`。
> - 词缀药水（`on: hit-player`）未生效 → Plan 5 效果系统。
> - 固定 seed 调试命令 → Plan 8（`/maggoteers debug plan`）。
> - `SimplePlanner` 已删除（被 RunPlanner 取代）。
