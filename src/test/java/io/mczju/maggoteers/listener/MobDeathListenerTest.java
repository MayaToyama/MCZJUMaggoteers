package io.mczju.maggoteers.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDeathEvent;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MobDeathListenerTest {
    @Test
    void unregisterHandlerRunsBeforeInfernalNormalListener() throws Exception {
        Method method = MobDeathListener.class.getDeclaredMethod(
                "beforeInfernalDeath", EntityDeathEvent.class);
        assertEquals(EventPriority.LOWEST,
                method.getAnnotation(EventHandler.class).priority());
    }

    @Test
    void finalDropCleanupRunsAfterInfernalNormalListener() throws Exception {
        Method method = MobDeathListener.class.getDeclaredMethod(
                "onDeath", EntityDeathEvent.class);
        assertEquals(EventPriority.HIGHEST,
                method.getAnnotation(EventHandler.class).priority());
    }
}