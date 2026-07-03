package io.mczju.maggoteers;

import com.github.mczjuops.mczjugamecore.MCZJUGameCore;
import io.mczju.maggoteers.game.MaggoteersGame;
import io.mczju.maggoteers.game.MaggoteersRoom;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.nio.file.Files;

public final class MaggoteersPlugin extends JavaPlugin {
    private static MaggoteersPlugin instance;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        saveDefaultRooms();   // G1：必须先释放房间实例，再注册（注册时会 loadGameRoom）
        MCZJUGameCore.getGameManager().registerGame(MaggoteersGame.class, MaggoteersRoom.class);
        getLogger().info("Maggoteers (卫戍协议) enabled, game 'maggoteers' registered.");
    }

    @Override
    public void onDisable() {
        getLogger().info("Maggoteers disabled.");
    }

    public static MaggoteersPlugin getInstance() {
        return instance;
    }

    /**
     * 释放默认房间 JSON 到 MGC 数据目录（若缺失）。
     * <p>G1：{@code /mgc join maggoteers} 需要至少一个 READY 房间，否则返回 null（提示无空闲房间）。
     * 零字段房间内容 {@code {}}；gameId/roomName 由 MGC loader 按文件名回填。
     */
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
