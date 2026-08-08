package io.mczju.maggoteers.effect;

import com.github.mczjuops.mczjugamecore.game.AbstractGame;
import com.github.mczjuops.mczjugamecore.player.PlayerExt;
import io.mczju.maggoteers.MaggoteersPlugin;
import io.mczju.maggoteers.game.MaggoteersGame;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.scheduler.BukkitTask;

/** 战斗 trigger 监听 + ON_TICK_1S 心跳。lifecycle/player trigger 在各 fire-point 直接调 EffectService。 */
public final class EffectListener implements Listener {

    private static BukkitTask tickTask;
    private static int tickRef = 0;

    /** 启动 ON_TICK_1S 心跳（引用计数：多个对局共享同一个心跳任务）。 */
    public static void startTick() {
        if (++tickRef > 1) return;          // 已有心跳，只增引用计数
        tickTask = Bukkit.getScheduler().runTaskTimer(MaggoteersPlugin.getInstance(), () -> {
            io.mczju.maggoteers.game.ActiveMaggoteersGames.forEach(mg ->
                    EffectService.fireTrigger(mg, Trigger.ON_TICK_1S, 1));
        }, 20L, 20L);   // 每秒
    }

    public static void stopTick() {
        if (tickRef <= 0) return;
        if (--tickRef > 0) return;          // 还有对局在用
        if (tickTask != null) { tickTask.cancel(); tickTask = null; }
    }

    /** 玩家击杀实体 → ON_KILL（玩家方）。 */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onKill(EntityDeathEvent e) {
        Player killer = e.getEntity().getKiller();
        if (killer == null) return;
        MaggoteersGame g = gameOf(killer);
        if (g == null) return;
        LivingEntity victim = e.getEntity();
        TriggerContext ctx = new TriggerContext(Trigger.ON_KILL, victim, null);
        EffectService.fireTriggerPlayer(g, killer, Trigger.ON_KILL, ctx);
    }

    /** 玩家造成伤害 → ON_DAMAGE_DEALT（v1 近战 Player 仅）。 */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamageDealt(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player p)) return;
        MaggoteersGame g = gameOf(p);
        if (g == null) return;
        if (MagicDamageContext.shouldSuppressOnDamageDealt(g, p.getUniqueId())) return;
        Entity damager = e.getDamager();
        if (SummonRegistry.isTrackedSummon(damager) || SummonRegistry.isTrackedProjectile(damager)) return;
        LivingEntity victim = e.getEntity() instanceof LivingEntity le ? le : null;
        TriggerContext ctx = new TriggerContext(Trigger.ON_DAMAGE_DEALT, victim, null);
        EffectService.fireTriggerPlayer(g, p, Trigger.ON_DAMAGE_DEALT, ctx);
    }

    /** 玩家受敌人攻击 → ON_DAMAGE_TAKEN（环境/友军伤害不触发）。 */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamageTaken(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        MaggoteersGame g = gameOf(p);
        if (g == null) return;
        var attacker = EnemyAttackResolver.resolveAttacker(e, g);
        if (attacker.isEmpty()) return;
        TriggerContext ctx = new TriggerContext(Trigger.ON_DAMAGE_TAKEN, null, attacker.get());
        EffectService.fireTriggerPlayer(g, p, Trigger.ON_DAMAGE_TAKEN, ctx);
    }

    private static MaggoteersGame gameOf(Player p) {
        PlayerExt pe = new PlayerExt(p);
        if (!pe.isInGame()) return null;
        AbstractGame g = pe.getGame();
        return g instanceof MaggoteersGame mg ? mg : null;
    }
}
