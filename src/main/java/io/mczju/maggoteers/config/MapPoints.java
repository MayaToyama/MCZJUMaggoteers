package io.mczju.maggoteers.config;

import io.mczju.maggoteers.wave.Vec3;
import java.util.Map;

/** 单张图的 points.yml：玩家出生点（相对）+ 编号刷怪点（相对）。绝对坐标由 Coords 叠层原点。 */
public record MapPoints(Vec3 playerSpawn, Map<String, Vec3> spawnPoints) {
    public Vec3 point(String id) { return spawnPoints.get(id); }
}
