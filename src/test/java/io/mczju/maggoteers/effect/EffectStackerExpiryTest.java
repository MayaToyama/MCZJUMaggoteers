package io.mczju.maggoteers.effect;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EffectStackerExpiryTest {

    @Test
    void sweepReturnsRemovedIds() {
        PlayerEffect t = new PlayerEffect("burst", Effect.HEAL, new EffectContext(),
                Trigger.ON_KILL, Trigger.ON_DAMAGE_DEALT, -1, 0, null, Stack.IGNORE, 0, 0);
        List<PlayerEffect> list = new ArrayList<>(List.of(t));
        List<String> removed = EffectStacker.sweepExpiry(list, Trigger.ON_DAMAGE_DEALT);
        assertEquals(List.of("burst"), removed);
        assertTrue(list.isEmpty());
    }
}
