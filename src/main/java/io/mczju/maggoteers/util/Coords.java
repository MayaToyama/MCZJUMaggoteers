package io.mczju.maggoteers.util;

import io.mczju.maggoteers.wave.Vec3;

/** 坐标计算纯函数（与 Bukkit 解耦，便于单测）。 */
public final class Coords {
    private Coords() {}

    /** 绝对坐标 = 层原点 + 图内相对坐标（铁律①：绝对坐标永不入配置）。 */
    public static Vec3 resolve(Vec3 origin, Vec3 relative) {
        return origin.add(relative);
    }
}
