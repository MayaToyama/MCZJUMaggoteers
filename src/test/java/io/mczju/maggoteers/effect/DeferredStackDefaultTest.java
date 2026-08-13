package io.mczju.maggoteers.effect;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DeferredStackDefaultTest {
    @Test
    void omittedStackOnDeferredIsReplace() {
        assertEquals(Stack.REPLACE, ItemAbilityRegistry.defaultStackForStep(null, true));
        assertEquals(Stack.REPLACE, ItemAbilityRegistry.defaultStackForStep(Stack.REPLACE, true));
        assertEquals(Stack.IGNORE, ItemAbilityRegistry.defaultStackForStep(Stack.IGNORE, true));
    }
}
