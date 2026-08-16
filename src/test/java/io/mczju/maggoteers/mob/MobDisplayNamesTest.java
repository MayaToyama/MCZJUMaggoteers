package io.mczju.maggoteers.mob;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MobDisplayNamesTest {
    @Test
    void normalizeConfiguredName() {
        assertNull(MobDisplayNames.normalizeConfiguredName(null));
        assertNull(MobDisplayNames.normalizeConfiguredName("  "));
        assertEquals("<red>A</red>", MobDisplayNames.normalizeConfiguredName(" <red>A</red> "));
    }

    @Test
    void resolveTitleFallsBackToTypeName() {
        String plain = PlainTextComponentSerializer.plainText()
                .serialize(MobDisplayNames.resolveTitle(null, EntityType.ZOMBIE));
        assertEquals("ZOMBIE", plain);
    }

    @Test
    void resolveTitleUsesMiniMessage() {
        String plain = PlainTextComponentSerializer.plainText()
                .serialize(MobDisplayNames.resolveTitle("<gold>Boss</gold>", EntityType.CREEPER));
        assertEquals("Boss", plain);
    }
}
