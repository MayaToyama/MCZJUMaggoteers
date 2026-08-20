package io.mczju.maggoteers.game;

import com.github.mczjuops.mczjugamecore.game.AbstractGame;
import com.github.mczjuops.mczjugamecore.game.room.JsonGameRoom;
import io.mczju.maggoteers.MaggoteersPlugin;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * MGC 房间数据（{@code plugins/MCZJUGameCore/rooms/maggoteers/<name>.json}）。
 * <p>字段须 {@code public}，类型仅 Location / 包装类。MGC 1.0.7 编辑菜单不认 {@code @FieldDescription}。
 * 运维：{@code /mgcop room create maggoteers <名>}，{@code /mgcop room edit maggoteers <名>}。
 */
public final class MaggoteersRoom extends JsonGameRoom {

    /** 等待阶段集合点；未设置则 join 后不传送。 */
    public Location waitLocation;

    /** 离开对局 / 局末回程点（可跨世界，须指向 lobby 等已加载世界）；未设置则回退 waitLocation。 */
    public Location returnLocation;

    public static MaggoteersRoom of(AbstractGame game) {
        if (game == null) return null;
        return game.getGameRoom() instanceof MaggoteersRoom r ? r : null;
    }

    static void teleportWait(AbstractGame game, Player player) {
        MaggoteersRoom room = of(game);
        teleport(player, room == null ? null : room.waitLocation);
    }

    /**
     * 送回大厅：优先 {@link #returnLocation}，否则 {@link #waitLocation}。
     * Bukkit {@code teleport(Location)} 可跨世界（源世界可为 maggoteers_*）。
     */
    static void teleportReturn(AbstractGame game, Player player) {
        MaggoteersRoom room = of(game);
        if (room == null) return;
        Location dest = room.returnLocation != null ? room.returnLocation : room.waitLocation;
        teleport(player, dest);
    }

    private static void teleport(Player player, Location loc) {
        if (player == null || loc == null) return;
        // Gson 反序列化后若世界名无效，getWorld() 为 null，跨世界 tp 会静默失败
        if (loc.getWorld() == null) {
            MaggoteersPlugin plug = MaggoteersPlugin.getInstance();
            if (plug != null) {
                plug.getLogger().warning("房间传送目标世界未加载，跳过: " + loc);
            }
            return;
        }
        player.teleport(loc);
    }
}
