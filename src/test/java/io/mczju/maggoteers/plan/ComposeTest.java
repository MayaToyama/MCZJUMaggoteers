package io.mczju.maggoteers.plan;

import io.mczju.maggoteers.config.Affix;
import io.mczju.maggoteers.config.CoeffCfg;
import io.mczju.maggoteers.config.ScalingConfig;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ComposeTest {
    private static final ScalingConfig.Scaling SNAP = new ScalingConfig.Scaling(1.6, 1.3, 1.0, 1.8, 1.2);

    @Test
    void coeffOnlyTimesScaling() {
        Compose.MobScale m = Compose.compose(new CoeffCfg(2.0, 1.5, 1.0), List.of(), SNAP);
        assertEquals(2.0 * 1.6, m.hp(), 1e-9);
        assertEquals(1.5 * 1.3, m.dmg(), 1e-9);
        assertEquals(1.0 * 1.0, m.speed(), 1e-9);
        assertEquals(1.0 * 1.8, m.drop(), 1e-9);
    }

    @Test
    void multipleAffixesMultiply() {
        Affix armored = new Affix("armored", 2.0, 1, 1, 1, List.of(), null, "x");
        Affix berserk = new Affix("berserk", 1, 1.5, 1.2, 1, List.of(), null, "x");
        Compose.MobScale m = Compose.compose(new CoeffCfg(1.0, 1.0, 1.0), List.of(armored, berserk), SNAP);
        assertEquals(2.0 * 1.6, m.hp(), 1e-9);
        assertEquals(1.5 * 1.3, m.dmg(), 1e-9);
        assertEquals(1.2 * 1.0, m.speed(), 1e-9);
    }

    @Test
    void dropScalesWithGreedy() {
        Affix greedy = new Affix("greedy", 1, 1, 1, 1.5, List.of(), null, "x");
        Compose.MobScale m = Compose.compose(new CoeffCfg(1.0, 1.0, 1.0), List.of(greedy), SNAP);
        assertEquals(1.5 * 1.8, m.drop(), 1e-9);
    }
}
