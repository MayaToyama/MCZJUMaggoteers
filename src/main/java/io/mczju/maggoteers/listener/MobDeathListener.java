package io.mczju.maggoteers.listener;

import io.mczju.maggoteers.wave.WaveEngine;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;

/**
 * 怪物死亡监听（借鉴前代 VampireSurvivor MobDeathListener）。
 * <p>处理两类：{@link EntityDeathEvent}（普通死）与 {@link EntityExplodeEvent}（苦力怕等，可能先于 Death 触发）。
 * 两者都转发 {@link WaveEngine#handleMobDeath}；WaveEngine 内部幂等（BY_ENTITY remove 后第二次返回 false）。
 */
public class MobDeathListener implements Listener {

    @EventHandler
    public void onDeath(EntityDeathEvent e) {
        if (WaveEngine.handleMobDeath(e.getEntity().getUniqueId(), e.getEntity().getLocation())) {
            e.getDrops().clear();
        }
    }

    @EventHandler
    public void onExplode(EntityExplodeEvent e) {
        WaveEngine.handleMobDeath(e.getEntity().getUniqueId(), e.getEntity().getLocation());
        e.setYield(0f);
        e.blockList().clear();
    }
}
