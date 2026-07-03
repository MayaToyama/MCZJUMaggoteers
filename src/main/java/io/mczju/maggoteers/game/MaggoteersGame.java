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
