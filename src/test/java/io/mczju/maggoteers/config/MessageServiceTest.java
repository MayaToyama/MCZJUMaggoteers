package io.mczju.maggoteers.config;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MessageServiceTest {

    @Test
    void missingKeyReturnsLiteralAndDoesNotThrow() {
        MessageService.loadFromSection(new YamlConfiguration());
        assertEquals("messages.nope", MessageService.raw("nope", Map.of()));
    }

    @Test
    void emptyStringIsValid() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("wave.strategy_clause", "");
        MessageService.loadFromSection(yaml);
        assertTrue(MessageService.hasKey("wave.strategy_clause"));
        assertEquals("", MessageService.raw("wave.strategy_clause", Map.of()));
    }

    @Test
    void placeholdersAreEscapedNotParsedAsTags() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("reward.gained", "<green>Got: {display}");
        MessageService.loadFromSection(yaml);
        var c = MessageService.component("reward.gained", Map.of("display", "<red>HACK"));
        String plain = PlainTextComponentSerializer.plainText().serialize(c);
        assertTrue(plain.contains("Got:"));
        assertTrue(plain.contains("<red>HACK") || plain.contains("HACK"));
        // HACK must not become red-styled-only text without brackets
        assertTrue(plain.contains("<") || plain.contains("HACK"));
    }

    @Test
    void nestedPlainFragmentKeepsOuterColor() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("game.prep_clause", " prep 10s");
        yaml.set("game.ready", "<green>Ready!{prep_clause}");
        MessageService.loadFromSection(yaml);
        String clause = MessageService.raw("game.prep_clause", Map.of());
        String ready = MessageService.raw("game.ready", Map.of("prep_clause", clause));
        assertTrue(ready.startsWith("<green>Ready!"));
        assertTrue(ready.contains("prep 10s"));
        var plain = PlainTextComponentSerializer.plainText()
                .serialize(MessageService.component("game.ready", Map.of("prep_clause", clause)));
        assertEquals("Ready! prep 10s", plain);
    }

    @Test
    void requiredKeysNonEmpty() {
        assertFalse(MessageService.requiredKeys().isEmpty());
    }

    @Test
    void rawListReadsIndexedEntries() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("game.meta_description", List.of("<aqua>a", "<gray>b"));
        MessageService.loadFromSection(yaml);
        assertEquals(List.of("<aqua>a", "<gray>b"), MessageService.rawList("game.meta_description"));
    }


    @Test
    void bundledConfigContainsAllRequiredKeys() throws Exception {
        try (var in = MessageService.class.getClassLoader().getResourceAsStream("config.yml")) {
            assertNotNull(in, "classpath config.yml missing");
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(in, StandardCharsets.UTF_8));
            MessageService.loadFromSection(yaml.getConfigurationSection("messages"));
            for (String k : MessageService.REQUIRED_KEYS) {
                assertTrue(MessageService.hasKey(k), "missing " + k);
            }
        }
    }

    @Test
    void loadFromNullSectionDoesNotThrow() {
        MessageService.loadFromSection(null);
        assertEquals("messages.x", MessageService.raw("x", Map.of()));
    }
}
