package io.mczju.maggoteers.item;

import java.util.Locale;

/** PDC {@code maggoteers:kind} 枚举；{@link ItemInteractRouter} 据此分发右键处理器。 */
public enum ItemKind {
    CURRENCY_NORMAL("currency_normal"),
    CURRENCY_BOSS("currency_boss"),
    CLASS_TICKET("class_ticket"),
    REVIVE_COIN("revive_coin");

    private final String pdcValue;

    ItemKind(String pdcValue) { this.pdcValue = pdcValue; }

    public String pdcValue() { return pdcValue; }

    public static ItemKind fromPdc(String raw) {
        if (raw == null) return null;
        for (ItemKind k : values()) {
            if (k.pdcValue.equals(raw)) return k;
        }
        return null;
    }

    public static ItemKind fromPdcLoose(String raw) {
        if (raw == null) return null;
        String n = raw.toLowerCase(Locale.ROOT);
        for (ItemKind k : values()) {
            if (k.pdcValue.equals(n)) return k;
        }
        return null;
    }
}
