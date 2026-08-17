package io.mczju.maggoteers.world;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrphanWorldDirsTest {

    @TempDir Path root;

    @Test
    void findsLegacyRootLevelMaggoteersWorld() throws Exception {
        Path orphan = root.resolve("maggoteers_deadbeef");
        Files.createDirectories(orphan);
        Files.createDirectories(orphan.resolve("region"));

        List<File> found = OrphanWorldDirs.findUnder(root.toFile());
        assertEquals(Set.of(orphan.toFile().getCanonicalFile()),
                found.stream().map(OrphanWorldDirsTest::canon).collect(Collectors.toSet()));
    }

    @Test
    void findsPaper26NestedDimensionWorld() throws Exception {
        Path nested = root.resolve("world").resolve("dimensions").resolve("minecraft")
                .resolve("maggoteers_a24c38a1bd3cdf0f");
        Files.createDirectories(nested.resolve("region"));
        Files.writeString(nested.resolve("paper-world.yml"), "unused: true\n");

        Files.createDirectories(root.resolve("plugins").resolve("Maggoteers"));
        Files.createDirectories(root.resolve("plugins").resolve("MCZJUGameCore")
                .resolve("player_data").resolve("maggoteers"));
        Files.createDirectories(root.resolve("plugins").resolve("MCZJUGameCore")
                .resolve("rooms").resolve("maggoteers"));

        List<File> found = OrphanWorldDirs.findUnder(root.toFile());
        assertEquals(1, found.size());
        assertEquals(nested.toFile().getCanonicalFile(), found.get(0).getCanonicalFile());
    }

    @Test
    void ignoresNonWorldMaggoteersPrefixWithoutUnderscoreHexStyle() throws Exception {
        Files.createDirectories(root.resolve("maggoteers"));
        Files.createDirectories(root.resolve("not_maggoteers_x"));

        assertTrue(OrphanWorldDirs.findUnder(root.toFile()).isEmpty());
    }

    private static File canon(File f) {
        try {
            return f.getCanonicalFile();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}