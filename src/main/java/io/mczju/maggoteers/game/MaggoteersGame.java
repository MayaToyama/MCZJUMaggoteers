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
