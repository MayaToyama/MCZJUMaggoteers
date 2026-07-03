package io.mczju.maggoteers.listener;

import com.github.mczjuops.mczjugamecore.player.PlayerExt;
import io.mczju.maggoteers.game.MaggoteersGame;
import io.mczju.maggoteers.state.PlayerStateManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;

import java.util.Set;

/**
 * D1 净化 fallback：若"0s 255 级抵消"技巧不成立，按 DamageCause cancel（免疫 wither/poison 等）。
 * <p>对局内玩家若持有效果 id 形如 {@code immunity_<cause>} 的常驻效果，则对应 cause 伤害被取消。
 * Plan 6 先做 POISON/WITHER 两个示例；实现期实测 D1 技巧后，若技巧成立则可移除本监听。
 */
public final class PurifyListener implements Listener {
    private static final Set<EntityDamageEvent.DamageCause> PURIFIABLE =
            Set.of(EntityDamageEvent.DamageCause.POISON, EntityDamageEvent.DamageCause.WITHER);

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        if (!PURIFIABLE.contains(e.getCause())) return;
        var pe = new PlayerExt(p);
        if (!pe.isInGame() || !(pe.getGame() instanceof MaggoteersGame mg)) return;
        var ps = PlayerStateManager.get(mg, p.getUniqueId());
        if (ps == null) return;
        // 持有"免疫该 cause"的常驻效果则取消（效果 id 约定：immunity_<cause 小写>）
        String need = "immunity_" + e.getCause().name().toLowerCase();
        if (ps.effects().stream().anyMatch(x -> x.isPermanent() && x.id().equals(need))) {
            e.setCancelled(true);
        }
    }
}
