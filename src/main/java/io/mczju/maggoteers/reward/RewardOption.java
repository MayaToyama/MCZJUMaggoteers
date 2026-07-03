package io.mczju.maggoteers.reward;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.util.Map;

public record RewardOption(
        String id,
        String display,
        String description,
        Category category,
        String item,
        int amount
) {
    public enum Category { STAT, WEAPON, SUPPLY }

    public RewardOption {
        if (amount < 1) amount = 1;
    }

    public static RewardOption fromMap(Map<?, ?> m) {
        String id = str(m, "id");
        String display = str(m, "display");
        String desc = str(m, "description");
        Category cat = Category.valueOf(str(m, "category", "SUPPLY").toUpperCase());
        String item = str(m, "item");
        int amount = m.get("amount") instanceof Number n ? n.intValue() : 1;
        return new RewardOption(id, display, desc, cat, item, amount);
    }

    public String displayPlain() {
        return PlainTextComponentSerializer.plainText().serialize(
                net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(display));
    }

    private static String str(Map<?, ?> m, String key) { return str(m, key, ""); }

    private static String str(Map<?, ?> m, String key, String def) {
        Object v = m.get(key);
        return v == null ? def : String.valueOf(v);
    }
}
