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
        planReady = false;
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

    private void runWhenReady(Runnable action) {
        new BukkitRunnable() {
            @Override public void run() { cancel(); action.run(); }
        }.runTask(MaggoteersPlugin.getInstance());
    }
}
