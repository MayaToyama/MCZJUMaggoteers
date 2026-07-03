package io.mczju.maggoteers.state;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class PlayerStateTest {
    @Test
    void autoReviveConsumesCountAndStaysAlive() {
        PlayerState st = new PlayerState(UUID.randomUUID(), 2);
        assertTrue(st.tryAutoRevive());
        assertEquals(1, st.getReviveCount());
        assertTrue(st.isAlive());
        assertTrue(st.tryAutoRevive());
        assertEquals(0, st.getReviveCount());
        assertTrue(st.isAlive());
    }

    @Test
    void noReviveLeftReturnsFalseWithoutChange() {
        PlayerState st = new PlayerState(UUID.randomUUID(), 0);
        assertFalse(st.tryAutoRevive());
        assertEquals(0, st.getReviveCount());
        assertTrue(st.isAlive());
    }

    @Test
    void markDownTransitionsToSpectator() {
        PlayerState st = new PlayerState(UUID.randomUUID(), 1);
        assertTrue(st.tryAutoRevive());
        assertFalse(st.tryAutoRevive());
        st.markDown();
        assertFalse(st.isAlive());
    }

    @Test
    void addReviveCountClampsAtZero() {
        PlayerState st = new PlayerState(UUID.randomUUID(), 0);
        st.setReviveCount(-5);
        assertEquals(0, st.getReviveCount());
    }
}
