package io.mczju.maggoteers.reward;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class RewardItemPresentationConfigTest {

    @Test
    void everyCollectibleUsesRecoveryCompassBaseWithAValidModelAndLore() throws IOException {
        YamlConfiguration mappings = resourceYaml("collectibles.yml");
        YamlConfiguration items = resourceYaml("items/collectibles.yml");
        ConfigurationSection collectibles = requireSection(mappings, "collectibles");

        Set<String> itemIds = new HashSet<>();
        for (String rewardId : collectibles.getKeys(false)) {
            ConfigurationSection mapping = requireSection(collectibles, rewardId);
            String item = mapping.getString("item");
            if (item != null) itemIds.add(item);
            ConfigurationSection levels = mapping.getConfigurationSection("items_by_level");
            if (levels != null) {
                for (String level : levels.getKeys(false)) {
                    itemIds.add(levels.getString(level));
                }
            }
        }

        assertFalse(itemIds.isEmpty());
        for (String itemId : itemIds) {
            ConfigurationSection item = requireSection(items, itemId);
            // RECOVERY_COMPASS: inert vs camel husk (RABBIT_FOOT is consumed on that interact)
            assertEquals("RECOVERY_COMPASS", item.getString("material"),
                    itemId + " must use inert base material");
            String model = item.getString("itemModel");
            assertNotNull(model, itemId + " must define its rewardplan appearance");
            assertNotNull(Material.matchMaterial(model), itemId + " has invalid itemModel " + model);
            assertFalse(item.getStringList("lore").isEmpty(), itemId + " must include rewardplan lore");
        }

        assertEquals("CHIPPED_ANVIL",
                requireSection(items, "maggoteers:charm_a1w_atk15").getString("itemModel"));
        assertEquals("FILLED_MAP",
                requireSection(items, "maggoteers:charm_a1s_lost_key").getString("itemModel"));
        assertEquals("RED_BANNER",
                requireSection(items, "maggoteers:charm_class_vanguard").getString("itemModel"));
    }

    @Test
    void everyRewardWeaponHasFunctionalLore() throws IOException {
        YamlConfiguration rewards = resourceYaml("rewards.yml");
        YamlConfiguration items = resourceYaml("items/maggoteers.yml");
        ConfigurationSection pools = requireSection(rewards, "reward_pools");
        Set<String> weaponIds = new HashSet<>();

        for (String poolId : pools.getKeys(false)) {
            ConfigurationSection pool = requireSection(pools, poolId);
            collectWeaponIds(pool.getMapList("options"), weaponIds);
        }

        assertFalse(weaponIds.isEmpty());
        for (String itemId : weaponIds) {
            ConfigurationSection item = requireSection(items, itemId);
            List<String> lore = item.getStringList("lore");
            assertFalse(lore.isEmpty(), itemId + " must include rewardplan lore");
        }
    }

    private static void collectWeaponIds(List<Map<?, ?>> options, Set<String> out) {
        for (Map<?, ?> option : options) {
            if ("WEAPON".equals(String.valueOf(option.get("category")))) {
                out.add(String.valueOf(option.get("item")));
            }
            Object grants = option.get("grants");
            if (grants instanceof List<?> list) {
                @SuppressWarnings("unchecked")
                List<Map<?, ?>> childOptions = (List<Map<?, ?>>) (List<?>) list;
                collectWeaponIds(childOptions, out);
            }
        }
    }

    private static ConfigurationSection requireSection(ConfigurationSection yaml, String path) {
        ConfigurationSection section = yaml.getConfigurationSection(path);
        assertNotNull(section, "missing configuration section " + path);
        return section;
    }

    private static YamlConfiguration resourceYaml(String path) throws IOException {
        try (var in = RewardItemPresentationConfigTest.class.getClassLoader().getResourceAsStream(path)) {
            assertNotNull(in, "missing resource " + path);
            String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            return YamlConfiguration.loadConfiguration(new StringReader(text));
        }
    }
}
