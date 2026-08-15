package io.mczju.maggoteers.ui;

import com.github.mczjuops.mczjugamecore.game.AbstractGame;
import com.github.mczjuops.mczjugamecore.player.PlayerExt;
import io.mczju.maggoteers.MaggoteersPlugin;
import io.mczju.maggoteers.config.MessageService;
import io.mczju.maggoteers.game.ClassSelectGate;
import io.mczju.maggoteers.game.MaggoteersGame;
import io.mczju.maggoteers.state.PlayerState;
import io.mczju.maggoteers.state.PlayerStateManager;
import io.mczju.maggoteers.wave.WaveEngine;
import io.mczju.maggoteers.wave.WaveScheduler;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.*;

import java.util.IdentityHashMap;
import java.util.Map;

/** 局内 sidebar：玩家/复活/层/地图/波次/剩余怪。 */
public final class RunScoreboard {
    private static final Map<AbstractGame, BukkitTask> TASKS = new IdentityHashMap<>();

    private RunScoreboard() {}

    public static void start(AbstractGame game) {
        stop(game);
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(MaggoteersPlugin.getInstance(),
                () -> refresh(game), 10L, 20L);
        TASKS.put(game, task);
    }

    public static void stop(AbstractGame game) {
        BukkitTask t = TASKS.remove(game);
        if (t != null) t.cancel();
        for (PlayerExt pe : game.getPlayers()) {
            Player p = pe.player();
            if (p != null) p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        }
    }

    private static void refresh(AbstractGame game) {
        if (game instanceof MaggoteersGame mg && mg.isInClassSelectPhase()) {
            refreshClassSelect(game);
            return;
        }
        var snap = WaveScheduler.snapshot(game);
        if (snap == null) return;
        int mobs = WaveEngine.livingCount(game);
        int restSec = snap.restSecondsLeft();

        for (PlayerExt pe : game.getPlayers()) {
            Player p = pe.player();
            if (p == null) continue;
            Scoreboard sb = Bukkit.getScoreboardManager().getNewScoreboard();
            Objective obj = sb.registerNewObjective("maggoteers", Criteria.DUMMY,
                    MessageService.legacy("scoreboard.title", Map.of()));
            obj.setDisplaySlot(DisplaySlot.SIDEBAR);

            int line = 15;
            obj.getScore(MessageService.legacy("scoreboard.line_act", Map.of(
                    "act", String.valueOf(snap.actIndex() + 1),
                    "map", snap.mapId()))).setScore(line--);

            String phase = WaveScheduler.phaseName(snap.phase());
            String waveLine = restSec > 0
                    ? MessageService.legacy("scoreboard.line_wave_resting", Map.of(
                            "wave", String.valueOf(snap.waveIndex() + 1),
                            "wave_count", String.valueOf(snap.waveCount()),
                            "phase", phase,
                            "rest", String.valueOf(restSec)))
                    : MessageService.legacy("scoreboard.line_wave", Map.of(
                            "wave", String.valueOf(snap.waveIndex() + 1),
                            "wave_count", String.valueOf(snap.waveCount()),
                            "phase", phase));
            obj.getScore(waveLine).setScore(line--);
            obj.getScore(MessageService.legacy("scoreboard.line_mobs", Map.of(
                    "mobs", String.valueOf(mobs)))).setScore(line--);
            obj.getScore(MessageService.legacy("scoreboard.separator", Map.of())).setScore(line--);

            for (PlayerExt o : game.getPlayers()) {
                Player op = o.player();
                if (op == null) continue;
                PlayerState st = PlayerStateManager.get(game, op.getUniqueId());
                int rev = st == null ? 0 : st.getReviveCount();
                String statusKey = st != null && !st.isAlive()
                        ? "scoreboard.status_spec" : "scoreboard.status_fight";
                obj.getScore(playerLine(statusKey, op.getName(), rev)).setScore(line--);
            }

            p.setScoreboard(sb);
        }
    }

    private static void refreshClassSelect(AbstractGame game) {
        for (PlayerExt pe : game.getPlayers()) {
            Player p = pe.player();
            if (p == null) continue;
            Scoreboard sb = Bukkit.getScoreboardManager().getNewScoreboard();
            Objective obj = sb.registerNewObjective("maggoteers", Criteria.DUMMY,
                    MessageService.legacy("scoreboard.title", Map.of()));
            obj.setDisplaySlot(DisplaySlot.SIDEBAR);
            int line = 10;
            obj.getScore(MessageService.legacy("scoreboard.class_header", Map.of())).setScore(line--);
            obj.getScore(MessageService.legacy("scoreboard.class_hint", Map.of())).setScore(line--);
            obj.getScore(MessageService.legacy("scoreboard.separator", Map.of())).setScore(line--);
            for (PlayerExt o : game.getPlayers()) {
                Player op = o.player();
                if (op == null) continue;
                PlayerState st = PlayerStateManager.get(game, op.getUniqueId());
                int rev = st == null ? 0 : st.getReviveCount();
                String statusKey = ClassSelectGate.hasChosen(game, op.getUniqueId())
                        ? "scoreboard.class_chosen" : "scoreboard.class_pending";
                obj.getScore(playerLine(statusKey, op.getName(), rev)).setScore(line--);
            }
            p.setScoreboard(sb);
        }
    }

    /**
     * Status keys carry MiniMessage colors; nesting them into {@code player_line} would
     * escape those tags. Legacy-render status, then append player_line with empty status.
     */
    private static String playerLine(String statusKey, String name, int revives) {
        String status = MessageService.legacy(statusKey, Map.of());
        String rest = MessageService.legacy("scoreboard.player_line", Map.of(
                "status", "",
                "name", name,
                "revives", String.valueOf(revives)));
        return status + rest;
    }
}
