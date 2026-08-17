package io.mczju.maggoteers.effect;

import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TriggerContextTest {

    @Test
    void resolveOriginPrefersEventLocation() {
        World world = (World) Proxy.newProxyInstance(
                World.class.getClassLoader(),
                new Class[]{World.class},
                (proxy, method, args) -> {
                    if ("getName".equals(method.getName())) {
                        return "world";
                    }
                    if ("getUID".equals(method.getName())) {
                        return UUID.randomUUID();
                    }
                    Class<?> ret = method.getReturnType();
                    if (ret.equals(boolean.class)) {
                        return false;
                    }
                    if (ret.isPrimitive()) {
                        return 0;
                    }
                    return null;
                });
        Location death = new Location(world, 10, 64, 20);
        TriggerContext ctx = TriggerContext.atEvent(Trigger.ON_DEATH, death);
        Location resolved = TriggerContext.resolveOrigin(null, ctx);
        assertEquals(10, resolved.getX(), 0.01);
        assertEquals(64, resolved.getY(), 0.01);
        assertEquals(20, resolved.getZ(), 0.01);
        assertTrue(resolved.getWorld() != null);
    }

    @Test
    void deathSiteRequiresOnDeathTrigger() {
        EffectContext ctx = new EffectContext();
        ctx.put(EffectKeys.ENTITY, "WOLF");
        ctx.put(EffectKeys.CLEANUP, "wave_clear");
        ctx.put(EffectKeys.ANCHOR, "death_site");
        assertTrue(SummonParamsParser.validate(ctx, Trigger.ON_WAVE_CLEAR, false).isPresent());
        assertTrue(SummonParamsParser.validate(ctx, Trigger.ON_DEATH, false).isEmpty());
    }
}