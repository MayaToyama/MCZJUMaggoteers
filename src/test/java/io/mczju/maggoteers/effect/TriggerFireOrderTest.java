package io.mczju.maggoteers.effect;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class TriggerFireOrderTest {
    @Test
    void dispatchRunsBeforeSweep() {
        List<String> order = new ArrayList<>();
        AtomicBoolean presentDuringDispatch = new AtomicBoolean(false);
        List<String> effects = new ArrayList<>(List.of("ability:eighty:0"));

        TriggerFireOrder.run(
                () -> {
                    presentDuringDispatch.set(effects.contains("ability:eighty:0"));
                    order.add("dispatch");
                },
                () -> {
                    order.add("sweep");
                    effects.clear();
                    return List.of("ability:eighty:0");
                });

        assertEquals(List.of("dispatch", "sweep"), order);
        assertTrue(presentDuringDispatch.get(),
                "same-trigger row must still exist during dispatch");
        assertTrue(effects.isEmpty());
    }
}
