package io.mczju.maggoteers.plan;

import io.mczju.maggoteers.config.CoeffCfg;
import io.mczju.maggoteers.config.ScalingConfig;

/**
 * 数值合成纯函数：最终倍率 = coeff × scaling（§7.1；affix 层已退役，改技能系统）。
 * 与 Bukkit 解耦，纯单测。
 */
public final class Compose {

    /** 一只怪的最终倍率（hp/dmg/speed/scale/follow_range）。 */
    public record MobScale(double hp, double dmg, double speed, double scale, double followRange) {}

    public static MobScale compose(CoeffCfg coeff, ScalingConfig.Scaling snap) {
        return new MobScale(
                coeff.hp() * snap.mobHp(), coeff.dmg() * snap.mobDamage(),
                coeff.speed() * snap.mobSpeed(),
                coeff.scale(), coeff.followRange());
    }

    private Compose() {}
}
