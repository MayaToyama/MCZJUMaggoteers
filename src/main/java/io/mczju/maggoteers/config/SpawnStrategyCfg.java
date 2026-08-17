package io.mczju.maggoteers.config;

import java.util.List;

/** waves.yml 一条 strategy。 */
public record SpawnStrategyCfg(
        String id,
        String displayName,
        List<StepCfg> steps,
        int repeat,
        List<RewardItemCfg> clearReward
) {
    public SpawnStrategyCfg {
        displayName = displayName == null ? "" : displayName;
        steps = List.copyOf(steps);
        clearReward = List.copyOf(clearReward);
    }

    /** 关卡显示名；缺省回落 strategy id。 */
    public String displayNameOrId() {
        return displayName.isBlank() ? id : displayName;
    }
}
