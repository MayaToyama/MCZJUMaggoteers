package io.mczju.maggoteers.mob;

import com.github.mczjuops.mczjugamecore.game.AbstractGame;
import io.mczju.maggoteers.game.MaggoteersGame;
import io.mczju.maggoteers.state.PlayerStateManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;

import java.util.UUID;

/** 骑乘小队：controller 选目标，root 位移。 */
public final class MountedSquadAiService {

    private static final double TARGET_RANGE = 32.0;

    private MountedSquadAiService() {}

    public static void tick(AbstractGame game) {
        if (!(game instanceof MaggoteersGame)) return;
        for (MountedSquadRegistry.Squad squad : MountedSquadRegistry.snapshot().values()) {
            var rootEnt = Bukkit.getEntity(squad.rootUuid());
            var ctrlEnt = Bukkit.getEntity(squad.controllerUuid());
            if (!(rootEnt instanceof LivingEntity root) || root.isDead() || !root.isValid()) {
                MountedSquadRegistry.removeByRoot(squad.rootUuid());
                continue;
            }
            if (!(ctrlEnt instanceof Mob controller) || controller.isDead() || !controller.isValid()) {
                MountedSquadRegistry.removeByRoot(squad.rootUuid());
                continue;
            }
            Player target = nearestParticipant(game, root.getLocation());
            if (target == null) continue;
            controller.setTarget(target);
            if (root instanceof Mob rootMob) {
                rootMob.getPathfinder().moveTo(target, movementSpeed(rootMob));
            }
        }
    }

    private static double movementSpeed(Mob mob) {
        AttributeInstance spd = mob.getAttribute(Attribute.MOVEMENT_SPEED);
        if (spd == null) return 0.25;
        // Pathfinder#moveTo 的 speed 与实体移速属性同量级；勿 ×20（会导致蜘蛛骑士异常冲刺）
        return Math.max(0.05, spd.getValue());
    }

    private static Player nearestParticipant(AbstractGame game, Location from) {
        Player best = null;
        double bestD = TARGET_RANGE * TARGET_RANGE;
        if (!(game instanceof MaggoteersGame)) return null;
        for (UUID uuid : PlayerStateManager.uuidsInGame(game)) {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null || !p.isOnline()) continue;
            if (!PlayerStateManager.isRunParticipant(game, p)) continue;
            if (!p.getWorld().equals(from.getWorld())) continue;
            double d = p.getLocation().distanceSquared(from);
            if (d <= bestD) {
                bestD = d;
                best = p;
            }
        }
        return best;
    }
}
