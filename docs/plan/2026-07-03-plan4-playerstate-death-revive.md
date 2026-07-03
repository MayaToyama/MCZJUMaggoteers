# The Maggoteers — Plan 4: PlayerState + 死亡/复活 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为每玩家建立**本局真相源 `PlayerState`**（reviveCount + 存活状态），实现 `MaggoteersDeathStrategy`（借鉴前代 VampireSurvivor `SurvivorPlayerDeathStrategy`）：死亡时 `reviveCount>0` → 自动原地复活（含 `1→0` 仍复活、短暂无敌、维持冒险模式）；`==0` → 转观察者；**场上无冒险模式玩家 → 失败结算（`endGame`，outcome=FAIL）**。复活币救援/数量增加的核心 API 在本 Plan 提供（GUI 在 Plan 6）。

**Architecture:** `PlayerStateManager`（`IdentityHashMap<game, Map<uuid,PlayerState>>`，借鉴 VampireSurvivor `SessionManager`）持有每局每玩家 `PlayerState{reviveCount, alive}`。`MaggoteersDeathStrategy extends AbstractPlayerDeathStrategy`，由 `MaggoteersGame.getPlayerDeathStrategy()` 注入（**已源码核实**：MGC 用此 override 取策略）。`GameOutcome{IN_PROGRESS,WIN,FAIL}` 落在 `MaggoteersGame` 实例上（D4）：`WaveScheduler` 胜利调 `game.win()`、`DeathStrategy` 失败调 `game.fail()`，二者都 `setOutcome + endGame`。`onGameStart` 对每玩家 `switchProfile` + 设冒险模式 + `PlayerStateManager.init`；结束钩子 `destroyAll`。

**Tech Stack:** Paper 1.21.7 API，Java 21，Maven，MCZJUGameCore 1.0.5，JUnit 5（PlayerState 状态机纯单测）。

## Global Constraints

- **前置条件**：Plan 3 DoD 已满足——`RunPlanner`/`WaveScheduler`/`WaveEngine`/`WorldService` 三层闭环 + VICTORY 可玩。本 Plan 叠加玩家死亡/复活/失败判定。
- **接入契约（已源码核实）**：
  - `AbstractGame.getPlayerDeathStrategy()` override 返回 `new XxxDeathStrategy(this)`（VampireSurvivor `SurvivorGame:75` 实证）。策略构造 `super(game)`，重写 `onPlayerDeath(PlayerExt victim, PlayerDeathEvent event)`。
  - `onGameStart` 中 `getPlayers()` 已完整填充；`pe.switchProfile(this.getId())` 切到干净游戏 profile（VampireSurvivor `SurvivorGame:147` 实证）——本 Plan 补上（Plan 1–3 缺失）。
  - 结束对局统一 `MCZJUGameCore.getGameManager().endGame(game)`。
- **D4（outcome 标志）**：胜利与失败都走 `endGame`→`onGameEnd`，须在游戏状态存显式 `outcome`，`onGameEnd` 据此分支结算（Plan 7 落地结算金额；本 Plan 只设标志）。
- **铁律** 继承；本 Plan 不引入配置内容。
- **本机无 JDK/Maven**：构建/部署由人执行；Claude 侧只写文件。

---

## 本 Plan 范围与衔接

- **Plan 4 做**：`PlayerState`/`PlayerStateManager`（含 `revivePlayer`/`addReviveCount` API）、`GameOutcome`、`MaggoteersDeathStrategy`（复活/观察者/失败）、`getPlayerDeathStrategy` 注入、`switchProfile`+冒险模式初始化、`outcome` 标志 + `win()/fail()`、`WaveScheduler` 胜利改调 `win()`。
- **Plan 4 不做**：
  - 复活币**物品 + GUI**（头像选择死者/活者）→ Plan 6（本 Plan 提供 `PlayerStateManager.revivePlayer/addReviveCount` 供其调用）。
  - 结算金额（`onGameEnd` 按 outcome 算账户货币）→ Plan 7。
  - 复活时效果 resync（`EffectService.resync`）→ Plan 5（本 Plan 仅 heal/无敌；Plan 5 在复活钩子里补 resync）。

---

## Task 1: PlayerState + PlayerStateManager + 单测

**Files:**
- Create: `src/main/java/io/mczju/maggoteers/state/PlayerState.java`
- Create: `src/main/java/io/mczju/maggoteers/state/PlayerStateManager.java`
- Create: `src/test/java/io/mczju/maggoteers/state/PlayerStateTest.java`

**Interfaces:**
- Produces:
  - `PlayerState{UUID uuid; int reviveCount; boolean alive}` + 读写方法 + 行为方法 `tryAutoRevive(): boolean`（reviveCount>0 则 `--`、保持 alive=true、返回 true；否则返回 false 不改状态）、`markDown(): void`（alive=false）。
  - `PlayerStateManager`：
    - `init(AbstractGame, UUID, int reviveCount)`、`get(AbstractGame, UUID): PlayerState`
    - `revivePlayer(AbstractGame, UUID): boolean`（**复活币**：把观察者拉回冒险模式；返回是否成功）—— Plan 6 GUI 调
    - `addReviveCount(AbstractGame, UUID, int n)`（**复活币**：给活者 +n 复活次数）—— Plan 6 GUI 调
    - `isAnyAlive(AbstractGame): boolean`、`destroyAll(AbstractGame)`

- [ ] **Step 1: 写 `PlayerState`（状态机 + 行为，便于纯单测）**

```java
package io.mczju.maggoteers.state;

import java.util.UUID;

/**
 * 单局单玩家**本局真相源**（§19）：reviveCount（剩余自动复活次数）+ alive（冒险=参战 / 观察=倒下）。
 * <p>Plan 5 起在此扩展效果列表；Plan 6 起扩展 acquiredUnique 集。本 Plan 仅含死亡/复活所需字段。
 *
 * <h3>死亡状态机</h3>
 * <ul>
 *   <li>{@link #tryAutoRevive()}：reviveCount&gt;0 → --、保持 alive、返回 true（含 1→0 仍复活）</li>
 *   <li>{@link #tryAutoRevive()} 返回 false（==0）→ 由调用方 {@link #markDown()} 转 alive=false</li>
 * </ul>
 */
public final class PlayerState {
    private final UUID uuid;
    private int reviveCount;
    private boolean alive;

    public PlayerState(UUID uuid, int reviveCount) {
        this.uuid = uuid;
        this.reviveCount = Math.max(0, reviveCount);
        this.alive = true;
    }

    public UUID uuid() { return uuid; }
    public int getReviveCount() { return reviveCount; }
    public void setReviveCount(int n) { this.reviveCount = Math.max(0, n); }
    public boolean isAlive() { return alive; }
    public void setAlive(boolean alive) { this.alive = alive; }

    /** 死亡时调：有复活次数则消耗一次、保持存活，返回 true；否则返回 false（调用方再 markDown）。 */
    public boolean tryAutoRevive() {
        if (reviveCount > 0) {
            reviveCount--;
            alive = true;
            return true;
        }
        return false;
    }

    /** 复活次数耗尽：标记为倒下（观察者）。 */
    public void markDown() { alive = false; }
}
```

- [ ] **Step 2: 写 `PlayerStateManager`（借鉴 VampireSurvivor SessionManager 的 IdentityHashMap 结构）**

```java
package io.mczju.maggoteers.state;

import com.github.mczjuops.mczjugamecore.game.AbstractGame;
import org.bukkit.GameMode;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 本局每玩家 {@link PlayerState} 注册表（借鉴前代 VampireSurvivor SessionManager）。
 * <p>{@code onGameStart} 初始化；各结束钩子 {@link #destroyAll}。
 */
public final class PlayerStateManager {
    private static final Map<AbstractGame, Map<UUID, PlayerState>> BY_GAME = new IdentityHashMap<>();

    /** 开局为某玩家建状态（reviveCount 来自 config lives.default）。 */
    public static PlayerState init(AbstractGame game, UUID uuid, int reviveCount) {
        PlayerState st = new PlayerState(uuid, reviveCount);
        BY_GAME.computeIfAbsent(game, g -> new HashMap<>()).put(uuid, st);
        return st;
    }

    public static PlayerState get(AbstractGame game, UUID uuid) {
        Map<UUID, PlayerState> m = BY_GAME.get(game);
        return m == null ? null : m.get(uuid);
    }

    /** 场上是否还有冒险模式（参战）玩家。 */
    public static boolean isAnyAlive(AbstractGame game) {
        Map<UUID, PlayerState> m = BY_GAME.get(game);
        if (m == null) return false;
        return m.values().stream().anyMatch(PlayerState::isAlive);
    }

    /**
     * 复活币 · 对死者：拉回冒险模式（仅复活，不加次数）。返回是否成功（死者才需复活）。
     * <p>Plan 6 复活币 GUI 对死者头像调用。
     */
    public static boolean revivePlayer(AbstractGame game, UUID uuid) {
        PlayerState st = get(game, uuid);
        if (st == null || st.isAlive()) return false;   // 活者不需要复活
        st.setAlive(true);
        applyAdventure(Bukkit.getPlayer(uuid));         // 设回冒险模式 + 满血（主线程）
        return true;
    }

    /**
     * 复活币 · 对活者：reviveCount + n（不改变当前存活状态）。
     * <p>Plan 6 复活币 GUI 对活者头像调用。
     */
    public static void addReviveCount(AbstractGame game, UUID uuid, int n) {
        PlayerState st = get(game, uuid);
        if (st == null) return;
        st.setReviveCount(st.getReviveCount() + n);
    }

    /** 把玩家设为冒险模式并满血（复活/开局用，主线程）。 */
    static void applyAdventure(Player p) {
        if (p == null) return;
        p.setGameMode(GameMode.ADVENTURE);
        p.setHealth(p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH) != null
                ? p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue() : 20.0);
        p.setFallDistance(0f);
        p.setFireTicks(0);
        p.setNoDamageTicks(40);   // 复活短暂无敌 2s
    }

    /** 结束钩子：清掉本局所有玩家状态。 */
    public static void destroyAll(AbstractGame game) {
        BY_GAME.remove(game);
    }

    private PlayerStateManager() {}
}
```

- [ ] **Step 3: 写 `PlayerStateTest`（状态机纯单测）**

```java
package io.mczju.maggoteers.state;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class PlayerStateTest {
    @Test
    void autoReviveConsumesCountAndStaysAlive() {
        PlayerState st = new PlayerState(UUID.randomUUID(), 2);
        assertTrue(st.tryAutoRevive()); assertEquals(1, st.getReviveCount()); assertTrue(st.isAlive());
        assertTrue(st.tryAutoRevive()); assertEquals(0, st.getReviveCount()); assertTrue(st.isAlive()); // 1→0 仍复活
    }

    @Test
    void noReviveLeftReturnsFalseWithoutChange() {
        PlayerState st = new PlayerState(UUID.randomUUID(), 0);
        assertFalse(st.tryAutoRevive());
        assertEquals(0, st.getReviveCount());
        assertTrue(st.isAlive());   // tryAutoRevive 不改 alive；调用方再 markDown
    }

    @Test
    void markDownTransitionsToSpectator() {
        PlayerState st = new PlayerState(UUID.randomUUID(), 1);
        assertTrue(st.tryAutoRevive());   // 用掉最后一次
        assertFalse(st.tryAutoRevive());  // 再死，无次数
        st.markDown();
        assertFalse(st.isAlive());
    }

    @Test
    void addReviveCountClampsAtZero() {
        PlayerState st = new PlayerState(UUID.randomUUID(), 0);
        st.setReviveCount(-5);
        assertEquals(0, st.getReviveCount());
    }
}
```

- [ ] **Step 4: 构建并跑测试**

Run: `mvn -q test`
Expected: `BUILD SUCCESS`，`PlayerStateTest` 通过（其余 Plan 1–3 测试仍绿）。

- [ ] **Step 5: 提交**

```bash
git add src/main/java/io/mczju/maggoteers/state \
        src/test/java/io/mczju/maggoteers/state/PlayerStateTest.java
git commit -m "feat(plan4): PlayerState 死亡状态机 + PlayerStateManager"
```

---

## Task 2: GameOutcome + MaggoteersDeathStrategy + getPlayerDeathStrategy 注入

**Files:**
- Create: `src/main/java/io/mczju/maggoteers/game/GameOutcome.java`
- Create: `src/main/java/io/mczju/maggoteers/game/MaggoteersDeathStrategy.java`

**Interfaces:**
- Produces:
  - `GameOutcome{IN_PROGRESS, WIN, FAIL}`（D4 显式结局标志）。
  - `MaggoteersDeathStrategy extends AbstractPlayerDeathStrategy`（构造 `super(game)`，重写 `onPlayerDeath`）。
    - 死亡：`tryAutoRevive()` true → 原地复活（heal+无敌，保持冒险）；false → `markDown()` + SPECTATOR + **若 `!isAnyAlive(game)` → `((MaggoteersGame)game).fail()`**。

- [ ] **Step 1: 写 `GameOutcome`**

```java
package io.mczju.maggoteers.game;

/** 对局结局标志（D4）：onGameEnd 据此分支结算。 */
public enum GameOutcome {
    IN_PROGRESS, WIN, FAIL
}
```

- [ ] **Step 2: 写 `MaggoteersDeathStrategy`（借鉴 VampireSurvivor SurvivorPlayerDeathStrategy，改 reviveCount 逻辑）**

```java
package io.mczju.maggoteers.game;

import com.github.mczjuops.mczjugamecore.game.AbstractGame;
import com.github.mczjuops.mczjugamecore.player.PlayerExt;
import com.github.mczjuops.mczjugamecore.player.strategy.AbstractPlayerDeathStrategy;
import io.mczju.maggoteers.state.PlayerState;
import io.mczju.maggoteers.state.PlayerStateManager;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;

/**
 * 死亡策略（借鉴前代 VampireSurvivor SurvivorPlayerDeathStrategy，改 reviveCount 复活机制）。
 * <p>事件取消（不走原版死亡/重生屏）；reviveCount&gt;0 自动复活，==0 转观察者；场上无冒险玩家 → 失败。
 */
public class MaggoteersDeathStrategy extends AbstractPlayerDeathStrategy {

    public MaggoteersDeathStrategy(AbstractGame game) {
        super(game);
    }

    @Override
    public void onPlayerDeath(PlayerExt victim, PlayerDeathEvent event) {
        Player p = victim.player();
        event.setCancelled(true);          // 不走原版死亡屏/重生

        PlayerState st = PlayerStateManager.get(game, p.getUniqueId());
        if (st == null) {
            // 无状态兜底：直接失败（理论不应发生）
            ((MaggoteersGame) game).fail();
            return;
        }

        if (st.tryAutoRevive()) {
            // 自动复活：原地、保持冒险、短暂无敌
            healAndInvuln(p);
            game.sender().info("<yellow>" + p.getName()
                    + " 倒下，自动复活！（剩余 " + st.getReviveCount() + " 次）");
            return;
        }

        // 复活次数耗尽 → 观察者
        st.markDown();
        healAndInvuln(p);
        p.setGameMode(org.bukkit.GameMode.SPECTATOR);
        game.sender().warn("<red>" + p.getName() + " 倒下！转为观察者（可用复活币救援）。");

        if (!PlayerStateManager.isAnyAlive(game)) {
            ((MaggoteersGame) game).fail();   // 场上无人参战 → 失败结算
        }
    }

    /** 取消死亡后重置为稳定状态（满血、清火/坠落、2s 无敌），避免残留异常。 */
    private static void healAndInvuln(Player p) {
        var maxHp = p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
        double max = maxHp != null ? maxHp.getValue() : 20.0;
        p.setHealth(Math.min(p.getHealth() <= 0 ? max : Math.max(p.getHealth(), 1.0), max));
        p.setFallDistance(0f);
        p.setFireTicks(0);
        p.setNoDamageTicks(40);
    }
}
```

- [ ] **Step 3: 构建确认编译通过**

Run: `mvn -q -DskipTests compile`
Expected: `BUILD SUCCESS`（`MaggoteersGame.fail()` 在 Task 3 定义；本 Step 仅编译 DeathStrategy 自身——若编译报 `fail()` 未定义，先做 Task 3 Step 1 再回来编译）。

> 执行顺序提示：DeathStrategy 调 `((MaggoteersGame) game).fail()`，而 `fail()` 在 Task 3 Step 1 加。**建议先做 Task 3 Step 1（加 outcome + win/fail），再做本 Task Step 2/3**，一次编译通过。两 Task 可一并提交。

- [ ] **Step 4: 提交（与 Task 3 合并提交，见 Task 3 末尾）**

---

## Task 3: MaggoteersGame 集成（outcome + win/fail + switchProfile + 冒险模式 + PlayerState 初始化/销毁）+ WaveScheduler 胜利调 win()

**Files:**
- Modify: `src/main/java/io/mczju/maggoteers/game/MaggoteersGame.java`
- Modify: `src/main/java/io/mczju/maggoteers/wave/WaveScheduler.java`（VICTORY 改调 `game.win()`）
- Modify: `src/main/java/io/mczju/maggoteers/MaggoteersPlugin.java`（注册死亡策略不需要——MGC 反射调 `getPlayerDeathStrategy()`；无需改 Plugin）

**Interfaces:**
- Consumes: `PlayerStateManager`、`MaggoteersDeathStrategy`、`GameOutcome`、config `lives.default`。
- Produces:
  - `MaggoteersGame.getOutcome()/setOutcome(...)`、`win()`、`fail()`。
  - `MaggoteersGame.getPlayerDeathStrategy()` override。
  - 开局对每玩家 `switchProfile` + 冒险模式 + `PlayerStateManager.init(reviveCount=lives.default)`。
  - 结束钩子 `PlayerStateManager.destroyAll`。

- [ ] **Step 1: 改 `MaggoteersGame`（加 outcome/win/fail + death strategy + 玩家初始化）**

在 Plan 3 版 `MaggoteersGame` 基础上整文件替换：

```java
package io.mczju.maggoteers.game;

import com.github.mczjuops.mczjugamecore.MCZJUGameCore;
import com.github.mczjuops.mczjugamecore.game.AbstractGame;
import com.github.mczjuops.mczjugamecore.game.GameMeta;
import com.github.mczjuops.mczjugamecore.game.strategy.wait.DefaultGameWaitStrategy;
import com.github.mczjuops.mczjugamecore.game.strategy.wait.GameWaitStrategy;
import com.github.mczjuops.mczjugamecore.player.PlayerExt;
import com.github.mczjuops.mczjugamecore.player.strategy.AbstractPlayerDeathStrategy;
import io.mczju.maggoteers.MaggoteersPlugin;
import io.mczju.maggoteers.plan.ActPlan;
import io.mczju.maggoteers.plan.RunPlanner;
import io.mczju.maggoteers.state.PlayerStateManager;
import io.mczju.maggoteers.wave.WaveScheduler;
import io.mczju.maggoteers.world.WorldService;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;

/**
 * 本局游戏。Plan 4：叠加玩家死亡/复活/失败——switchProfile + 冒险模式 + PlayerState 初始化；
 * DeathStrategy 注入；win()/fail() 设 outcome 后 endGame。
 */
public class MaggoteersGame extends AbstractGame {

    private volatile boolean planReady = false;
    private volatile List<ActPlan> plannedActs;
    private volatile String planError;

    /** 对局结局（D4）。 */
    private GameOutcome outcome = GameOutcome.IN_PROGRESS;

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

    /** Plan 4：注入死亡策略（MGC 反射调此 override，已源码核实 SurvivorGame:75）。 */
    @Override
    public AbstractPlayerDeathStrategy getPlayerDeathStrategy() {
        return new MaggoteersDeathStrategy(this);
    }

    public GameOutcome getOutcome() { return outcome; }
    public void setOutcome(GameOutcome o) { this.outcome = o; }

    /** 胜利：Act3 Boss 死，WaveScheduler 调。 */
    public void win() {
        if (outcome != GameOutcome.IN_PROGRESS) return;
        outcome = GameOutcome.WIN;
        MCZJUGameCore.getGameManager().endGame(this);
    }

    /** 失败：场上无冒险玩家，DeathStrategy 调。 */
    public void fail() {
        if (outcome != GameOutcome.IN_PROGRESS) return;
        outcome = GameOutcome.FAIL;
        MCZJUGameCore.getGameManager().endGame(this);
    }

    @Override
    protected boolean onGameInit() {
        planReady = false;
        return true;
    }

    @Override
    protected void onGameStart() {
        runWhenReady(this::startInWorld);
    }

    /** 主线程：建世界 → 初始化玩家（profile/冒险/PlayerState）→ 异步 RunPlanner → 门闩开波。 */
    private void startInWorld() {
        World w = WorldService.create(this);
        if (w == null) { sender().error("世界创建失败，对局终止。"); return; }

        int lives = MaggoteersPlugin.getInstance().getConfig().getInt("lives.default", 2);
        for (PlayerExt pe : getPlayers()) {
            pe.switchProfile(this.getId());                 // 干净游戏 profile（VampireSurvivor:147 实证）
            pe.player().setGameMode(GameMode.ADVENTURE);
            PlayerStateManager.init(this, pe.player().getUniqueId(), lives);
        }

        final long seed = WorldService.getSeed(this);
        final int players = getPlayers().size();
        sender().info("<gray>正在生成本局剧本（异步）…");
        Bukkit.getScheduler().runTaskAsynchronously(MaggoteersPlugin.getInstance(), () -> {
            try { plannedActs = RunPlanner.plan(seed, players); }
            catch (Exception e) { planError = e.getMessage();
                MaggoteersPlugin.getInstance().getLogger().severe("RunPlanner 失败：" + e.getMessage()); }
            finally { planReady = true; }
        });
        new BukkitRunnable() {
            int ticks = 0;
            @Override public void run() {
                if (planReady) { cancel(); startWaves(); }
                else if (++ticks > 20 * 30) { cancel(); sender().warn("剧本生成超时(30s)，终止。"); }
            }
        }.runTaskTimer(MaggoteersPlugin.getInstance(), 1L, 1L);
    }

    private void startWaves() {
        if (plannedActs == null) {
            sender().error("剧本生成失败" + (planError != null ? "：" + planError : "") + "，对局终止。");
            fail();
            return;
        }
        WaveScheduler.start(this, plannedActs);
        sender().info("<green>卫戍协议 开局！三层共 "
                + plannedActs.stream().mapToInt(a -> a.waves().size()).sum() + " 波。每人复活 " 
                + MaggoteersPlugin.getInstance().getConfig().getInt("lives.default", 2) + " 次。");
    }

    @Override protected void onGameCancel() { WaveScheduler.stop(this); PlayerStateManager.destroyAll(this); WorldService.cleanup(this); }
    @Override protected void onGameAbort()  { WaveScheduler.stop(this); PlayerStateManager.destroyAll(this); WorldService.cleanup(this); }
    @Override protected void onGameEnd()    { WaveScheduler.stop(this); PlayerStateManager.destroyAll(this); WorldService.cleanup(this); }
    // Plan 7：onGameEnd 按 outcome（WIN/FAIL）结算账户货币。

    private void runWhenReady(Runnable action) {
        new BukkitRunnable() { @Override public void run() { cancel(); action.run(); } }
                .runTask(MaggoteersPlugin.getInstance());
    }
}
```

- [ ] **Step 2: `WaveScheduler` 胜利改调 `win()`（Plan 2 的 VICTORY 分支）**

在 `WaveScheduler.advance(...)` 的 Act3 通关分支，把：
```java
        MCZJUGameCore.getGameManager().endGame(game);   // 胜利结算（Plan 7 接 outcome=WIN）
```
改为：
```java
        ((MaggoteersGame) game).win();   // 设 outcome=WIN + endGame
```
并补 import：
```java
import io.mczju.maggoteers.game.MaggoteersGame;
```
（`MCZJUGameCore` 的 import 若 WaveScheduler 别处不再用可留可删；保留无害。）

- [ ] **Step 3: 构建 + 部署**

Run: `mvn -q -DskipTests package`
Expected: `BUILD SUCCESS`。部署 jar，重启。

- [ ] **Step 4: 提交 Task 2 + Task 3**

```bash
git add src/main/java/io/mczju/maggoteers/game \
        src/main/java/io/mczju/maggoteers/wave/WaveScheduler.java
git commit -m "feat(plan4): DeathStrategy 复活/观察者/失败 + outcome + switchProfile 集成"
```

---

## Task 4: 端到端 in-game 验证（死亡/复活/失败）

**Files:** 无（手动验证）。

- [ ] **Step 1: 自动复活链（reviveCount=2）**

1. `config.yml` 确认 `lives.default: 2`。`/mgc join maggoteers` 开局。
2. 找怪打到致死（让僵尸打或 `/kill` 模拟——`/kill` 会触发 PlayerDeathEvent）。Expected：聊天 `XXX 倒下，自动复活！（剩余 1 次）`，玩家原地满血、2s 无敌、仍是冒险模式。
3. 再死两次。Expected：第二次 `（剩余 0 次）`；第三次 `XXX 倒下！转为观察者`，玩家变 SPECTATOR。

- [ ] **Step 2: 单人失败结算**

1. 单人局，耗尽复活次数后第 3 次死亡（转观察者）。Expected：场上无冒险玩家 → `fail()` → 对局结束（世界清理）。聊天/控制台见失败。
2. 控制：`MaggoteersGame.getOutcome()` 应为 `FAIL`（Plan 7 调试命令前，看控制台 `onGameEnd` 是否触发 + 世界是否清理即可）。

- [ ] **Step 3: 多人 · 队友倒下但有人存活 → 不失败**

1. 双人局（`wait.min_players: 2`）。一人耗尽复活转观察者，另一人仍冒险。
2. Expected：**不**触发失败，对局继续；观察者可被（Plan 6 复活币）救援——本 Plan 用 `PlayerStateManager.revivePlayer(game, uuid)` 手动测：
   - 观察者玩家执行 `/minecraft:data` 无法直接调；本 Plan 用一个小临时验证：在控制台或临时 `/maggoteers` 占位（Plan 8）调 `revivePlayer` 不现实。
   - **替代验证**：单测 `PlayerStateTest` 已覆盖状态机；复活币 GUI 的端到端验证留到 Plan 6（届时 GUI 直接调 `revivePlayer`）。
3. 当第二人也转观察者（场上无冒险玩家）→ `fail()` 结束。

- [ ] **Step 4: 胜利链不受影响**

1. 正常打完三层（不死或复活后通关）。Expected：Act3 Boss 死 → `win()` → 通关（outcome=WIN），世界清理。

- [ ] **Step 5: 验证 dev-log**

在 `docs/dev-log.md` 追加 Plan 4 完成条目。

> **本 Plan 端到端验证限制**：复活币救援（`revivePlayer`/`addReviveCount`）的 GUI 在 Plan 6，本 Plan 只能用单测 + 状态机推断覆盖；"队友倒下→不失败"靠多人手测。`switchProfile` 是否被 MGC 自动调用过（导致重复）——若开局玩家背包异常清空两次，移除本 Plan 的 `switchProfile` 调用（MGC 已自动切），dev-log 记录。

---

## Plan 4 验收标准（Definition of Done）

- [ ] `mvn package` 产 jar，`mvn test` 绿（新增 `PlayerStateTest`）。
- [ ] 死亡自动复活 2 次（`reviveCount` 2→1→0 仍复活），第 3 次转观察者。
- [ ] 单人耗尽复活 → 失败结算（`outcome=FAIL`，对局结束、世界清理）。
- [ ] 多人：一人转观察者但有人存活 → 继续不失败；全员观察者 → 失败。
- [ ] 胜利链正常（Act3 Boss 死 → `win()` → outcome=WIN）。
- [ ] `getPlayerDeathStrategy()` 注入生效（MGC 用我们的策略，事件被取消不走原版死亡屏）。
- [ ] 开局玩家为冒险模式、干净 profile。
- [ ] `docs/dev-log.md` 追加 Plan 4 完成条目。

> **已知遗留（后续 Plan 处理）**：
> - 复活币物品 + 头像 GUI（对死者=复活、对活者=+次数）→ Plan 6（调本 Plan 的 `revivePlayer`/`addReviveCount`）。
> - 结算金额（`onGameEnd` 按 WIN/FAIL 算账户货币）→ Plan 7。
> - 复活时 `EffectService.resync(player)` 补回死亡清掉的药水 → Plan 5（在复活路径加 hook）。
> - `switchProfile` 是否需手动调（MGC 可能自动）→ 实测后定，dev-log 记录。
