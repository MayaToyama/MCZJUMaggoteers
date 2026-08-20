# The Maggoteers — Plan 2: 地图 + 波次 v1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Paste 4-quadrant NBT maps into the void world, run a single pausable `WaveScheduler` state machine that spawns vanilla mobs via `WaveEngine` (借鉴前代 VampireSurvivor `WaveManager` 的 `IdentityHashMap` + UUID 反查 + 5-tick 安全扫描），清完每层 `M+N+1` 波、跨三层，Act3 Boss 死 → 胜利结算。本 Plan **不**做缩放/词缀/RunPlanner/效果/货币（数值仅按 `coeff` 走，count 固定）——这些在 Plan 3/5/6。

**Architecture:** 定义稳定的"已解析波次模型" `ActPlan{mapId, waves[]}` / `WaveSpec{steps[], repeat, clearReward[]}` / `SpawnStep{point(Vec3 绝对), type, count, hpMult, dmgMult, speedMult, delayTicks, affixes[]}`，作为 Plan 2 ↔ Plan 3 的契约：`SimplePlanner`（同步、coeff-only、count 固定）在 Plan 2 产出 `List<ActPlan>`；Plan 3 的异步种子化 `RunPlanner` 替换它，**WaveScheduler/WaveEngine 不变**。`MapRepository` 读 `maps/actN/<mapId>/{points.yml,4×nbt}`；`StructurePaster` 按层原点 + 4 象限偏移粘贴 NBT（缺失则铺玻璃平台兜底，保证 v1 可玩）；`WaveScheduler` 单 `runTaskTimer` 心跳驱动 `SPAWNING→ACTIVE→CLEARED→REST→下一波/下一层/VICTORY`；跨层 = 粘贴下一层 4 象限 + 传送新 `playerSpawn`。

**Tech Stack:** Paper 1.21.7 API，Java 21，Maven，MCZJUGameCore 1.0.5 (provided)，JUnit 5 (test，纯逻辑)。

## Global Constraints

- **前置条件**：Plan 1 DoD 已满足——`MaggoteersGame`（生命周期 + 就绪门闩 + `WorldService.create/cleanup`）、`WorldService`、`VoidGenerator`、`WorldNames`、`MaggoteersRoom`、默认房间释放、孤儿世界清理**均已就位并可运行**。本 Plan 在此之上叠加地图/波次。
- **三条铁律（§0）**：①绝对坐标永不入配置（图内 `points.yml` 用相对坐标，运行时 `act_origins[act] + 相对`）；②定义唯一处（刷怪策略只在 `waves.yml`）；③内容增删零代码。
- **接入契约（继承 Plan 1）**：`createWorld()` 主线程；`onGameStart` 不阻塞；`getGameManager().endGame(game)` 结束对局；`sender()` 广播；`getPlayers()` 返回 `List<PlayerExt>`。
- **借鉴前代（已验证）**：`WaveEngine` 照搬 `MCZJUvampireSurvivor/wave/WaveManager.java`（`IdentityHashMap<game,runtime>` + `UUID→runtime` 反查 + 5-tick 安全扫描防爆炸怪卡波 + BossBar）；`StructurePaster` 照搬其 `WorldManager.loadStructure` 的 `Bukkit.getStructureManager().loadStructure(File)` + `structure.place(...)`。
- **本机无 JDK/Maven**：`mvn` 与部署由人在 Windows IntelliJ 或装好 JDK 的 WSL 执行；Claude 侧只写文件。
- **构建产物**：`target/Maggoteers-0.1.0-SNAPSHOT.jar`，部署到 `E:\MCpaper\plugins\`。

---

## 本 Plan 范围与后续 Plan 衔接

- **Plan 2 做**：地图粘贴、`WaveEngine`/`WaveScheduler`、原版怪（coeff HP/DMG/速度）、三层闭环 + VICTORY。
- **Plan 2 不做（留 seam）**：
  - 缩放/词缀/count-roll → Plan 3（届时 `SpawnStep.hpMult` 由 RunPlanner 算成 `coeff×affix×scaling`，`SimplePlanner` 被 `RunPlanner` 替换）。
  - `clearReward` 发放（货币/补给）→ Plan 6（WaveSpec 已带 `clearReward[]` 字段，Plan 2 仅广播"本波清除"，不发放物品）。
  - 休整升级菜单 / 跳过 / 延长 → Plan 6（Plan 2 的 REST 相位只倒计时 `rest.duration_sec` 后自动推进）。
  - 死亡/复活/失败判定 → Plan 4（Plan 2 仅 VICTORY；玩家可自由死活，不影响波次推进）。

---

## Task 1: 已解析波次模型 + 刷怪点坐标解析（纯函数 + 单测）

**Files:**
- Create: `src/main/java/io/mczju/maggoteers/wave/Vec3.java`
- Create: `src/main/java/io/mczju/maggoteers/wave/SpawnStep.java`
- Create: `src/main/java/io/mczju/maggoteers/wave/WaveSpec.java`
- Create: `src/main/java/io/mczju/maggoteers/plan/ActPlan.java`
- Create: `src/main/java/io/mczju/maggoteers/util/Coords.java`
- Create: `src/test/java/io/mczju/maggoteers/util/CoordsTest.java`
- Create: `src/test/java/io/mczju/maggoteers/wave/WaveSpecTest.java`

**Interfaces:**
- Produces:
  - `Vec3(double x,y,z)` — 不可变绝对坐标（**不含 World**，异步安全，E8）。
  - `SpawnStep{Vec3 point; EntityType type; int count; double hpMult,dmgMult,speedMult; int delayTicks; List<String> affixes; List<PotionSpec> potions}`（Plan 2 用 `hpMult=coeff.hp`、`affixes/potions` 为空；Plan 3 由 RunPlanner 填 `coeff×affix×scaling` 与词缀）。
  - `WaveSpec{List<SpawnStep> steps; int repeat; List<RewardItem> clearReward}`（`repeat` 展开 = steps 重复 repeat 次，每次点同点刷 count 只）。
  - `RewardItem{String itemId; int amount}`。
  - `ActPlan{String mapId; Vec3 playerSpawn(绝对); List<WaveSpec> waves}`。
  - `Coords.resolve(Vec3 origin, Vec3 relative): Vec3` —— 绝对 = origin + relative。
  - `WaveSpec.expand(): List<SpawnStep>` —— 把 `steps × repeat` 展平成有序 spawn 序列（repeat=2 → steps 接两遍）。

- [ ] **Step 1: 写 `Vec3`**

```java
package io.mczju.maggoteers.wave;

/** 不可变三维坐标（绝对或相对）。刻意不持有 {@link org.bukkit.World}，便于异步线程预生成（E8）。 */
public record Vec3(double x, double y, double z) {
    /** 加法：常用于 绝对 = 层原点 + 相对。 */
    public Vec3 add(Vec3 o) { return new Vec3(x + o.x, y + o.y, z + o.z); }
}
```

- [ ] **Step 2: 写 `SpawnStep` / `RewardItem` / `WaveSpec`**

```java
package io.mczju.maggoteers.wave;

import org.bukkit.entity.EntityType;
import java.util.List;

/**
 * 一条刷怪指令，数值已"完全解析"：{@code hpMult/dmgMult/speedMult} 是最终倍率，
 * 主线程生成时直接套用，不再做任何池查询/随机/乘法。
 * <p>Plan 2：hpMult=coeff.hp（affixes/potions 空）。Plan 3：RunPlanner 填 coeff×affix×scaling + 词缀药水。
 *
 * @param point      刷怪点绝对坐标（已叠层原点）
 * @param type       原版实体类型
 * @param count      一次刷几只（Plan 2 固定；Plan 3 由 RunPlanner 做 count-roll）
 * @param hpMult     最终血量倍率（× 原版基础血量）
 * @param dmgMult    最终攻击倍率
 * @param speedMult  最终速度倍率
 * @param delayTicks 距本波开始的延迟（tick）；0=与首步同刷
 * @param affixes    词缀 id（Plan 3 起填，用于头顶显示）
 * @param potions    词缀药水（Plan 3 起填）
 */
public record SpawnStep(Vec3 point, EntityType type, int count,
                        double hpMult, double dmgMult, double speedMult,
                        int delayTicks, List<String> affixes, List<PotionSpec> potions) {
    public SpawnStep {
        affixes = affixes == null ? List.of() : List.copyOf(affixes);
        potions = potions == null ? List.of() : List.copyOf(potions);
    }
}
```

```java
package io.mczju.maggoteers.wave;

/** 词缀药水（Plan 3 起用；Plan 2 模型占位）。 */
public record PotionSpec(String effect, int amp, int dur) {}
```

```java
package io.mczju.maggoteers.wave;

/** 通关奖励项（ItemCreator 物品 id + 数量）；Plan 6 起由 RewardService 发放。 */
public record RewardItem(String itemId, int amount) {}
```

```java
package io.mczju.maggoteers.wave;

import java.util.ArrayList;
import java.util.List;

/**
 * 一波：steps 按序（含 delayTicks 时序）执行，整体重复 {@code repeat} 次。
 * <p>展开语义：{@link #expand()} 把 steps 串 repeat 份；每份内每步 spawn {@link SpawnStep#count} 只于其 point。
 */
public record WaveSpec(List<SpawnStep> steps, int repeat, List<RewardItem> clearReward) {
    public WaveSpec {
        steps = List.copyOf(steps);
        clearReward = clearReward == null ? List.of() : List.copyOf(clearReward);
        repeat = Math.max(1, repeat);
    }

    /** 展平成有序 spawn 序列（steps × repeat）。repeat 份之间无额外延迟（每步自带 delayTicks）。 */
    public List<SpawnStep> expand() {
        List<SpawnStep> out = new ArrayList<>(steps.size() * repeat);
        for (int r = 0; r < repeat; r++) out.addAll(steps);
        return out;
    }
}
```

- [ ] **Step 3: 写 `ActPlan`**

```java
package io.mczju.maggoteers.plan;

import io.mczju.maggoteers.wave.Vec3;
import io.mczju.maggoteers.wave.WaveSpec;
import java.util.List;

/** 一层的已解析剧本：地图 id + 玩家出生点（绝对）+ 有序波次（weak×M、strong×N、boss×1）。 */
public record ActPlan(String mapId, Vec3 playerSpawn, List<WaveSpec> waves) {
    public ActPlan {
        waves = List.copyOf(waves);
    }
}
```

- [ ] **Step 4: 写 `Coords` 纯函数**

```java
package io.mczju.maggoteers.util;

import io.mczju.maggoteers.wave.Vec3;

/** 坐标计算纯函数（与 Bukkit 解耦，便于单测）。 */
public final class Coords {
    private Coords() {}

    /** 绝对坐标 = 层原点 + 图内相对坐标（铁律①：绝对坐标永不入配置）。 */
    public static Vec3 resolve(Vec3 origin, Vec3 relative) {
        return origin.add(relative);
    }
}
```

- [ ] **Step 5: 写失败测试 `CoordsTest`**

```java
package io.mczju.maggoteers.util;

import io.mczju.maggoteers.wave.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class CoordsTest {
    @Test
    void resolveAddsOriginAndRelative() {
        Vec3 act2Origin = new Vec3(1024, 64, 0);
        assertEquals(new Vec3(1036, 65, 8), Coords.resolve(act2Origin, new Vec3(12, 1, 8)));
    }

    @Test
    void resolveAtOriginIsRelative() {
        assertEquals(new Vec3(4, 65, 4), Coords.resolve(new Vec3(0, 0, 0), new Vec3(4, 65, 4)));
    }
}
```

- [ ] **Step 6: 写失败测试 `WaveSpecTest`**

```java
package io.mczju.maggoteers.wave;

import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class WaveSpecTest {
    private static SpawnStep step(int delay) {
        return new SpawnStep(new Vec3(0, 64, 0), EntityType.ZOMBIE, 5,
                1.0, 1.0, 1.0, delay, List.of(), List.of());
    }

    @Test
    void expandRepeatsStepsInOrder() {
        WaveSpec w = new WaveSpec(List.of(step(0), step(100)), 2, List.of());
        List<SpawnStep> exp = w.expand();
        assertEquals(4, exp.size());
        assertEquals(0, exp.get(0).delayTicks());
        assertEquals(100, exp.get(1).delayTicks());
        assertEquals(0, exp.get(2).delayTicks());   // 第二份重新从首步开始
    }

    @Test
    void repeatClampedToOne() {
        WaveSpec w = new WaveSpec(List.of(step(0)), 0, List.of());
        assertEquals(1, w.expand().size());
    }
}
```

- [ ] **Step 7: 构建并跑测试**

Run: `mvn -q test`
Expected: `BUILD SUCCESS`，`CoordsTest` + `WaveSpecTest` 通过。

- [ ] **Step 8: 提交**

```bash
git add src/main/java/io/mczju/maggoteers/wave/Vec3.java \
        src/main/java/io/mczju/maggoteers/wave/SpawnStep.java \
        src/main/java/io/mczju/maggoteers/wave/PotionSpec.java \
        src/main/java/io/mczju/maggoteers/wave/RewardItem.java \
        src/main/java/io/mczju/maggoteers/wave/WaveSpec.java \
        src/main/java/io/mczju/maggoteers/plan/ActPlan.java \
        src/main/java/io/mczju/maggoteers/util/Coords.java \
        src/test/java/io/mczju/maggoteers/util/CoordsTest.java \
        src/test/java/io/mczju/maggoteers/wave/WaveSpecTest.java
git commit -m "feat(plan2): 已解析波次模型 + 刷怪点坐标解析纯函数"
```

---

## Task 2: 配置扩充 + MapRepository + 默认样例资源

**Files:**
- Modify: `src/main/resources/config.yml`（加 `act_origins` / `waves_per_act` / `rest`）
- Create: `src/main/resources/waves.yml`（默认样例：每层 weak/strong/boss 各一条 strategy）
- Create: `src/main/resources/maps/act1/ruined_keep/points.yml`（默认样例）
- Create: `src/main/resources/maps/act2/frozen_halls/points.yml`
- Create: `src/main/resources/maps/act3/obsidian_spire/points.yml`
- Create: `src/main/java/io/mczju/maggoteers/config/WavesConfig.java`
- Create: `src/main/java/io/mczju/maggoteers/config/MapPoints.java`
- Create: `src/main/java/io/mczju/maggoteers/world/MapRepository.java`

**Interfaces:**
- Consumes: `MaggoteersPlugin.getInstance()`（资源释放）；Bukkit `YamlConfiguration`。
- Produces:
  - `WavesConfig.load(JavaPlugin)` / `getInstance()`：`getStrategy(id): SpawnStrategyCfg`、`getPool(act, tier): List<PoolEntry>`。
  - `MapPoints`：`Vec3 playerSpawn`、`Map<String,Vec3> spawnPoints`（相对坐标）。
  - `MapRepository.load(JavaPlugin)` / `getInstance()`：`getMap(act, mapId): MapEntry{mapId, points, nbtFiles{nw,sw,ne,se}}`、`pickRandomMapId(act, Random)`。
  - 配置 POJO `SpawnStrategyCfg{String id; List<StepCfg> steps; int repeat; List<RewardItemCfg> clearReward}`、`StepCfg{String point; EntityType type; int count; CoeffCfg coeff; int delaySec}`、`CoeffCfg{double hp,dmg,speed}`。

- [ ] **Step 1: 扩充 `config.yml`（在现有内容后追加）**

把 `config.yml` 整文件替换为：

```yaml
wait:
  min_players: 1
lives:
  default: 2
# —— Plan 2 新增 ——
act_origins:
  act1: [0, 64, 0]
  act2: [1024, 64, 0]
  act3: [2048, 64, 0]
waves_per_act:
  act1: { weak: 3, strong: 2 }   # M, N → 每层 M+N+1 波
  act2: { weak: 4, strong: 3 }
  act3: { weak: 4, strong: 3 }
rest:
  duration_sec: 30
  extend_sec: 15
  extend_max: 2
map:
  fallback_platform: true   # nbt 缺失时铺玻璃平台（v1 可玩兜底）
```

- [ ] **Step 2: 写默认 `waves.yml`**

```yaml
# 定义唯一处（铁律②）：刷怪策略 + 池子。Plan 2 仅 coeff（无 affix/scaling）。
pools:
  act1:
    weak:   [ {strategy: w_zombie_swarm,   weight: 3} ]
    strong: [ {strategy: s_skeleton_line,  weight: 2} ]
    boss:   [ {strategy: b_iron_guard,     weight: 1} ]
  act2:
    weak:   [ {strategy: w_zombie_swarm,   weight: 3} ]
    strong: [ {strategy: s_creeper_rush,   weight: 2} ]
    boss:   [ {strategy: b_ravager,        weight: 1} ]
  act3:
    weak:   [ {strategy: w_zombie_swarm,   weight: 3} ]
    strong: [ {strategy: s_creeper_rush,   weight: 2} ]
    boss:   [ {strategy: b_warden_puppet,  weight: 1} ]

strategies:
  w_zombie_swarm:
    repeat: 2
    clearReward: [ {item: maggoteers:currency_normal, amount: 2} ]
    steps:
      - { point: "1", type: ZOMBIE, count: 5, coeff: {hp: 1.0, dmg: 1.0, speed: 1.0}, delay: 0 }
      - { point: "9", type: ZOMBIE, count: 5, coeff: {hp: 1.0, dmg: 1.0, speed: 1.0}, delay: 5 }
  s_skeleton_line:
    repeat: 1
    clearReward: [ {item: maggoteers:currency_normal, amount: 2} ]
    steps:
      - { point: "1", type: SKELETON, count: 4, coeff: {hp: 1.2, dmg: 1.0, speed: 1.0}, delay: 0 }
      - { point: "9", type: SKELETON, count: 4, coeff: {hp: 1.2, dmg: 1.0, speed: 1.0}, delay: 4 }
  s_creeper_rush:
    repeat: 1
    clearReward: [ {item: maggoteers:currency_normal, amount: 2} ]
    steps:
      - { point: "1", type: CREEPER, count: 3, coeff: {hp: 1.0, dmg: 1.2, speed: 1.1}, delay: 0 }
      - { point: "9", type: CREEPER, count: 3, coeff: {hp: 1.0, dmg: 1.2, speed: 1.1}, delay: 3 }
  b_iron_guard:
    repeat: 1
    clearReward: [ {item: maggoteers:currency_boss, amount: 1} ]
    steps:
      - { point: boss, type: IRON_GOLEM, count: 1, coeff: {hp: 8.0, dmg: 1.5, speed: 1.0}, delay: 0 }
  b_ravager:
    repeat: 1
    clearReward: [ {item: maggoteers:currency_boss, amount: 1} ]
    steps:
      - { point: boss, type: RAVAGER, count: 1, coeff: {hp: 10.0, dmg: 1.6, speed: 1.0}, delay: 0 }
  b_warden_puppet:
    repeat: 1
    clearReward: [ {item: maggoteers:currency_boss, amount: 1} ]
    steps:
      - { point: boss, type: WARDEN, count: 1, coeff: {hp: 14.0, dmg: 1.8, speed: 1.0}, delay: 0 }
```

> 注：`point` 用字符串（`"1"`/`"9"`/`"boss"`）以便 YAML 统一；`MapPoints` 的 key 也是 String。

- [ ] **Step 3: 写默认三张图的 `points.yml`**

`src/main/resources/maps/act1/ruined_keep/points.yml`：
```yaml
playerSpawn: { x: 0.5, y: 65, z: 0.5 }
spawnPoints:
  "1":   { x: 8,  y: 65, z: 8 }
  "9":   { x: -8, y: 65, z: -8 }
  boss:  { x: 0,  y: 65, z: 0 }
```
`src/main/resources/maps/act2/frozen_halls/points.yml`：内容同上（v1 复用坐标，地图名不同）。
`src/main/resources/maps/act3/obsidian_spire/points.yml`：内容同上。

> 4 个象限 NBT（`nw/sw/ne/se.nbt`）需运维用结构方块导出后放进 `plugins/Maggoteers/maps/actN/<mapId>/`。**缺失时 StructurePaster 铺玻璃平台兜底**（Task 3），故 v1 无 NBT 也能跑。

- [ ] **Step 4: 写配置 POJO `WavesConfig` + 内部 `*Cfg`**

```java
package io.mczju.maggoteers.config;

import org.bukkit.entity.EntityType;

/** 系数倍率（波次内 coeff）。Plan 3 由 RunPlanner 再叠 affix×scaling。 */
public record CoeffCfg(double hp, double dmg, double speed) {
    public CoeffCfg { if (hp <= 0) hp = 1; if (dmg <= 0) dmg = 1; if (speed <= 0) speed = 1; }
}
```

```java
package io.mczju.maggoteers.config;

import org.bukkit.entity.EntityType;

/** waves.yml 一条 step（未解析：point 是 id 字符串，坐标待 MapRepository 解析）。 */
public record StepCfg(String point, EntityType type, int count, CoeffCfg coeff, int delaySec) {}
```

```java
package io.mczju.maggoteers.config;

import java.util.List;

/** waves.yml 一条 strategy。 */
public record SpawnStrategyCfg(String id, List<StepCfg> steps, int repeat, List<RewardItemCfg> clearReward) {
    public SpawnStrategyCfg { steps = List.copyOf(steps); clearReward = List.copyOf(clearReward); }
}
```

```java
package io.mczju.maggoteers.config;

public record RewardItemCfg(String item, int amount) {}
```

```java
package io.mczju.maggoteers.config;

import io.mczju.maggoteers.MaggoteersPlugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 加载并缓存 waves.yml（strategies + pools）。onEnable 调一次。 */
public final class WavesConfig {
    private static WavesConfig INSTANCE;

    private final Map<String, SpawnStrategyCfg> strategies = new HashMap<>();
    /** act -> tier(weak/strong/boss) -> 池条目 */
    private final Map<String, Map<String, List<PoolEntry>>> pools = new HashMap<>();

    public record PoolEntry(String strategy, double weight) {}

    /** 从 waves.yml 文件加载（不混入 config.yml）。onEnable 调一次。 */
    public static void loadFromFile(JavaPlugin plugin) {
        plugin.saveResource("waves.yml", false);
        var file = new java.io.File(plugin.getDataFolder(), "waves.yml");
        var cfg = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file);
        INSTANCE = new WavesConfig();

        var stratSec = cfg.getConfigurationSection("strategies");
        if (stratSec != null) {
            for (String id : stratSec.getKeys(false)) {
                INSTANCE.strategies.put(id, parseStrategy(id, stratSec.getConfigurationSection(id)));
            }
        }
        var poolsSec = cfg.getConfigurationSection("pools");
        if (poolsSec != null) {
            for (String act : poolsSec.getKeys(false)) {
                Map<String, List<PoolEntry>> tierMap = new HashMap<>();
                var actSec = poolsSec.getConfigurationSection(act);
                if (actSec == null) continue;
                for (String tier : List.of("weak", "strong", "boss")) {
                    var tierList = actSec.getMapList(tier);
                    List<PoolEntry> entries = new ArrayList<>();
                    for (var m : tierList) {
                        entries.add(new PoolEntry((String) m.get("strategy"),
                                ((Number) m.getOrDefault("weight", 1)).doubleValue()));
                    }
                    tierMap.put(tier, entries);
                }
                INSTANCE.pools.put(act, tierMap);
            }
        }
        plugin.getLogger().info("WavesConfig 已加载：" + INSTANCE.strategies.size() + " strategies, "
                + INSTANCE.pools.size() + " acts。");
    }

    private static SpawnStrategyCfg parseStrategy(String id, ConfigurationSection s) {
        if (s == null) throw new IllegalStateException("strategy " + id + " 配置缺失");
        int repeat = s.getInt("repeat", 1);
        List<StepCfg> steps = new ArrayList<>();
        for (var m : s.getMapList("steps")) {
            String point = String.valueOf(m.get("point"));
            EntityType type = EntityType.valueOf(String.valueOf(m.get("type")).toUpperCase());
            int count = ((Number) m.getOrDefault("count", 1)).intValue();
            var c = (Map<?, ?>) m.getOrDefault("coeff", Map.of());
            double hp = ((Number) c.getOrDefault("hp", 1.0)).doubleValue();
            double dmg = ((Number) c.getOrDefault("dmg", 1.0)).doubleValue();
            double spd = ((Number) c.getOrDefault("speed", 1.0)).doubleValue();
            int delay = ((Number) m.getOrDefault("delay", 0)).intValue();
            steps.add(new StepCfg(point, type, count, new CoeffCfg(hp, dmg, spd), delay));
        }
        List<RewardItemCfg> rewards = new ArrayList<>();
        for (var m : s.getMapList("clearReward")) {
            rewards.add(new RewardItemCfg((String) m.get("item"),
                    ((Number) m.getOrDefault("amount", 1)).intValue()));
        }
        return new SpawnStrategyCfg(id, steps, repeat, rewards);
    }

    public static WavesConfig getInstance() {
        if (INSTANCE == null) throw new IllegalStateException("WavesConfig 未加载！");
        return INSTANCE;
    }

    public SpawnStrategyCfg getStrategy(String id) { return strategies.get(id); }

    public List<PoolEntry> getPool(String act, String tier) {
        return pools.getOrDefault(act, Map.of()).getOrDefault(tier, List.of());
    }

    private WavesConfig() {}
}
```

> 注：`WavesConfig` 顶部 `import org.bukkit.configuration.file.FileConfiguration;` 若未使用可删（保留无害）。

- [ ] **Step 5: 写 `MapPoints` + `MapRepository`**

```java
package io.mczju.maggoteers.config;

import io.mczju.maggoteers.wave.Vec3;
import java.util.Map;

/** 单张图的 points.yml：玩家出生点（相对）+ 编号刷怪点（相对）。绝对坐标由 Coords 叠层原点。 */
public record MapPoints(Vec3 playerSpawn, Map<String, Vec3> spawnPoints) {
    public Vec3 point(String id) { return spawnPoints.get(id); }
}
```

```java
package io.mczju.maggoteers.world;

import io.mczju.maggoteers.MaggoteersPlugin;
import io.mczju.maggoteers.config.MapPoints;
import io.mczju.maggoteers.wave.Vec3;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.*;

/** 读 plugins/Maggoteers/maps/actN/<mapId>/ 的 points.yml + 4 象限 nbt 清单。 */
public final class MapRepository {

    public record MapEntry(String mapId, MapPoints points, Set<String> nbtFiles) {
        public boolean hasNbt(String quad) { return nbtFiles.contains(quad + ".nbt"); }
    }

    private static final Map<String, List<MapEntry>> BY_ACT = new HashMap<>();
    private static final Random RNG = new Random();

    public static void load(JavaPlugin plugin) {
        BY_ACT.clear();
        File root = new File(plugin.getDataFolder(), "maps");
        // 释放默认样例 points.yml（不覆盖，便于运维改）
        for (String act : List.of("act1", "act2", "act3")) {
            String mapId = switch (act) { case "act1" -> "ruined_keep"; case "act2" -> "frozen_halls"; default -> "obsidian_spire"; };
            String path = "maps/" + act + "/" + mapId + "/points.yml";
            plugin.saveResource(path, false);
            File actDir = new File(root, act);
            if (!actDir.isDirectory()) continue;
            List<MapEntry> entries = new ArrayList<>();
            for (File mapDir : actDir.listFiles(File::isDirectory)) {
                File pf = new File(mapDir, "points.yml");
                if (!pf.isFile()) continue;
                var cfg = YamlConfiguration.loadConfiguration(pf);
                MapPoints points = parsePoints(cfg);
                Set<String> nbts = new HashSet<>();
                for (String q : List.of("nw", "sw", "ne", "se")) {
                    if (new File(mapDir, q + ".nbt").isFile()) nbts.add(q + ".nbt");
                }
                entries.add(new MapEntry(mapDir.getName(), points, nbts));
            }
            BY_ACT.put(act, entries);
        }
        plugin.getLogger().info("MapRepository 已加载：" + BY_ACT);
    }

    private static MapPoints parsePoints(YamlConfiguration cfg) {
        var ps = cfg.getConfigurationSection("playerSpawn");
        Vec3 spawn = ps == null ? new Vec3(0.5, 65, 0.5)
                : new Vec3(ps.getDouble("x"), ps.getDouble("y"), ps.getDouble("z"));
        Map<String, Vec3> pts = new HashMap<>();
        var sp = cfg.getConfigurationSection("spawnPoints");
        if (sp != null) {
            for (String id : sp.getKeys(false)) {
                var s = sp.getConfigurationSection(id);
                if (s != null) pts.put(id, new Vec3(s.getDouble("x"), s.getDouble("y"), s.getDouble("z")));
            }
        }
        return new MapPoints(spawn, pts);
    }

    public static List<MapEntry> getMaps(String act) { return BY_ACT.getOrDefault(act, List.of()); }

    /** 随机抽一张图（Plan 3 起改由种子 RNG）。无图返回 null。 */
    public static MapEntry pickRandom(String act) {
        List<MapEntry> list = getMaps(act);
        return list.isEmpty() ? null : list.get(RNG.nextInt(list.size()));
    }

    private MapRepository() {}
}
```

- [ ] **Step 6: 在 `MaggoteersPlugin.onEnable` 末尾加载配置**

在 `registerGame(...)` 之后追加（注意 Plan 1 已有 `saveDefaultRooms()` 与 `registerGame`）：

```java
        // Plan 2：加载 waves.yml + maps
        WavesConfig.loadFromFile(this);
        MapRepository.load(this);
```

并补 import：
```java
import io.mczju.maggoteers.config.WavesConfig;
import io.mczju.maggoteers.world.MapRepository;
```

- [ ] **Step 7: 构建确认编译通过**

Run: `mvn -q -DskipTests compile`
Expected: `BUILD SUCCESS`。

- [ ] **Step 8: 部署 + 验证资源释放**

Run: `mvn -q -DskipTests package`，部署 jar。启动服务器。
Expected：`plugins/Maggoteers/` 下出现 `waves.yml` 与 `maps/actN/<mapId>/points.yml`；控制台有 `WavesConfig 已加载` 与 `MapRepository 已加载`。

- [ ] **Step 9: 提交**

```bash
git add src/main/resources/config.yml src/main/resources/waves.yml src/main/resources/maps \
        src/main/java/io/mczju/maggoteers/config src/main/java/io/mczju/maggoteers/world/MapRepository.java \
        src/main/java/io/mczju/maggoteers/MaggoteersPlugin.java
git commit -m "feat(plan2): waves.yml/points.yml 配置 + MapRepository + 默认样例资源"
```

---

## Task 3: StructurePaster（4 象限 NBT 粘贴 + 玻璃平台兜底）+ MapEntry 目录字段

**Files:**
- Create: `src/main/java/io/mczju/maggoteers/world/StructurePaster.java`
- Modify: `src/main/java/io/mczju/maggoteers/world/MapRepository.java`（`MapEntry` 加 `File dir`）

**Interfaces:**
- Consumes: `MapRepository.MapEntry`（含 `dir()`）、`Coords`、config `map.fallback_platform`。
- Produces: `StructurePaster.pasteAct(World, int originX, int baseY, int originZ, File nbtDir, MapEntry map, boolean fallback)` —— 粘贴该层 4 象限；缺 NBT 且 fallback=true 时铺玻璃平台。

- [ ] **Step 1: 给 `MapRepository.MapEntry` 增加 `dir` 字段（定位 NBT 目录）**

`MapEntry` record 改为：
```java
public record MapEntry(String mapId, MapPoints points, Set<String> nbtFiles, File dir) {
    public boolean hasNbt(String quad) { return nbtFiles.contains(quad + ".nbt"); }
}
```
Task 2 Step 5 的 `entries.add(...)` 改为传入目录：
```java
entries.add(new MapEntry(mapDir.getName(), points, nbts, mapDir));
```
（`MapRepository` 顶部已 `import java.io.File;`，无需补。）

- [ ] **Step 2: 写 `StructurePaster`（照搬 VampireSurvivor `WorldManager.loadStructure` 粘贴调用 + 兜底平台）**

```java
package io.mczju.maggoteers.world;

import io.mczju.maggoteers.world.MapRepository.MapEntry;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.structure.Mirror;
import org.bukkit.block.structure.StructureRotation;
import org.bukkit.structure.Structure;
import org.bukkit.util.BlockVector;

import java.io.File;
import java.util.Random;
import java.util.logging.Logger;

/**
 * 把某层 4 象限 NBT 粘贴进虚空世界（照搬前代 VampireSurvivor WorldManager.loadStructure 已验证方法）。
 * <p>4 象限在层原点基础上按固定偏移拼成完整地图：
 * <pre>
 *   NW(-32,0,-32)  NE(0,0,-32)
 *   SW(-32,0,  0)  SE(0,0,  0)
 * </pre>
 * 每 32×32 一象限；粘贴点 = 层原点 + 象限偏移。
 * <p>缺 NBT 且 fallback=true → 铺 64×64 玻璃平台 + 中央实心柱（v1 可玩兜底，防落虚空）。
 */
public final class StructurePaster {
    private static final Logger LOG = Logger.getLogger("Maggoteers");
    private static final int QUAD = 32;

    /**
     * @param world     目标世界
     * @param originX   层原点 X
     * @param baseY     层原点 Y（粘贴基准高度）
     * @param originZ   层原点 Z
     * @param nbtDir    maps/actN/<mapId>/ 目录（含 nw/sw/ne/se.nbt）
     * @param map       地图条目（hasNbt 判断哪几象限有 nbt）
     * @param fallback  true=未粘到任何 nbt 时铺玻璃平台
     */
    public static void pasteAct(World world, int originX, int baseY, int originZ,
                                File nbtDir, MapEntry map, boolean fallback) {
        boolean anyPasted = false;
        for (String q : new String[]{"nw", "sw", "ne", "se"}) {
            if (!map.hasNbt(q)) continue;
            File nbt = new File(nbtDir, q + ".nbt");
            if (!nbt.isFile()) continue;
            try {
                Structure s = Bukkit.getStructureManager().loadStructure(nbt);
                if (s == null) continue;
                BlockVector origin = new BlockVector(originX + offX(q), baseY, originZ + offZ(q));
                s.place(world, origin, true, StructureRotation.NONE, Mirror.NONE, 0, 1.0f, new Random());
                anyPasted = true;
                LOG.info("已粘贴 " + q + ".nbt @ " + origin);
            } catch (Exception e) {
                LOG.warning("粘贴 " + q + ".nbt 失败：" + e.getMessage());
            }
        }
        if (!anyPasted && fallback) {
            buildPlatform(world, originX, baseY, originZ);
            LOG.warning("未粘贴任何 NBT，已铺设兜底玻璃平台 @ (" + originX + "," + baseY + "," + originZ + ")");
        }
    }

    /** 64×64 玻璃平台 + 中央下方实心柱（保证玩家不落虚空）。 */
    private static void buildPlatform(World w, int ox, int y, int oz) {
        for (int dx = -QUAD; dx < QUAD; dx++) {
            for (int dz = -QUAD; dz < QUAD; dz++) {
                w.getBlockAt(ox + dx, y - 1, oz + dz).setType(Material.GLASS);
            }
        }
        for (int dy = 0; dy < 40; dy++) w.getBlockAt(ox, y - 1 - dy, oz).setType(Material.GLASS);
    }

    private static int offX(String q) { return q.endsWith("e") ? 0 : -QUAD; }   // ne/se: +0 ; nw/sw: -32
    private static int offZ(String q) { return q.startsWith("s") ? 0 : -QUAD; }  // sw/se: +0 ; nw/ne: -32

    private StructurePaster() {}
}
```

- [ ] **Step 3: 构建确认编译通过**

Run: `mvn -q -DskipTests compile`
Expected: `BUILD SUCCESS`。

- [ ] **Step 4: 提交**

```bash
git add src/main/java/io/mczju/maggoteers/world/StructurePaster.java \
        src/main/java/io/mczju/maggoteers/world/MapRepository.java
git commit -m "feat(plan2): StructurePaster 4象限NBT粘贴 + 玻璃平台兜底"
```

---

## Task 4: MobFactory（生成 + coeff 倍率 + 装备 + 头顶名）

**Files:**
- Create: `src/main/java/io/mczju/maggoteers/mob/MobFactory.java`

**Interfaces:**
- Consumes: `SpawnStep`（已解析绝对点 + 最终倍率）、`org.bukkit.World`。
- Produces: `MobFactory.spawn(World, SpawnStep): LivingEntity`（套 hpMult/dmgMult/speedMult，drop 率 0，可选头顶名）。
  - 返回的实体**未**计入 WaveEngine 追踪（由 WaveEngine 调用后自行登记）。

- [ ] **Step 1: 写 `MobFactory`（照搬 VampireSurvivor WaveManager.spawnMob / applyAttributes / applyEquipment）**

```java
package io.mczju.maggoteers.mob;

import io.mczju.maggoteers.wave.SpawnStep;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;

import java.util.logging.Logger;

/**
 * 在指定世界生成一只原版怪，套 {@link SpawnStep} 的最终倍率（hpMult/dmgMult/speedMult）。
 * 借鉴前代 VampireSurvivor WaveManager.spawnMob/applyAttributes。Plan 2：无词缀药水/装备（默认空）。
 */
public final class MobFactory {
    private static final Logger LOG = Logger.getLogger("Maggoteers");

    public static LivingEntity spawn(Location loc, SpawnStep step) {
        if (loc.getWorld() == null) return null;
        try {
            var raw = loc.getWorld().spawnEntity(loc, step.type());
            if (!(raw instanceof LivingEntity le)) { raw.remove(); return null; }
            le.setRemoveWhenFarAway(false);
            applyMultipliers(le, step.hpMult(), step.dmgMult(), step.speedMult());
            applyName(le, step);
            if (le instanceof Mob m) m.setAware(true);
            return le;
        } catch (Exception e) {
            LOG.warning("生成怪物失败 " + step.type() + "：" + e.getMessage());
            return null;
        }
    }

    /** hpMult/dmgMult/speedMult 直接乘到基础属性（Plan 3：这些值已是 coeff×affix×scaling）。 */
    private static void applyMultipliers(LivingEntity le, double hpMult, double dmgMult, double spdMult) {
        AttributeInstance hp = le.getAttribute(Attribute.MAX_HEALTH);
        if (hp != null) {
            double scaled = hp.getBaseValue() * Math.max(0.0001, hpMult);
            hp.setBaseValue(scaled);
            le.setHealth(Math.min(scaled, le.getHealth() <= 0 ? scaled : scaled));
        }
        AttributeInstance dmg = le.getAttribute(Attribute.ATTACK_DAMAGE);
        if (dmg != null) dmg.setBaseValue(dmg.getBaseValue() * Math.max(0.0001, dmgMult));
        AttributeInstance spd = le.getAttribute(Attribute.MOVEMENT_SPEED);
        if (spd != null) spd.setBaseValue(spd.getBaseValue() * Math.max(0.0001, spdMult));
    }

    private static void applyName(LivingEntity le, SpawnStep step) {
        // Plan 3：词缀显示；Plan 2 仅在 boss 类实体标记类型名。
        if (!step.affixes().isEmpty()) {
            le.customName(Component.text(String.join(" ", step.affixes())));
            le.setCustomNameVisible(true);
        }
    }

    private MobFactory() {}
}
```

> 注：`le.setHealth(...)`——生成瞬间实体满血；直接 `le.setHealth(scaled)` 即可。若 `scaled` 极大触发原版上限，接受（Boss 高血量在 1.21 由 `MAX_HEALTH` 基础值支撑，上限 1024.0 默认；超大可后续调）。简化为：`le.setHealth(Math.min(scaled, 1024.0));`

把 Step 1 中 `applyMultipliers` 的 `le.setHealth(...)` 一行替换为：
```java
            le.setHealth(Math.min(scaled, 1024.0));
```

- [ ] **Step 2: 构建确认编译通过**

Run: `mvn -q -DskipTests compile`
Expected: `BUILD SUCCESS`。

- [ ] **Step 3: 提交**

```bash
git add src/main/java/io/mczju/maggoteers/mob/MobFactory.java
git commit -m "feat(plan2): MobFactory 原版怪生成 + coeff 倍率套用"
```

---

## Task 5: WaveRuntime + WaveEngine + MobDeathListener（5-tick 安全扫描）

**Files:**
- Create: `src/main/java/io/mczju/maggoteers/wave/WaveRuntime.java`
- Create: `src/main/java/io/mczju/maggoteers/wave/WaveEngine.java`
- Create: `src/main/java/io/mczju/maggoteers/listener/MobDeathListener.java`
- Modify: `src/main/java/io/mczju/maggoteers/MaggoteersPlugin.java`（注册监听器）

**Interfaces:**
- Consumes: `MaggoteersGame`（作 map key + `getPlayers()`）、`MobFactory`、`WaveSpec`/`SpawnStep`。
- Produces:
  - `WaveRuntime`（照搬 VampireSurvivor `WaveContext`）：`Set<UUID> livingMobs`、`Map<UUID,SpawnStep> mobSteps`、`Map<UUID,BossBar> bossBars`、`boolean cleared`。
  - `WaveEngine.start(game, runtime)` / `stop(game)` / `spawnStep(game, SpawnStep)` / `handleMobDeath(uuid, loc): boolean` / `isTracked(uuid)` / `livingCount(game)` / `tick(runtime)`（5-tick 安全扫描，由 WaveScheduler 心跳驱动，**不再自建 cleanupTask**——交给 scheduler 的统一心跳）。

- [ ] **Step 1: 写 `WaveRuntime`（照搬 `WaveContext`，去掉自管 task）**

```java
package io.mczju.maggoteers.wave;

import com.github.mczjuops.mczjugamecore.game.AbstractGame;
import org.bukkit.boss.BossBar;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 单对局波次运行时状态（借鉴前代 VampireSurvivor WaveContext）。
 * <p>由 {@link WaveEngine} 以 {@code IdentityHashMap<game, runtime>} 持有；心跳扫描由 WaveScheduler 统一驱动。
 */
public final class WaveRuntime {
    final AbstractGame game;
    /** 本波仍存活怪物 UUID。 */
    final Set<UUID> livingMobs = new HashSet<>();
    /** UUID → 其 SpawnStep（死亡时定位掉落/Boss 条；Plan 2 主要用于 Boss 条清理）。 */
    final Map<UUID, SpawnStep> mobSteps = new HashMap<>();
    /** Boss 血条（按实体 UUID）。 */
    final Map<UUID, BossBar> bossBars = new HashMap<>();
    /** 本波是否已判定清除（防重复触发）。 */
    boolean cleared = false;

    WaveRuntime(AbstractGame game) { this.game = game; }
}
```

- [ ] **Step 2: 写 `WaveEngine`（照搬 `WaveManager` 的追踪/死亡/扫描核心，去掉波次调度——调度归 WaveScheduler）**

```java
package io.mczju.maggoteers.wave;

import com.github.mczjuops.mczjugamecore.game.AbstractGame;
import io.mczju.maggoteers.MaggoteersPlugin;
import io.mczju.maggoteers.mob.MobFactory;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.EntityType;

import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * 波次执行器：生成、追踪、死亡处理、5-tick 安全扫描（照搬前代 VampireSurvivor WaveManager 核心）。
 * <p><b>只管"本波怪物的生灭"，不管"波次/相位调度"</b>——后者由 {@link WaveScheduler} 驱动。
 */
public final class WaveEngine {
    private static final Map<AbstractGame, WaveRuntime> RUNTIMES = new IdentityHashMap<>();
    /** UUID → runtime，供死亡监听器/扫描 O(1) 反查。 */
    private static final Map<UUID, WaveRuntime> BY_ENTITY = new HashMap<>();
    private static final Logger LOG = Logger.getLogger("Maggoteers");

    public static WaveRuntime start(AbstractGame game) {
        WaveRuntime rt = new WaveRuntime(game);
        RUNTIMES.put(game, rt);
        return rt;
    }

    public static WaveRuntime runtime(AbstractGame game) { return RUNTIMES.get(game); }

    public static int livingCount(AbstractGame game) {
        WaveRuntime rt = RUNTIMES.get(game);
        return rt == null ? 0 : rt.livingMobs.size();
    }

    public static boolean isTracked(UUID uuid) { return BY_ENTITY.containsKey(uuid); }

    /**
     * 生成一个 SpawnStep 的全部 count 只怪于其绝对点，登记进追踪。
     * Boss 类（单只、高血）自动挂 BossBar 给全员。
     */
    public static void spawnStep(AbstractGame game, WaveRuntime rt, SpawnStep step, World world) {
        for (int i = 0; i < step.count(); i++) {
            double dx = (i % 3 - 1) * 1.5;          // 多只同点 ±1.5 散开，避免完全重叠
            double dz = (i / 3 - 1) * 1.5;
            Location loc = new Location(world, step.point().x() + dx, step.point().y(), step.point().z() + dz);
            LivingEntity le = MobFactory.spawn(loc, step);
            if (le == null) continue;
            UUID uuid = le.getUniqueId();
            rt.livingMobs.add(uuid);
            rt.mobSteps.put(uuid, step);
            BY_ENTITY.put(uuid, rt);
            if (step.count() == 1 && step.hpMult() >= 6.0) {   // Boss 启发：单只 + 高血
                attachBossBar(game, rt, le);
            }
        }
    }

    private static void attachBossBar(AbstractGame game, WaveRuntime rt, LivingEntity le) {
        BossBar bar = Bukkit.createBossBar(le.getType().name(), BarColor.RED, BarStyle.SOLID);
        bar.setProgress(1.0);
        for (var pe : game.getPlayers()) bar.addPlayer(pe.player());
        rt.bossBars.put(le.getUniqueId(), bar);
    }

    /** 停止并清理本局所有追踪怪 + Boss 条。 */
    public static void stop(AbstractGame game) {
        WaveRuntime rt = RUNTIMES.remove(game);
        if (rt == null) return;
        for (UUID uuid : new HashSet<>(rt.livingMobs)) {
            var e = Bukkit.getEntity(uuid);
            if (e != null) e.remove();
            BY_ENTITY.remove(uuid);
        }
        rt.livingMobs.clear();
        rt.mobSteps.clear();
        rt.bossBars.values().forEach(BossBar::removeAll);
        rt.bossBars.clear();
    }

    /** 死亡处理：移除追踪；返回 true 表示是我们的怪。 */
    public static boolean handleMobDeath(UUID uuid, Location loc) {
        WaveRuntime rt = BY_ENTITY.remove(uuid);
        if (rt == null) return false;
        rt.livingMobs.remove(uuid);
        rt.mobSteps.remove(uuid);
        BossBar bar = rt.bossBars.remove(uuid);
        if (bar != null) bar.removeAll();
        // Plan 2：不掉落货币（Plan 6 接 CurrencyService）；clearReward 由 scheduler 在 livingMobs 空 时广播。
        return true;
    }

    /**
     * 5-tick 安全扫描（由 WaveScheduler 心跳调用）：把已消失/爆炸实体强制计入死亡，刷新 Boss 条。
     * 照搬 VampireSurvivor WaveManager.tickContext。
     */
    public static void tick(WaveRuntime rt) {
        for (UUID uuid : new HashSet<>(rt.livingMobs)) {
            var raw = Bukkit.getEntity(uuid);
            if (raw == null || raw.isDead()) {
                handleMobDeath(uuid, raw != null ? raw.getLocation() : null);
                continue;
            }
            BossBar bar = rt.bossBars.get(uuid);
            if (bar != null && raw instanceof LivingEntity le) {
                AttributeInstance hp = le.getAttribute(Attribute.MAX_HEALTH);
                double max = hp != null ? hp.getValue() : 20.0;
                bar.setProgress(Math.max(0.0, Math.min(1.0, le.getHealth() / max)));
            }
        }
    }

    private WaveEngine() {}
}
```

- [ ] **Step 3: 写 `MobDeathListener`（监听 EntityDeathEvent + EntityExplodeEvent，转发给 WaveEngine）**

```java
package io.mczju.maggoteers.listener;

import io.mczju.maggoteers.wave.WaveEngine;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;

/**
 * 怪物死亡监听（借鉴前代 VampireSurvivor MobDeathListener）。
 * <p>处理两类：{@link EntityDeathEvent}（普通死）与 {@link EntityExplodeEvent}（苦力怕等，可能先于 Death 触发）。
 * 两者都转发 {@link WaveEngine#handleMobDeath}；WaveEngine 内部幂等（BY_ENTITY remove 后第二次返回 false）。
 */
public class MobDeathListener implements Listener {

    @EventHandler
    public void onDeath(EntityDeathEvent e) {
        if (WaveEngine.handleMobDeath(e.getEntity().getUniqueId(), e.getEntity().getLocation())) {
            e.getDrops().clear();   // 我们的怪不掉原版物（货币/物品由系统控制）
        }
    }

    @EventHandler
    public void onExplode(EntityExplodeEvent e) {
        // 爆炸怪可能在此先被处理；后续 DeathEvent 再来时 WaveEngine 已无记录，安全。
        WaveEngine.handleMobDeath(e.getEntity().getUniqueId(), e.getEntity().getLocation());
        e.setYield(0f);
        e.blockList().clear();     // 不破坏地图（MOB_GRIEFING 已关，双保险）
    }
}
```

- [ ] **Step 4: 在 `MaggoteersPlugin.onEnable` 注册监听器**

在 `MapRepository.load(this);` 之后追加：
```java
        getServer().getPluginManager().registerEvents(new io.mczju.maggoteers.listener.MobDeathListener(), this);
```

- [ ] **Step 5: 构建确认编译通过**

Run: `mvn -q -DskipTests compile`
Expected: `BUILD SUCCESS`。

- [ ] **Step 6: 提交**

```bash
git add src/main/java/io/mczju/maggoteers/wave/WaveRuntime.java \
        src/main/java/io/mczju/maggoteers/wave/WaveEngine.java \
        src/main/java/io/mczju/maggoteers/listener/MobDeathListener.java \
        src/main/java/io/mczju/maggoteers/MaggoteersPlugin.java
git commit -m "feat(plan2): WaveEngine/WaveRuntime + 死亡监听 + 5-tick安全扫描"
```

---

## Task 6: SimplePlanner（同步 coeff-only 出 ActPlan 列表）

**Files:**
- Create: `src/main/java/io/mczju/maggoteers/plan/SimplePlanner.java`
- Create: `src/test/java/io/mczju/maggoteers/plan/SimplePlannerTest.java`

**Interfaces:**
- Consumes: `WavesConfig`（strategies + pools）、`MapRepository`（pickRandom + points）、config `act_origins` + `waves_per_act`、`Coords`。
- Produces: `SimplePlanner.plan(JavaPlugin, int playerCount): List<ActPlan>` —— 三层各抽图、按 `waves_per_act` roll weak×M/strong×N/boss×1，展开成 `WaveSpec`（绝对坐标、coeff-only）。
  - **Plan 3 的 `RunPlanner` 实现同一产出契约**（`List<ActPlan>`），届时替换此类。

- [ ] **Step 1: 写 `SimplePlanner`**

```java
package io.mczju.maggoteers.plan;

import io.mczju.maggoteers.config.RewardItemCfg;
import io.mczju.maggoteers.config.SpawnStrategyCfg;
import io.mczju.maggoteers.config.StepCfg;
import io.mczju.maggoteers.config.WavesConfig;
import io.mczju.maggoteers.util.Coords;
import io.mczju.maggoteers.wave.*;
import io.mczju.maggoteers.world.MapRepository;
import io.mczju.maggoteers.world.MapRepository.MapEntry;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

/**
 * Plan 2 同步规划器：plain-Random 抽图 + 加权 roll 波次 + 展开 strategy + 解析绝对坐标（coeff-only）。
 * <p><b>Plan 3 替换点</b>：{@link io.mczju.maggoteers.plan.RunPlanner}（异步、种子化、coeff×affix×scaling）产出同一 {@code List<ActPlan>} 契约。
 */
public final class SimplePlanner {

    public static List<ActPlan> plan(JavaPlugin plugin, int playerCount) {
        Random rng = new Random();
        List<ActPlan> acts = new ArrayList<>();
        for (String act : List.of("act1", "act2", "act3")) {
            acts.add(planAct(plugin, act, rng));
        }
        return acts;
    }

    private static ActPlan planAct(JavaPlugin plugin, String act, Random rng) {
        MapEntry map = MapRepository.pickRandom(act);
        if (map == null) throw new IllegalStateException("act " + act + " 无可用地图");
        Vec3 origin = readOrigin(plugin, act);

        int weakN = plugin.getConfig().getInt("waves_per_act." + act + ".weak", 3);
        int strongN = plugin.getConfig().getInt("waves_per_act." + act + ".strong", 2);

        List<WaveSpec> waves = new ArrayList<>();
        for (int i = 0; i < weakN; i++)   waves.add(rollWave(act, "weak", map, origin, rng));
        for (int i = 0; i < strongN; i++) waves.add(rollWave(act, "strong", map, origin, rng));
        waves.add(rollWave(act, "boss", map, origin, rng));   // M+N+1 的 +1

        Vec3 playerSpawnAbs = Coords.resolve(origin, map.points().playerSpawn());
        return new ActPlan(map.mapId(), playerSpawnAbs, waves);
    }

    /** 加权随机 roll 一条 strategy，解析成 WaveSpec（绝对坐标 + coeff 倍率）。 */
    private static WaveSpec rollWave(String act, String tier, MapEntry map, Vec3 origin, Random rng) {
        var pool = WavesConfig.getInstance().getPool(act, tier);
        if (pool.isEmpty()) throw new IllegalStateException(act + "/" + tier + " 池为空");
        SpawnStrategyCfg strat = WavesConfig.getInstance().getStrategy(weightedPick(pool, rng).strategy());
        if (strat == null) throw new IllegalStateException("strategy 未定义: " + pool);

        List<SpawnStep> steps = new ArrayList<>();
        for (StepCfg sc : strat.steps()) {
            Vec3 rel = map.points().point(sc.point());
            if (rel == null) throw new IllegalStateException(
                    "刷怪点 " + sc.point() + " 在 " + map.mapId() + "/points.yml 未定义（G3）");
            Vec3 abs = Coords.resolve(origin, rel);
            steps.add(new SpawnStep(abs, sc.type(), sc.count(),
                    sc.coeff().hp(), sc.coeff().dmg(), sc.coeff().speed(),
                    sc.delaySec() * 20, List.of(), List.of()));
        }
        List<RewardItem> rewards = strat.clearReward().stream()
                .map(r -> new RewardItem(r.item(), r.amount())).toList();
        return new WaveSpec(steps, strat.repeat(), rewards);
    }

    private static WavesConfig.PoolEntry weightedPick(List<WavesConfig.PoolEntry> pool, Random rng) {
        double total = pool.stream().mapToDouble(WavesConfig.PoolEntry::weight).sum();
        double r = rng.nextDouble() * total;
        double acc = 0;
        for (var e : pool) { acc += e.weight(); if (r <= acc) return e; }
        return pool.get(pool.size() - 1);
    }

    private static Vec3 readOrigin(JavaPlugin plugin, String act) {
        var list = plugin.getConfig().getIntegerList("act_origins." + act);
        return new Vec3(list.isEmpty() ? 0 : list.get(0), list.size() > 1 ? list.get(1) : 64, list.size() > 2 ? list.get(2) : 0);
    }

    private SimplePlanner() {}
}
```

- [ ] **Step 2: 写单测 `SimplePlannerTest`（验证 G3 报错 + 波数 = M+N+1；roll 本身非确定，只验结构）**

> 单测需 mock-free：因 `WavesConfig`/`MapRepository` 是静态单例、依赖 Bukkit 配置，难以纯单测。**改为针对 `weightedPick` 与 `readOrigin` 抽出的纯逻辑做包级测试**：把 `weightedPick` 改为 package-private static，并新增 `Origins` 纯解析。

为可测，把 origin 解析抽出纯函数到 `util/Origins`：

Create `src/main/java/io/mczju/maggoteers/util/Origins.java`：
```java
package io.mczju.maggoteers.util;

import io.mczju.maggoteers.wave.Vec3;
import java.util.List;

/** 从 config 的整数列表解析层原点（纯函数，便于单测）。 */
public final class Origins {
    private Origins() {}

    public static Vec3 parse(List<Integer> list, int defaultY) {
        int x = (list == null || list.isEmpty()) ? 0 : list.get(0);
        int y = (list == null || list.size() < 2) ? defaultY : list.get(1);
        int z = (list == null || list.size() < 3) ? 0 : list.get(2);
        return new Vec3(x, y, z);
    }
}
```

把 `SimplePlanner.readOrigin` 改为调用 `Origins.parse(list, 64)`，并把 `weightedPick` 改 `static`（已是），可见性保持 package-private（去掉 `private` → 留默认）。即在 `weightedPick` 签名行去掉 `private`，改为：
```java
    static WavesConfig.PoolEntry weightedPick(List<WavesConfig.PoolEntry> pool, Random rng) {
```

Create `src/test/java/io/mczju/maggoteers/util/OriginsTest.java`：
```java
package io.mczju.maggoteers.util;

import io.mczju.maggoteers.wave.Vec3;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;

class OriginsTest {
    @Test
    void parseFullList() {
        assertEquals(new Vec3(1024, 64, 0), Origins.parse(List.of(1024, 64, 0), 64));
    }
    @Test
    void parseEmptyUsesDefaults() {
        assertEquals(new Vec3(0, 64, 0), Origins.parse(List.of(), 64));
    }
    @Test
    void parsePartial() {
        assertEquals(new Vec3(2048, 70, 0), Origins.parse(List.of(2048, 70), 64));
    }
}
```

- [ ] **Step 3: 构建并跑测试**

Run: `mvn -q test`
Expected: `BUILD SUCCESS`，新增 `OriginsTest` 通过（`CoordsTest`/`WaveSpecTest` 仍绿）。

- [ ] **Step 4: 提交**

```bash
git add src/main/java/io/mczju/maggoteers/plan/SimplePlanner.java \
        src/main/java/io/mczju/maggoteers/util/Origins.java \
        src/main/java/io/mczju/maggoteers/plan \
        src/test/java/io/mczju/maggoteers/util/OriginsTest.java
git commit -m "feat(plan2): SimplePlanner 同步coeff-only规划 + Origins纯函数"
```

---

## Task 7: WaveScheduler（单可暂停状态机）+ 接入 MaggoteersGame

**Files:**
- Create: `src/main/java/io/mczju/maggoteers/wave/WaveScheduler.java`
- Modify: `src/main/java/io/mczju/maggoteers/game/MaggoteersGame.java`（门闩就绪后启动 scheduler）

**Interfaces:**
- Consumes: `List<ActPlan>`（SimplePlanner 产出）、`WorldService.get(game)`、`WaveEngine`、`StructurePaster`、`MaggoteersPlugin.getInstance()`、config `rest.duration_sec` + `act_origins`。
- Produces:
  - `WaveScheduler.start(game, List<ActPlan>)` —— 粘贴 Act1、传送、启动心跳。
  - `WaveScheduler.stop(game)` —— 取消心跳 + `WaveEngine.stop`。
  - `WaveScheduler.pause(game)` / `resume(game)` —— 暂停=取消任务保留游标；恢复=重建。（Plan 6 休整菜单 / 职业选择用）

- [ ] **Step 1: 写 `WaveScheduler`（单 `runTaskTimer` 心跳驱动相位机）**

```java
package io.mczju.maggoteers.wave;

import com.github.mczjuops.mczjugamecore.game.AbstractGame;
import com.github.mczjuops.mczjugamecore.MCZJUGameCore;
import io.mczju.maggoteers.MaggoteersPlugin;
import io.mczju.maggoteers.plan.ActPlan;
import io.mczju.maggoteers.world.StructurePaster;
import io.mczju.maggoteers.world.WorldService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

/**
 * 单一可暂停状态机心跳，驱动 {@code SPAWNING→ACTIVE→CLEARED→REST→下一波/下一层/VICTORY}。
 * <p>暂停 = 取消任务保留游标（phase/actIndex/waveIndex/stepCursor/restEndTicks）；恢复 = 重建任务。
 * 借鉴设计 §7.3。Plan 2：REST 仅倒计时自动推进（升级菜单在 Plan 6）。
 *
 * <p><b>SPAWNING 推进</b>：当前波 steps 经 {@link WaveSpec#expand()} 展平且**按 delayTicks 升序**
 * （由 {@link io.mczju.maggoteers.plan.SimplePlanner} 保证）；心跳用 {@code stepCursor} 线性推进，
 * 把所有 {@code delayTicks <= elapsed} 的 step 一次性 spawn，到尾即转 ACTIVE。
 */
public final class WaveScheduler {

    enum Phase { SPAWNING, ACTIVE, CLEARED, REST, DONE }

    static final class Cursor {
        final List<ActPlan> acts;
        int actIndex = 0;
        int waveIndex = 0;
        Phase phase = Phase.SPAWNING;
        long waveStartTicks;
        long restEndTicks;
        /** 当前波 expand() 列表里下一个待 spawn 的下标（线性推进）。 */
        int stepCursor = 0;
        List<SpawnStep> currentExpanded = List.of();
        WaveRuntime runtime;
        BukkitTask task;
        Cursor(List<ActPlan> acts) { this.acts = acts; }
    }

    private static final Map<AbstractGame, Cursor> CURSORS = new IdentityHashMap<>();

    public static void start(AbstractGame game, List<ActPlan> acts) {
        Cursor c = new Cursor(acts);
        CURSORS.put(game, c);
        enterAct(game, c, 0);          // 粘贴 Act1 + 传送 + 进 wave0 SPAWNING
        scheduleHeartbeat(game, c);
    }

    public static void stop(AbstractGame game) {
        Cursor c = CURSORS.remove(game);
        if (c == null) return;
        if (c.task != null) c.task.cancel();
        WaveEngine.stop(game);
    }

    public static void pause(AbstractGame game) {
        Cursor c = CURSORS.get(game);
        if (c != null && c.task != null) { c.task.cancel(); c.task = null; }
    }

    public static void resume(AbstractGame game) {
        Cursor c = CURSORS.get(game);
        if (c != null && c.task == null && c.phase != Phase.DONE) scheduleHeartbeat(game, c);
    }

    // ── 进层 ──────────────────────────────────────────────────────────────
    private static void enterAct(AbstractGame game, Cursor c, int actIndex) {
        c.actIndex = actIndex;
        ActPlan act = c.acts.get(actIndex);
        World w = WorldService.get(game);
        if (w == null) return;
        var originList = MaggoteersPlugin.getInstance().getConfig().getIntegerList("act_origins." + actId(actIndex));
        int ox = originList.isEmpty() ? 0 : originList.get(0);
        int oy = originList.size() > 1 ? originList.get(1) : 64;
        int oz = originList.size() > 2 ? originList.get(2) : 0;
        boolean fallback = MaggoteersPlugin.getInstance().getConfig().getBoolean("map.fallback_platform", true);
        var map = findMap(actIndex, act.mapId());
        if (map != null) StructurePaster.pasteAct(w, ox, oy, oz, map.dir(), map, fallback);

        var spawn = act.playerSpawn();
        var loc = new org.bukkit.Location(w, spawn.x(), spawn.y(), spawn.z());
        game.getPlayers().forEach(pe -> pe.player().teleport(loc));
        broadcast(game, Component.text("▶ 进入第 " + (actIndex + 1) + " 层", NamedTextColor.GOLD));
        beginWave(game, c, 0);
    }

    private static void beginWave(AbstractGame game, Cursor c, int waveIndex) {
        c.waveIndex = waveIndex;
        c.phase = Phase.SPAWNING;
        c.stepCursor = 0;
        WaveSpec spec = c.acts.get(c.actIndex).waves().get(waveIndex);
        c.currentExpanded = spec.expand();
        c.waveStartTicks = Bukkit.getCurrentTick();
        c.runtime = (c.runtime == null) ? WaveEngine.start(game) : c.runtime;
        c.runtime.cleared = false;
        broadcast(game, Component.text("▶ 第 " + (waveIndex + 1) + " / "
                + c.acts.get(c.actIndex).waves().size() + " 波", NamedTextColor.RED));
    }

    // ── 心跳 ──────────────────────────────────────────────────────────────
    private static void scheduleHeartbeat(AbstractGame game, Cursor c) {
        c.task = Bukkit.getScheduler().runTaskTimer(MaggoteersPlugin.getInstance(), () -> tick(game, c), 5L, 5L);
    }

    private static void tick(AbstractGame game, Cursor c) {
        if (c.phase == Phase.DONE) return;
        if (c.runtime != null) WaveEngine.tick(c.runtime);   // 始终跑 5-tick 安全扫描

        switch (c.phase) {
            case SPAWNING -> {
                long elapsed = Bukkit.getCurrentTick() - c.waveStartTicks;
                World w = WorldService.get(game);
                while (c.stepCursor < c.currentExpanded.size()
                        && c.currentExpanded.get(c.stepCursor).delayTicks() <= elapsed) {
                    WaveEngine.spawnStep(game, c.runtime, c.currentExpanded.get(c.stepCursor), w);
                    c.stepCursor++;
                }
                if (c.stepCursor >= c.currentExpanded.size()) c.phase = Phase.ACTIVE;
            }
            case ACTIVE -> {
                if (WaveEngine.livingCount(game) == 0) {
                    c.phase = Phase.CLEARED;
                    onWaveCleared(game, c);
                }
            }
            case REST -> {
                if (Bukkit.getCurrentTick() >= c.restEndTicks) advance(game, c);
            }
            default -> {}
        }
    }

    private static void onWaveCleared(AbstractGame game, Cursor c) {
        WaveSpec spec = c.acts.get(c.actIndex).waves().get(c.waveIndex);
        broadcast(game, Component.text("✔ 本波清除！" + (spec.clearReward().isEmpty()
                ? "" : "（奖励发放见 Plan 6）"), NamedTextColor.GREEN));
        int restSec = MaggoteersPlugin.getInstance().getConfig().getInt("rest.duration_sec", 30);
        c.restEndTicks = Bukkit.getCurrentTick() + restSec * 20L;
        c.phase = Phase.REST;
    }

    private static void advance(AbstractGame game, Cursor c) {
        int waveCount = c.acts.get(c.actIndex).waves().size();
        if (c.waveIndex + 1 < waveCount) { beginWave(game, c, c.waveIndex + 1); return; }
        if (c.actIndex + 1 < c.acts.size()) { enterAct(game, c, c.actIndex + 1); return; }
        // Act3 通关
        c.phase = Phase.DONE;
        if (c.task != null) c.task.cancel();
        WaveEngine.stop(game);
        broadcast(game, Component.text("🏆 通关 卫戍协议！", NamedTextColor.GOLD));
        MCZJUGameCore.getGameManager().endGame(game);   // 胜利结算（Plan 7 接 outcome=WIN）
    }

    // ── 工具 ──────────────────────────────────────────────────────────────
    private static String actId(int i) { return "act" + (i + 1); }

    private static io.mczju.maggoteers.world.MapRepository.MapEntry findMap(int actIndex, String mapId) {
        return io.mczju.maggoteers.world.MapRepository.getMaps(actId(actIndex)).stream()
                .filter(m -> m.mapId().equals(mapId)).findFirst().orElse(null);
    }

    private static void broadcast(AbstractGame game, Component msg) {
        game.getPlayers().forEach(pe -> pe.player().sendMessage(msg));
    }

    private WaveScheduler() {}
}
```

- [ ] **Step 2: 保证 `SimplePlanner` 输出的 steps 按 `delaySec` 升序**

SPAWNING 的游标线性推进要求 `WaveSpec.expand()` 内 steps 已按时序升序。在 Task 6 的 `SimplePlanner.rollWave` 里，把：
```java
        for (StepCfg sc : strat.steps()) {
```
改为先排序再迭代：
```java
        List<StepCfg> ordered = new ArrayList<>(strat.steps());
        ordered.sort(Comparator.comparingInt(StepCfg::delaySec));
        for (StepCfg sc : ordered) {
```
（`SimplePlanner` 已 `import java.util.*;`，`Comparator` 可用。）

- [ ] **Step 3: 构建确认编译通过**

Run: `mvn -q -DskipTests compile`
Expected: `BUILD SUCCESS`。

- [ ] **Step 4: 提交**

```bash
git add src/main/java/io/mczju/maggoteers/wave/WaveScheduler.java \
        src/main/java/io/mczju/maggoteers/plan/SimplePlanner.java
git commit -m "feat(plan2): WaveScheduler 单可暂停状态机(三层闭环+VICTORY)"
```

---

## Task 8: 接入 MaggoteersGame（门闩就绪 → SimplePlanner → WaveScheduler）

**Files:**
- Modify: `src/main/java/io/mczju/maggoteers/game/MaggoteersGame.java`

**Interfaces:**
- Consumes: `SimplePlanner`、`WaveScheduler`、`WorldService`、`MapRepository`、`WavesConfig`。
- Produces: 开局闭环：就绪 → 建/取世界 → `SimplePlanner.plan` → `WaveScheduler.start`；结束 → `WaveScheduler.stop`。

- [ ] **Step 1: 改 `MaggoteersGame.startInWorld` 与生命周期**

把 Plan 1 Task 4 的 `MaggoteersGame` 整文件替换为：

```java
package io.mczju.maggoteers.game;

import com.github.mczjuops.mczjugamecore.game.AbstractGame;
import com.github.mczjuops.mczjugamecore.game.GameMeta;
import com.github.mczjuops.mczjugamecore.game.strategy.wait.DefaultGameWaitStrategy;
import com.github.mczjuops.mczjugamecore.game.strategy.wait.GameWaitStrategy;
import io.mczju.maggoteers.MaggoteersPlugin;
import io.mczju.maggoteers.plan.ActPlan;
import io.mczju.maggoteers.plan.SimplePlanner;
import io.mczju.maggoteers.wave.WaveScheduler;
import io.mczju.maggoteers.world.WorldService;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;

/**
 * 本局游戏。Plan 2：就绪 → 建世界 → SimplePlanner 出三层剧本 → WaveScheduler 启动波次。
 * 结束/取消/中止 → WaveScheduler.stop + WorldService.cleanup。
 */
public class MaggoteersGame extends AbstractGame {

    private volatile boolean planReady = true;
    private List<ActPlan> acts;

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
        planReady = true;   // Plan 3：置 false + 异步 RunPlanner，就绪后置 true
        return true;
    }

    @Override
    protected void onGameStart() {
        runWhenReady(this::startInWorld);
    }

    /** 主线程：建世界 → 规划 → 启动波次。 */
    private void startInWorld() {
        World w = WorldService.create(this);
        if (w == null) { sender().error("世界创建失败，对局终止。"); return; }
        int players = getPlayers().size();
        try {
            acts = SimplePlanner.plan(MaggoteersPlugin.getInstance(), players);
        } catch (Exception e) {
            sender().error("剧本生成失败：" + e.getMessage());
            return;
        }
        // 入 Act1 由 WaveScheduler.start 负责（粘贴+传送+开波）
        WaveScheduler.start(this, acts);
        sender().info("<green>卫戍协议 开局！三层共 "
                + acts.stream().mapToInt(a -> a.waves().size()).sum() + " 波。");
    }

    @Override protected void onGameCancel() { WaveScheduler.stop(this); WorldService.cleanup(this); }
    @Override protected void onGameAbort()  { WaveScheduler.stop(this); WorldService.cleanup(this); }
    @Override protected void onGameEnd()    { WaveScheduler.stop(this); WorldService.cleanup(this); }

    /** 就绪门闩（继承 Plan 1）。 */
    private void runWhenReady(Runnable action) {
        new BukkitRunnable() {
            int ticks = 0;
            @Override public void run() {
                if (planReady) { cancel(); action.run(); }
                else if (++ticks > 20 * 30) { cancel(); sender().warn("就绪超时(30s)，强制开始。"); action.run(); }
            }
        }.runTaskTimer(MaggoteersPlugin.getInstance(), 1L, 1L);
    }
}
```

- [ ] **Step 2: 构建 + 部署**

Run: `mvn -q -DskipTests package`
Expected: `BUILD SUCCESS`。部署 jar，重启。

- [ ] **Step 3: 端到端 in-game 验证（手动）**

1. `/mgc join maggoteers` → 开局。Expected：被传送进虚空，**脚下有玻璃平台**（无 NBT 兜底）或粘贴的地图；聊天见 `▶ 进入第 1 层` → `▶ 第 1/6 波`。
2. 杀光第 1 波怪。Expected：聊天 `✔ 本波清除！`，等 30s（`rest.duration_sec`）后自动 `▶ 第 2/6 波`。
3. 打完 Act1 全部 6 波（3 weak+2 strong+1 boss）。Expected：`▶ 进入第 2 层`，被传送到 Act2 区域（坐标 +1024），继续刷怪。
4. 打到 Act3 Boss（WARDEN，挂红 BossBar）并击杀。Expected：`🏆 通关 卫戍协议！`，对局结束，世界清理。
5. **爆炸怪验证**：在 creeper 波让苦力怕自爆死亡。Expected：5-tick 内被计入击杀，波次不卡住。
6. **中途退出**：`/mgc leave`。Expected：`WaveScheduler.stop` + 世界删除，控制台见卸载日志。

- [ ] **Step 4: 提交**

```bash
git add src/main/java/io/mczju/maggoteers/game/MaggoteersGame.java
git commit -m "feat(plan2): MaggoteersGame 接入 SimplePlanner+WaveScheduler 开局闭环"
```

---

## Plan 2 验收标准（Definition of Done）

- [ ] `mvn package` 产 jar，`mvn test` 绿（`CoordsTest`/`WaveSpecTest`/`OriginsTest`）。
- [ ] 开局自动进 Act1，刷出原版怪（zombie/skeleton/creeper/iron_golem/ravager/warden），HP/DMG 按 coeff 倍率。
- [ ] 清波 → 30s 休整 → 自动下一波；Act1 6 波打完 → 传送 Act2；Act3 Boss 死 → `🏆 通关` + 对局结束。
- [ ] 苦力怕自爆不卡波（5-tick 安全扫描）；Boss 挂 BossBar。
- [ ] `/mgc leave` / 退服 → 世界卸载删除，无残留。
- [ ] `docs/dev-log.md` 追加 Plan 2 完成条目（日期 + 做了什么 + 遗留）。

> **已知遗留（后续 Plan 处理）**：
> - 无缩放/词缀/count-roll（Plan 3：RunPlanner 替换 SimplePlanner，`SpawnStep` 倍率变 coeff×affix×scaling）。
> - `clearReward` 物品未发放（Plan 6 RewardService + 货币体系）。
> - REST 相位无升级菜单/跳过/延长（Plan 6）。
> - 玩家死亡无惩罚/复活机制、无失败判定（Plan 4）。
> - 无效果系统（Plan 5）。
> - 地图 4 象限 NBT 需运维用结构方块导出；v1 用玻璃平台兜底。
> - 默认 `points.yml` 三图坐标相同（仅占位）；地图内容差异由后续 NBT 体现。
