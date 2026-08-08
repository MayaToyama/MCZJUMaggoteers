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
            case STAT, BUNDLE -> isVisibleStat(opt, ps, pool);
        };
    }

    private static boolean isVisibleStat(RewardOption opt, PlayerState ps, RewardPool pool) {
        if (opt.isBundle()) {
            return opt.grants().stream().allMatch(g -> isVisibleStat(g, ps, pool));
        }
        if (ps == null) return true;
        PlayerEffect pe = ps.effects().stream().filter(e -> e.id().equals(opt.id())).findFirst().orElse(null);
        if (pe == null) return true;
        if (opt.stack() == Stack.UPGRADE_LEVEL) {
            int cap = UpgradeLevelCaps.resolve(pool, opt);
            return pe.level() < cap;
        }
        return false;
    }
}
