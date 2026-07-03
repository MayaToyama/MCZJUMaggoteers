package io.mczju.maggoteers.wave;

import com.github.mczjuops.mczjugamecore.game.AbstractGame;
import com.github.mczjuops.mczjugamecore.menu.MenuFacade;
import io.mczju.maggoteers.MaggoteersPlugin;
import io.mczju.maggoteers.game.MaggoteersGame;
import io.mczju.maggoteers.plan.ActPlan;
import io.mczju.maggoteers.plan.RunConfig;
import io.mczju.maggoteers.reward.RewardService;
import io.mczju.maggoteers.world.MapRepository;
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
 */
public final class WaveScheduler {

    public enum Phase { SPAWNING, ACTIVE, CLEARED, REST, DONE }

    public record RunSnapshot(
            int actIndex, String mapId, int waveIndex, int waveCount,
            Phase phase, String lastTier, int restSecondsLeft
    ) {
        public String phaseName() {
            return switch (phase) {
                case SPAWNING -> "刷怪";
                case ACTIVE -> "战斗";
                case CLEARED -> "清场";
                case REST -> "休整";
                case DONE -> "结束";
            };
        }
    }

    static final class Cursor {
        final List<ActPlan> acts;
        final RunConfig runCfg;
        int actIndex = 0;
        int waveIndex = 0;
        Phase phase = Phase.SPAWNING;
        long waveStartTicks;
        long restEndTicks;
        int stepCursor = 0;
        List<SpawnStep> currentExpanded = List.of();
        WaveRuntime runtime;
        BukkitTask task;
        String lastTier = "weak";

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

    public static RunSnapshot snapshot(AbstractGame game) {
        Cursor c = CURSORS.get(game);
        if (c == null) return null;
        ActPlan act = c.acts.get(c.actIndex);
        int restLeft = c.phase == Phase.REST
                ? Math.max(0, (int) ((c.restEndTicks - Bukkit.getCurrentTick()) / 20L)) : 0;
        return new RunSnapshot(c.actIndex, act.mapId(), c.waveIndex, act.waves().size(),
                c.phase, c.lastTier, restLeft);
    }

    public static void skipRest(AbstractGame game) {
        Cursor c = CURSORS.get(game);
        if (c != null && c.phase == Phase.REST) advance(game, c);
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
        broadcast(game, Component.text("▶ 进入第 " + (actIndex + 1) + " 层 · " + act.mapId(), NamedTextColor.GOLD));
        beginWave(game, c, 0);
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
        broadcast(game, Component.text("▶ 第 " + (waveIndex + 1) + " / "
                + c.acts.get(c.actIndex).waves().size() + " 波", NamedTextColor.RED));
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
        List<io.mczju.maggoteers.wave.RewardItem> rewards = spec.clearReward();
        if (!rewards.isEmpty()) {
            RewardService.grantClearRewards(
                    game.getPlayers().stream().map(pe -> pe.player()).toList(), rewards);
            broadcast(game, Component.text("✔ 本波清除！奖励已发放。", NamedTextColor.GREEN));
        } else {
            broadcast(game, Component.text("✔ 本波清除！", NamedTextColor.GREEN));
        }
        int restSec = MaggoteersPlugin.getInstance().getConfig().getInt("rest.duration_sec", 30);
        c.restEndTicks = Bukkit.getCurrentTick() + restSec * 20L;
        c.phase = Phase.REST;
        for (var pe : game.getPlayers()) {
            if (pe.player().getGameMode() == org.bukkit.GameMode.ADVENTURE) {
                MenuFacade.open("maggoteers-rest", pe.player(), game);
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
        broadcast(game, Component.text("🏆 通关 卫戍协议！", NamedTextColor.GOLD));
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
