package io.mczju.maggoteers.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class RetiredAffixConfigTest {
    private static final Set<String> RETIRED = Set.of(
            "toxic", "poisonous", "vampiric", "lifesteal", "sticky", "quicksand",
            "frostgrasp", "squid", "truck", "hidesuwa", "teleport", "cataclysm");

    @Test
    void affixesYamlHasNoCombatTriggerSchema() throws IOException {
        var in = RetiredAffixConfigTest.class.getClassLoader().getResourceAsStream("affixes.yml");
        assertNotNull(in);
        String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(
                new java.io.StringReader(text));
        ConfigurationSection affixes = yaml.getConfigurationSection("affixes");
        assertNotNull(affixes);
        assertFalse(affixes.getKeys(false).isEmpty());
        for (String id : affixes.getKeys(false)) {
            assertFalse(affixes.contains("affixes." + id + ".on"), id);
        }
        for (String retired : RETIRED) {
            assertFalse(affixes.contains(retired), "retired affix still defined: " + retired);
        }
    }

    @Test
    void wavesYamlHasNoRetiredNativeAffixIds() throws IOException {
        var in = RetiredAffixConfigTest.class.getClassLoader().getResourceAsStream("waves.yml");
        assertNotNull(in);
        String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(
                new java.io.StringReader(text));
        ConfigurationSection strategies = yaml.getConfigurationSection("strategies");
        assertNotNull(strategies);
        List<String> found = new ArrayList<>();
        for (String id : strategies.getKeys(false)) {
            collectNativeAffixes(strategies.getConfigurationSection(id), found);
        }
        assertTrue(found.stream().noneMatch(RETIRED::contains),
                () -> "retired ids in waves.yml: " + found.stream().filter(RETIRED::contains).distinct().toList());
    }

    private static void collectNativeAffixes(ConfigurationSection strategy, List<String> found) {
        if (strategy == null) return;
        for (Map<?, ?> step : strategy.getMapList("steps")) {
            collectFromNode(step, found);
        }
    }

    private static void collectFromNode(Map<?, ?> node, List<String> found) {
        Object aff = node.get("affixes");
        if (aff instanceof List<?> list) {
            for (Object o : list) found.add(String.valueOf(o));
        }
        if (node.get("passengers") instanceof List<?> passengers) {
            for (Object o : passengers) {
                if (o instanceof Map<?, ?> m) collectFromNode(m, found);
            }
        }
        if (node.get("on_death") instanceof List<?> deaths) {
            for (Object o : deaths) {
                if (o instanceof Map<?, ?> m) collectFromNode(m, found);
            }
        }
    }
}