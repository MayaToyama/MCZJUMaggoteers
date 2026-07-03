package io.mczju.maggoteers.reward;

import java.util.List;
import java.util.Map;

public record RewardPool(String id, int cost, String currency, List<RewardOption> options) {
    public RewardPool {
        options = List.copyOf(options);
    }

    public String currencyKind() {
        return "boss".equals(currency) ? io.mczju.maggoteers.item.ItemService.CURRENCY_BOSS
                : io.mczju.maggoteers.item.ItemService.CURRENCY_NORMAL;
    }
}
