package io.mczju.maggoteers.wave;

import com.github.mczjuops.mczjugamecore.game.AbstractGame;
import com.github.mczjuops.mczjugamecore.menu.MenuFacade;
import io.mczju.maggoteers.MaggoteersPlugin;
import io.mczju.maggoteers.config.MessageService;
import io.mczju.maggoteers.game.MaggoteersGame;
import io.mczju.maggoteers.plan.ActPlan;
import io.mczju.maggoteers.plan.RunConfig;
import io.mczju.maggoteers.reward.RewardService;
import io.mczju.maggoteers.world.MapRepository;
import io.mczju.maggoteers.world.StructurePaster;
import io.mczju.maggoteers.world.WorldService;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

/**
 * 单一可暂停状态机心跳，驱动 {@code SPAWNING→ACTIVE→CLEARED→REST→下一波/下一层/VICTORY}。
 */
public final class WaveScheduler {

    public enum Phase { PREP, SPAWNING, ACTIVE, CLEARED, REST, DONE }

    public record RunSnapshot(
            int actIndex, String mapId, int waveIndex, int waveCount,
            Phase phase, String lastTier, int restSecondsLeft
    ) {
        public String phaseName() {
            return WaveScheduler.phaseName(phase);
        }
    }

    /** Plain phase label for scoreboard / UI (no MiniMessage tags). */
    public static String phaseName(Phase phase) {
        return MessageService.raw("wave.phase_" + phase.name().toLowerCase(Locale.ROOT), Map.of());
    }

    static final class Cursor {
        final List<ActPlan> acts;
        final RunConfig runCfg;
        int actIndex = 0;
        int waveIndex = 0;
        Phase phase = Phase.SPAWNING;
        long waveStartTicks;
        long restEndTicks;
        long prepEndTicks;
        int stepCursor = 0;
        List<SpawnStep> currentExpanded = List.of();
        WaveRuntime runtime;
        BukkitTask task;
        String lastTier = "weak";
        /** 最近击杀 Boss 所在层（Boss 池粘滞，§9.2）。 */
        int lastBossKillAct = 0;
        /** 本休整期已投跳过票的玩家（全员同意才跳过）。 */
        final Set<UUID> skipVotes = new HashSet<>();

        Cursor(List<ActPlan> acts, RunConfig runCfg) {
            this.acts = acts;
            this.runCfg = runCfg;
        }
    }

    private static final Map<AbstractGame, Cursor> CURSORS = new IdentityHashMap<>();

    public static void start(AbstractGame game, List<ActPlan> acts, RunConfig runCfg) {
        Cursor c = new Cursor(acts, runCfg);
        CURSORS.put(game, c);
        enterAct(game, c, 0);
        scheduleHeartbeat(game, c);
    }

    public static void start(AbstractGame game, List<ActPlan> acts) {
        start(game, acts, loadRunConfig());
    }

    public static void stop(AbstractGame game) {
        Cursor c = CURSORS.remove(game);
        if (c == null) return;
        if (c.task != null) c.task.cancel();
        WaveEngine.stop(game);
    }

    /** 波次状态机仍跟踪的对局（不依赖 MGC gameList）。 */
    public static java.util.Set<AbstractGame> activeGames() {
        return java.util.Collections.unmodifiableSet(CURSORS.keySet());
    }

    /** 结算用：返回 {actIndex, wavesClearedInCurrentAct}（无 cursor 返回 {0,0}）。 */
    public static int[] progress(AbstractGame game) {
        Cursor c = CURSORS.get(game);
        if (c == null) return new int[]{0, 0};
        return new int[]{c.actIndex, c.waveIndex};
    }

    public static RunSnapshot snapshot(AbstractGame game) {
        Cursor c = CURSORS.get(game);
        if (c == null) return null;
        ActPlan act = c.acts.get(c.actIndex);
        int restLeft = c.phase == Phase.REST
                ? Math.max(0, (int) ((c.restEndTicks - Bukkit.getCurrentTick()) / 20L)) : 0;
        if (c.phase == Phase.PREP) {
            restLeft = Math.max(0, (int) ((c.prepEndTicks - Bukkit.getCurrentTick()) / 20L));
        }
        return new RunSnapshot(c.actIndex, act.mapId(), c.waveIndex, act.waves().size(),
                c.phase, c.lastTier, restLeft);
    }

    /** 已投跳过票人数。 */
    public static int skipVoteCount(AbstractGame game) {
        Cursor c = CURSORS.get(game);
        return c == null ? 0 : c.skipVotes.size();
    }

    /** 休整期可投票的玩家总数（冒险模式玩家）。 */
    public static int skipVoteTotal(AbstractGame game) {
        int total = 0;
        for (UUID uuid : io.mczju.maggoteers.state.PlayerStateManager.uuidsInGame(game)) {
            Player pl = Bukkit.getPlayer(uuid);
            if (pl != null && io.mczju.maggoteers.state.PlayerStateManager.isRunParticipant(game, pl)) {
                total++;
            }
        }
        return total;
    }

    /**
     * 休整跳过投票（§9.3）：记录该玩家赞成票；全员同意才 advance。
     * @return true 表示本票促成跳过（已 advance），false 表示仍需更多票。
     */
    public static boolean voteSkip(AbstractGame game, UUID uuid) {
        Cursor c = CURSORS.get(game);
        if (c == null || c.phase != Phase.REST || uuid == null) return false;
        Player voter = Bukkit.getPlayer(uuid);
        if (voter == null || !io.mczju.maggoteers.state.PlayerStateManager.isRunParticipant(game, voter)) return false;
        if (c.skipVotes.contains(uuid)) return false;
        c.skipVotes.add(uuid);
        int total = skipVoteTotal(game);
        int voted = c.skipVotes.size();
        String voterName = voter.getName();
        broadcast(game, MessageService.component("wave.skip_vote", Map.of(
                "voted", String.valueOf(voted),
                "total", String.valueOf(total),
                "player", voterName)));
        if (voted >= total) {
            broadcast(game, MessageService.component("wave.skip_passed", Map.of()));
            advance(game, c);
            return true;
        }
        return false;
    }

    /** Boss 池粘滞层索引（最近击杀 Boss 所在层，§9.2）。 */
    public static int bossPoolActIndex(AbstractGame game) {
        Cursor c = CURSORS.get(game);
        return c == null ? 0 : c.lastBossKillAct;
    }

    /** 返回当前层的 playerSpawn 绝对坐标（已叠 act_origins），供死亡复活等场景传送。null 表示无 cursor / 无世界。 */
    public static org.bukkit.Location currentSpawnLocation(AbstractGame game) {
        Cursor c = CURSORS.get(game);
        if (c == null) return null;
        World w = WorldService.get(game);
        if (w == null) return null;
        var spawn = c.acts.get(c.actIndex).playerSpawn();
        return new org.bukkit.Location(w, spawn.x(), spawn.y(), spawn.z());
    }

    /** 是否处于波次休整期（可右键货币打开升级菜单）。 */
    public static boolean isRestPhase(AbstractGame game) {
        Cursor c = CURSORS.get(game);
        return c != null && c.phase == Phase.REST;
    }

    /** 是否处于进层准备倒计时（此期间不刷怪）。 */
    public static boolean isPrepPhase(AbstractGame game) {
        Cursor c = CURSORS.get(game);
        return c != null && c.phase == Phase.PREP;
    }

    /**
     * 调试：跳到指定层/波（0-based）。清当前怪、跳过休整；跨层时会重新粘贴结构并传送。
     */
    public static boolean debugSeekWave(AbstractGame game, int actIndex, int waveIndex) {
        Cursor c = CURSORS.get(game);
        if (c == null || c.phase == Phase.DONE) return false;
        if (actIndex < 0 || actIndex >= c.acts.size()) return false;
        if (waveIndex < 0 || waveIndex >= c.acts.get(actIndex).waves().size()) return false;
        WaveEngine.killAllTracked(game);
        if (actIndex != c.actIndex) {
            pasteActForCursor(game, c, actIndex);
            c.actIndex = actIndex;
        }
        beginWave(game, c, waveIndex);
        return true;
    }

    /** 调试：相对当前层波次偏移（可跨层，waveIndex 钳制在目标层范围内）。 */
    public static boolean debugShiftWave(AbstractGame game, int delta) {
        Cursor c = CURSORS.get(game);
        if (c == null || c.phase == Phase.DONE || delta == 0) return false;
        int act = c.actIndex;
        int wave = c.waveIndex + delta;
        while (wave < 0 && act > 0) {
            act--;
            wave += c.acts.get(act).waves().size();
        }
        while (wave >= c.acts.get(act).waves().size() && act + 1 < c.acts.size()) {
            wave -= c.acts.get(act).waves().size();
            act++;
        }
        if (act < 0 || act >= c.acts.size()) return false;
        int max = c.acts.get(act).waves().size();
        if (wave < 0 || wave >= max) return false;
        return debugSeekWave(game, act, wave);
    }

    public static void pause(AbstractGame game) {
        Cursor c = CURSORS.get(game);
        if (c != null && c.task != null) { c.task.cancel(); c.task = null; }
    }

    public static void resume(AbstractGame game) {
        Cursor c = CURSORS.get(game);
        if (c != null && c.task == null && c.phase != Phase.DONE) scheduleHeartbeat(game, c);
    }

    private static void enterAct(AbstractGame game, Cursor c, int actIndex) {
        c.actIndex = actIndex;
        pasteActForCursor(game, c, actIndex);
        ActPlan act = c.acts.get(actIndex);
        broadcast(game, MessageService.component("wave.enter_act", Map.of(
                "act", String.valueOf(actIndex + 1),
                "map", act.mapId())));
        io.mczju.maggoteers.effect.EffectService.fireTrigger((MaggoteersGame) game, io.mczju.maggoteers.effect.Trigger.ON_ACT_ENTER);
        io.mczju.maggoteers.effect.SummonRegistry.onActEnter((MaggoteersGame) game);
        beginActPrep(game, c);
    }

    private static void pasteActForCursor(AbstractGame game, Cursor c, int actIndex) {
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
        game.getPlayers().forEach(pe -> {
            pe.player().teleport(loc);
            pe.player().setFallDistance(0f);
        });
    }

    private static void beginActPrep(AbstractGame game, Cursor c) {
        int prepSec = MaggoteersPlugin.getInstance().getConfig().getInt("act_enter.prep_sec", 10);
        if (prepSec <= 0) {
            beginWave(game, c, 0);
            return;
        }
        c.phase = Phase.PREP;
        c.prepEndTicks = Bukkit.getCurrentTick() + prepSec * 20L;
        broadcast(game, MessageService.component("wave.prep", Map.of("prep", String.valueOf(prepSec))));
    }

    private static void beginWave(AbstractGame game, Cursor c, int waveIndex) {
        c.waveIndex = waveIndex;
        c.phase = Phase.SPAWNING;
        c.stepCursor = 0;
        c.lastTier = tierForWave(c, waveIndex);
        WaveSpec spec = c.acts.get(c.actIndex).waves().get(waveIndex);
        c.currentExpanded = spec.expand();
        c.waveStartTicks = Bukkit.getCurrentTick();
        c.runtime = (c.runtime == null) ? WaveEngine.start(game) : c.runtime;
        c.runtime.cleared = false;
        String strategyClause = spec.strategyId().isBlank()
                ? ""
                : MessageService.raw("wave.strategy_clause", Map.of("strategy", spec.strategyId()));
        broadcast(game, MessageService.component("wave.begin", Map.of(
                "wave", String.valueOf(waveIndex + 1),
                "wave_count", String.valueOf(c.acts.get(c.actIndex).waves().size()),
                "tier", c.lastTier,
                "strategy_clause", strategyClause)));
    }

    private static String tierForWave(Cursor c, int waveIndex) {
        String act = actId(c.actIndex);
        int weak = c.runCfg.weak(act);
        int strong = c.runCfg.strong(act);
        if (waveIndex < weak) return "weak";
        if (waveIndex < weak + strong) return "strong";
        return "boss";
    }

    private static void scheduleHeartbeat(AbstractGame game, Cursor c) {
        c.task = Bukkit.getScheduler().runTaskTimer(MaggoteersPlugin.getInstance(), () -> tick(game, c), 5L, 5L);
    }

    private static void tick(AbstractGame game, Cursor c) {
        if (c.phase == Phase.DONE) return;
        if (c.runtime != null) WaveEngine.tick(c.runtime);
        World tw = WorldService.get(game);
        if (tw != null) WorldService.applyTimeLock(tw);

        switch (c.phase) {
            case PREP -> {
                if (Bukkit.getCurrentTick() >= c.prepEndTicks) beginWave(game, c, 0);
            }
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
        // 发放通关奖励 + 触发 ON_WAVE_CLEAR + 清理召唤物（终局波也需要，C5）
        WaveSpec spec = c.acts.get(c.actIndex).waves().get(c.waveIndex);
        List<io.mczju.maggoteers.wave.RewardItem> rewards = spec.clearReward();
        if (!rewards.isEmpty()) {
            RewardService.grantClearRewards(
                    game.getPlayers().stream().map(pe -> pe.player()).toList(), rewards);
            broadcast(game, MessageService.component("wave.cleared_rewarded", Map.of()));
        } else {
            broadcast(game, MessageService.component("wave.cleared", Map.of()));
        }
        try {
            io.mczju.maggoteers.effect.EffectService.fireTrigger(
                    (MaggoteersGame) game, io.mczju.maggoteers.effect.Trigger.ON_WAVE_CLEAR);
        } catch (RuntimeException ex) {
            MaggoteersPlugin.getInstance().getLogger()
                    .warning("ON_WAVE_CLEAR failed: " + ex.getMessage());
        }
        try {
            io.mczju.maggoteers.effect.SummonRegistry.onWaveClear((MaggoteersGame) game);
        } catch (RuntimeException ex) {
            MaggoteersPlugin.getInstance().getLogger()
                    .warning("SummonRegistry.onWaveClear failed: " + ex.getMessage());
        }

        // Boss 池粘滞（§9.2）：击杀本层 Boss 后更新粘滞层
        if ("boss".equals(c.lastTier)) c.lastBossKillAct = c.actIndex;

        // 终局 Boss 也进休整：粘滞已切到 act3_boss，可花掉剩余 Boss 币；休整结束/跳过 → advance → win
        boolean isFinalWave = c.actIndex == c.acts.size() - 1
                && c.waveIndex == c.acts.get(c.actIndex).waves().size() - 1;
        if (isFinalWave) {
            broadcast(game, MessageService.component("wave.final_rest", Map.of()));
        }

        int restSec = MaggoteersPlugin.getInstance().getConfig().getInt("rest.duration_sec", 30);
        c.restEndTicks = Bukkit.getCurrentTick() + restSec * 20L;
        c.phase = Phase.REST;
        c.skipVotes.clear();
        for (UUID uuid : io.mczju.maggoteers.state.PlayerStateManager.uuidsInGame(game)) {
            Player pl = Bukkit.getPlayer(uuid);
            if (pl != null && io.mczju.maggoteers.state.PlayerStateManager.isRunParticipant(game, pl)) {
                MenuFacade.open("maggoteers-rest", pl, game);
            }
        }
    }

    private static void advance(AbstractGame game, Cursor c) {
        int waveCount = c.acts.get(c.actIndex).waves().size();
        if (c.waveIndex + 1 < waveCount) { beginWave(game, c, c.waveIndex + 1); return; }
        if (c.actIndex + 1 < c.acts.size()) { enterAct(game, c, c.actIndex + 1); return; }
        c.phase = Phase.DONE;
        if (c.task != null) c.task.cancel();
        WaveEngine.stop(game);
        broadcast(game, MessageService.component("wave.victory", Map.of()));
        ((MaggoteersGame) game).win();
    }

    private static String actId(int i) { return "act" + (i + 1); }

    private static MapRepository.MapEntry findMap(int actIndex, String mapId) {
        return MapRepository.getMaps(actId(actIndex)).stream()
                .filter(m -> m.mapId().equals(mapId)).findFirst().orElse(null);
    }

    private static void broadcast(AbstractGame game, Component msg) {
        game.getPlayers().forEach(pe -> pe.player().sendMessage(msg));
    }

    private static RunConfig loadRunConfig() {
        var plugin = MaggoteersPlugin.getInstance();
        return io.mczju.maggoteers.plan.RunPlanner.loadRunConfig(plugin);
    }

    private WaveScheduler() {}
}
