package io.mczju.maggoteers.mob;

import com.github.mczjuops.mczjugamecore.game.AbstractGame;
import io.mczju.maggoteers.effect.EffectContext;
import io.mczju.maggoteers.effect.EffectKeys;
import io.mczju.maggoteers.effect.SummonParams;
import io.mczju.maggoteers.game.MaggoteersGame;
import io.mczju.maggoteers.plan.ActPlan;
import io.mczju.maggoteers.util.GameRegistries;
import io.mczju.maggoteers.wave.Vec3;
import io.mczju.maggoteers.wave.WaveEngine;
import io.mczju.maggoteers.wave.WaveScheduler;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * 怪物技能效果执行。SUMMON（含 homing 弹道）/TELEPORT（安全传送）均已实现。
 * <p>target 键（EffectKeys.TARGETS）：self（默认，作用于怪自身）| target（触发上下文目标/伤害来源落点）| area（以怪为中心 radius 内敌对）。
 */
public final class MobEffectExecutor {

    private MobEffectExecutor() {}

    public static void execute(LivingEntity mob, MobEffectSpec es,
                               LivingEntity target, LivingEntity source) {
        if (mob == null || es == null) return;
        switch (es.effect()) {
            case HEALTH -> applyHealth(mob, es, target);
            case ATTRIBUTE -> applyAttribute(mob, es, target);
            case POTION -> applyPotion(mob, es, target);
            case SUMMON -> applySummon(mob, es, target, source);
            case TELEPORT -> applyTeleport(mob, es, target);
            case DISPLACE -> applyDisplace(mob, es, target);
            default -> { }
        }
    }

    private static void applySummon(LivingEntity mob, MobEffectSpec es,
                                    LivingEntity target, LivingEntity source) {
        EffectContext p = es.params();
        String name = p.get(EffectKeys.ENTITY);
        if (name == null || name.isBlank()) return;
        EntityType type = GameRegistries.entityType(name);
        if (type == null) return;
        MaggoteersGame game = gameOf(mob.getUniqueId());
        if (game == null) return;
        int count = Math.max(1, p.getOrDefault(EffectKeys.COUNT, 1));
        double offsetY = p.getOrDefault(EffectKeys.OFFSET_Y, 0.0);
        // 召唤锚点：target: player → mob 半径内最近玩家位置（storm 落雷用）；否则以 mob 为锚点
        Player atPlayer = null;
        String ts = p.get(EffectKeys.TARGETS);
        if (ts != null && "player".equalsIgnoreCase(ts.trim())) {
            double pr = p.getOrDefault(EffectKeys.RADIUS, 8.0);
            atPlayer = nearestPlayer(mob, pr);
            if (atPlayer == null || atPlayer.isDead() || !atPlayer.isValid()) return;
        }
        // mob 侧 SUMMON projectile 形态：顶层 projectile_speed（archer/tosser/molten/necromancer），或未来 projectile 键
        if (p.has(EffectKeys.PROJECTILE_SPEED) || p.get(EffectKeys.PROJECTILE) != null) {
            double speed = p.getOrDefault(EffectKeys.PROJECTILE_SPEED, 1.0);
            String homing = p.get(EffectKeys.HOMING_TARGET);
            String h = homing == null ? "" : homing.trim().toLowerCase(Locale.ROOT);
            // initialTarget 按 homing 预设选触发上下文：attack_target→target；damage_source/killer→source
            LivingEntity init = null;
            if ("attack_target".equals(h)) init = target;
            else if ("damage_source".equals(h) || "killer".equals(h)) init = source;
            for (int i = 0; i < count; i++) {
                MobProjectileService.shoot(mob, game, type,
                        new SummonParams.ProjectileParams(speed), homing, init);
            }
            return;
        }
        for (int i = 0; i < count; i++) {
            Location at = atPlayer != null
                    ? atPlayer.getLocation()
                    : mob.getLocation().clone().add(0, offsetY + 0.8 * i, 0);
            // 落雷（IM DualStormSkill：玩家处 strikeLightning）：LightningStrike 非 LivingEntity，走独立分支
            if (type == EntityType.LIGHTNING_BOLT) {
                mob.getWorld().strikeLightning(at);
                continue;
            }
            LivingEntity spawned = (LivingEntity) mob.getWorld().spawnEntity(at, type);
            if (spawned instanceof org.bukkit.entity.Mob mobSpawned) {
                mobSpawned.setTarget(target instanceof org.bukkit.entity.Mob m ? m : null);
            }
        }
    }

    private static void applyTeleport(LivingEntity mob, MobEffectSpec es, LivingEntity target) {
        EffectContext p = es.params();
        String t = p.get(EffectKeys.TARGETS);
        boolean toTarget = t != null && "target".equalsIgnoreCase(t.trim());
        LivingEntity anchor = toTarget && target != null ? target : mob;
        double offsetY = p.getOrDefault(EffectKeys.OFFSET_Y, 2.0);
        Vec3 origin = layerOrigin(mob);
        Location dest = TeleportSafety.safeTarget(anchor, offsetY, origin.x(), origin.z());
        if (dest != null) mob.teleport(dest);
    }

    /**
     * DISPLACE（参考 IM RangeTosserSkill）：把目标玩家水平拉向怪物。
     * <p>target 键：target（触发上下文玩家，可 null）| player（缺省/默认：mob 半径内最近玩家）。
     * 豁免与 IM 一致：蹲下（isSneaking）、创造、旁观。params：radius（默认 8）/ force（默认 1.2）/ upward（默认 0.2）。
     */
    private static void applyDisplace(LivingEntity mob, MobEffectSpec es, LivingEntity target) {
        EffectContext p = es.params();
        String t = p.get(EffectKeys.TARGETS);
        Player victim = null;
        if (t != null && "target".equalsIgnoreCase(t.trim()) && target instanceof Player tp && !tp.isDead()) {
            victim = tp;
        } else {
            double radius = p.getOrDefault(EffectKeys.RADIUS, 8.0);
            victim = nearestPlayer(mob, radius);
        }
        if (victim == null || !victim.isValid() || victim.isDead()) return;
        if (victim.isSneaking()) return;   // 蹲下豁免（IM）
        GameMode gm = victim.getGameMode();
        if (gm == GameMode.CREATIVE || gm == GameMode.SPECTATOR) return;   // 创造/旁观豁免（IM）
        Vector toMob = mob.getLocation().toVector().subtract(victim.getLocation().toVector()).setY(0);
        if (toMob.lengthSquared() < 0.01) return;
        toMob.normalize();
        double force = p.getOrDefault(EffectKeys.FORCE, 1.2);
        double up = p.getOrDefault(EffectKeys.UPWARD, 0.2);
        victim.setVelocity(toMob.multiply(force).setY(up));
        try {
            victim.getWorld().playSound(victim.getLocation(), Sound.ENTITY_BREEZE_JUMP, 1f, 0.8f);
        } catch (IllegalArgumentException ignored) { }
    }

    /** mob 半径内最近的存活玩家（DISPLACE 用；不带任何豁免，豁免在调用方）。 */
    private static Player nearestPlayer(LivingEntity mob, double radius) {
        Player best = null;
        double bestDist = Double.MAX_VALUE;
        if (mob.getWorld() == null) return null;
        for (Entity en : mob.getWorld().getNearbyEntities(mob.getLocation(), radius, radius, radius)) {
            if (!(en instanceof Player pl) || !pl.isValid() || pl.isDead()) continue;
            double d = pl.getLocation().distanceSquared(mob.getLocation());
            if (d < bestDist) {
                bestDist = d;
                best = pl;
            }
        }
        return best;
    }

    /** 当前层原点（ActPlan.playerSpawn 已叠层原点绝对坐标，Act2/3 非 (0,0)）；无对局/越界回落 (0,0)。 */
    private static Vec3 layerOrigin(LivingEntity mob) {
        MaggoteersGame game = gameOf(mob.getUniqueId());
        if (game != null) {
            int[] prog = WaveScheduler.progress(game);
            List<ActPlan> acts = game.getPlannedActs();
            if (acts != null && prog[0] >= 0 && prog[0] < acts.size()) {
                return acts.get(prog[0]).playerSpawn();
            }
        }
        return new Vec3(0, 0, 0);
    }

    private static MaggoteersGame gameOf(UUID uuid) {
        AbstractGame g = WaveEngine.gameOfEntity(uuid);
        return g instanceof MaggoteersGame mg ? mg : null;
    }

    private static void applyHealth(LivingEntity mob, MobEffectSpec es, LivingEntity target) {
        EffectContext p = es.params();
        Double amount = p.get(EffectKeys.AMOUNT);
        Double damage = p.get(EffectKeys.DAMAGE);
        Double radius = p.get(EffectKeys.RADIUS);
        if (isArea(p) && radius != null && radius > 0) {
            for (LivingEntity le : nearbyHostiles(mob, radius)) {
                applySingle(le, amount, damage);
            }
            return;
        }
        applySingle(resolveTarget(mob, target, p), amount, damage);
    }

    private static void applySingle(LivingEntity victim, Double amount, Double damage) {
        if (victim == null) return;
        // 复活路径（1up 等 KILLED 自疗）：死亡事件已 cancel，实体仍可 setHealth 拉满复活
        if (amount != null && amount > 0 && victim.isDead()) {
            victim.setHealth(Math.min(victim.getMaxHealth(), victim.getHealth() + amount));
            return;
        }
        if (victim.isDead()) return;
        if (amount != null) {
            if (amount > 0) victim.setHealth(Math.min(victim.getMaxHealth(), victim.getHealth() + amount));
            else victim.damage(-amount, victim);
        } else if (damage != null && damage > 0) {
            victim.damage(damage, victim);
        }
    }

    private static void applyAttribute(LivingEntity mob, MobEffectSpec es, LivingEntity target) {
        EffectContext p = es.params();
        LivingEntity victim = resolveTarget(mob, target, p);
        if (victim == null || victim.isDead()) return;
        int dur = p.getOrDefault(EffectKeys.DURATION_TICKS, 0);
        if (dur <= 0) return;   // 常驻属性不在本效果做（spawn 属性由 coeff 承担）
        MobTempAttributeService.apply(victim, p, dur);
    }

    private static void applyPotion(LivingEntity mob, MobEffectSpec es, LivingEntity target) {
        EffectContext p = es.params();
        LivingEntity victim = resolveTarget(mob, target, p);
        if (victim == null || victim.isDead()) return;
        PotionEffectType type = p.get(EffectKeys.POTION);
        if (type == null) {
            String name = p.get(EffectKeys.POTION_NAME);
            if (name != null) type = GameRegistries.potionEffect(name);
        }
        if (type == null) return;
        int amp = p.getOrDefault(EffectKeys.AMP, 0);
        int dur = p.getOrDefault(EffectKeys.DURATION_TICKS, 0);
        if (dur <= 0) dur = 20 * 60 * 60;   // 旧词缀 dur 0 语义=常驻；用 1 小时近似
        victim.addPotionEffect(new PotionEffect(type, dur, amp));
    }

    private static boolean isArea(EffectContext p) {
        String t = p.get(EffectKeys.TARGETS);
        return t != null && "area".equalsIgnoreCase(t.trim());
    }

    /** self→mob；target→触发目标（null 回落 mob）；area→mob（以 mob 为圆心）。 */
    private static LivingEntity resolveTarget(LivingEntity mob, LivingEntity target, EffectContext p) {
        String t = p.get(EffectKeys.TARGETS);
        if (t == null) return mob;
        String norm = t.trim().toLowerCase(Locale.ROOT);
        if ("target".equals(norm)) return target != null && !target.isDead() ? target : mob;
        return mob;
    }

    /** 以 mob 为中心半径内敌对实体（非玩家 + WaveEngine 追踪怪；含 mob 自身命中规则由调用方决定）。 */
    static List<LivingEntity> nearbyHostiles(LivingEntity mob, double radius) {
        List<LivingEntity> out = new java.util.ArrayList<>();
        if (mob.getWorld() == null) return out;
        for (Entity en : mob.getWorld().getNearbyEntities(mob.getLocation(), radius, radius, radius)) {
            if (!(en instanceof LivingEntity le) || en instanceof Player) continue;
            if (en.equals(mob)) continue;
            if (!WaveEngine.isTracked(le.getUniqueId())) continue;
            out.add(le);
        }
        return out;
    }
}
