package io.mczju.maggoteers.effect;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** held BUFF_AREA 显式 targets 不得被 withDefaults 盖成 allies（否则会减速施法者）。 */
class HerafingerHeldParseTest {

    @Test
    void withDefaultsKeepsExplicitHitTarget() {
        EffectContext raw = new EffectContext();
        raw.put(EffectKeys.TARGETS, "hit_target");
        EffectContext p = MagicEffectParams.withDefaults(Effect.BUFF_AREA, raw);
        assertEquals("hit_target", p.get(EffectKeys.TARGETS),
                "lost targets would default to allies+include_self and slow the caster");
    }
}
