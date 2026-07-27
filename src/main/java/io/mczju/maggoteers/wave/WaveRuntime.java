package io.mczju.maggoteers.wave;

import com.github.mczjuops.mczjugamecore.game.AbstractGame;
import org.bukkit.boss.BossBar;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 单对局波次运行时状态（借鉴前代 VampireSurvivor WaveContext）。
 * <p>由 {@link WaveEngine} 以 {@code IdentityHashMap<game, runtime>} 持有；心跳扫描由 WaveScheduler 统一驱动。
 */
public final class WaveRuntime {
    final AbstractGame game;
    /** 本波仍存活怪物 UUID。 */
    final Set<UUID> livingMobs = new HashSet<>();
    /** UUID → 其 SpawnStep（Boss 条等；乘客/死亡召唤可能为 null）。 */
    final Map<UUID, SpawnStep> mobSteps = new HashMap<>();
    /** UUID → 运行时档案（词缀命中、死亡召唤、掉落倍率）。 */
    final Map<UUID, MobSpawnProfile> mobProfiles = new HashMap<>();
    /** Boss 血条（按实体 UUID）。 */
    final Map<UUID, BossBar> bossBars = new HashMap<>();
    /** 本波是否已判定清除（防重复触发）。 */
    boolean cleared = false;

    WaveRuntime(AbstractGame game) { this.game = game; }
}
