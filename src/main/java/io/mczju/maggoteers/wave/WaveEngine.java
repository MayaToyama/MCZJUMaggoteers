package io.mczju.maggoteers.wave;

import com.github.mczjuops.mczjugamecore.game.AbstractGame;
import io.mczju.maggoteers.mob.MobFactory;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.LivingEntity;

import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 波次执行器：生成、追踪、死亡处理、5-tick 安全扫描（照搬前代 VampireSurvivor WaveManager 核心）。
 * <p><b>只管"本波怪物的生灭"，不管"波次/相位调度"</b>——后者由 {@link WaveScheduler} 驱动。
 */
public final class WaveEngine {
    private static final Map<AbstractGame, WaveRuntime> RUNTIMES = new IdentityHashMap<>();
    /** UUID → runtime，供死亡监听器/扫描 O(1) 反查。 */
    private static final Map<UUID, WaveRuntime> BY_ENTITY = new HashMap<>();

    public static WaveRuntime start(AbstractGame game) {
        WaveRuntime rt = new WaveRuntime(game);
        RUNTIMES.put(game, rt);
        return rt;
    }

    public static WaveRuntime runtime(AbstractGame game) { return RUNTIMES.get(game); }

    public static int livingCount(AbstractGame game) {
        WaveRuntime rt = RUNTIMES.get(game);
        return rt == null ? 0 : rt.livingMobs.size();
    }

    public static boolean isTracked(UUID uuid) { return BY_ENTITY.containsKey(uuid); }

    /** 查某怪 UUID 的 SpawnStep（供 CombatAffixListener 查 on:hit-player 药水）。无则 null。 */
    public static SpawnStep entityStep(UUID uuid) {
        WaveRuntime rt = BY_ENTITY.get(uuid);
        return rt == null ? null : rt.mobSteps.get(uuid);
    }

    /**
     * 生成一个 SpawnStep 的全部 count 只怪于其绝对点，登记进追踪。
     * Boss 类（单只、高血）自动挂 BossBar 给全员。
     */
    public static void spawnStep(AbstractGame game, WaveRuntime rt, SpawnStep step, World world) {
        for (int i = 0; i < step.count(); i++) {
            double dx = (i % 3 - 1) * 1.5;
            double dz = (i / 3 - 1) * 1.5;
            Location loc = new Location(world, step.point().x() + dx, step.point().y(), step.point().z() + dz);
            LivingEntity le = MobFactory.spawn(loc, step);
            if (le == null) continue;
            UUID uuid = le.getUniqueId();
            rt.livingMobs.add(uuid);
            rt.mobSteps.put(uuid, step);
            BY_ENTITY.put(uuid, rt);
            if (step.count() == 1 && step.hpMult() >= 6.0) {
                attachBossBar(game, rt, le);
            }
        }
    }

    private static void attachBossBar(AbstractGame game, WaveRuntime rt, LivingEntity le) {
        BossBar bar = Bukkit.createBossBar(le.getType().name(), BarColor.RED, BarStyle.SOLID);
        bar.setProgress(1.0);
        for (var pe : game.getPlayers()) bar.addPlayer(pe.player());
        rt.bossBars.put(le.getUniqueId(), bar);
    }

    /** 停止并清理本局所有追踪怪 + Boss 条。 */
    public static void stop(AbstractGame game) {
        WaveRuntime rt = RUNTIMES.remove(game);
        if (rt == null) return;
        for (UUID uuid : new HashSet<>(rt.livingMobs)) {
            var e = Bukkit.getEntity(uuid);
            if (e != null) e.remove();
            BY_ENTITY.remove(uuid);
        }
        rt.livingMobs.clear();
        rt.mobSteps.clear();
        rt.bossBars.values().forEach(BossBar::removeAll);
        rt.bossBars.clear();
    }

    /** 死亡处理：移除追踪；返回 true 表示是我们的怪。 */
    public static boolean handleMobDeath(UUID uuid, Location loc) {
        WaveRuntime rt = BY_ENTITY.remove(uuid);
        if (rt == null) return false;
        rt.livingMobs.remove(uuid);
        rt.mobSteps.remove(uuid);
        BossBar bar = rt.bossBars.remove(uuid);
        if (bar != null) bar.removeAll();
        return true;
    }

    /**
     * 5-tick 安全扫描（由 WaveScheduler 心跳调用）：把已消失/爆炸实体强制计入死亡，刷新 Boss 条。
     * 照搬 VampireSurvivor WaveManager.tickContext。
     */
    public static void tick(WaveRuntime rt) {
        for (UUID uuid : new HashSet<>(rt.livingMobs)) {
            var raw = Bukkit.getEntity(uuid);
            if (raw == null || raw.isDead()) {
                handleMobDeath(uuid, raw != null ? raw.getLocation() : null);
                continue;
            }
            BossBar bar = rt.bossBars.get(uuid);
            if (bar != null && raw instanceof LivingEntity le) {
                AttributeInstance hp = le.getAttribute(Attribute.MAX_HEALTH);
                double max = hp != null ? hp.getValue() : 20.0;
                bar.setProgress(Math.max(0.0, Math.min(1.0, le.getHealth() / max)));
            }
        }
    }

    private WaveEngine() {}
}
