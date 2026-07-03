package io.mczju.maggoteers.config;

import org.bukkit.entity.EntityType;

/** waves.yml 一条 step（未解析：point 是 id 字符串，坐标待 MapRepository 解析）。 */
public record StepCfg(String point, EntityType type, int count, CoeffCfg coeff, int delaySec) {}
