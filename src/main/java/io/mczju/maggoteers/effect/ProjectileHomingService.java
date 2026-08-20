package io.mczju.maggoteers.effect;

import com.github.mczjuops.mczjugamecore.game.GameState;
import io.mczju.maggoteers.MaggoteersPlugin;
import io.mczju.maggoteers.game.MaggoteersGame;
import io.mczju.maggoteers.wave.WaveEngine;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.util.Vector;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 弹道 homing：每 tick 把速度重设为目标方向（平滑转向）。
 * <p>homing_target 预设（与 {@link EffectKeys#HOMING_TARGET} 一致）：
 * nearest_enemy（追最近本局怪）| nearest_player（追最近对局玩家）|
 * attack_target / damage_source / killer（追 initialTarget，目标死则回落最近玩家）| 缺省=不追踪。
 * <p>目标死亡/失效/对局结束 → 停止转向（实体保持惯性飞行）。
 */
public final class ProjectileHomingService {

    private static final Map<UUID, HomingEntry> ACTIVE = new ConcurrentHashMap<>();
    private static final double TURN_FACTOR = 0.35;   // 每 tick 目标方向权重（越小越平滑）

    private record HomingEntry(MaggoteersGame game, String target, LivingEntity initial, double speed) {}

    private ProjectileHomingService() {}

    public static void start(MaggoteersPlugin plugin) {
        Bukkit.getScheduler().runTaskTimer(plugin, ProjectileHomingService::homeAll, 1L, 1L);
    }

    /** initialTarget 仅 attack_target/damage_source/killer 预设时使用（可 null）。 */
    public static void home(MaggoteersGame game, Projectile proj,
                            LivingEntity initialTarget, String homingTarget) {
        if (proj == null || game == null) return;
        String t = homingTarget == null ? null : homingTarget.trim().toLowerCase(Locale.ROOT);
        if (t == null || t.isEmpty() || "none".equals(t)) return;
        ACTIVE.put(proj.getUniqueId(),
                new HomingEntry(game, t, initialTarget, proj.getVelocity().length()));
    }

    public static void stop(UUID projectileUuid) {
        ACTIVE.remove(projectileUuid);
    }

    private static void homeAll() {
        if (ACTIVE.isEmpty()) return;
        for (var it = ACTIVE.entrySet().iterator(); it.hasNext(); ) {
            var e = it.next();
            UUID pid = e.getKey();
            HomingEntry entry = e.getValue();
            Entity raw = Bukkit.getEntity(pid);
            if (!(raw instanceof Projectile proj) || !proj.isValid() || proj.isDead()) {
                it.remove();
                continue;
            }
            // 弹道不在 WaveEngine.BY_ENTITY（那里只追踪怪）→ 改查发起对局状态：对局已结束则停 homing
            if (entry.game() == null || entry.game().getState() == GameState.END) {
                it.remove();
                continue;
            }
            LivingEntity target = resolve(proj.getLocation(), entry);
            if (target == null || target.isDead() || !target.isValid()) {
                continue;   // 无目标则保持当前速度（不再转向）
            }
            Vector dir = target.getLocation().toVector()
                    .subtract(proj.getLocation().toVector()).normalize();
            Vector cur = proj.getVelocity();
            Vector next = cur.multiply(1 - TURN_FACTOR)
                    .add(dir.multiply(TURN_FACTOR * entry.speed()))
                    .normalize().multiply(entry.speed());
            proj.setVelocity(next);
        }
    }

    private static LivingEntity resolve(Location loc, HomingEntry entry) {
        String t = entry.target();
        if ("attack_target".equals(t) || "damage_source".equals(t) || "killer".equals(t)) {
            LivingEntity init = entry.initial();
            if (init != null && !init.isDead() && init.isValid()) return init;
            return nearestPlayer(loc, entry.game());   // 上下文目标已死：回落追最近玩家
        }
        if ("nearest_player".equals(t)) {
            return nearestPlayer(loc, entry.game());
        }
        return WaveEngine.nearestTrackedMob(loc, entry.game(), 64.0);   // nearest_enemy（默认）
    }

    private static LivingEntity nearestPlayer(Location loc, MaggoteersGame game) {
        LivingEntity best = null;
        double bestDist = Double.MAX_VALUE;
        for (var pe : game.getPlayers()) {
            Player p = pe.player();
            if (p == null || !p.isValid() || p.isDead()) continue;
            double d = p.getLocation().distanceSquared(loc);
            if (d < bestDist) {
                bestDist = d;
                best = p;
            }
        }
        return best;
    }
}
