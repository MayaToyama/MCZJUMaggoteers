package io.mczju.maggoteers.effect;

import org.bukkit.attribute.Attribute;
import org.bukkit.potion.PotionEffectType;

/** Effect 参数的标准键（§10.2 各 Effect 的 params 契约）。 */
public final class EffectKeys {
    public static final EffectKey<Attribute> ATTR = new EffectKey<>("attr");
    public static final EffectKey<String> OP = new EffectKey<>("op");           // "PERCENT" | "FLAT"
    public static final EffectKey<Double> VALUE = new EffectKey<>("value");
    public static final EffectKey<PotionEffectType> POTION = new EffectKey<>("potion");
    public static final EffectKey<Integer> AMP = new EffectKey<>("amp");
    public static final EffectKey<Integer> DURATION_TICKS = new EffectKey<>("duration_ticks"); // 0=常驻
    public static final EffectKey<Double> AMOUNT = new EffectKey<>("amount");   // HEAL
    public static final EffectKey<Double> RADIUS = new EffectKey<>("radius");   // DAMAGE_AREA
    public static final EffectKey<Double> DAMAGE = new EffectKey<>("damage");   // DAMAGE_AREA
    public static final EffectKey<Integer> COUNT = new EffectKey<>("count");    // GRANT_REVIVE
    private EffectKeys() {}
}
