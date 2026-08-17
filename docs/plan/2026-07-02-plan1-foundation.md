# The Maggoteers — Plan 1: Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A Paper plugin that registers as an MGC game `maggoteers`, releases a default room, and on `/mgc join maggoteers` creates a fresh void world, teleports players in, and cleans up the world when the game ends — the load-bearing skeleton every later phase plugs into.

**Architecture:** `MaggoteersPlugin` (main) registers `MaggoteersGame extends AbstractGame` + `MaggoteersRoom extends JsonGameRoom` with MGC at `onEnable`. `MaggoteersGame` owns the lifecycle; in `onGameStart` a readiness latch (BukkitRunnable poll) builds a void world via `WorldService` on the main thread and teleports players; `onGameEnd/Abort/Cancel` call `WorldService.cleanup` (unload + async delete). No waves/effects/economy yet — this plan only proves the skeleton.

**Tech Stack:** Paper 1.21.7 API, Java 21, Maven, MCZJUGameCore 1.0.5 (provided, jitpack), JUnit 5 (test).

## Global Constraints

- **服务端/Java**：Paper **1.21.7**；**JDK 21**；构建 **Maven**。
- **依赖坐标**：`io.papermc.paper:paper-api:1.21.7-R0.1-SNAPSHOT`（provided）；`com.github.mczju-ops:MCZJUGameCore:1.0.5`（provided）。仓库 `https://repo.papermc.io/repository/maven-public/` + `https://jitpack.io`。
- **不引入** WorldEdit / MythicMobs / InfernalMobs。
- **身份**：MGC 游戏 id `maggoteers`；主包 `io.mczju.maggoteers`；主类 `io.mczju.maggoteers.MaggoteersPlugin`；游戏内显示名 `<gold>卫戍协议`。
- **三条铁律**：①绝对坐标永不入配置；②定义唯一处；③内容增删零代码（本 Plan 不涉及内容，但后续 Plan 须遵守）。
- **接入契约（MGC 1.0.5，已源码核实）**：
  - `registerGame(Class, Class)` 注册时自动 `loadGameRoom`，从 `plugins/MCZJUGameCore/rooms/maggoteers/*.json` 加载房间实例；**无 READY 房间则 `/mgc join` 失败**。
  - 房间 JSON 内容按 Gson 反序列化进房间类 public 字段；零字段房间用 `{}`；gameId/roomName 由 loader 按文件名回填。
  - `WorldCreator.createWorld()` **必须主线程**；`onGameStart()` 返回 `void`、不能阻塞 → 用**就绪门闩**（轮询任务）。
  - `PlayerExt.giveItem(ItemStack)` 走背包；`giveItem(String)` 走 MGC ItemManager（非 ItemCreator）——本 Plan 暂不发放物品，仅记。
- **环境说明**：开发机当前**未装 JDK/Maven**；`mvn` 与部署步骤由人在 Windows IntelliJ 或装好 JDK 的 WSL 内执行。本机（Claude 侧）只负责写文件。
- **构建产物**：`target/Maggoteers-<version>.jar`，部署到测试服 `E:\MCpaper\plugins\`（同目录需有 `MCZJUGameCore`、`MCZJUItemCreator`）。

---

## 后续 Plan 路线（每份独立可测，本文件只实现 Plan 1）

1. **Plan 1 — Foundation**（本文件）：注册 + 默认房间 + 虚空世界生命周期。
2. Plan 2 — 地图 + 波次 v1（结构粘贴、WaveEngine/Scheduler、原版怪、打完三层通关）。
3. Plan 3 — RunPlanner + 缩放 + 词缀（异步种子预生成；纯逻辑单元测试）。
4. Plan 4 — PlayerState + 死亡/复活。
5. Plan 5 — 效果系统（Trigger/Effect/到期/堆叠）。
6. Plan 6 — 货币 + 奖励 + 升级菜单 + 职业选择。
7. Plan 7 — 持久化 + 商店 + 解锁 + 排行榜。
8. Plan 8 — 命令 + 配置校验 + 调试 + 打磨。

---

## Task 1: Maven 骨架 + 主类外壳 + 纯函数测试框架

**Files:**
- Create: `pom.xml`
- Create: `src/main/resources/plugin.yml`
- Create: `src/main/resources/config.yml`
- Create: `src/main/java/io/mczju/maggoteers/MaggoteersPlugin.java`
- Create: `src/main/java/io/mczju/maggoteers/world/WorldNames.java`
- Create: `src/test/java/io/mczju/maggoteers/world/WorldNamesTest.java`

**Interfaces:**
- Produces: `MaggoteersPlugin.getInstance(): MaggoteersPlugin`；`WorldNames.forSeed(long): String`。

- [ ] **Step 1: 写 `pom.xml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <groupId>io.mczju</groupId>
  <artifactId>Maggoteers</artifactId>
  <version>0.1.0-SNAPSHOT</version>
  <packaging>jar</packaging>

  <properties>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    <maven.compiler.release>21</maven.compiler.release>
  </properties>

  <repositories>
    <repository><id>papermc-repo</id><url>https://repo.papermc.io/repository/maven-public/</url></repository>
    <repository><id>jitpack.io</id><url>https://jitpack.io</url></repository>
  </repositories>

  <dependencies>
    <dependency><groupId>io.papermc.paper</groupId><artifactId>paper-api</artifactId><version>1.21.7-R0.1-SNAPSHOT</version><scope>provided</scope></dependency>
    <dependency><groupId>com.github.mczju-ops</groupId><artifactId>MCZJUGameCore</artifactId><version>1.0.5</version><scope>provided</scope></dependency>
    <dependency><groupId>org.junit.jupiter</groupId><artifactId>junit-jupiter</artifactId><version>5.10.2</version><scope>test</scope></dependency>
  </dependencies>

  <build>
    <finalName>Maggoteers-${project.version}</finalName>
    <resources>
      <resource><directory>src/main/resources</directory><filtering>true</filtering></resource>
    </resources>
    <plugins>
      <plugin><groupId>org.apache.maven.plugins</groupId><artifactId>maven-compiler-plugin</artifactId><version>3.13.0</version><configuration><release>21</release></configuration></plugin>
      <plugin><groupId>org.apache.maven.plugins</groupId><artifactId>maven-surefire-plugin</artifactId><version>3.2.5</version></plugin>
    </plugins>
  </build>
</project>
```

- [ ] **Step 2: 写 `src/main/resources/plugin.yml`**

```yaml
name: Maggoteers
version: '${project.version}'
main: io.mczju.maggoteers.MaggoteersPlugin
api-version: '1.21.7'
depend: [MCZJUGameCore]
softdepend: [MCZJUItemCreator]
```

- [ ] **Step 3: 写 `src/main/resources/config.yml`（骨架，后续 Plan 扩充）**

```yaml
wait:
  min_players: 1
lives:
  default: 2
# 后续 Plan 补充：scaling / waves_per_act / rest / settlement / act_origins / initial_equipment
```

- [ ] **Step 4: 写 `src/main/java/io/mczju/maggoteers/MaggoteersPlugin.java`**

```java
package io.mczju.maggoteers;

import org.bukkit.plugin.java.JavaPlugin;

/** 插件主类。注册游戏、释放默认房间、托管生命周期。 */
public final class MaggoteersPlugin extends JavaPlugin {
    private static MaggoteersPlugin instance;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        getLogger().info("Maggoteers (卫戍协议) enabling.");
        // Task 2 在此追加：saveDefaultRooms() + registerGame(...)
    }

    @Override
    public void onDisable() {
        getLogger().info("Maggoteers disabled.");
    }

    public static MaggoteersPlugin getInstance() {
        return instance;
    }
}
```

- [ ] **Step 5: 写纯函数 `src/main/java/io/mczju/maggoteers/world/WorldNames.java`**

```java
package io.mczju.maggoteers.world;

/** 世界命名纯函数（与 Bukkit 解耦，便于单测）。 */
public final class WorldNames {
    private WorldNames() {}

    /** 由种子推导本局世界名：maggoteers_<16进制>。 */
    public static String forSeed(long seed) {
        return "maggoteers_" + Long.toHexString(seed);
    }
}
```

- [ ] **Step 6: 写失败测试 `src/test/java/io/mczju/maggoteers/world/WorldNamesTest.java`**

```java
package io.mczju.maggoteers.world;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class WorldNamesTest {
    @Test
    void forSeedProducesHexWithMaggoteersPrefix() {
        assertEquals("maggoteers_0", WorldNames.forSeed(0L));
        assertEquals("maggoteers_deadbeef", WorldNames.forSeed(0xdeadbeefL));
    }
}
```

- [ ] **Step 7: 构建并跑测试**

Run: `mvn -q test`
Expected: `BUILD SUCCESS`，`WorldNamesTest` 通过。
> 若本机无 JDK：在装好 JDK21+Maven 的环境（Windows IntelliJ 终端或 WSL）执行。

- [ ] **Step 8: 打包确认产物**

Run: `mvn -q -DskipTests package`
Expected: 生成 `target/Maggoteers-0.1.0-SNAPSHOT.jar`。

- [ ] **Step 9: 提交**

```bash
git add pom.xml src/main src/test
git commit -m "feat(plan1): Maven 骨架 + 主类 + 纯函数测试框架"
```

---

## Task 2: 游戏类/房间类 + 默认房间释放 + 注册

**Files:**
- Create: `src/main/java/io/mczju/maggoteers/game/MaggoteersGame.java`
- Create: `src/main/java/io/mczju/maggoteers/game/MaggoteersRoom.java`
- Modify: `src/main/java/io/mczju/maggoteers/MaggoteersPlugin.java`（onEnable 追加注册 + 释放房间）

**Interfaces:**
- Consumes: MGC `AbstractGame`/`GameMeta`/`DefaultGameWaitStrategy`/`JsonGameRoom`/`MCZJUGameCore.getGameManager()`；`MaggoteersPlugin.getInstance()`；`getConfig()`。
- Produces: `MaggoteersGame`（id `maggoteers`，生命周期 stub：onGameStart 仅日志，Task 4 填充世界逻辑）；`MaggoteersRoom`（零字段占位）。

- [ ] **Step 1: 写 `MaggoteersRoom`**

```java
package io.mczju.maggoteers.game;

import com.github.mczjuops.mczjugamecore.game.room.JsonGameRoom;

/**
 * 房间类（每局自建虚空世界，房间字段不用，零字段占位）。
 * <p>房间实例 JSON 由 {@link io.mczju.maggoteers.MaggoteersPlugin#saveDefaultRooms()} 释放到
 * {@code plugins/MCZJUGameCore/rooms/maggoteers/default.json}，内容 {@code {}} 即可。
 */
public class MaggoteersRoom extends JsonGameRoom {
}
```

- [ ] **Step 2: 写 `MaggoteersGame`（生命周期先 stub，Task 4 接世界）**

```java
package io.mczju.maggoteers.game;

import com.github.mczjuops.mczjugamecore.game.AbstractGame;
import com.github.mczjuops.mczjugamecore.game.GameMeta;
import com.github.mczjuops.mczjugamecore.game.strategy.wait.DefaultGameWaitStrategy;
import com.github.mczjuops.mczjugamecore.game.strategy.wait.GameWaitStrategy;
import io.mczju.maggoteers.MaggoteersPlugin;
import org.bukkit.Material;
import java.util.List;

/** 本局游戏（每局一个实例）。Plan 1：仅占位生命周期，世界逻辑在 Task 4 接入。 */
public class MaggoteersGame extends AbstractGame {

    @Override
    public String getId() {
        return "maggoteers";
    }

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
        // Task 4 改：异步预生成（Plan 3 引入 RunPlanner）。Plan 1 直接放行。
        return true;
    }

    @Override
    protected void onGameStart() {
        // Task 4 改：就绪门闩 → WorldService.create → 传送。
        sender().info("<gray>[Plan1 stub] onGameStart 触发（世界逻辑在 Task 4 接入）");
    }

    @Override
    protected void onGameCancel() {
        // Task 4 改：WorldService.cleanup(this)。
    }

    @Override
    protected void onGameAbort() {
        // Task 4 改：WorldService.cleanup(this)。
    }

    @Override
    protected void onGameEnd() {
        // Task 4 改：WorldService.cleanup(this)。
    }
}
```

- [ ] **Step 3: 在 `MaggoteersPlugin` 加 `saveDefaultRooms()` 并注册游戏**

把 `onEnable` 的注释行替换为真实调用，并新增方法：

```java
package io.mczju.maggoteers;

import com.github.mczjuops.mczjugamecore.MCZJUGameCore;
import io.mczju.maggoteers.game.MaggoteersGame;
import io.mczju.maggoteers.game.MaggoteersRoom;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.nio.file.Files;

public final class MaggoteersPlugin extends JavaPlugin {
    private static MaggoteersPlugin instance;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        saveDefaultRooms();   // G1：必须先释放房间实例，再注册（注册时会 loadGameRoom）
        MCZJUGameCore.getGameManager().registerGame(MaggoteersGame.class, MaggoteersRoom.class);
        getLogger().info("Maggoteers (卫戍协议) enabled, game 'maggoteers' registered.");
    }

    @Override
    public void onDisable() {
        getLogger().info("Maggoteers disabled.");
    }

    public static MaggoteersPlugin getInstance() {
        return instance;
    }

    /**
     * 释放默认房间 JSON 到 MGC 数据目录（若缺失）。
     * <p>G1：{@code /mgc join maggoteers} 需要至少一个 READY 房间，否则返回 null（提示无空闲房间）。
     * 零字段房间内容 {@code {}}；gameId/roomName 由 MGC loader 按文件名回填。
     */
    private void saveDefaultRooms() {
        var mgc = JavaPlugin.getPlugin(MCZJUGameCore.class);
        if (mgc == null) {
            getLogger().severe("MCZJUGameCore 未加载，无法释放默认房间！游戏将无法 join。");
            return;
        }
        File dir = new File(mgc.getDataFolder(), "rooms/maggoteers");
        File def = new File(dir, "default.json");
        if (def.exists()) return;
        if (!dir.exists() && !dir.mkdirs()) {
            getLogger().warning("无法创建目录 " + dir);
            return;
        }
        try {
            Files.writeString(def.toPath(), "{}");
            getLogger().info("已释放默认房间: " + def.getPath());
        } catch (Exception e) {
            getLogger().warning("写出默认房间失败: " + e.getMessage());
        }
    }
}
```

- [ ] **Step 4: 构建并部署**

Run: `mvn -q -DskipTests package`
Expected: `BUILD SUCCESS`。把 `target/Maggoteers-0.1.0-SNAPSHOT.jar` 复制进 `E:\MCpaper\plugins\`（确保同目录有 `MCZJUGameCore`）。

- [ ] **Step 5: 启动服务器，验证注册与房间**

启动 Paper 测试服。Expected（控制台/游戏内）：
- 控制台有 `Maggoteers (卫戍协议) enabled, game 'maggoteers' registered.`
- 控制台有 `已释放默认房间: ...rooms\maggoteers\default.json`（首次）。
- 游戏内 `/mgc` 打开主菜单，能看到 `<gold>卫戍协议` 图标。
- `/mgcop room list maggoteers` 列出 `default`。
- `/mgc join maggoteers` **不再提示**"没有空闲的房间"，而是成功进入等待（Plan 1 此时 onGameStart 只发一条 stub 文本，无世界——预期，Task 4 接世界）。

- [ ] **Step 6: 提交**

```bash
git add src/main/java/io/mczju/maggoteers/MaggoteersPlugin.java src/main/java/io/mczju/maggoteers/game
git commit -m "feat(plan1): 注册 maggoteers 游戏 + 默认房间释放"
```

---

## Task 3: WorldService（虚空生成 / 创建 / 清理 / 孤儿清理）

**Files:**
- Create: `src/main/java/io/mczju/maggoteers/world/VoidGenerator.java`
- Create: `src/main/java/io/mczju/maggoteers/world/WorldService.java`

**Interfaces:**
- Consumes: `WorldNames.forSeed(long)`；`MaggoteersPlugin.getInstance()`；MGC `AbstractGame`（仅作 map key）。
- Produces:
  - `WorldService.create(AbstractGame game): org.bukkit.World`（主线程；建虚空世界 + GameRule）
  - `WorldService.get(AbstractGame): World`
  - `WorldService.cleanup(AbstractGame)`（传送回主世界 → unload → 异步删目录）
  - `WorldService.cleanupOrphansOnEnable()`（扫描已加载 + 目录残留的 `maggoteers_*` 卸载删除）
  - `VoidGenerator`（ChunkGenerator，所有 `shouldGenerate*` false）

- [ ] **Step 1: 写 `VoidGenerator`（照搬前代 VampireSurvivor 已验证实现，1.21.7 可编译）**

```java
package io.mczju.maggoteers.world;

import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;

import java.util.Random;

/** 虚空区块生成器：不生成任何地形/结构/生物。 */
public final class VoidGenerator extends ChunkGenerator {
    @Override public boolean shouldGenerateNoise()       { return false; }
    @Override public boolean shouldGenerateSurface()     { return false; }
    @Override public boolean shouldGenerateDecorations() { return false; }
    @Override public boolean shouldGenerateMobs()        { return false; }
    @Override public boolean shouldGenerateStructures()  { return false; }
    @Override public boolean shouldGenerateBedrock()     { return false; }
    @Override public boolean shouldGenerateCaves()       { return false; }

    @Override
    public void generateSurface(WorldInfo worldInfo, Random random, int chunkX, int chunkZ, ChunkData chunkData) {
        // 完全空白
    }
}
```

> 注：1.21.7 的 `shouldGenerate*()` 无参重载虽可能标注 deprecated，但前代 VampireSurvivor 在 1.21.7 用此签名编译通过；保留以兼容。若编译报签名不匹配，改为带参 `(WorldInfo, Random, int, int)` 版本即可（二者择一存在）。

- [ ] **Step 2: 写 `WorldService`**

```java
package io.mczju.maggoteers.world;

import com.github.mczjuops.mczjugamecore.game.AbstractGame;
import io.mczju.maggoteers.MaggoteersPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;

/** 每局专属虚空世界的完整生命周期。借鉴前代 VampireSurvivor/WorldManager。 */
public final class WorldService {

    private static final Map<AbstractGame, World> worlds = new IdentityHashMap<>();
    private static final Logger LOG = Logger.getLogger("Maggoteers");

    /** 主线程调用：为本对局创建虚空世界。返回 null 表示失败。 */
    public static World create(AbstractGame game) {
        long seed = ThreadLocalRandom.current().nextLong();
        String name = WorldNames.forSeed(seed);
        World w = new WorldCreator(name)
                .environment(World.Environment.NORMAL)
                .generator(new VoidGenerator())
                .createWorld();
        if (w == null) {
            LOG.severe("无法创建世界 " + name);
            return null;
        }
        w.setSpawnLocation(0, 64, 0);
        w.setDifficulty(Difficulty.HARD);
        w.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        w.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
        w.setGameRule(GameRule.NATURAL_REGENERATION, false);
        w.setGameRule(GameRule.MOB_GRIEFING, false);
        w.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        worlds.put(game, w);
        LOG.info("已为对局创建世界 " + name);
        return w;
    }

    public static World get(AbstractGame game) {
        return worlds.get(game);
    }

    /** 传送玩家回主世界 → 卸载 → 异步删目录。 */
    public static void cleanup(AbstractGame game) {
        World w = worlds.remove(game);
        if (w == null) return;
        Location hub = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0).getSpawnLocation();
        for (Player p : new ArrayList<>(w.getPlayers())) {
            if (hub != null) p.teleport(hub);
        }
        File folder = w.getWorldFolder();
        String name = w.getName();
        Bukkit.unloadWorld(w, false);
        new BukkitRunnable() {
            @Override public void run() { deleteDir(folder); }
        }.runTaskAsynchronously(MaggoteersPlugin.getInstance());
        LOG.info("已卸载并删除世界 " + name);
    }

    /** onEnable 调用：清理上次崩服残留的 maggoteers_* 世界。 */
    public static void cleanupOrphansOnEnable() {
        // 1) 已被 Bukkit 自动加载的
        for (World w : new ArrayList<>(Bukkit.getWorlds())) {
            if (w.getName().startsWith("maggoteers_")) {
                Bukkit.unloadWorld(w, false);
                deleteDir(w.getWorldFolder());
                LOG.warning("清理孤立世界(已加载) " + w.getName());
            }
        }
        // 2) 服务器根目录下未加载的残留文件夹
        File root = Bukkit.getWorldContainer(); // 通常是服务端根目录
        File[] kids = root.listFiles();
        if (kids == null) return;
        for (File f : kids) {
            if (f.isDirectory() && f.getName().startsWith("maggoteers_")) {
                deleteDir(f);
                LOG.warning("清理孤立世界(目录) " + f.getName());
            }
        }
    }

    private static void deleteDir(File f) {
        if (f == null || !f.exists()) return;
        File[] kids = f.listFiles();
        if (kids != null) {
            for (File c : kids) {
                if (c.isDirectory()) deleteDir(c);
                else c.delete();
            }
        }
        f.delete();
    }

    private WorldService() {}
}
```

- [ ] **Step 3: 构建确认编译通过**

Run: `mvn -q -DskipTests compile`
Expected: `BUILD SUCCESS`（无符号/签名错误）。
> 若 `VoidGenerator` 的 `shouldGenerate*()` 报编译错误：改用带参签名
> `@Override public boolean shouldGenerateNoise(WorldInfo wi, Random r, int x, int z){ return false; }`（对其余 6 个同理），再编译。

- [ ] **Step 4: 提交**

```bash
git add src/main/java/io/mczju/maggoteers/world/VoidGenerator.java src/main/java/io/mczju/maggoteers/world/WorldService.java
git commit -m "feat(plan1): WorldService 虚空世界生命周期 + 孤儿清理"
```

---

## Task 4: 接入生命周期（就绪门闩 + 建世界 + 传送 + 清理）

**Files:**
- Modify: `src/main/java/io/mczju/maggoteers/game/MaggoteersGame.java`（替换 Task 2 的 stub）
- Modify: `src/main/java/io/mczju/maggoteers/MaggoteersPlugin.java`（onEnable 加孤儿清理）

**Interfaces:**
- Consumes: `WorldService.create/get/cleanup/cleanupOrphansOnEnable`；`AbstractGame.getPlayers()/sender()`；`BukkitRunnable`。
- Produces: 可玩的"进入虚空世界 → 离开删世界"骨架。

- [ ] **Step 1: 改 `MaggoteersGame`，填入世界逻辑**

整文件替换为：

```java
package io.mczju.maggoteers.game;

import com.github.mczjuops.mczjugamecore.game.AbstractGame;
import com.github.mczjuops.mczjugamecore.game.GameMeta;
import com.github.mczjuops.mczjugamecore.game.strategy.wait.DefaultGameWaitStrategy;
import com.github.mczjuops.mczjugamecore.game.strategy.wait.GameWaitStrategy;
import io.mczju.maggoteers.MaggoteersPlugin;
import io.mczju.maggoteers.world.WorldService;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;

/**
 * 本局游戏。Plan 1：进图 = 建虚空世界 + 传送；离场/结束 = 清世界。
 * <p>B4/G4：onGameStart 不能阻塞、createWorld 必须主线程 → 用就绪门闩（BukkitRunnable 轮询），
 * 就绪后在主线程执行建世界+传送。Plan 3 引入 RunPlanner 后，planReady 由异步剧本就绪时置 true。
 */
public class MaggoteersGame extends AbstractGame {

    private volatile boolean planReady = true;   // Plan 1：无异步剧本，直接就绪。

    @Override
    public String getId() {
        return "maggoteers";
    }

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
        // Plan 1：无异步准备。Plan 3：这里置 planReady=false 并启动异步 RunPlanner，就绪后置 true。
        planReady = true;
        return true;
    }

    @Override
    protected void onGameStart() {
        runWhenReady(this::startInWorld);
    }

    /** 主线程执行：建世界 → 传送全员到 (0.5,65,0.5)。 */
    private void startInWorld() {
        World w = WorldService.create(this);
        if (w == null) {
            sender().error("世界创建失败，对局终止。");
            return;
        }
        Location spawn = new Location(w, 0.5, 65, 0.5);
        getPlayers().forEach(pe -> pe.player().teleport(spawn));
        sender().info("<green>已进入 <gold>卫戍协议 <green>世界！(Plan 1：仅世界生成，波次将在后续 Plan 加入)");
    }

    @Override
    protected void onGameCancel() {
        WorldService.cleanup(this);
    }

    @Override
    protected void onGameAbort() {
        WorldService.cleanup(this);
    }

    @Override
    protected void onGameEnd() {
        WorldService.cleanup(this);
    }

    /** 就绪门闩：每 tick 轮询 planReady，就绪（或 30s 超时）后执行 action 并自取消。 */
    private void runWhenReady(Runnable action) {
        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (planReady) {
                    cancel();
                    action.run();
                } else if (++ticks > 20 * 30) {
                    cancel();
                    sender().warn("就绪超时（30s），强制开始。");
                    action.run();
                }
            }
        }.runTaskTimer(MaggoteersPlugin.getInstance(), 1L, 1L);
    }
}
```

- [ ] **Step 2: 在 `MaggoteersPlugin.onEnable` 开头加孤儿清理**

在 `saveDefaultConfig();` 之后、`saveDefaultRooms();` 之前插入：

```java
        io.mczju.maggoteers.world.WorldService.cleanupOrphansOnEnable();
```

（即 onEnable 头部变为：`instance=this; saveDefaultConfig(); WorldService.cleanupOrphansOnEnable(); saveDefaultRooms(); registerGame(...)`。）

- [ ] **Step 3: 构建 + 部署**

Run: `mvn -q -DskipTests package`
Expected: `BUILD SUCCESS`。部署 jar 到测试服 `plugins/`，重启。

- [ ] **Step 4: 端到端 in-game 验证（手动）**

1. `/mgc join maggoteers` → 等待人齐后自动开始（或 `/mgc start`）。
   Expected：被传送到一片虚空（脚下有出生点的方块可站——若 `setSpawnLocation(0,64,0)` 处无方块，玩家会下落；Plan 1 接受，Plan 2 粘贴地图后即有地面）。控制台出现 `已为对局创建世界 maggoteers_<hex>`。
2. 确认 `plugins/` 同级出现 `maggoteers_<hex>/` 目录（世界文件夹）。
3. `/mgc leave`（或退出服务器）→ 对局终止。
   Expected：控制台出现 `已卸载并删除世界 maggoteers_<hex>`；`maggoteers_<hex>/` 目录消失；玩家回到主世界出生点。
4. **崩服恢复验证**：再次 `/mgc join` 建世界后，**直接强制关服**（不开 `stop`）。重启服务器。
   Expected：onEnable 日志出现 `清理孤立世界(已加载/目录) maggoteers_<hex>`；服务器启动后无 `maggoteers_*` 世界残留。

- [ ] **Step 5: 提交**

```bash
git add src/main/java/io/mczju/maggoteers/game/MaggoteersGame.java src/main/java/io/mczju/maggoteers/MaggoteersPlugin.java
git commit -m "feat(plan1): 就绪门闩+建世界+传送+清理 闭环"
```

---

## Plan 1 验收标准（Definition of Done）

- [ ] `mvn package` 产 `Maggoteers-0.1.0-SNAPSHOT.jar`，`mvn test` 绿。
- [ ] 服务器装 MGC 1.0.5 + 本 jar，启动无异常；`/mgc` 菜单见"卫戍协议"。
- [ ] `/mgc join maggoteers` → 进入虚空世界；`/mgc leave` → 世界删除。
- [ ] 崩服重启后 `maggoteers_*` 孤立世界被清理。
- [ ] `docs/dev-log.md` 追加 Plan 1 完成条目（日期 + 做了什么 + 遗留：出生点无方块、波次未实现）。

> **已知遗留（后续 Plan 处理）**：出生点无地面方块（Plan 2 粘贴地图解决）；无波次/敌人（Plan 2）；无 RunPlanner 异步（Plan 3，届时 `planReady` 改为异步置位）；无装备/货币/效果（Plan 4–7）。
