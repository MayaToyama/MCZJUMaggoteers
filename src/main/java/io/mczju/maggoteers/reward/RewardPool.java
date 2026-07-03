package io.mczju.maggoteers.reward;

import io.mczju.maggoteers.item.ItemKind;

import java.util.List;
import java.util.Map;

public record RewardPool(String id, int cost, String currency, List<RewardOption> options) {
    public RewardPool {
        options = List.copyOf(options);
    }

    public String currencyKind() {
        return "boss".equals(currency) ? ItemKind.CURRENCY_BOSS.pdcValue()
                : ItemKind.CURRENCY_NORMAL.pdcValue();
    }

    public ItemKind currencyItemKind() {
        return "boss".equals(currency) ? ItemKind.CURRENCY_BOSS : ItemKind.CURRENCY_NORMAL;
    }
}
