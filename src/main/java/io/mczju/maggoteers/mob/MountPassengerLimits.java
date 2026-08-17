package io.mczju.maggoteers.mob;

import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.Camel;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.MagmaCube;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Slime;
import org.bukkit.entity.Strider;

/** 坐骑族：直接乘客上限与 spawn 后处理。 */
public final class MountPassengerLimits {

    private MountPassengerLimits() {}

    public static int directPassengerLimit(LivingEntity carrier) {
        if (carrier instanceof Camel) return 2;
        if (carrier instanceof AbstractHorse) return 1;
        if (carrier instanceof Strider) return 1;
        return 1;
    }

    public static void prepareMount(LivingEntity le) {
        if (le instanceof Mob m) {
            m.setAware(true);
            le.setRemoveWhenFarAway(false);
        }
        if (le instanceof AbstractHorse horse) {
            horse.setAdult();
            horse.setTamed(true);
        }
        if (le instanceof Slime slime && !(le instanceof MagmaCube)) {
            if (slime.getSize() < 2) slime.setSize(2);
        }
        if (le instanceof MagmaCube cube && cube.getSize() < 2) {
            cube.setSize(2);
        }
    }
}
