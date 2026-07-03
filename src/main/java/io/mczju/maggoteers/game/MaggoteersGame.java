package io.mczju.maggoteers.game;

import com.github.mczjuops.mczjugamecore.MCZJUGameCore;
import com.github.mczjuops.mczjugamecore.game.AbstractGame;
import com.github.mczjuops.mczjugamecore.game.GameMeta;
import com.github.mczjuops.mczjugamecore.game.strategy.wait.DefaultGameWaitStrategy;
import com.github.mczjuops.mczjugamecore.game.strategy.wait.GameWaitStrategy;
import com.github.mczjuops.mczjugamecore.menu.MenuFacade;
import com.github.mczjuops.mczjugamecore.player.PlayerExt;
import com.github.mczjuops.mczjugamecore.player.strategy.AbstractPlayerDeathStrategy;
import io.mczju.maggoteers.MaggoteersPlugin;
import io.mczju.maggoteers.item.ItemService;
import io.mczju.maggoteers.plan.ActPlan;
import io.mczju.maggoteers.plan.RunPlanner;
import io.mczju.maggoteers.reward.RewardService;
import io.mczju.maggoteers.state.PlayerStateManager;
import io.mczju.maggoteers.ui.RunScoreboard;
import io.mczju.maggoteers.wave.WaveScheduler;
import io.mczju.maggoteers.world.WorldService;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;
import java.util.Random;

public class MaggoteersGame extends AbstractGame {

    private volatile boolean planReady = false;
    private volatile List<ActPlan> plannedActs;
    private volatile String planError;

    private GameOutcome outcome = GameOutcome.IN_PROGRESS;
    private volatile boolean wavesStarted = false;

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
    public AbstractPlayerDeathStrategy getPlayerDeathStrategy() {
        return new MaggoteersDeathStrategy(this);
    }

    public GameOutcome getOutcome() { return outcome; }
    public void setOutcome(GameOutcome o) { this.outcome = o; }

    public void win() {
        if (outcome != GameOutcome.IN_PROGRESS) return;
        outcome = GameOutcome.WIN;
        MCZJUGameCore.getGameManager().endGame(this);
    }

    public void fail() {
        if (outcome != GameOutcome.IN_PROGRESS) return;
        outcome = GameOutcome.FAIL;
        MCZJUGameCore.getGameManager().endGame(this);
    }

    @Override
    protected boolean onGameInit() {
        planReady = false;
        wavesStarted = false;
        return true;
    }

    @Override
    protected void onGameStart() {
        runWhenReady(this::startInWorld);
    }

    private void startInWorld() {
        World w = WorldService.create(this);
        if (w == null) { sender().error("世界创建失败，对局终止。"); return; }

        int lives = MaggoteersPlugin.getInstance().getConfig().getInt("lives.default", 2);
        ClassSelectGate.reset(this);
        for (PlayerExt pe : getPlayers()) {
            pe.switchProfile(this.getId());
            pe.player().setGameMode(GameMode.ADVENTURE);
            pe.player().getInventory().clear();
            PlayerStateManager.init(this, pe.player().getUniqueId(), lives);
            ItemService.giveInitialEquipment(pe.player());
        }

        final long seed = WorldService.getSeed(this);
        final int players = getPlayers().size();
        sender().info("<gray>正在生成本局剧本（异步）…");
        Bukkit.getScheduler().runTaskAsynchronously(MaggoteersPlugin.getInstance(), () -> {
            try {
                plannedActs = RunPlanner.plan(seed, players);
            } catch (Exception e) {
                planError = e.getMessage();
                MaggoteersPlugin.getInstance().getLogger().severe("RunPlanner 失败：" + e.getMessage());
            } finally {
                planReady = true;
            }
        });
        new BukkitRunnable() {
            int ticks = 0;
            @Override public void run() {
                if (planReady) { cancel(); beginClassSelect(); }
                else if (++ticks > 20 * 30) { cancel(); sender().warn("剧本生成超时(30s)，终止。"); }
            }
        }.runTaskTimer(MaggoteersPlugin.getInstance(), 1L, 1L);
    }

    private void beginClassSelect() {
        if (plannedActs == null) {
            sender().error("剧本生成失败" + (planError != null ? "：" + planError : "") + "，对局终止。");
            fail();
            return;
        }
        sender().info("<yellow>请选择职业（全员选完或超时后开局）");
        for (PlayerExt pe : getPlayers()) {
            MenuFacade.open("maggoteers-class", pe.player(), this);
        }
        int timeout = MaggoteersPlugin.getInstance().getConfig().getInt("class_select.timeout_sec", 45);
        new BukkitRunnable() {
            int left = timeout;
            @Override public void run() {
                if (outcome != GameOutcome.IN_PROGRESS) { cancel(); return; }
                if (ClassSelectGate.allChosen(MaggoteersGame.this)) { cancel(); onAllClassesChosen(); return; }
                if (--left <= 0) {
                    cancel();
                    var pool = RewardService.pool("class");
                    ClassSelectGate.autoPickRemaining(MaggoteersGame.this,
                            pool == null ? List.of() : pool.options(), new Random());
                    onAllClassesChosen();
                }
            }
        }.runTaskTimer(MaggoteersPlugin.getInstance(), 20L, 20L);
    }

    /** 全员职业选完（或超时兜底）后启动波次。 */
    public void onAllClassesChosen() {
        if (plannedActs == null || outcome != GameOutcome.IN_PROGRESS || wavesStarted) return;
        wavesStarted = true;
        RunScoreboard.start(this);
        WaveScheduler.start(this, plannedActs);
        sender().info("<green>卫戍协议 开局！三层共 "
                + plannedActs.stream().mapToInt(a -> a.waves().size()).sum() + " 波。每人复活 "
                + MaggoteersPlugin.getInstance().getConfig().getInt("lives.default", 2) + " 次。");
    }

    private void cleanupRun() {
        WaveScheduler.stop(this);
        RunScoreboard.stop(this);
        ClassSelectGate.clear(this);
        PlayerStateManager.destroyAll(this);
        WorldService.cleanup(this);
    }

    @Override protected void onGameCancel() { cleanupRun(); }
    @Override protected void onGameAbort()  { cleanupRun(); }
    @Override protected void onGameEnd()    { cleanupRun(); }

    private void runWhenReady(Runnable action) {
        new BukkitRunnable() {
            @Override public void run() { cancel(); action.run(); }
        }.runTask(MaggoteersPlugin.getInstance());
    }
}
