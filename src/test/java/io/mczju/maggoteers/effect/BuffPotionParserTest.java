package io.mczju.maggoteers.effect;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BuffPotionParserTest {

    @Test
    void emptyOrNullReturnsEmpty() {
        assertTrue(BuffPotionParser.parseList(null).isEmpty());
        assertTrue(BuffPotionParser.parseList(List.of()).isEmpty());
    }

    @Test
    void skipsEntriesWithoutPotionKey() {
        assertTrue(BuffPotionParser.parseList(List.of(Map.of("amp", 1))).isEmpty());
        assertTrue(BuffPotionParser.parseList(List.of("not-a-map")).isEmpty());
    }
}