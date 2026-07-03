package io.mczju.maggoteers.config;

import java.util.List;

/** waves.yml 一条 strategy。 */
public record SpawnStrategyCfg(String id, List<StepCfg> steps, int repeat, List<RewardItemCfg> clearReward) {
    public SpawnStrategyCfg {
        steps = List.copyOf(steps);
        clearReward = List.copyOf(clearReward);
    }
}
