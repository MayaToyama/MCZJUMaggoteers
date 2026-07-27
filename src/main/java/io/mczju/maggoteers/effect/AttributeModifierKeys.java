package io.mczju.maggoteers.effect;

import net.kyori.adventure.key.Key;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;

import java.util.ArrayList;

/** Paper 26.2：{@link AttributeModifier#getKey()} 为 Adventure {@link Key}，与 {@link NamespacedKey} 比较需统一。 */
public final class AttributeModifierKeys {

    private AttributeModifierKeys() {}

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
