package io.mczju.maggoteers.world;

import io.mczju.maggoteers.config.MapPoints;
import io.mczju.maggoteers.wave.Vec3;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.*;

/** 读 plugins/Maggoteers/maps/actN/<mapId>/ 的 points.yml + 4 象限 nbt 清单。 */
public final class MapRepository {

    public record MapEntry(String mapId, MapPoints points, Set<String> nbtFiles, File dir) {
        public boolean hasNbt(String quad) { return nbtFiles.contains(quad + ".nbt"); }
    }

    private static final Map<String, List<MapEntry>> BY_ACT = new HashMap<>();
    private static final Random RNG = new Random();

    public static void load(JavaPlugin plugin) {
        BY_ACT.clear();
        File root = new File(plugin.getDataFolder(), "maps");
        for (String act : List.of("act1", "act2", "act3")) {
            String mapId = switch (act) {
                case "act1" -> "ruined_keep";
                case "act2" -> "frozen_halls";
                default -> "obsidian_spire";
            };
            String path = "maps/" + act + "/" + mapId + "/points.yml";
            plugin.saveResource(path, false);
            File actDir = new File(root, act);
            if (!actDir.isDirectory()) continue;
            List<MapEntry> entries = new ArrayList<>();
            File[] mapDirs = actDir.listFiles(File::isDirectory);
            if (mapDirs == null) continue;
            for (File mapDir : mapDirs) {
                File pf = new File(mapDir, "points.yml");
                if (!pf.isFile()) continue;
                var cfg = YamlConfiguration.loadConfiguration(pf);
                MapPoints points = parsePoints(cfg);
                Set<String> nbts = new HashSet<>();
                for (String q : List.of("nw", "sw", "ne", "se")) {
                    if (new File(mapDir, q + ".nbt").isFile()) nbts.add(q + ".nbt");
                }
                entries.add(new MapEntry(mapDir.getName(), points, nbts, mapDir));
            }
            BY_ACT.put(act, entries);
        }
        plugin.getLogger().info("MapRepository 已加载：" + BY_ACT);
    }

    private static MapPoints parsePoints(YamlConfiguration cfg) {
        var ps = cfg.getConfigurationSection("playerSpawn");
        Vec3 spawn = ps == null ? new Vec3(0.5, 65, 0.5)
                : new Vec3(ps.getDouble("x"), ps.getDouble("y"), ps.getDouble("z"));
        Map<String, Vec3> pts = new HashMap<>();
        var sp = cfg.getConfigurationSection("spawnPoints");
        if (sp != null) {
            for (String id : sp.getKeys(false)) {
                var s = sp.getConfigurationSection(id);
                if (s != null) pts.put(id, new Vec3(s.getDouble("x"), s.getDouble("y"), s.getDouble("z")));
            }
        }
        return new MapPoints(spawn, pts);
    }

    public static List<MapEntry> getMaps(String act) { return BY_ACT.getOrDefault(act, List.of()); }

    /** 随机抽一张图（Plan 3 起改由种子 RNG）。无图返回 null。 */
    public static MapEntry pickRandom(String act) {
        List<MapEntry> list = getMaps(act);
        return list.isEmpty() ? null : list.get(RNG.nextInt(list.size()));
    }

    private MapRepository() {}
}
