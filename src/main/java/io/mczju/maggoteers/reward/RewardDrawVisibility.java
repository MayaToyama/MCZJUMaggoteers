package io.mczju.maggoteers.reward;

import io.mczju.maggoteers.effect.PlayerEffect;
import io.mczju.maggoteers.effect.Stack;
import io.mczju.maggoteers.state.PlayerState;

import java.util.Set;

/** Pure draw visibility for reward pools (STAT/WEAPON once, SUPPLY repeatable, UPGRADE until max). */
public final class RewardDrawVisibility {

    private RewardDrawVisibility() {}

    public static boolean isVisible(RewardOption opt, PlayerState ps, Set<String> acquiredIds) {
        return isVisible(opt, ps, acquiredIds, null);
    }

    public static boolean isVisible(RewardOption opt, PlayerState ps, Set<String> acquiredIds, RewardPool pool) {
        return switch (opt.category()) {
            case SUPPLY -> true;
            case WEAPON -> acquiredIds == null || !acquiredIds.contains(opt.id());
            case STAT, BUNDLE -> isVisibleStat(opt, ps, acquiredIds, pool);
        };
    }

    private static boolean isVisibleStat(RewardOption opt, PlayerState ps, Set<String> acquiredIds,
                                         RewardPool pool) {
        if (opt.isBundle()) {
            // bundle 以自身 id 的 acquiredUnique 判定一次性（R11）：含 WEAPON/SUPPLY grant 的
            // bundle 不会因 grant 不进 acquiredUnique 而恒可见、无限重复发放。
            return acquiredIds == null || !acquiredIds.contains(opt.id());
        }
        if (ps == null) return true;
        PlayerEffect pe = ps.effects().stream().filter(e -> e.id().equals(opt.id())).findFirst().orElse(null);
        if (pe == null) {
            // One-shot STATs that never leave an effect (legacy instant GRANT_REVIVE) still
            // mark acquiredUnique; UPGRADE_LEVEL first acquire has empty acquired → visible.
            if (opt.stack() != Stack.UPGRADE_LEVEL
                    && acquiredIds != null
                    && acquiredIds.contains(opt.id())) {
                return false;
            }
            return true;
        }
        if (opt.stack() == Stack.UPGRADE_LEVEL) {
            int cap = UpgradeLevelCaps.resolve(pool, opt);
            return pe.level() < cap;
        }
        return false;
    }
}
