package io.mczju.maggoteers.state;

import com.github.mczjuops.mczjugamecore.game.AbstractGame;
import io.mczju.maggoteers.game.MaggoteersGame;
import io.mczju.maggoteers.effect.EffectService;
import io.mczju.maggoteers.effect.Trigger;
import io.mczju.maggoteers.effect.TriggerContext;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 本局每玩家 {@link PlayerState} 注册表（借鉴前代 VampireSurvivor SessionManager）。
 */
public final class PlayerStateManager {
    private static final Map<AbstractGame, Map<UUID, PlayerState>> BY_GAME = new IdentityHashMap<>();

    /** 开局为某玩家建状态（reviveCount 来自 config lives.default）。 */
    public static PlayerState init(AbstractGame game, UUID uuid, int reviveCount) {
        PlayerState st = new PlayerState(uuid, reviveCount);
        BY_GAME.computeIfAbsent(game, g -> new HashMap<>()).put(uuid, st);
        return st;
    }

    public static PlayerState get(AbstractGame game, UUID uuid) {
        Map<UUID, PlayerState> m = BY_GAME.get(game);
        return m == null ? null : m.get(uuid);
    }

    /** 按玩家 UUID 查找所在对局（不依赖 MGC {@code getGame()} 实例是否一致）。 */
    public static MaggoteersGame gameForPlayer(UUID uuid) {
        for (var e : BY_GAME.entrySet()) {
            if (e.getValue().containsKey(uuid) && e.getKey() instanceof MaggoteersGame mg) {
                return mg;
            }
        }
        return null;
    }

    /**
     * 本局仍在役：有 {@link PlayerState} 且 {@link PlayerState#isAlive()}。
     * 不检查 GameMode（测试/创造模式切换不影响光环、治疗等）。
     */
    public static boolean isRunParticipant(AbstractGame game, Player p) {
        if (p == null || !p.isOnline() || game == null) return false;
        PlayerState st = get(game, p.getUniqueId());
        return st != null && st.isAlive();
    }

    /** 场上是否还有仍在役玩家（仅看 PlayerState）。 */
    public static boolean isAnyAlive(AbstractGame game) {
        Map<UUID, PlayerState> m = BY_GAME.get(game);
        if (m == null) return false;
        return m.values().stream().anyMatch(PlayerState::isAlive);
    }

    /**
     * 复活币 · 对死者：拉回冒险模式（仅复活，不加次数）。返回是否成功（死者才需复活）。
     * <p>Plan 6 复活币 GUI 对死者头像调用。
     */
    public static boolean revivePlayer(AbstractGame game, UUID uuid) {
        PlayerState st = get(game, uuid);
        if (st == null || st.isAlive()) return false;
        st.setAlive(true);
        Player p = Bukkit.getPlayer(uuid);
        if (p == null) return true;
        applyAdventure(p);
        if (game instanceof MaggoteersGame mg) {
            EffectService.fireTriggerPlayer(mg, p, Trigger.ON_REVIVE,
                    TriggerContext.atEvent(Trigger.ON_REVIVE, p.getLocation()));
            EffectService.resync(p);
            io.mczju.maggoteers.reward.CollectibleService.resync(p, mg);
        }
        restoreFullHealth(p);
        return true;
    }

    /**
     * 复活币 · 对活者：reviveCount + n（不改变当前存活状态）。
     * <p>Plan 6 复活币 GUI 对活者头像调用。
     */
    public static void addReviveCount(AbstractGame game, UUID uuid, int n) {
        PlayerState st = get(game, uuid);
        if (st == null) return;
        st.setReviveCount(st.getReviveCount() + n);
    }

    /** 把玩家设为冒险模式（血量须另调 {@link #restoreFullHealth}，通常在 resync 之后）。 */
    static void applyAdventure(Player p) {
        if (p == null) return;
        p.setGameMode(GameMode.ADVENTURE);
        p.setFallDistance(0f);
        p.setFireTicks(0);
        p.setNoDamageTicks(40);
    }

    /** 按当前 MAX_HEALTH 属性回满血（须在 EffectService.resync 之后调用）。 */
    public static void restoreFullHealth(Player p) {
        if (p == null) return;
        var maxHp = p.getAttribute(Attribute.MAX_HEALTH);
        double max = maxHp != null ? maxHp.getValue() : 20.0;
        p.setHealth(max);
        p.setFallDistance(0f);
        p.setFireTicks(0);
        p.setNoDamageTicks(40);
    }

    /** 结束钩子：清掉本局所有玩家状态。 */
    public static void destroyAll(AbstractGame game) {
        BY_GAME.remove(game);
    }

    /** 仍有 PlayerState 的对局（含 MGC gameList 未收录的运行中实例）。 */
    public static Set<AbstractGame> gamesWithState() {
        return Set.copyOf(BY_GAME.keySet());
    }

    public static Set<UUID> uuidsInGame(AbstractGame game) {
        Map<UUID, PlayerState> m = BY_GAME.get(game);
        return m == null ? Set.of() : Set.copyOf(m.keySet());
    }

    private PlayerStateManager() {}
}
