package io.mczju.maggoteers.plan;

import io.mczju.maggoteers.wave.Vec3;
import io.mczju.maggoteers.wave.WaveSpec;
import java.util.List;

/** 一层的已解析剧本：地图 id + 玩家出生点（绝对）+ 有序波次（weak×M、strong×N、boss×1）。 */
public record ActPlan(String mapId, Vec3 playerSpawn, List<WaveSpec> waves) {
    public ActPlan {
        waves = List.copyOf(waves);
    }
}
