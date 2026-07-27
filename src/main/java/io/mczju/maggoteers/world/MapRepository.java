package io.mczju.maggoteers.world;

import io.mczju.maggoteers.config.MapPoints;
import io.mczju.maggoteers.config.WavesConfig;
import io.mczju.maggoteers.wave.Vec3;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.*;

/** 读 plugins/Maggoteers/maps/actN/<mapId>/ 的 points.yml + structure.nbt 是否存在。 */
public final class MapRepository {

    public record MapEntry(String mapId, MapPoints points, boolean hasStructure, File dir,
                           Map<String, List<WavesConfig.PoolEntry>> specialWaves) {}

    private static final Map<String, List<MapEntry>> BY_ACT = new HashMap<>();
    private static final Random RNG = new Random();

    /** jar 内 bundled 地图：首次启动释放 points.yml / special_waves.yml / structure.nbt（若存在）。 */
    private static final Map<String, List<String>> BUNDLED_MAP_IDS = Map.of(
            "act1", List.of("jungle", "desert"),
            "act2", List.of("graveyard", "badlands"),
            "act3", List.of("mushrooms"));

    public static void load(JavaPlugin plugin) {
        BY_ACT.clear();
        File root = new File(plugin.getDataFolder(), "maps");
        for (var e : BUNDLED_MAP_IDS.entrySet()) {
            for (String mapId : e.getValue()) {
                ensureResource(plugin, "maps/" + e.getKey() + "/" + mapId + "/points.yml");
                ensureResource(plugin, "maps/" + e.getKey() + "/" + mapId + "/special_waves.yml");
                ensureResource(plugin, "maps/" + e.getKey() + "/" + mapId + "/structure.nbt");
            }
        }
        for (String act : List.of("act1", "act2", "act3")) {
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
                boolean hasStructure = new File(mapDir, "structure.nbt").isFile();
                entries.add(new MapEntry(mapDir.getName(), points, hasStructure, mapDir,
                        parseSpecialWaves(mapDir)));
            }
            BY_ACT.put(act, entries);
        }
        plugin.getLogger().info("MapRepository 已加载：" + BY_ACT);
    }

    private static Map<String, List<WavesConfig.PoolEntry>> parseSpecialWaves(File mapDir) {
        File f = new File(mapDir, "special_waves.yml");
        if (!f.isFile()) return Map.of();
        var cfg = YamlConfiguration.loadConfiguration(f);
        Map<String, List<WavesConfig.PoolEntry>> out = new HashMap<>();
        for (String tier : List.of("weak", "strong", "boss")) {
            var list = cfg.getMapList(tier);
            if (list.isEmpty()) continue;
            List<WavesConfig.PoolEntry> entries = new ArrayList<>();
            for (var m : list) {
                entries.add(new WavesConfig.PoolEntry((String) m.get("strategy"),
                        num(m.get("weight"), 1).doubleValue()));
            }
            out.put(tier, entries);
        }
        return out;
    }

    private static Number num(Object v, Number fallback) {
        return v instanceof Number n ? n : fallback;
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

    public static MapLibrary library() {
        return new MapLibrary(BY_ACT);
    }

    /** 随机抽一张图（Plan 3 起改由种子 RNG）。无图返回 null。 */
    public static MapEntry pickRandom(String act) {
        List<MapEntry> list = getMaps(act);
        return list.isEmpty() ? null : list.get(RNG.nextInt(list.size()));
    }

    private static void ensureResource(JavaPlugin plugin, String path) {
        if (plugin.getResource(path) == null) return;
        plugin.saveResource(path, false);
    }

    private MapRepository() {}
}
