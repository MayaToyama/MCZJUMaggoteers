package io.mczju.maggoteers.reward;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RewardPoolRoutingTest {

    @Test
    void normalShopTier_act3AlwaysStrong() {
        assertEquals("strong", RewardService.normalShopTier(2, "weak"));
        assertEquals("strong", RewardService.normalShopTier(2, "strong"));
        assertEquals("strong", RewardService.normalShopTier(2, "boss"));
    }

    @Test
    void normalShopTier_act1And2FollowLastTier() {
        assertEquals("weak", RewardService.normalShopTier(0, "weak"));
        assertEquals("strong", RewardService.normalShopTier(0, "strong"));
        assertEquals("strong", RewardService.normalShopTier(0, "boss"));
        assertEquals("weak", RewardService.normalShopTier(1, "weak"));
        assertEquals("strong", RewardService.normalShopTier(1, "strong"));
    }

    @Test
    void poolForWave_ids() {
        assertEquals("act3_strong", RewardService.poolForWave(2, "strong"));
        assertEquals("act3_boss", RewardService.poolForWave(2, "boss"));
        assertEquals("act2_boss", RewardService.poolForWave(1, "boss"));
    }
}
