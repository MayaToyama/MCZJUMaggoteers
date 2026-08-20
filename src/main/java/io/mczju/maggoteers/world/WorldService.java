package io.mczju.maggoteers.world;

import com.github.mczjuops.mczjugamecore.game.AbstractGame;
import io.mczju.maggoteers.MaggoteersPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameRules;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;

/** 每局专属虚空世界的完整生命周期。借鉴前代 VampireSurvivor/WorldManager。 */
public final class WorldService {

    private static final Map<AbstractGame, World> worlds = new IdentityHashMap<>();
    private static final Map<AbstractGame, Long> SEEDS = new IdentityHashMap<>();
    private static final Logger LOG = Logger.getLogger("Maggoteers");

    /** 主线程调用：为本对局创建虚空世界。返回 null 表示失败。 */
    public static World create(AbstractGame game) {
        long seed = ThreadLocalRandom.current().nextLong();
        String name = WorldNames.forSeed(seed);
        World w = new WorldCreator(name)
                .environment(World.Environment.NORMAL)
                .generator(new VoidGenerator())
                .createWorld();
        if (w == null) {
            LOG.severe("无法创建世界 " + name);
            return null;
        }
        w.setSpawnLocation(0, 64, 0);
        w.setDifficulty(Difficulty.HARD);
        w.setGameRule(GameRules.ADVANCE_TIME, false);
        w.setGameRule(GameRules.ADVANCE_WEATHER, false);
        w.setGameRule(GameRules.NATURAL_HEALTH_REGENERATION, false);
        w.setGameRule(GameRules.MOB_GRIEFING, false);
        w.setGameRule(GameRules.SPAWN_MOBS, false);
        applyTimeLock(w);
        w.setStorm(false);
        w.setThundering(false);
        w.setClearWeatherDuration(Integer.MAX_VALUE);
        SEEDS.put(game, seed);
        worlds.put(game, w);
        LOG.info("已为对局创建世界 " + name);
        return w;
    }

    public static World get(AbstractGame game) {
        World w = worlds.get(game);
        if (w != null) applyTimeLock(w);
        return w;
    }

    /** 按 config {@code world.time_lock} 锁定昼夜（默认 night，避免怪物日晒）。 */
    public static void applyTimeLock(World w) {
        if (w == null) return;
        String mode = MaggoteersPlugin.getInstance().getConfig().getString("world.time_lock", "night");
        if ("none".equalsIgnoreCase(mode)) return;
        long tick = switch (mode.toLowerCase()) {
            case "day", "noon" -> 6000L;
            case "midnight" -> 18000L;
            default -> 13000L; // night（略深，仍可见）
        };
        w.setTime(tick);
    }

    public static long getSeed(AbstractGame game) {
        Long s = SEEDS.get(game);
        return s == null ? 0L : s;
    }

    /**
     * 卸载并异步删目录。约定：仅在世界内已无玩家时调用（由 MGC leave / 大厅流程保证）。
     */
    public static void cleanup(AbstractGame game) {
        World w = worlds.get(game);
        if (w == null) {
            SEEDS.remove(game);
            worlds.remove(game);
            return;
        }
        if (!w.getPlayers().isEmpty()) {
            LOG.warning("cleanup 时世界仍有玩家，跳过卸载与删目录: " + w.getName()
                    + " players=" + w.getPlayers().size());
            return;
        }
        File folder = w.getWorldFolder();
        String name = w.getName();
        if (!Bukkit.unloadWorld(w, false)) {
            LOG.warning("unloadWorld 失败，跳过删目录: " + name);
            return;
        }
        worlds.remove(game);
        SEEDS.remove(game);
        new BukkitRunnable() {
            @Override public void run() { deleteDir(folder); }
        }.runTaskAsynchronously(MaggoteersPlugin.getInstance());
        LOG.info("已卸载并删除世界 " + name);
    }

    /** onEnable 调用：清理上次崩服残留的 maggoteers_* 世界。 */
    public static void cleanupOrphansOnEnable() {
        // 1) 已被 Bukkit 自动加载的
        for (World w : new ArrayList<>(Bukkit.getWorlds())) {
            if (w.getName().startsWith("maggoteers_")) {
                Bukkit.unloadWorld(w, false);
                deleteDir(w.getWorldFolder());
                LOG.warning("清理孤立世界(已加载) " + w.getName());
            }
        }
        // 2) 磁盘残留：含 Paper 26.2 嵌套路径 world/dimensions/minecraft/maggoteers_*
        File root = Bukkit.getWorldContainer();
        for (File f : OrphanWorldDirs.findUnder(root)) {
            deleteDir(f);
            LOG.warning("清理孤立世界(目录) " + f.getAbsolutePath());
        }
    }

    private static void deleteDir(File f) {
        if (f == null || !f.exists()) return;
        File[] kids = f.listFiles();
        if (kids != null) {
            for (File c : kids) {
                if (c.isDirectory()) deleteDir(c);
                else c.delete();
            }
        }
        f.delete();
    }

    private WorldService() {}
}
