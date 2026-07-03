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
        planReady = true;
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
