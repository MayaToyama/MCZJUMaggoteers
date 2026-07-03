package io.mczju.maggoteers.world;

import com.github.mczjuops.mczjugamecore.game.AbstractGame;
import io.mczju.maggoteers.MaggoteersPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.entity.Player;
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
        w.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        w.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
        w.setGameRule(GameRule.NATURAL_REGENERATION, false);
        w.setGameRule(GameRule.MOB_GRIEFING, false);
        w.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        SEEDS.put(game, seed);
        worlds.put(game, w);
        LOG.info("已为对局创建世界 " + name);
        return w;
    }

    public static World get(AbstractGame game) {
        return worlds.get(game);
    }

    public static long getSeed(AbstractGame game) {
        Long s = SEEDS.get(game);
        return s == null ? 0L : s;
    }

    /** 传送玩家回主世界 → 卸载 → 异步删目录。 */
    public static void cleanup(AbstractGame game) {
        World w = worlds.remove(game);
        SEEDS.remove(game);
        if (w == null) return;
        Location hub = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0).getSpawnLocation();
        for (Player p : new ArrayList<>(w.getPlayers())) {
            if (hub != null) p.teleport(hub);
        }
        File folder = w.getWorldFolder();
        String name = w.getName();
        Bukkit.unloadWorld(w, false);
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
        // 2) 服务器根目录下未加载的残留文件夹
        File root = Bukkit.getWorldContainer(); // 通常是服务端根目录
        File[] kids = root.listFiles();
        if (kids == null) return;
        for (File f : kids) {
            if (f.isDirectory() && f.getName().startsWith("maggoteers_")) {
                deleteDir(f);
                LOG.warning("清理孤立世界(目录) " + f.getName());
            }
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
