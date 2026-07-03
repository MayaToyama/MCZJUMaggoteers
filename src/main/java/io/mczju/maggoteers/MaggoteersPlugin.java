package io.mczju.maggoteers;

import org.bukkit.plugin.java.JavaPlugin;

/** 插件主类。注册游戏、释放默认房间、托管生命周期。 */
public final class MaggoteersPlugin extends JavaPlugin {
    private static MaggoteersPlugin instance;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        getLogger().info("Maggoteers (卫戍协议) enabling.");
        // Task 2 在此追加：saveDefaultRooms() + registerGame(...)
    }

    @Override
    public void onDisable() {
        getLogger().info("Maggoteers disabled.");
    }

    public static MaggoteersPlugin getInstance() {
        return instance;
    }
}
