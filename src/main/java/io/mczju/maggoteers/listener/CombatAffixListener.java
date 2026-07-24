package io.mczju.maggoteers.listener;

import com.github.mczjuops.mczjugamecore.player.PlayerExt;
import io.mczju.maggoteers.config.Affix;
import io.mczju.maggoteers.config.AffixService;
import io.mczju.maggoteers.game.MaggoteersGame;
import io.mczju.maggoteers.wave.PotionSpec;
import io.mczju.maggoteers.wave.SpawnStep;
import io.mczju.maggoteers.wave.WaveEngine;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public final class CombatAffixListener implements Listener {

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMobDamagePlayer(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player victim)) return;
        SpawnStep step = WaveEngine.entityStep(e.getDamager().getUniqueId());
        if (step == null) return;
        var pe = new PlayerExt(victim);
        if (!pe.isInGame() || !(pe.getGame() instanceof MaggoteersGame)) return;

        var affixSvc = AffixService.getInstance();
        for (String affixId : step.affixes()) {
            Affix a = affixSvc.get(affixId);
            if (a == null || !"hit-player".equals(a.on())) continue;
            for (PotionSpec ps : a.potions()) {
                PotionEffectType type = io.mczju.maggoteers.util.GameRegistries.potionEffect(ps.effect());
                if (type != null) victim.addPotionEffect(new PotionEffect(type, ps.dur(), ps.amp()));
            }
        }
    }
}
