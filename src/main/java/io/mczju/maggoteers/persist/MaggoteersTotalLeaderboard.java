package io.mczju.maggoteers.persist;

import com.github.mczjuops.mczjugamecore.player.data.AbstractPlayerData;
import com.github.mczjuops.mczjugamecore.score.leaderboard.PlayerDataLeaderboard;
import io.mczju.maggoteers.config.MessageService;

import java.util.Map;

/** 累计获得（totalEarned）降序排行榜（§13.5）。展示实体由运维 /mgcop leaderboard create 放置。 */
public class MaggoteersTotalLeaderboard extends PlayerDataLeaderboard {
    @Override public String getTitle() {
        return MessageService.raw("leaderboard.title", Map.of());
    }
    @Override public String getSubtitle() {
        return MessageService.raw("leaderboard.subtitle", Map.of());
    }
    @Override protected Class<? extends AbstractPlayerData> getPlayerDataClass() { return MaggoteersPlayerData.class; }
    @Override protected String getFieldName() { return "totalEarned"; }
    // fetchEntries() 由 PlayerDataLeaderboard 按 getFieldName 反射实现
    // getSortOrder() 默认 DESCENDING（AbstractLeaderboard），无需重写
}
