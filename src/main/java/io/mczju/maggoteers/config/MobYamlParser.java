package io.mczju.maggoteers.config;

import io.mczju.maggoteers.util.GameRegistries;
import io.mczju.maggoteers.wave.MobEquipment;
import org.bukkit.entity.EntityType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class MobYamlParser {
    private static final int MAX_NEST_DEPTH = 8;

    private MobYamlParser() {}

    public static CoeffCfg parseCoeff(Map<?, ?> m) {
        Map<?, ?> c = m.get("coeff") instanceof Map<?, ?> cm ? cm : Map.of();
        return new CoeffCfg(
                num(c.get("hp"), 1.0).doubleValue(),
                num(c.get("dmg"), 1.0).doubleValue(),
                num(c.get("speed"), 1.0).doubleValue(),
                num(c.get("scale"), 1.0).doubleValue(),
                num(c.get("follow_range"), 1.0).doubleValue());
    }

    public static List<String> parseAffixes(Map<?, ?> m) {
        List<String> affixes = new ArrayList<>();
        Object affRaw = m.get("affixes");
        if (affRaw instanceof List<?> al) {
            for (Object o : al) affixes.add(String.valueOf(o));
        }
        return affixes;
    }

    public static InfernalCfg parseInfernal(Map<?, ?> m) {
        if (!(m.get("infernal") instanceof Map<?, ?> raw)) return InfernalCfg.NONE;
        int level = num(raw.get("level"), 1).intValue();
        List<String> ids = new ArrayList<>();
        if (raw.get("affixes") instanceof List<?> list) {
            for (Object value : list) ids.add(String.valueOf(value));
        }
        try {
            return new InfernalCfg(level, ids);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("invalid infernal.level: " + level, e);
        }
    }

    public static List<MobEquipment> parseEquipment(Object raw) {
        if (!(raw instanceof List<?> list) || list.isEmpty()) return List.of();
        List<MobEquipment> out = new ArrayList<>();
        for (Object o : list) {
            if (!(o instanceof Map<?, ?> map)) continue;
            String slot = String.valueOf(map.get("slot"));
            String item = map.get("item") != null ? String.valueOf(map.get("item"))
                    : (map.get("item_id") != null ? String.valueOf(map.get("item_id")) : "");
            if (!item.isBlank()) out.add(new MobEquipment(slot, item));
        }
        return out;
    }

    public static List<PassengerCfg> parsePassengers(Object raw) {
        return parsePassengersDepth(raw, 1, "step");
    }

    public static List<DeathSpawnCfg> parseOnDeath(Object raw) {
        return parseOnDeathDepth(raw, 1, "on_death");
    }

    private static List<PassengerCfg> parsePassengersDepth(Object raw, int depth, String context) {
        if (!(raw instanceof List<?> list) || list.isEmpty()) return List.of();
        if (depth > MAX_NEST_DEPTH) {
            throw new IllegalStateException("passengers nest depth > " + MAX_NEST_DEPTH + " (" + context + ")");
        }
        List<PassengerCfg> out = new ArrayList<>();
        for (Object o : list) {
            if (!(o instanceof Map<?, ?> m)) continue;
            EntityType type = GameRegistries.entityType(String.valueOf(m.get("type")));
            int count = num(m.get("count"), 1).intValue();
            CoeffCfg coeff = parseCoeff(m);
            List<String> affixes = parseAffixes(m);
            InfernalCfg infernal = parseInfernal(m);
            List<MobEquipment> equipment = parseEquipment(m.get("equipment"));
            List<PassengerCfg> nested = parsePassengersDepth(m.get("passengers"), depth + 1, type.name());
            List<DeathSpawnCfg> onDeath = parseOnDeathDepth(m.get("on_death"), depth + 1, type.name());
            out.add(new PassengerCfg(type, count, coeff, affixes, infernal, equipment, onDeath, nested));
        }
        return out;
    }

    private static List<DeathSpawnCfg> parseOnDeathDepth(Object raw, int depth, String context) {
        if (!(raw instanceof List<?> list) || list.isEmpty()) return List.of();
        if (depth > MAX_NEST_DEPTH) {
            throw new IllegalStateException("on_death nest depth > " + MAX_NEST_DEPTH + " (" + context + ")");
        }
        List<DeathSpawnCfg> out = new ArrayList<>();
        for (Object o : list) {
            if (!(o instanceof Map<?, ?> m)) continue;
            EntityType type = GameRegistries.entityType(String.valueOf(m.get("type")));
            int count = num(m.get("count"), 1).intValue();
            CoeffCfg coeff = parseCoeff(m);
            List<String> affixes = parseAffixes(m);
            InfernalCfg infernal = parseInfernal(m);
            List<MobEquipment> equipment = parseEquipment(m.get("equipment"));
            List<DeathSpawnCfg> nested = parseOnDeathDepth(m.get("on_death"), depth + 1, type.name());
            out.add(new DeathSpawnCfg(type, count, coeff, affixes, infernal, equipment, nested));
        }
        return out;
    }

    private static Number num(Object v, Number fallback) {
        return v instanceof Number n ? n : fallback;
    }
}