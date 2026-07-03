package io.mczju.maggoteers.plan;

import io.mczju.maggoteers.wave.Vec3;
import java.util.Map;

/**
 * RunPlanner 的纯配置快照：层原点（来自 act_origins）+ 每层 weak/strong 波数（来自 waves_per_act）。
 */
public record RunConfig(Map<String, Vec3> origins, Map<String, int[]> wavesPerAct) {
    public Vec3 origin(String act) { return origins.getOrDefault(act, new Vec3(0, 64, 0)); }
    public int weak(String act)    { return wavesPerAct.getOrDefault(act, new int[]{3, 2})[0]; }
    public int strong(String act)  { return wavesPerAct.getOrDefault(act, new int[]{3, 2})[1]; }
}
