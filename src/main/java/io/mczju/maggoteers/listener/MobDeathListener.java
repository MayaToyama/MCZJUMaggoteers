package io.mczju.maggoteers.listener;

import io.mczju.maggoteers.integration.InfernalMobsBridge;
import io.mczju.maggoteers.wave.WaveEngine;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.ExplosionPrimeEvent;
import org.bukkit.event.entity.SlimeSplitEvent;
import org.bukkit.potion.PotionEffect;

import java.util.ArrayList;

/**
 * 怪物死亡监听（借鉴前代 VampireSurvivor MobDeathListener）。
 * <p>处理两类：{@link EntityDeathEvent}（普通死）与 {@link EntityExplodeEvent}（苦力怕等，可能先于 Death 触发）。
 * 两者都转发 {@link WaveEngine#handleMobDeath}；WaveEngine 内部幂等（BY_ENTITY remove 后第二次返回 false）。
 * <p>局内史莱姆/岩浆怪取消分裂，避免 livingMobs 被原版分裂打乱。
 */
public class MobDeathListener implements Listener {

    /**
     * 苦力怕等爆炸前清掉追踪怪身上的药水。原版会把存活药水打成 AreaEffectCloud；
     * affix 常用极大 dur（隐身/致盲/凋零），玩家踩到会挂数小时 buff。
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void beforeTrackedExplode(ExplosionPrimeEvent e) {
        if (!(e.getEntity() instanceof LivingEntity le)) return;
        if (!WaveEngine.isTracked(le.getUniqueId())) return;
        for (PotionEffect pe : new ArrayList<>(le.getActivePotionEffects())) {
            le.removePotionEffect(pe.getType());
        }
    }

    /** 在 IM NORMAL 死亡监听器之前注销，抑制 IM 战利品与死亡技能。 */
    @EventHandler(priority = EventPriority.LOWEST)
    public void beforeInfernalDeath(EntityDeathEvent event) {
        var uuid = event.getEntity().getUniqueId();
        if (WaveEngine.isTracked(uuid) && InfernalMobsBridge.isManaged(uuid)) {
            InfernalMobsBridge.unregisterManaged(uuid);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(EntityDeathEvent e) {
        if (WaveEngine.handleMobDeath(e.getEntity().getUniqueId(), e.getEntity().getLocation())) {
            e.getDrops().clear();
            e.setDroppedExp(0);
        }
    }

    @EventHandler
    public void onExplode(EntityExplodeEvent e) {
        WaveEngine.handleMobDeath(e.getEntity().getUniqueId(), e.getEntity().getLocation());
        e.setYield(0f);
        e.blockList().clear();
    }

    @EventHandler
    public void onSlimeSplit(SlimeSplitEvent e) {
        // EntityDeathEvent 里已 untrack；靠 handleMobDeath 预先 mark + 此处消费
        if (WaveEngine.isTracked(e.getEntity().getUniqueId())
                || WaveEngine.consumeSplitCancel(e.getEntity().getUniqueId())) {
            e.setCancelled(true);
        }
    }
}
