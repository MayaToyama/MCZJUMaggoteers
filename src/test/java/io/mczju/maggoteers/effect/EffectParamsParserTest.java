package io.mczju.maggoteers.effect;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** EffectParamsParser（魔法武器 use_ability 参数）YAML→EffectContext 映射回归。 */
class EffectParamsParserTest {

    @Test
    void parsesSummonHomingTarget() {
        YamlConfiguration sec = new YamlConfiguration();
        sec.set("entity", "SMALL_FIREBALL");
        sec.set("homing_target", "nearest_enemy");
        sec.createSection("projectile").set("speed", 1.2);

        EffectContext ctx = EffectParamsParser.parse(sec);
        assertEquals("SMALL_FIREBALL", ctx.get(EffectKeys.ENTITY));
        assertEquals("nearest_enemy", ctx.get(EffectKeys.HOMING_TARGET));
        assertEquals(1.2, ctx.get(EffectKeys.PROJECTILE).get(EffectKeys.PROJECTILE_SPEED), 1e-6);
    }
}
