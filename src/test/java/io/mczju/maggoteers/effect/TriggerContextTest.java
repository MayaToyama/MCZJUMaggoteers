package io.mczju.maggoteers.effect;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
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
    void resolveOriginUsesKillVictimWhenNoEventLocation() {
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
        LivingEntity victim = (LivingEntity) Proxy.newProxyInstance(
                LivingEntity.class.getClassLoader(),
                new Class[]{LivingEntity.class},
                (proxy, method, args) -> {
                    if ("getLocation".equals(method.getName())) {
                        return new Location(world, 3, 70, 9);
                    }
                    if ("getWorld".equals(method.getName())) {
                        return world;
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
        TriggerContext ctx = new TriggerContext(Trigger.ON_KILL, victim, null);
        Location resolved = TriggerContext.resolveOrigin(null, ctx);
        assertEquals(3, resolved.getX(), 0.01);
        assertEquals(70, resolved.getY(), 0.01);
        assertEquals(9, resolved.getZ(), 0.01);
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

    @Test
    void counterIdSurvivesWithCounter() {
        TriggerContext base = TriggerContext.empty().withFired(Trigger.ON_COUNTER);
        TriggerContext c = base.withCounter("fire_every_5_attacks");
        assertEquals(Trigger.ON_COUNTER, c.fired());
        assertEquals("fire_every_5_attacks", c.counterId());
        // withHitTarget 保留 counterId（串联链路里战斗字段不丢来源）
        assertEquals("fire_every_5_attacks",
                c.withHitTarget(null).counterId());
    }
}