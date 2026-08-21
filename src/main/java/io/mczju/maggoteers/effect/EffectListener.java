package io.mczju.maggoteers.effect;

import io.mczju.maggoteers.MaggoteersPlugin;
import io.mczju.maggoteers.game.MaggoteersGame;
import io.mczju.maggoteers.state.PlayerStateManager;
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

    /**
     * 启动 ON_TICK_1S 心跳（引用计数：多个对局共享同一个心跳任务）。
     * <p>本心跳只负责 {@link Trigger#ON_TICK_1S} 触发器分发（玩家触发型被动定时器）；
     * 与 {@link io.mczju.maggoteers.listener.GameplayTickListener}（光环/常驻药水/满腹维护）
     * 职责不同、枚举源不同（这里 {@code ActiveMaggoteersGames}，那边 {@code gamesWithState()}），
     * 有意分开，勿合并——合并会改变两者各自的遍历与时机语义。
     */
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
        MaggoteersGame g = PlayerStateManager.gameOf(killer);
        if (g == null) return;
        LivingEntity victim = e.getEntity();
        TriggerContext ctx = new TriggerContext(
                Trigger.ON_KILL, victim, null, victim.getLocation().clone());
        EffectService.fireTriggerPlayer(g, killer, Trigger.ON_KILL, ctx);
    }

    /** 玩家造成伤害 → ON_DAMAGE_DEALT（v1 近战 Player 仅）。 */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamageDealt(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player p)) return;
        MaggoteersGame g = PlayerStateManager.gameOf(p);
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
        MaggoteersGame g = PlayerStateManager.gameOf(p);
        if (g == null) return;
        // Guardian spike reflect + thorns DAMAGE_AREA would recurse without this.
        if (MagicDamageContext.shouldSuppressOnDamageTaken(g, p.getUniqueId())) return;
        var attacker = EnemyAttackResolver.resolveAttacker(e, g);
        if (attacker.isEmpty()) return;
        TriggerContext ctx = new TriggerContext(Trigger.ON_DAMAGE_TAKEN, null, attacker.get());
        EffectService.fireTriggerPlayer(g, p, Trigger.ON_DAMAGE_TAKEN, ctx);
    }

}
