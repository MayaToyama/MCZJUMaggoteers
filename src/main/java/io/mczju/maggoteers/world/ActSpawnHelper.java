package io.mczju.maggoteers.world;

import com.github.mczjuops.mczjugamecore.game.AbstractGame;
import io.mczju.maggoteers.MaggoteersPlugin;
import io.mczju.maggoteers.plan.ActPlan;
import io.mczju.maggoteers.world.MapRepository.MapEntry;
import org.bukkit.Location;
import org.bukkit.World;

/** 开局进图：粘贴 Act1 并传送至 playerSpawn（职业选择前的特殊休整期）。 */
public final class ActSpawnHelper {
    private ActSpawnHelper() {}

    public static void pasteAndTeleport(AbstractGame game, ActPlan act, int actIndex) {
        World w = WorldService.get(game);
        if (w == null) return;
        String actKey = "act" + (actIndex + 1);
        var originList = MaggoteersPlugin.getInstance().getConfig().getIntegerList("act_origins." + actKey);
        int ox = originList.isEmpty() ? 0 : originList.get(0);
        int oy = originList.size() > 1 ? originList.get(1) : 64;
        int oz = originList.size() > 2 ? originList.get(2) : 0;
        boolean fallback = MaggoteersPlugin.getInstance().getConfig().getBoolean("map.fallback_platform", true);
        MapEntry map = MapRepository.getMaps(actKey).stream()
                .filter(m -> m.mapId().equals(act.mapId())).findFirst().orElse(null);
        if (map != null) {
            StructurePaster.pasteAct(w, ox, oy, oz, map.dir(), map, fallback);
        }
        var spawn = act.playerSpawn();
        Location loc = new Location(w, spawn.x(), spawn.y(), spawn.z());
        game.getPlayers().forEach(pe -> {
            pe.player().teleport(loc);
            pe.player().setFallDistance(0f);
        });
    }
}
