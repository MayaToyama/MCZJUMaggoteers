package io.mczju.maggoteers;

import com.github.mczjuops.mczjugamecore.MCZJUGameCore;
import com.github.mczjuops.mczjugamecore.menu.MenuFacade;
import io.mczju.maggoteers.config.AffixService;

import io.mczju.maggoteers.config.ScalingConfig;
import io.mczju.maggoteers.config.WavesConfig;
import io.mczju.maggoteers.game.MaggoteersGame;
import io.mczju.maggoteers.game.MaggoteersRoom;
import io.mczju.maggoteers.item.ItemInteractRouter;
import io.mczju.maggoteers.item.ItemService;
import io.mczju.maggoteers.listener.MobDeathListener;
import io.mczju.maggoteers.menu.ClassSelectMenu;
import io.mczju.maggoteers.menu.PickMenu;
import io.mczju.maggoteers.menu.RestMenu;
import io.mczju.maggoteers.reward.RewardService;
import io.mczju.maggoteers.world.MapRepository;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.nio.file.Files;

public final class MaggoteersPlugin extends JavaPlugin {
    private static MaggoteersPlugin instance;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        saveResourceIfMissing("rewards.yml");
        io.mczju.maggoteers.world.WorldService.cleanupOrphansOnEnable();
        saveDefaultRooms();
        MCZJUGameCore.getGameManager().registerGame(MaggoteersGame.class, MaggoteersRoom.class);
        MCZJUGameCore.getPlayerDataManager().registerPlayerData("maggoteers", io.mczju.maggoteers.persist.MaggoteersPlayerData.class);
        MCZJUGameCore.getLeaderboardManager().registerLeaderboard("maggoteers_total", io.mczju.maggoteers.persist.MaggoteersTotalLeaderboard.class);
        AffixService.load(this);
        WavesConfig.loadFromFile(this);
        MapRepository.load(this);
        ScalingConfig.load(this);
        ItemService.init(this);
        io.mczju.maggoteers.item.ItemInteractRouter.registerHandler(
                "maggoteers:test_blade", new io.mczju.maggoteers.item.interact.TestBladeHandler());
        RewardService.load(this);
        MenuFacade.registerMenu("maggoteers-class", ClassSelectMenu.class);
        MenuFacade.registerMenu("maggoteers-rest", RestMenu.class);
        MenuFacade.registerMenu("maggoteers-pick", PickMenu.class);
        MenuFacade.registerMenu("maggoteers-revive", io.mczju.maggoteers.menu.ReviveMenu.class);
        MenuFacade.registerMenu("maggoteers-shop", io.mczju.maggoteers.menu.UnlockShopMenu.class);
        getServer().getPluginManager().registerEvents(new MobDeathListener(), this);
        getServer().getPluginManager().registerEvents(new io.mczju.maggoteers.listener.CombatAffixListener(), this);
        getServer().getPluginManager().registerEvents(new ItemInteractRouter(), this);
        getServer().getPluginManager().registerEvents(new io.mczju.maggoteers.effect.EffectListener(), this);
        getServer().getPluginManager().registerEvents(new io.mczju.maggoteers.listener.PurifyListener(), this);
        getServer().getPluginManager().registerEvents(new io.mczju.maggoteers.listener.GameplayTickListener(), this);
        io.mczju.maggoteers.listener.GameplayTickListener.start();
        var mc = getCommand("maggoteers");
        if (mc != null) mc.setExecutor(new io.mczju.maggoteers.command.MaggoteersCommand());
        getLogger().info("Maggoteers (卫戍协议) enabled, game 'maggoteers' registered.");
    }

    @Override
    public void onDisable() {
        io.mczju.maggoteers.listener.GameplayTickListener.stop();
        io.mczju.maggoteers.effect.EffectListener.stopTick();
        getLogger().info("Maggoteers disabled.");
    }

    public static MaggoteersPlugin getInstance() {
        return instance;
    }

    private void saveResourceIfMissing(String name) {
        File f = new File(getDataFolder(), name);
        if (!f.exists()) saveResource(name, false);
    }

    private void saveDefaultRooms() {
        var mgc = JavaPlugin.getPlugin(MCZJUGameCore.class);
        if (mgc == null) {
            getLogger().severe("MCZJUGameCore 未加载，无法释放默认房间！游戏将无法 join。");
            return;
        }
        File dir = new File(mgc.getDataFolder(), "rooms/maggoteers");
        File def = new File(dir, "default.json");
        if (def.exists()) return;
        if (!dir.exists() && !dir.mkdirs()) {
            getLogger().warning("无法创建目录 " + dir);
            return;
        }
        try {
            Files.writeString(def.toPath(), "{}");
            getLogger().info("已释放默认房间: " + def.getPath());
        } catch (Exception e) {
            getLogger().warning("写出默认房间失败: " + e.getMessage());
        }
    }
}
