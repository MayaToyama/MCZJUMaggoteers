package io.mczju.maggoteers.item.fx;

import io.mczju.maggoteers.MaggoteersPlugin;
import io.mczju.maggoteers.util.ParticleEffects;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

/** 按 {@link MagicUseFx} 播放声效与粒子预设。 */
public final class MagicFxService {
    private MagicFxService() {}

    public static void play(Player player, MagicUseFx fx) {
        if (fx == null || player == null) return;
        Location at = player.getLocation();
        if (fx.sound() != null) {
            player.playSound(at, fx.sound(), fx.soundVolume(), fx.soundPitch());
        }
        MagicFxPresets.play(player, fx);
    }

    public static void playOnEntity(Entity entity, MagicUseFx fx) {
        if (entity instanceof Player p) play(p, fx);
        else if (entity != null && fx != null) {
            MagicFxPresets.dotAbove(entity.getLocation(), fx);
        }
    }
}
