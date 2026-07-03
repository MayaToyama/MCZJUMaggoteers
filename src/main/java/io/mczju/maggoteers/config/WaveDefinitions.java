package io.mczju.maggoteers.config;

import io.mczju.maggoteers.config.WavesConfig.PoolEntry;
import java.util.List;
import java.util.Map;

/** waves.yml 的纯数据视图（strategies + pools），供 RunPlanner 纯函数消费、单测构造。 */
public record WaveDefinitions(
        Map<String, SpawnStrategyCfg> strategies,
        Map<String, Map<String, List<PoolEntry>>> pools) {
    public WaveDefinitions {
        strategies = Map.copyOf(strategies);
        pools = Map.copyOf(pools);
    }

    public SpawnStrategyCfg strategy(String id) { return strategies.get(id); }

    public List<PoolEntry> pool(String act, String tier) {
        return pools.getOrDefault(act, Map.of()).getOrDefault(tier, List.of());
    }
}
