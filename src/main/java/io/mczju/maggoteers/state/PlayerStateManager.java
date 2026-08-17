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
import java.util.logging.Logger;

/**
 * 本局每玩家 {@link PlayerState} 注册表（借鉴前代 VampireSurvivor SessionManager）。
 * <p>除按对局 Identity 索引外，另有 UUID 索引：避免 MGC {@code getGame()} 与开局
 * {@code this} 非同一实例时查找失败（会导致职业属性不生效、死亡直接 fail）。
 */
public final class PlayerStateManager {
    private static final Logger LOG = Logger.getLogger("Maggoteers");
    private static final Map<AbstractGame, Map<UUID, PlayerState>> BY_GAME = new IdentityHashMap<>();
    private static final Map<UUID, PlayerState> BY_UUID = new HashMap<>();
    private static final Map<UUID, MaggoteersGame> GAME_BY_UUID = new HashMap<>();

    /** 开局为某玩家建状态（reviveCount 来自 config lives.default）。 */
    public static PlayerState init(AbstractGame game, UUID uuid, int reviveCount) {
        PlayerState st = new PlayerState(uuid, reviveCount);
        BY_GAME.computeIfAbsent(game, g -> new HashMap<>()).put(uuid, st);
        BY_UUID.put(uuid, st);
        if (game instanceof MaggoteersGame mg) {
            GAME_BY_UUID.put(uuid, mg);
        }
        return st;
    }

    public static PlayerState get(AbstractGame game, UUID uuid) {
        if (uuid == null) return null;
        if (game != null) {
            Map<UUID, PlayerState> m = BY_GAME.get(game);
            if (m != null) {
                PlayerState st = m.get(uuid);
                if (st != null) return st;
            }
        }
        // 回退：不依赖 game 实例 identity（MGC getGame() 偶发不一致）
        return BY_UUID.get(uuid);
    }

    /** 仅按 UUID 取状态（死亡 / 效果分发优先用这个）。 */
    public static PlayerState getByUuid(UUID uuid) {
        return uuid == null ? null : BY_UUID.get(uuid);
    }

    /** 按玩家 UUID 查找所在对局（不依赖 MGC {@code getGame()} 实例是否一致）。 */
    public static MaggoteersGame gameForPlayer(UUID uuid) {
        if (uuid == null) return null;
        MaggoteersGame direct = GAME_BY_UUID.get(uuid);
        if (direct != null) return direct;
        for (var e : BY_GAME.entrySet()) {
            if (e.getValue().containsKey(uuid) && e.getKey() instanceof MaggoteersGame mg) {
                return mg;
            }
        }
        return null;
    }

    /** 解析玩家所在对局：先 registry（更稳），回退 MGC {@code PlayerExt} 实例判断。 */
    public static MaggoteersGame gameOf(Player p) {
        if (p == null) return null;
        MaggoteersGame bound = gameForPlayer(p.getUniqueId());
        if (bound != null) return bound;
        var pe = new com.github.mczjuops.mczjugamecore.player.PlayerExt(p);
        if (!pe.isInGame()) return null;
        AbstractGame g = pe.getGame();
        return g instanceof MaggoteersGame mg ? mg : null;
    }

    /**
     * 本局仍在役：有 {@link PlayerState} 且 {@link PlayerState#isAlive()}。
     * 不检查 GameMode（测试/创造模式切换不影响光环、治疗等）。
     */
    public static boolean isRunParticipant(AbstractGame game, Player p) {
        if (p == null || !p.isOnline()) return false;
        PlayerState st = get(game, p.getUniqueId());
        return st != null && st.isAlive();
    }

    /** 场上是否还有仍在役玩家（仅看 PlayerState）。 */
    public static boolean isAnyAlive(AbstractGame game) {
        if (game != null) {
            Map<UUID, PlayerState> m = BY_GAME.get(game);
            if (m != null && m.values().stream().anyMatch(PlayerState::isAlive)) {
                return true;
            }
            // MGC getPlayers() + UUID 索引：不依赖 IdentityHashMap 的 game 实例是否一致
            for (var pe : game.getPlayers()) {
                if (pe == null || pe.player() == null) continue;
                PlayerState st = BY_UUID.get(pe.player().getUniqueId());
                if (st != null && st.isAlive()) return true;
            }
        }
        // game identity 对不上时：看该局登记过的 UUID
        if (game instanceof MaggoteersGame) {
            for (var e : GAME_BY_UUID.entrySet()) {
                if (e.getValue() == game) {
                    PlayerState st = BY_UUID.get(e.getKey());
                    if (st != null && st.isAlive()) return true;
                }
            }
        }
        return false;
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
        MaggoteersGame mg = game instanceof MaggoteersGame g ? g : gameForPlayer(uuid);
        if (mg != null) {
            EffectService.fireTriggerPlayer(mg, p, Trigger.ON_REVIVE,
                    TriggerContext.atEvent(Trigger.ON_REVIVE, p.getLocation()));
            EffectService.resync(p);
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
        clearFallFire(p);
        p.setNoDamageTicks(40);
    }

    /** 按当前 MAX_HEALTH 属性回满血（须在 EffectService.resync 之后调用）。 */
    public static void restoreFullHealth(Player p) {
        if (p == null) return;
        healToMax(p);
        clearFallFire(p);
        p.setNoDamageTicks(40);
    }

    /** 清摔落距离与着火状态（复活/进层/回大厅共用）。 */
    public static void clearFallFire(Player p) {
        if (p == null) return;
        p.setFallDistance(0f);
        p.setFireTicks(0);
    }

    /** 按当前 MAX_HEALTH 回满血；MAX_HEALTH 异常 ≤0 时回落 20.0（保留 restoreFullHealth 原守卫语义）。 */
    public static void healToMax(Player p) {
        if (p == null) return;
        var maxHp = p.getAttribute(Attribute.MAX_HEALTH);
        double max = maxHp != null ? maxHp.getValue() : 20.0;
        if (max <= 0) max = 20.0;
        p.setHealth(max);
    }

    /** 结束钩子：清掉本局所有玩家状态。 */
    public static void destroyAll(AbstractGame game) {
        Map<UUID, PlayerState> m = BY_GAME.remove(game);
        if (m != null) {
            for (UUID uuid : m.keySet()) {
                BY_UUID.remove(uuid);
                GAME_BY_UUID.remove(uuid);
            }
            return;
        }
        // identity 对不上：按 MaggoteersGame 引用扫 UUID 索引
        if (game instanceof MaggoteersGame mg) {
            GAME_BY_UUID.entrySet().removeIf(e -> {
                if (e.getValue() == mg) {
                    BY_UUID.remove(e.getKey());
                    return true;
                }
                return false;
            });
        }
    }

    /** 仍有 PlayerState 的对局（含 MGC gameList 未收录的运行中实例）。 */
    public static Set<AbstractGame> gamesWithState() {
        return Set.copyOf(BY_GAME.keySet());
    }

    public static Set<UUID> uuidsInGame(AbstractGame game) {
        if (game != null) {
            Map<UUID, PlayerState> m = BY_GAME.get(game);
            if (m != null) return Set.copyOf(m.keySet());
        }
        if (game instanceof MaggoteersGame mg) {
            Set<UUID> out = new java.util.HashSet<>();
            for (var e : GAME_BY_UUID.entrySet()) {
                if (e.getValue() == mg) out.add(e.getKey());
            }
            return Set.copyOf(out);
        }
        return Set.of();
    }

    /** 诊断：状态缺失时打日志（勿在热路径高频调用）。 */
    public static void warnMissingState(String where, AbstractGame game, UUID uuid) {
        LOG.warning(where + ": PlayerState 缺失 uuid=" + uuid
                + " gameIdentity=" + (game == null ? "null" : System.identityHashCode(game))
                + " byUuid=" + (BY_UUID.containsKey(uuid))
                + " gamesTracked=" + BY_GAME.size());
    }

    private PlayerStateManager() {}
}
