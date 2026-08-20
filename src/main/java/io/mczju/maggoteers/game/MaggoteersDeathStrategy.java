package io.mczju.maggoteers.game;

import com.github.mczjuops.mczjugamecore.game.AbstractGame;
import com.github.mczjuops.mczjugamecore.player.PlayerExt;
import com.github.mczjuops.mczjugamecore.player.strategy.AbstractPlayerDeathStrategy;
import io.mczju.maggoteers.config.MessageService;
import io.mczju.maggoteers.effect.EffectService;
import io.mczju.maggoteers.effect.Trigger;
import io.mczju.maggoteers.effect.TriggerContext;
import io.mczju.maggoteers.state.PlayerState;
import io.mczju.maggoteers.state.PlayerStateManager;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 死亡策略（借鉴前代 VampireSurvivor SurvivorPlayerDeathStrategy，改 reviveCount 复活机制）。
 * <p>须在取消死亡后立刻拉离 0 血，并防重入：否则 ON_DEATH/resync 窗口内可连扣多次复活并秒 fail。
 */
public class MaggoteersDeathStrategy extends AbstractPlayerDeathStrategy {

    /** 正在处理死亡的玩家（防 PlayerDeathEvent 重入连扣 reviveCount）。 */
    private static final Set<UUID> HANDLING = ConcurrentHashMap.newKeySet();

    public MaggoteersDeathStrategy(AbstractGame game) {
        super(game);
    }

    @Override
    public void onPlayerDeath(PlayerExt victim, PlayerDeathEvent event) {
        Player p = victim.player();
        UUID uuid = p.getUniqueId();
        event.setCancelled(true);
        // 立刻离开 0 血，避免本 tick 内再次触发死亡
        stabilizeAlive(p);

        if (!HANDLING.add(uuid)) {
            MaggoteersPluginLog.info("DeathStrategy 忽略重入死亡: " + p.getName());
            return;
        }
        try {
            handleDeath(p, uuid);
        } finally {
            HANDLING.remove(uuid);
        }
    }

    private void handleDeath(Player p, UUID uuid) {
        MaggoteersGame mg = AllyTargeting.resolveForPlayer(p, game);
        if (mg == null && game instanceof MaggoteersGame g) mg = g;
        if (mg == null) {
            mg = PlayerStateManager.gameForPlayer(uuid);
        }

        PlayerState st = PlayerStateManager.getByUuid(uuid);
        if (st == null && mg != null) {
            st = PlayerStateManager.get(mg, uuid);
        }
        if (st == null) {
            PlayerStateManager.warnMissingState("DeathStrategy", game, uuid);
            p.setGameMode(GameMode.ADVENTURE);
            PlayerStateManager.restoreFullHealth(p);
            MaggoteersPluginLog.warn("死亡时无 PlayerState，已强制复活: " + p.getName());
            if (mg != null) {
                mg.sender().warn(MessageService.raw("death.missing_state", Map.of("player", p.getName())));
            }
            return;
        }

        if (mg == null) {
            PlayerStateManager.restoreFullHealth(p);
            MaggoteersPluginLog.warn("死亡时无法解析 MaggoteersGame，已强制复活: " + p.getName());
            return;
        }

        Location deathLoc = p.getLocation().clone();
        MaggoteersPluginLog.info("DeathStrategy: " + p.getName()
                + " reviveCount=" + st.getReviveCount()
                + " alive=" + st.isAlive()
                + " gameIdentity=" + System.identityHashCode(mg));

        try {
            EffectService.fireTriggerPlayer(mg, p, Trigger.ON_DEATH,
                    TriggerContext.atEvent(Trigger.ON_DEATH, deathLoc));
        } catch (RuntimeException ex) {
            MaggoteersPluginLog.warn("ON_DEATH 触发异常: " + ex.getMessage());
        }
        // 触发效果可能再次把血打空
        stabilizeAlive(p);

        if (st.tryAutoRevive()) {
            Location spawn = io.mczju.maggoteers.wave.WaveScheduler.currentSpawnLocation(mg);
            if (spawn == null) {
                spawn = deathLoc;
            }
            p.teleport(spawn);
            p.setGameMode(GameMode.ADVENTURE);
            stabilizeAlive(p);
            try {
                // 死亡已 cancel，原版不会清药水；先剥临时药水，再 resync 常驻（PlayerState 真相源）
                EffectService.clearAllActivePotions(p);
                EffectService.fireTriggerPlayer(mg, p, Trigger.ON_REVIVE,
                        TriggerContext.atEvent(Trigger.ON_REVIVE, deathLoc));
                EffectService.resync(p);
            } catch (RuntimeException ex) {
                MaggoteersPluginLog.warn("ON_REVIVE/resync 异常: " + ex.getMessage());
            } finally {
                PlayerStateManager.restoreFullHealth(p);
            }
            MaggoteersPluginLog.info("自动复活: " + p.getName()
                    + " 剩余 reviveCount=" + st.getReviveCount());
            mg.sender().info(MessageService.raw("death.auto_revive", Map.of(
                    "player", p.getName(),
                    "remaining", String.valueOf(st.getReviveCount()))));
            return;
        }

        st.markDown();
        io.mczju.maggoteers.effect.AuraService.stripAllFromSource(mg, uuid);
        stabilizeAlive(p);
        p.setGameMode(GameMode.SPECTATOR);
        MaggoteersPluginLog.info("转观察者: " + p.getName() + "（复活次数耗尽）");
        mg.sender().warn(MessageService.raw("death.to_spectator", Map.of("player", p.getName())));

        if (!PlayerStateManager.isAnyAlive(mg)) {
            MaggoteersPluginLog.info("全员倒下，markDefeated(): " + p.getName());
            mg.markDefeated();
        }
    }

    /** 取消死亡后立刻保证 hp≥1，堵住重入窗口。 */
    private static void stabilizeAlive(Player p) {
        if (p == null || !p.isOnline()) return;
        var maxHp = p.getAttribute(Attribute.MAX_HEALTH);
        double max = maxHp != null ? maxHp.getValue() : 20.0;
        if (max <= 0) max = 20.0;
        double hp = p.getHealth();
        if (hp < 1.0) {
            p.setHealth(Math.min(1.0, max));
        }
        PlayerStateManager.clearFallFire(p);
        p.setNoDamageTicks(Math.max(p.getNoDamageTicks(), 40));
    }

    /** 避免 DeathStrategy 直接依赖 MaggoteersPlugin 循环引用时的编译噪音。 */
    private static final class MaggoteersPluginLog {
        private static final java.util.logging.Logger LOG =
                java.util.logging.Logger.getLogger("Maggoteers");
        static void info(String msg) { LOG.info(msg); }
        static void warn(String msg) { LOG.warning(msg); }
    }
}
