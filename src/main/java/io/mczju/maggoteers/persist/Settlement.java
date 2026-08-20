package io.mczju.maggoteers.persist;

import io.mczju.maggoteers.game.GameOutcome;

/**
 * 结算纯函数（§7.6，与 Bukkit 解耦，单测）。
 * <p>WIN = win_flat；FAIL = fail_per_act × 已过层数 + fail_per_wave × 当前层已过波数；IN_PROGRESS = 0。
 */
public final class Settlement {
    public record Input(GameOutcome outcome, int actsCleared, int wavesClearedInCurrentAct) {}

    public static int grant(Input in, int winFlat, int failPerAct, int failPerWave) {
        if (in == null || in.outcome() == null) return 0;
        return switch (in.outcome()) {
            case WIN  -> winFlat;
            case FAIL -> Math.max(0, failPerAct) * Math.max(0, in.actsCleared())
                       + Math.max(0, failPerWave) * Math.max(0, in.wavesClearedInCurrentAct());
            default   -> 0;   // IN_PROGRESS
        };
    }

    private Settlement() {}
}
