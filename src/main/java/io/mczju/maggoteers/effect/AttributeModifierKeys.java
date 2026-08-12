package io.mczju.maggoteers.effect;

import net.kyori.adventure.key.Key;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;

import java.util.ArrayList;

/**
 * Paper 26.2：{@link AttributeModifier#getKey()} 为 Adventure {@link Key}，与 {@link NamespacedKey} 比较需统一。
 * <p>效果 id 常含 {@code :}（如 {@code ability:maggoteers:xxx}、{@code held:maggoteers:xxx:0}），
 * 不能直接写入 NamespacedKey path，须先 {@link #sanitizeKeyPath(String)}。
 */
public final class AttributeModifierKeys {

    private AttributeModifierKeys() {}

    /**
     * NamespacedKey path 仅允许 {@code [a-z0-9_-./]}；把其余字符（含 {@code :}）换成 {@code _}。
     */
    public static String sanitizeKeyPath(String raw) {
        if (raw == null || raw.isBlank()) return "unnamed";
        StringBuilder sb = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
                    || c == '_' || c == '-' || c == '.' || c == '/') {
                sb.append(c);
            } else if (c >= 'A' && c <= 'Z') {
                sb.append(Character.toLowerCase(c));
            } else {
                sb.append('_');
            }
        }
        String out = sb.toString();
        return out.isEmpty() ? "unnamed" : (out.length() > 256 ? out.substring(0, 256) : out);
    }

    public static NamespacedKey pluginKey(org.bukkit.plugin.Plugin plugin, String rawPath) {
        return new NamespacedKey(plugin, sanitizeKeyPath(rawPath));
    }

    public static boolean matches(AttributeModifier modifier, NamespacedKey expected) {
        if (modifier == null || expected == null) return false;
        Key k = modifier.getKey();
        return expected.getNamespace().equals(k.namespace()) && expected.getKey().equals(k.value());
    }

    public static boolean matchesPluginNamespace(AttributeModifier modifier, String pluginNamespace) {
        return modifier != null && modifier.getKey().namespace().equals(pluginNamespace);
    }

    public static void removeIfMatches(AttributeInstance inst, NamespacedKey key) {
        if (inst == null || key == null) return;
        for (AttributeModifier m : new ArrayList<>(inst.getModifiers())) {
            if (matches(m, key)) inst.removeModifier(m);
        }
    }

    public static void replace(AttributeInstance inst, NamespacedKey key, double value,
                               AttributeModifier.Operation operation) {
        if (inst == null || key == null) return;
        removeIfMatches(inst, key);
        inst.addModifier(new AttributeModifier(key, value, operation));
    }
}
