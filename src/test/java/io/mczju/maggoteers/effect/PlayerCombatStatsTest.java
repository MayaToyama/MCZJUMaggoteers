package io.mczju.maggoteers.effect;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlayerCombatStatsTest {

    @Test
    void multiplierSumsPercentLayers() {
        List<PlayerEffect> effects = List.of(
                magicAttr("m1", 0.10),
                magicAttr("m2", 0.15)
        );
        assertEquals(1.25, PlayerCombatStats.magicDamageMultiplierFromEffects(effects), 1e-9);
    }

    @Test
    void ignoresNonMagicAttributes() {
        EffectContext ctx = new EffectContext();
        ctx.put(EffectKeys.ATTR_NAME, "ATTACK_DAMAGE");
        ctx.put(EffectKeys.OP, "PERCENT");
        ctx.put(EffectKeys.VALUE, 0.50);
        PlayerEffect pe = new PlayerEffect(
                "dmg", Effect.ADD_ATTRIBUTE, ctx, null,
                null, 0, 0, null, Stack.ADD, 0, 0);
        assertEquals(1.0, PlayerCombatStats.magicDamageMultiplierFromEffects(List.of(pe)), 1e-9);
    }

    @Test
    void ignoresFlatMagicDamage() {
        List<PlayerEffect> effects = List.of(magicAttrFlat("bad", 5.0));
        assertEquals(1.0, PlayerCombatStats.magicDamageMultiplierFromEffects(effects), 1e-9);
    }

    @Test
    void ignoresTriggeredTemplates() {
        EffectContext ctx = new EffectContext();
        ctx.put(EffectKeys.ATTR_NAME, "MAGIC_DAMAGE");
        ctx.put(EffectKeys.OP, "PERCENT");
        ctx.put(EffectKeys.VALUE, 0.50);
        PlayerEffect template = new PlayerEffect(
                "stack_hit", Effect.ADD_ATTRIBUTE, ctx, Trigger.ON_DAMAGE_TAKEN,
                null, 0, 0, null, Stack.ADD, 0, 0);
        assertEquals(1.0, PlayerCombatStats.magicDamageMultiplierFromEffects(List.of(template)), 1e-9);
    }

    @Test
    void countsGrantLayersNotTemplates() {
        List<PlayerEffect> effects = List.of(
                magicAttr("stack_hit", 0.10, Trigger.ON_DAMAGE_TAKEN),
                magicAttr("stack_hit:grant", 0.05, null),
                magicAttr("stack_hit:grant", 0.05, null)
        );
        assertEquals(1.10, PlayerCombatStats.magicDamageMultiplierFromEffects(effects), 1e-9);
    }

    private static PlayerEffect magicAttr(String id, double percent) {
        return magicAttr(id, percent, null);
    }

    private static PlayerEffect magicAttr(String id, double percent, Trigger fire) {
        EffectContext ctx = new EffectContext();
        ctx.put(EffectKeys.ATTR_NAME, "MAGIC_DAMAGE");
        ctx.put(EffectKeys.OP, "PERCENT");
        ctx.put(EffectKeys.VALUE, percent);
        return new PlayerEffect(
                id, Effect.ADD_ATTRIBUTE, ctx, fire,
                null, 0, 0, null, Stack.ADD, 0, 0);
    }

    private static PlayerEffect magicAttrFlat(String id, double flat) {
        EffectContext ctx = new EffectContext();
        ctx.put(EffectKeys.ATTR_NAME, "MAGIC_DAMAGE");
        ctx.put(EffectKeys.OP, "FLAT");
        ctx.put(EffectKeys.VALUE, flat);
        return new PlayerEffect(
                id, Effect.ADD_ATTRIBUTE, ctx, null,
                null, 0, 0, null, Stack.ADD, 0, 0);
    }
}
