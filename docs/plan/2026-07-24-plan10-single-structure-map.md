# The Maggoteers — Plan 10: 单结构 NBT 地图加载 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 每层地图改为只加载单个 `structure.nbt`（原点角对齐层原点），硬切掉四象限；缺文件仍铺 64×64 玻璃平台。

**Architecture:** 最小改动：`MapRepository.MapEntry` 用 `hasStructure` 取代 `nbtFiles`/`hasNbt`；`StructurePaster.pasteAct` 在层原点粘贴一次 `structure.nbt`。`ActSpawnHelper` / `WaveScheduler` 调用签名不变。同步 `CLAUDE.md` 与 `dev-log`。

**Tech Stack:** Paper 26.2、Java 25、Maven、Bukkit `StructureManager`。

**设计 spec：** `docs/superpowers/specs/2026-07-24-single-structure-map-design.md`

## Global Constraints

- 固定文件名：`structure.nbt`（相对 `maps/actN/<mapId>/`）。
- 粘贴点 = `act_origins` 层原点；结构往 +X/+Y/+Z 展开。
- **不**识别、不兼容 `nw/sw/ne/se.nbt`。
- 缺 `structure.nbt` 且 `fallback=true` → 现有 64×64 玻璃平台。
- 不改懒粘贴时机、不改 `act_origins`、不提交示例大型 `.nbt`。
- 铁律①：绝对坐标不入配置。
- **构建：** Windows 可用 IDEA/`mvn.cmd` + JDK 25；或 `scripts/build-with-jdk25.sh`。提交仅在用户明确要求时执行（本仓库惯例）。

---

## File map

| 文件 | 职责 |
|---|---|
| `MapRepository.java` | 探测 `structure.nbt` → `MapEntry.hasStructure` |
| `StructurePaster.java` | 单文件粘贴 + 玻璃兜底 |
| `RunPlannerTest.java` | `MapEntry` 构造参数随 record 变更 |
| `CLAUDE.md` | §5 文档改为单结构约定 |
| `docs/dev-log.md` | 追加本 plan 决策记录 |
| 不改 | `ActSpawnHelper`、`WaveScheduler`（仅消费 `pasteAct`） |

---

### Task 1: `MapEntry.hasStructure` + 修测试构造

**Files:**
- Modify: `src/main/java/io/mczju/maggoteers/world/MapRepository.java`
- Modify: `src/test/java/io/mczju/maggoteers/plan/RunPlannerTest.java`

**Interfaces:**
- Consumes: 无
- Produces: `MapEntry(String mapId, MapPoints points, boolean hasStructure, File dir, Map<String, List<WavesConfig.PoolEntry>> specialWaves)`；访问器 `hasStructure()`（record 组件）。

- [ ] **Step 1: 改 `MapEntry` 与 `load`**

将 `MapRepository` 中 record 与加载逻辑改为：

```java
/** 读 plugins/Maggoteers/maps/actN/<mapId>/ 的 points.yml + structure.nbt 是否存在。 */
public final class MapRepository {

    public record MapEntry(String mapId, MapPoints points, boolean hasStructure, File dir,
                           Map<String, List<WavesConfig.PoolEntry>> specialWaves) {}
    // ... BY_ACT, RNG 不变 ...

    // 在 load() 的 mapDir 循环内，替换 nbt 扫描为：
                boolean hasStructure = new File(mapDir, "structure.nbt").isFile();
                entries.add(new MapEntry(mapDir.getName(), points, hasStructure, mapDir,
                        parseSpecialWaves(mapDir)));
```

删除 `hasNbt(String quad)` 与对 `nw/sw/ne/se` 的 `Set` 扫描。类顶部 javadoc 改为提及 `structure.nbt`。

- [ ] **Step 2: 更新 `RunPlannerTest` 中所有 `new MapEntry(...)`**

将第三个参数从 `Set.of()` / `Set.of(...)` 改为 `false`（测试不依赖真实 NBT）：

```java
MapEntry entry = new MapEntry("m1", pts, false, null, Map.of());
```

文件中另有一处带 points 的构造，同样把 `Set.of()`（或等价 Set）换成 `false`。

- [ ] **Step 3: 编译并跑相关测试**

```powershell
mvn -q -Dtest=RunPlannerTest test
```

Expected: `BUILD SUCCESS`；`RunPlannerTest` 全绿。  
（此时 `StructurePaster` 仍引用旧 `hasNbt`，全量 `compile` 会失败——属预期；Task 2 一并修。）

若只跑 test 因主代码编译失败，先执行 Step 1 后用：

```powershell
mvn -q test-compile
```

确认测试源码无 `MapEntry` 构造错误；全绿留给 Task 2 结束时的 `mvn test`。

- [ ] **Step 4: Commit（仅当用户要求提交时）**

```bash
git add src/main/java/io/mczju/maggoteers/world/MapRepository.java \
        src/test/java/io/mczju/maggoteers/plan/RunPlannerTest.java
git commit -m "$(cat <<'EOF'
refactor(maps): MapEntry 改为 hasStructure，探测 structure.nbt

EOF
)"
```

---

### Task 2: `StructurePaster` 单文件粘贴

**Files:**
- Modify: `src/main/java/io/mczju/maggoteers/world/StructurePaster.java`

**Interfaces:**
- Consumes: `MapEntry.hasStructure()`；`mapDir` 下的 `structure.nbt`
- Produces: `pasteAct(World, int originX, int baseY, int originZ, File nbtDir, MapEntry map, boolean fallback)` 行为改为单文件；签名不变以便 `ActSpawnHelper` / `WaveScheduler` 零改动。

- [ ] **Step 1: 整文件替换为单结构实现**

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
 * 把某层单个 structure.nbt 粘贴进虚空世界（StructureManager.loadStructure + place）。
 * <p>粘贴点 = 层原点；对齐结构导出原点角，结构往 +X/+Y/+Z 展开。
 * <p>缺 structure.nbt 且 fallback=true → 铺 64×64 玻璃平台 + 中央下方实心柱。
 */
public final class StructurePaster {
    private static final Logger LOG = Logger.getLogger("Maggoteers");
    private static final int PLATFORM_HALF = 32;
    private static final String STRUCTURE_FILE = "structure.nbt";

    /**
     * @param world    目标世界
     * @param originX  层原点 X
     * @param baseY    层原点 Y（粘贴基准高度）
     * @param originZ  层原点 Z
     * @param nbtDir   maps/actN/&lt;mapId&gt;/ 目录（含 structure.nbt）
     * @param map      地图条目（hasStructure 表示目录内有 structure.nbt）
     * @param fallback true=未粘贴成功时铺玻璃平台
     */
    public static void pasteAct(World world, int originX, int baseY, int originZ,
                                File nbtDir, MapEntry map, boolean fallback) {
        boolean pasted = false;
        if (map.hasStructure()) {
            File nbt = new File(nbtDir, STRUCTURE_FILE);
            if (nbt.isFile()) {
                try {
                    Structure s = Bukkit.getStructureManager().loadStructure(nbt);
                    if (s != null) {
                        BlockVector origin = new BlockVector(originX, baseY, originZ);
                        s.place(world, origin, true, StructureRotation.NONE, Mirror.NONE,
                                0, 1.0f, new Random());
                        pasted = true;
                        LOG.info("已粘贴 " + STRUCTURE_FILE + " @ " + origin);
                    }
                } catch (Exception e) {
                    LOG.warning("粘贴 " + STRUCTURE_FILE + " 失败：" + e.getMessage());
                }
            }
        }
        if (!pasted && fallback) {
            buildPlatform(world, originX, baseY, originZ);
            LOG.warning("未粘贴 " + STRUCTURE_FILE + "，已铺设兜底玻璃平台 @ ("
                    + originX + "," + baseY + "," + originZ + ")");
        }
    }

    /** 64×64 玻璃平台（顶面 = baseY）+ 中央下方实心柱。 */
    private static void buildPlatform(World w, int ox, int baseY, int oz) {
        for (int dx = -PLATFORM_HALF; dx < PLATFORM_HALF; dx++) {
            for (int dz = -PLATFORM_HALF; dz < PLATFORM_HALF; dz++) {
                w.getBlockAt(ox + dx, baseY, oz + dz).setType(Material.GLASS);
            }
        }
        for (int dy = 1; dy <= 40; dy++) {
            w.getBlockAt(ox, baseY - dy, oz).setType(Material.GLASS);
        }
    }

    private StructurePaster() {}
}
```

- [ ] **Step 2: 全量测试 + 打包**

```powershell
mvn -q test package
```

Expected: `BUILD SUCCESS`；测试全绿；产出 `target/Maggoteers-*-SNAPSHOT.jar`。

- [ ] **Step 3: Commit（仅当用户要求提交时）**

```bash
git add src/main/java/io/mczju/maggoteers/world/StructurePaster.java \
        src/main/java/io/mczju/maggoteers/world/MapRepository.java \
        src/test/java/io/mczju/maggoteers/plan/RunPlannerTest.java
git commit -m "$(cat <<'EOF'
feat(maps): 单文件 structure.nbt 粘贴，移除四象限

EOF
)"
```

---

### Task 3: 文档同步（CLAUDE.md + dev-log）

**Files:**
- Modify: `CLAUDE.md`（§2.3 表、§4.2、§5.1、§5.2、§7.3、§18 中「4 象限」表述）
- Modify: `docs/dev-log.md`（文首追加一条）
- Modify: `docs/superpowers/specs/2026-07-24-single-structure-map-design.md`（状态改为已批准）

**Interfaces:**
- Consumes: Task 1–2 已落地行为
- Produces: 项目圣经与决策日志与实现一致

- [ ] **Step 1: 更新 `CLAUDE.md` 关键片段**

`§4.2` 表中 `StructurePaster` 一行改为「粘贴单文件 `structure.nbt`」。

`§5.1` 懒粘贴 / 层原点：

- 「开局只粘 Act 1 的 4 个象限」→「开局只粘 Act 1 的 `structure.nbt`」
- 「进 Act 2/3 时再粘该层 4 个」→「进 Act 2/3 时再粘该层 `structure.nbt`」
- 「4 象限 NBT 在原点基础上按固定 NW/SW/NE/SE 偏移」→「`structure.nbt` 原点角对齐层原点，往 +X/+Y/+Z 展开」

`§5.2` 目录树改为：

```
plugins/Maggoteers/maps/
  act1/<mapId>/  例如 ruined_keep/
      structure.nbt                   # 结构方块导出的整图
      points.yml                      # playerSpawn + spawnPoints{编号→相对坐标}
      special_waves.yml               # 本图专属波（引用 waves.yml 的 strategy id + 池 + weight）
  act2/...  act3/...
```

并在该节加一句：相对坐标须按结构原点书写；缺 `structure.nbt` 时玻璃平台兜底（示例 `points.yml` 可含负坐标以适配居中平台）。

`§2.3` `onGameStart` 表、「跨层」`§7.3`、`§18` D8：凡「粘贴 4 象限」改为「粘贴该层 `structure.nbt`」。

- [ ] **Step 2: 追加 `docs/dev-log.md` 文首条目**

```markdown
## 2026-07-24 — Plan 10：单结构 NBT 地图加载

### 做了什么
- `MapEntry`：`hasStructure` 取代四象限 `nbtFiles`；探测 `structure.nbt`。
- `StructurePaster`：层原点单次粘贴；缺文件仍 64×64 玻璃兜底。
- `CLAUDE.md` §5 与相关「4 象限」表述同步。

### 决策与原因
- 硬切换、固定文件名、原点角对齐——与结构方块工作流一致，少配置分支。
- 不保留四象限兼容；示例相对坐标在有真实 NBT 后需按结构原点重写。

### 遗留
- 运维导出并放置各图 `structure.nbt`；按结构重写 `points.yml`。
- in-game：有/无 nbt 两种路径冒烟。
```

- [ ] **Step 3: Spec 状态**

将 `docs/superpowers/specs/2026-07-24-single-structure-map-design.md` 顶部状态改为：`已批准并实现（Plan 10）`。

- [ ] **Step 4: Commit（仅当用户要求提交时）**

```bash
git add CLAUDE.md docs/dev-log.md \
        docs/superpowers/specs/2026-07-24-single-structure-map-design.md \
        docs/plan/2026-07-24-plan10-single-structure-map.md
git commit -m "$(cat <<'EOF'
docs: Plan 10 单结构地图约定写入 CLAUDE / dev-log

EOF
)"
```

---

## Spec coverage (self-review)

| Spec 要求 | Task |
|---|---|
| 每图 `structure.nbt`、按 Act 分目录 | Task 1 + 3 |
| 原点角对齐层原点 | Task 2 |
| 硬切四象限 | Task 1–2 |
| 玻璃兜底 | Task 2 |
| 调用方签名不变 | Task 2（显式保持） |
| `CLAUDE.md` 同步 | Task 3 |
| 验收 mvn test/package | Task 2 Step 2 |
| 不交大型 nbt / 不改 act_origins | 范围外，无任务 |

无 TBD/TODO 占位；`MapEntry` 第三参数在 Task 1/2 一致为 `boolean hasStructure`。
