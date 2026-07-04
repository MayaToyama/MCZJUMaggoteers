package io.mczju.maggoteers.persist;

import com.github.mczjuops.mczjugamecore.player.data.AbstractPlayerData;
import com.github.mczjuops.mczjugamecore.score.leaderboard.PlayerDataLeaderboard;

/** 累计获得（totalEarned）降序排行榜（§13.5）。展示实体由运维 /mgcop leaderboard create 放置。 */
public class MaggoteersTotalLeaderboard extends PlayerDataLeaderboard {
    @Override public String getTitle() { return "<gold>卫戍协议 · 累计卫戍币"; }
    @Override public String getSubtitle() { return "<yellow>通关与失败结算的累计获得"; }
    @Override protected Class<? extends AbstractPlayerData> getPlayerDataClass() { return MaggoteersPlayerData.class; }
    @Override protected String getFieldName() { return "totalEarned"; }
    // fetchEntries() 由 PlayerDataLeaderboard 按 getFieldName 反射实现
    // getSortOrder() 默认 DESCENDING（AbstractLeaderboard），无需重写
}
