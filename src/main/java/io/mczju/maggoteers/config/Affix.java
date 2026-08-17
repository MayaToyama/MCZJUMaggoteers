package io.mczju.maggoteers.config;

import io.mczju.maggoteers.wave.PotionSpec;

import java.util.List;

/**
 * 词缀：挂在原版实体上的强化层（纯配置）。缺省倍率 1.0。
 */
public record Affix(String id, double hp, double dmg, double speed,
                    double scale, double followRange,
                    List<PotionSpec> potions, String display) {
    public Affix(String id, double hp, double dmg, double speed,
                 List<PotionSpec> potions, String display) {
        this(id, hp, dmg, speed, 1.0, 1.0, potions, display);
    }

    public Affix {
        if (hp <= 0) hp = 1;
        if (dmg <= 0) dmg = 1;
        if (speed <= 0) speed = 1;
        if (scale <= 0) scale = 1;
        if (followRange <= 0) followRange = 1;
        potions = potions == null ? List.of() : List.copyOf(potions);
    }
}
