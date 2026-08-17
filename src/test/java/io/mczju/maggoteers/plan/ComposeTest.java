package io.mczju.maggoteers.plan;

import io.mczju.maggoteers.config.Affix;
import io.mczju.maggoteers.config.CoeffCfg;
import io.mczju.maggoteers.config.ScalingConfig;
import io.mczju.maggoteers.mob.MobFactory;
import org.bukkit.Location;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ComposeTest {
    private static final ScalingConfig.Scaling SNAP = new ScalingConfig.Scaling(1.6, 1.3, 1.0, 1.2);

    @Test
    void coeffOnlyTimesScaling() {
        Compose.MobScale m = Compose.compose(new CoeffCfg(2.0, 1.5, 1.0), List.of(), SNAP);
        assertEquals(2.0 * 1.6, m.hp(), 1e-9);
        assertEquals(1.5 * 1.3, m.dmg(), 1e-9);
        assertEquals(1.0 * 1.0, m.speed(), 1e-9);
    }

    @Test
    void multipleAffixesMultiply() {
        Affix armored = new Affix("armored", 2.0, 1, 1, List.of(), "x");
        Affix berserk = new Affix("berserk", 1, 1.5, 1.2, List.of(), "x");
        Compose.MobScale m = Compose.compose(new CoeffCfg(1.0, 1.0, 1.0), List.of(armored, berserk), SNAP);
        assertEquals(2.0 * 1.6, m.hp(), 1e-9);
        assertEquals(1.5 * 1.3, m.dmg(), 1e-9);
        assertEquals(1.2 * 1.0, m.speed(), 1e-9);
    }

    @Test
    void spawnPadNineGridWrapsAndStaysWithinOneBlock() {
        Location base = new Location(null, 12.5, 65, 8.5);
        for (int i = 0; i < 20; i++) {
            Location at = MobFactory.spawnPadLocation(base, i);
            assertTrue(Math.abs(at.getX() - base.getX()) <= 1.0 + 1e-9);
            assertTrue(Math.abs(at.getZ() - base.getZ()) <= 1.0 + 1e-9);
        }
        Location ninth = MobFactory.spawnPadLocation(base, 9);
        Location zeroth = MobFactory.spawnPadLocation(base, 0);
        assertEquals(zeroth.getX(), ninth.getX(), 1e-9);
        assertEquals(zeroth.getZ(), ninth.getZ(), 1e-9);
    }
}
