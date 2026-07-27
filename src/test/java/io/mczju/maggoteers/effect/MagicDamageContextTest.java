package io.mczju.maggoteers.effect;

import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MagicDamageContextTest {

    @Test
    void runWithNullGameStillExecutes() {
        AtomicBoolean ran = new AtomicBoolean();
        MagicDamageContext.run(null, UUID.randomUUID(), () -> ran.set(true));
        assertTrue(ran.get());
    }

    @Test
    void runWithNullSourceStillExecutes() {
        AtomicBoolean ran = new AtomicBoolean();
        MagicDamageContext.run(null, null, () -> ran.set(true));
        assertTrue(ran.get());
    }

    @Test
    void suppressFalseWhenGameNull() {
        assertFalse(MagicDamageContext.shouldSuppressOnDamageDealt(null, UUID.randomUUID()));
    }
}