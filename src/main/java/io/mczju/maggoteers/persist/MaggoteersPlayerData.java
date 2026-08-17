package io.mczju.maggoteers.persist;

import com.github.mczjuops.mczjugamecore.player.data.JsonPlayerData;
import java.util.ArrayList;
import java.util.List;

/**
 * 跨局持久化（§13.1，MGC JsonPlayerData 落盘到 player_data/maggoteers/<uuid>.json）。
 * <p>字段必须 public（Gson 反射 + 排行榜按字段名 totalEarned 取值）。改完务必 {@link #setModified(true)}。
 */
public class MaggoteersPlayerData extends JsonPlayerData {
    public int balance = 0;            // 可花费账户货币
    public int totalEarned = 0;        // 累计获得（只增不减 = 排行榜积分）
    public List<String> unlocks = new ArrayList<>();   // 已解锁的 reward option.id

    public void grant(int amount) {
        if (amount <= 0) return;
        balance += amount;
        totalEarned += amount;
        setModified(true);
    }

    /** 调试：直接设余额（不改 totalEarned）。 */
    public void setBalance(int amount) {
        balance = Math.max(0, amount);
        setModified(true);
    }

    public boolean spend(int amount) {
        if (amount <= 0 || balance < amount) return false;
        balance -= amount;
        setModified(true);
        return true;
    }

    public boolean unlock(String optionId) {
        if (optionId == null || unlocks.contains(optionId)) return false;
        unlocks.add(optionId);
        setModified(true);
        return true;
    }
}
