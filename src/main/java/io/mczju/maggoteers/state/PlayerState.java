package io.mczju.maggoteers.state;

import java.util.UUID;

/**
 * 单局单玩家**本局真相源**（§19）：reviveCount（剩余自动复活次数）+ alive（冒险=参战 / 观察=倒下）。
 * <p>Plan 5 起在此扩展效果列表；Plan 6 起扩展 acquiredUnique 集。本 Plan 仅含死亡/复活所需字段。
 */
public final class PlayerState {
    private final UUID uuid;
    private int reviveCount;
    private boolean alive;

    public PlayerState(UUID uuid, int reviveCount) {
        this.uuid = uuid;
        this.reviveCount = Math.max(0, reviveCount);
        this.alive = true;
    }

    public UUID uuid() { return uuid; }
    public int getReviveCount() { return reviveCount; }
    public void setReviveCount(int n) { this.reviveCount = Math.max(0, n); }
    public boolean isAlive() { return alive; }
    public void setAlive(boolean alive) { this.alive = alive; }

    /** 死亡时调：有复活次数则消耗一次、保持存活，返回 true；否则返回 false（调用方再 markDown）。 */
    public boolean tryAutoRevive() {
        if (reviveCount > 0) {
            reviveCount--;
            alive = true;
            return true;
        }
        return false;
    }

    /** 复活次数耗尽：标记为倒下（观察者）。 */
    public void markDown() { alive = false; }
}
