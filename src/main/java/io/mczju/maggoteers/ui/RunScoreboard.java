package io.mczju.maggoteers.ui;

import com.github.mczjuops.mczjugamecore.game.AbstractGame;
import com.github.mczjuops.mczjugamecore.player.PlayerExt;
import io.mczju.maggoteers.MaggoteersPlugin;
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
            Objective obj = sb.registerNewObjective("maggoteers", Criteria.DUMMY, "§6卫戍协议");
            obj.setDisplaySlot(DisplaySlot.SIDEBAR);

            int line = 15;
            obj.getScore("§e层 " + (snap.actIndex() + 1) + " · §f" + snap.mapId()).setScore(line--);
            String phase = snap.phaseName();
            if (restSec > 0) phase += " §7(" + restSec + "s)";
            obj.getScore("§b波 " + (snap.waveIndex() + 1) + "/" + snap.waveCount() + " · " + phase).setScore(line--);
            obj.getScore("§c剩余怪物: " + mobs).setScore(line--);
            obj.getScore("§8────────").setScore(line--);

            for (PlayerExt o : game.getPlayers()) {
                Player op = o.player();
                if (op == null) continue;
                PlayerState st = PlayerStateManager.get(game, op.getUniqueId());
                int rev = st == null ? 0 : st.getReviveCount();
                String status = st != null && !st.isAlive() ? "§7[观]" : "§a[战]";
                obj.getScore(status + " §f" + op.getName() + " §e♥" + rev).setScore(line--);
            }

            p.setScoreboard(sb);
        }
    }

    private static void refreshClassSelect(AbstractGame game) {
        for (PlayerExt pe : game.getPlayers()) {
            Player p = pe.player();
            if (p == null) continue;
            Scoreboard sb = Bukkit.getScoreboardManager().getNewScoreboard();
            Objective obj = sb.registerNewObjective("maggoteers", Criteria.DUMMY, "§6卫戍协议");
            obj.setDisplaySlot(DisplaySlot.SIDEBAR);
            int line = 10;
            obj.getScore("§e选职业 · 开局休整").setScore(line--);
            obj.getScore("§7右键职业选择券").setScore(line--);
            obj.getScore("§8────────").setScore(line--);
            for (PlayerExt o : game.getPlayers()) {
                Player op = o.player();
                if (op == null) continue;
                PlayerState st = PlayerStateManager.get(game, op.getUniqueId());
                int rev = st == null ? 0 : st.getReviveCount();
                String tag = ClassSelectGate.hasChosen(game, op.getUniqueId()) ? "§a[已选]" : "§e[待选]";
                obj.getScore(tag + " §f" + op.getName() + " §e♥" + rev).setScore(line--);
            }
            p.setScoreboard(sb);
        }
    }
}
