package io.mczju.maggoteers.listener;

import com.github.mczjuops.mczjugamecore.game.AbstractGame;
import io.mczju.maggoteers.MaggoteersPlugin;
import io.mczju.maggoteers.effect.AuraService;
import io.mczju.maggoteers.effect.EffectService;
import io.mczju.maggoteers.game.MaggoteersGame;
import io.mczju.maggoteers.state.PlayerStateManager;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.scheduler.BukkitTask;

import java.util.UUID;

/**
 * 全局游戏玩法维护心跳：每秒对所有活跃对局中存活的玩家执行：
 * <ul>
 *   <li>{@link io.mczju.maggoteers.effect.AuraService#refresh} — 团队/对敌光环派生与 carrier 粒子</li>
 *   <li>刷新常驻药水（EffectService.refreshPermanentPotions）—— 修复 QA #1 药水过期问题</li>
 *   <li>维持满腹—— 修复 QA #3 饥饿消耗问题（NATURAL_REGENERATION=false 阻止回血）</li>
 * </ul>
 * <p>全局单任务（非 per-game），在 onEnable 启动、onDisable 停止。
 */
public final class GameplayTickListener implements Listener {

    private static BukkitTask task;

    /** 启动全局心跳（幂等）。 */
    public static void start() {
        if (task != null) return;
        task = org.bukkit.Bukkit.getScheduler().runTaskTimer(MaggoteersPlugin.getInstance(), () -> {
            for (AbstractGame g : PlayerStateManager.gamesWithState()) {
                if (!(g instanceof MaggoteersGame mg)) continue;
                AuraService.refresh(mg);
                for (UUID uuid : PlayerStateManager.uuidsInGame(mg)) {
                    Player p = org.bukkit.Bukkit.getPlayer(uuid);
                    if (p == null) continue;
                    var ps = PlayerStateManager.get(mg, uuid);
                    if (ps == null || !ps.isAlive()) continue;
                    // QA #1: 刷新常驻药水（每秒重施加 30s，永不过期）
                    EffectService.refreshPermanentPotions(p);
                    // QA #3: 维持满腹（饥饿不消耗，NATURAL_REGENERATION=false 已阻止回血）
                    p.setFoodLevel(20);
                    p.setSaturation(5f);
                    p.setExhaustion(0f);
                }
            }
        }, 20L, 20L);
    }

    /** 停止全局心跳。 */
    public static void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    public GameplayTickListener() {}
}
