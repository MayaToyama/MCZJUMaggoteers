package io.mczju.maggoteers.wave;

import com.github.mczjuops.mczjugamecore.game.AbstractGame;
import io.mczju.maggoteers.integration.InfernalMobsBridge;
import io.mczju.maggoteers.mob.MobFactory;
import io.mczju.maggoteers.mob.MountedSquadAiService;
import io.mczju.maggoteers.mob.MountedSquadRegistry;
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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 波次执行器：生成、追踪、死亡处理、5-tick 安全扫描（照搬前代 VampireSurvivor WaveManager 核心）。
 * <p><b>只管"本波怪物的生灭"，不管"波次/相位调度"</b>——后者由 {@link WaveScheduler} 驱动。
 */
public final class WaveEngine {
    private static final Map<AbstractGame, WaveRuntime> RUNTIMES = new IdentityHashMap<>();
    /** UUID → runtime，供死亡监听器/扫描 O(1) 反查。 */
    private static final Map<UUID, WaveRuntime> BY_ENTITY = new HashMap<>();
    /** 死亡 untrack 后 SlimeSplitEvent 才触发；先标记再消费。 */
    private static final Set<UUID> PENDING_SPLIT_CANCEL = ConcurrentHashMap.newKeySet();

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

    /** {@link #handleMobDeath} 移除追踪前对史莱姆/岩浆怪调用；供 {@code SlimeSplitEvent} 取消分裂。 */
    public static boolean consumeSplitCancel(UUID uuid) {
        return PENDING_SPLIT_CANCEL.remove(uuid);
    }

    /** 调试 spawnmob：登记进本局 livingMobs。 */
    public static void trackDebugMob(AbstractGame game, LivingEntity le, List<String> affixIds) {
        WaveRuntime rt = RUNTIMES.get(game);
        if (rt == null || le == null) return;
        trackSpawned(game, rt, null, le, new MobSpawnProfile(1.0, affixIds, io.mczju.maggoteers.config.InfernalCfg.NONE, List.of()));
    }

    /** 查某怪 UUID 的 SpawnStep（Boss 条等）。无则 null。 */
    public static SpawnStep entityStep(UUID uuid) {
        WaveRuntime rt = BY_ENTITY.get(uuid);
        return rt == null ? null : rt.mobSteps.get(uuid);
    }

    /**
     * 生成一个 SpawnStep 的全部 count 只怪于其绝对点，登记进追踪。
     * Boss 类（单只、高血）自动挂 BossBar 给全员。
     */
    public static void spawnStep(AbstractGame game, WaveRuntime rt, SpawnStep step, World world) {
        Location base = new Location(world, step.point().x(), step.point().y(), step.point().z());
        MobFactory.spawnStepGroup(base, step, (le, profile) -> trackSpawned(game, rt, step, le, profile));
    }

    private static void trackSpawned(AbstractGame game, WaveRuntime rt, SpawnStep step,
                                     LivingEntity le, MobSpawnProfile profile) {
        UUID uuid = le.getUniqueId();
        rt.livingMobs.add(uuid);
        if (step != null) rt.mobSteps.put(uuid, step);
        rt.mobProfiles.put(uuid, profile);
        BY_ENTITY.put(uuid, rt);
        if (step != null && step.count() == 1 && step.passengers().isEmpty() && step.hpMult() >= 6.0) {
            attachBossBar(game, rt, le);
        }
    }

    private static void attachBossBar(AbstractGame game, WaveRuntime rt, LivingEntity le) {
        BossBar bar = Bukkit.createBossBar(le.getType().name(), BarColor.RED, BarStyle.SOLID);
        bar.setProgress(1.0);
        for (var pe : game.getPlayers()) bar.addPlayer(pe.player());
        rt.bossBars.put(le.getUniqueId(), bar);
    }

    /** 清追踪表但不 remove 实体（调试跳波前由调用方清怪）。 */
    public static void clearTracking(AbstractGame game) {
        WaveRuntime rt = RUNTIMES.get(game);
        if (rt == null) return;
        for (UUID uuid : new HashSet<>(rt.livingMobs)) {
            InfernalMobsBridge.unregisterManaged(uuid);
            BY_ENTITY.remove(uuid);
        }
        rt.livingMobs.clear();
        rt.mobSteps.clear();
        rt.mobProfiles.clear();
        rt.bossBars.values().forEach(BossBar::removeAll);
        rt.bossBars.clear();
    }

    /** 移除当前波次追踪的所有实体。 */
    public static void killAllTracked(AbstractGame game) {
        WaveRuntime rt = RUNTIMES.get(game);
        if (rt == null) return;
        for (UUID uuid : new HashSet<>(rt.livingMobs)) {
            var e = Bukkit.getEntity(uuid);
            if (e != null) e.remove();
            InfernalMobsBridge.unregisterManaged(uuid);
        }
        clearTracking(game);
    }

    /** 停止并清理本局所有追踪怪 + Boss 条。 */
    public static void stop(AbstractGame game) {
        WaveRuntime rt = RUNTIMES.remove(game);
        if (rt == null) return;
        var ourMobs = new HashSet<>(rt.livingMobs);
        for (UUID uuid : ourMobs) {
            var e = Bukkit.getEntity(uuid);
            if (e != null) e.remove();
            InfernalMobsBridge.unregisterManaged(uuid);
            BY_ENTITY.remove(uuid);
            MountedSquadRegistry.removeByRoot(uuid);
        }
        rt.livingMobs.clear();
        rt.mobSteps.clear();
        rt.mobProfiles.clear();
        rt.bossBars.values().forEach(BossBar::removeAll);
        rt.bossBars.clear();
    }

    /** 死亡处理：移除追踪；死亡召唤计入本波 livingMobs。返回 true 表示是我们的怪。 */
    public static boolean handleMobDeath(UUID uuid, Location loc) {
        WaveRuntime rt = BY_ENTITY.get(uuid);
        if (rt == null) return false;
        InfernalMobsBridge.unregisterManaged(uuid);
        var dead = Bukkit.getEntity(uuid);
        if (dead instanceof org.bukkit.entity.Slime) {
            PENDING_SPLIT_CANCEL.add(uuid);
        }
        MobSpawnProfile profile = rt.mobProfiles.remove(uuid);
        rt.livingMobs.remove(uuid);
        rt.mobSteps.remove(uuid);
        BY_ENTITY.remove(uuid);
        MountedSquadRegistry.removeByRoot(uuid);
        BossBar bar = rt.bossBars.remove(uuid);
        if (bar != null) bar.removeAll();
        if (loc != null && profile != null && !profile.onDeath().isEmpty()) {
            spawnOnDeath(rt, loc, profile.onDeath());
        }
        return true;
    }

    private static void spawnOnDeath(WaveRuntime rt, Location loc, List<DeathSpawn> specs) {
        int spread = 0;
        for (DeathSpawn ds : specs) {
            for (int c = 0; c < ds.count(); c++) {
                Location at = MobFactory.spawnPadLocation(loc, spread).add(0, 0.15, 0);
                spread++;
                LivingEntity le = MobFactory.spawnDeathMob(at, ds);
                if (le != null) {
                    trackSpawned(rt.game, rt, null, le, MobSpawnProfile.fromDeathSpawn(ds));
                    if (!ds.passengers().isEmpty()) {
                        MobFactory.mountPassengersDelayed(le, ds.passengers(),
                                (passenger, profile) -> trackSpawned(rt.game, rt, null, passenger, profile));
                    }
                }
            }
        }
    }

    /**
     * 5-tick 安全扫描（由 WaveScheduler 心跳调用）：把已消失/爆炸实体强制计入死亡，刷新 Boss 条。
     * 照搬 VampireSurvivor WaveManager.tickContext。
     */
    public static void tick(WaveRuntime rt) {
        MountedSquadAiService.tick(rt.game);
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
