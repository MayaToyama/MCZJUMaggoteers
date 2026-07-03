package io.mczju.maggoteers.game;

import com.github.mczjuops.mczjugamecore.game.AbstractGame;
import com.github.mczjuops.mczjugamecore.player.PlayerExt;
import com.github.mczjuops.mczjugamecore.player.strategy.AbstractPlayerDeathStrategy;
import io.mczju.maggoteers.state.PlayerState;
import io.mczju.maggoteers.state.PlayerStateManager;
import org.bukkit.GameMode;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;

/**
 * 死亡策略（借鉴前代 VampireSurvivor SurvivorPlayerDeathStrategy，改 reviveCount 复活机制）。
 */
public class MaggoteersDeathStrategy extends AbstractPlayerDeathStrategy {

    public MaggoteersDeathStrategy(AbstractGame game) {
        super(game);
    }

    @Override
    public void onPlayerDeath(PlayerExt victim, PlayerDeathEvent event) {
        Player p = victim.player();
        event.setCancelled(true);

        PlayerState st = PlayerStateManager.get(game, p.getUniqueId());
        if (st == null) {
            ((MaggoteersGame) game).fail();
            return;
        }

        if (st.tryAutoRevive()) {
            healAndInvuln(p);
            io.mczju.maggoteers.effect.EffectService.fireTrigger((io.mczju.maggoteers.game.MaggoteersGame) game, io.mczju.maggoteers.effect.Trigger.ON_REVIVE);
            io.mczju.maggoteers.effect.EffectService.resync(p);
            game.sender().info("<yellow>" + p.getName()
                    + " 倒下，自动复活！（剩余 " + st.getReviveCount() + " 次）");
            return;
        }

        st.markDown();
        healAndInvuln(p);
        p.setGameMode(GameMode.SPECTATOR);
        io.mczju.maggoteers.effect.EffectService.fireTrigger((io.mczju.maggoteers.game.MaggoteersGame) game, io.mczju.maggoteers.effect.Trigger.ON_DEATH);
        game.sender().warn("<red>" + p.getName() + " 倒下！转为观察者（可用复活币救援）。");

        if (!PlayerStateManager.isAnyAlive(game)) {
            ((MaggoteersGame) game).fail();
        }
    }

    private static void healAndInvuln(Player p) {
        var maxHp = p.getAttribute(Attribute.MAX_HEALTH);
        double max = maxHp != null ? maxHp.getValue() : 20.0;
        p.setHealth(Math.min(p.getHealth() <= 0 ? max : Math.max(p.getHealth(), 1.0), max));
        p.setFallDistance(0f);
        p.setFireTicks(0);
        p.setNoDamageTicks(40);
    }
}
