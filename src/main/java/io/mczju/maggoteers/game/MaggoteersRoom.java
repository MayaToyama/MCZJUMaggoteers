package io.mczju.maggoteers.game;

import com.github.mczjuops.mczjugamecore.game.AbstractGame;
import com.github.mczjuops.mczjugamecore.game.room.JsonGameRoom;
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

    /** 对局结束回程点；未设置则结束时不传送。 */
    public Location returnLocation;

    public static MaggoteersRoom of(AbstractGame game) {
        if (game == null) return null;
        return game.getGameRoom() instanceof MaggoteersRoom r ? r : null;
    }

    static void teleportWait(AbstractGame game, Player player) {
        MaggoteersRoom room = of(game);
        teleport(player, room == null ? null : room.waitLocation);
    }

    static void teleportReturn(AbstractGame game, Player player) {
        MaggoteersRoom room = of(game);
        teleport(player, room == null ? null : room.returnLocation);
    }

    private static void teleport(Player player, Location loc) {
        if (player == null || loc == null || loc.getWorld() == null) return;
        player.teleport(loc);
    }
}
