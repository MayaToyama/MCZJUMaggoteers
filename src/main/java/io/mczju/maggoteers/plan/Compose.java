package io.mczju.maggoteers.plan;

import io.mczju.maggoteers.config.Affix;
import io.mczju.maggoteers.config.CoeffCfg;
import io.mczju.maggoteers.config.ScalingConfig;

import java.util.List;

/**
 * 数值合成纯函数：最终倍率 = coeff × Π(affix) × scaling（三层叠加，§7.1）。
 * 与 Bukkit 解耦，纯单测。
 */
public final class Compose {

    /** 一只怪的最终倍率四元组（hp/dmg/speed/drop）。 */
    public record MobScale(double hp, double dmg, double speed, double drop) {}

    public static MobScale compose(CoeffCfg coeff, List<Affix> affixes, ScalingConfig.Scaling snap) {
        double hp = coeff.hp(), dmg = coeff.dmg(), speed = coeff.speed(), drop = 1.0;
        for (Affix a : affixes) {
            hp *= a.hp();
            dmg *= a.dmg();
            speed *= a.speed();
            drop *= a.drop();
        }
        return new MobScale(hp * snap.mobHp(), dmg * snap.mobDamage(), speed * snap.mobSpeed(), drop * snap.currencyDrop());
    }

    private Compose() {}
}
