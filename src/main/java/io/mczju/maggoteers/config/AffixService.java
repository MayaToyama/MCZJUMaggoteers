package io.mczju.maggoteers.config;

import io.mczju.maggoteers.wave.PotionSpec;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.*;

/** 加载并缓存 affixes.yml。onEnable 调一次。 */
public final class AffixService {
    private static AffixService INSTANCE;
    final Map<String, Affix> affixes = new HashMap<>();

    public static void load(JavaPlugin plugin) {
        plugin.saveResource("affixes.yml", false);
        File f = new File(plugin.getDataFolder(), "affixes.yml");
        var cfg = YamlConfiguration.loadConfiguration(f);
        INSTANCE = new AffixService();
        var sec = cfg.getConfigurationSection("affixes");
        if (sec != null) {
            for (String id : sec.getKeys(false)) {
                INSTANCE.affixes.put(id, parse(id, sec.getConfigurationSection(id)));
            }
        }
        plugin.getLogger().info("AffixService 已加载 " + INSTANCE.affixes.size() + " 词缀。");
    }

    private static Affix parse(String id, ConfigurationSection s) {
        if (s == null) return new Affix(id, 1, 1, 1, 1, List.of(), null, id);
        List<PotionSpec> pots = new ArrayList<>();
        for (var m : s.getMapList("potions")) {
            pots.add(new PotionSpec((String) m.get("effect"),
                    num(m.get("amp"), 0).intValue(),
                    num(m.get("dur"), 0).intValue()));
        }
        return new Affix(id,
                s.getDouble("hp", 1.0), s.getDouble("dmg", 1.0),
                s.getDouble("speed", 1.0), s.getDouble("drop", 1.0),
                pots, s.getString("on"), s.getString("display", id));
    }

    private static Number num(Object v, Number fallback) {
        return v instanceof Number n ? n : fallback;
    }

    public static AffixService getInstance() {
        if (INSTANCE == null) throw new IllegalStateException("AffixService 未加载！");
        return INSTANCE;
    }

    public Affix get(String id) { return affixes.get(id); }

    /** 批量解析（缺失的 id 抛错，便于 G3-style fail-fast）。 */
    public List<Affix> resolve(List<String> ids) {
        List<Affix> out = new ArrayList<>();
        for (String id : ids) {
            Affix a = affixes.get(id);
            if (a == null) throw new IllegalStateException("词缀未定义: " + id);
            out.add(a);
        }
        return out;
    }

    /** 测试用：直接构造（绕过 Bukkit 文件加载）。 */
    public static AffixService forTesting(Map<String, Affix> data) {
        AffixService s = new AffixService();
        s.affixes.putAll(data);
        return s;
    }

    private AffixService() {}
}
