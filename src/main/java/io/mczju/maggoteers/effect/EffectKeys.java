package io.mczju.maggoteers.effect;

import org.bukkit.attribute.Attribute;
import org.bukkit.potion.PotionEffectType;

/** Effect 参数的标准键（§10.2 各 Effect 的 params 契约）。 */
public final class EffectKeys {
    public static final EffectKey<Attribute> ATTR = new EffectKey<>("attr");
    /** YAML 原始属性名（onEnable 解析失败时 refresh 再解析）。 */
    public static final EffectKey<String> ATTR_NAME = new EffectKey<>("attr_name");
    public static final EffectKey<String> POTION_NAME = new EffectKey<>("potion_name");
    public static final EffectKey<String> OP = new EffectKey<>("op");           // "PERCENT" | "FLAT"
    public static final EffectKey<Double> VALUE = new EffectKey<>("value");
    public static final EffectKey<PotionEffectType> POTION = new EffectKey<>("potion");
    public static final EffectKey<Integer> AMP = new EffectKey<>("amp");
    public static final EffectKey<Integer> DURATION_TICKS = new EffectKey<>("duration_ticks"); // 0=常驻
    public static final EffectKey<Double> AMOUNT = new EffectKey<>("amount");   // HEAL
    public static final EffectKey<Double> RADIUS = new EffectKey<>("radius");   // DAMAGE_AREA
    public static final EffectKey<Double> DAMAGE = new EffectKey<>("damage");   // DAMAGE_AREA / BEAM
    public static final EffectKey<Double> RAY_LENGTH = new EffectKey<>("ray_length");
    public static final EffectKey<Double> BEAM_RADIUS = new EffectKey<>("beam_radius");
    public static final EffectKey<Integer> COUNT = new EffectKey<>("count");    // GRANT_REVIVE
    public static final EffectKey<String> TARGETS = new EffectKey<>("targets");           // AURA
    public static final EffectKey<String> ENEMY_SCOPE = new EffectKey<>("enemy_scope");     // AURA
    public static final EffectKey<Boolean> INCLUDE_SELF = new EffectKey<>("include_self");  // AURA
    public static final EffectKey<Effect> GRANT_EFFECT = new EffectKey<>("grant_effect");   // AURA
    public static final EffectKey<EffectContext> GRANT_PARAMS = new EffectKey<>("grant_params");
    public static final EffectKey<Integer> GRANT_PULSE_SEC = new EffectKey<>("grant_pulse_sec");
    public static final EffectKey<Boolean> MARK_HEAD = new EffectKey<>("mark_head");
    /** 携带者脚下每秒播放的粒子（preset/particle/radius/density 等）。 */
    public static final EffectKey<EffectContext> CARRIER_FX = new EffectKey<>("carrier_fx");
    public static final EffectKey<EffectContext> MARK_FX = new EffectKey<>("mark_fx");
    private EffectKeys() {}
}
