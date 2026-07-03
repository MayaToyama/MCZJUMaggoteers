package io.mczju.maggoteers.config;

import org.bukkit.entity.EntityType;
import java.util.List;

/** waves.yml 一条 step（未解析）。affixes 为词缀 id 列表（Plan 3 起 RunPlanner 解析）。 */
public record StepCfg(String point, EntityType type, int count, CoeffCfg coeff,
                      int delaySec, List<String> affixes) {
    public StepCfg {
        affixes = affixes == null ? List.of() : List.copyOf(affixes);
    }
}
