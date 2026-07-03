package io.mczju.maggoteers.mob;

import io.mczju.maggoteers.wave.SpawnStep;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;

import java.util.logging.Logger;

/**
 * 在指定世界生成一只原版怪，套 {@link SpawnStep} 的最终倍率（hpMult/dmgMult/speedMult）。
 * 借鉴前代 VampireSurvivor WaveManager.spawnMob/applyAttributes。Plan 2：无词缀药水/装备（默认空）。
 */
public final class MobFactory {
    private static final Logger LOG = Logger.getLogger("Maggoteers");

    public static LivingEntity spawn(Location loc, SpawnStep step) {
        if (loc.getWorld() == null) return null;
        try {
            var raw = loc.getWorld().spawnEntity(loc, step.type());
            if (!(raw instanceof LivingEntity le)) { raw.remove(); return null; }
            le.setRemoveWhenFarAway(false);
            applyMultipliers(le, step.hpMult(), step.dmgMult(), step.speedMult());
            applyName(le, step);
            if (le instanceof Mob m) m.setAware(true);
            return le;
        } catch (Exception e) {
            LOG.warning("生成怪物失败 " + step.type() + "：" + e.getMessage());
            return null;
        }
    }

    /** hpMult/dmgMult/speedMult 直接乘到基础属性（Plan 3：这些值已是 coeff×affix×scaling）。 */
    private static void applyMultipliers(LivingEntity le, double hpMult, double dmgMult, double spdMult) {
        AttributeInstance hp = le.getAttribute(Attribute.MAX_HEALTH);
        if (hp != null) {
            double scaled = hp.getBaseValue() * Math.max(0.0001, hpMult);
            hp.setBaseValue(scaled);
            le.setHealth(Math.min(scaled, 1024.0));
        }
        AttributeInstance dmg = le.getAttribute(Attribute.ATTACK_DAMAGE);
        if (dmg != null) dmg.setBaseValue(dmg.getBaseValue() * Math.max(0.0001, dmgMult));
        AttributeInstance spd = le.getAttribute(Attribute.MOVEMENT_SPEED);
        if (spd != null) spd.setBaseValue(spd.getBaseValue() * Math.max(0.0001, spdMult));
    }

    private static void applyName(LivingEntity le, SpawnStep step) {
        if (!step.affixes().isEmpty()) {
            le.customName(Component.text(String.join(" ", step.affixes())));
            le.setCustomNameVisible(true);
        }
    }

    private MobFactory() {}
}
