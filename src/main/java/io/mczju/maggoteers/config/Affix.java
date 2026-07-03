package io.mczju.maggoteers.config;

import io.mczju.maggoteers.wave.PotionSpec;
import java.util.List;

/**
 * 词缀：挂在原版实体上的强化层（纯配置）。缺省倍率 1.0。
 */
public record Affix(String id, double hp, double dmg, double speed, double drop,
                    List<PotionSpec> potions, String on, String display) {
    public Affix {
        if (hp <= 0) hp = 1;
        if (dmg <= 0) dmg = 1;
        if (speed <= 0) speed = 1;
        if (drop <= 0) drop = 1;
        potions = potions == null ? List.of() : List.copyOf(potions);
    }
}
