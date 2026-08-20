package io.mczju.maggoteers.effect;

/**
 * 计数器规格（effect + trigger 中继器）。
 * <p>{@code countTrigger} 为输入；计满<b>只输出 {@code ON_COUNTER}</b>（由 CounterService
 * 对绑定该计数器 id 的被动 fire）。{@code resetOn} 为该 trigger 触发时清零（null=不自动清零；
 * 但计满即清零——秒表/循环语义）。{@code condition} 为该 trigger 计数时才评估（不满足则本次不计数）。
 */
public record CounterSpec(
        String id,
        CounterCountTrigger countTrigger,
        int amount,
        Trigger resetOn,
        CounterCondition condition
) {
    public CounterSpec {
        id = id == null ? "" : id.trim();
        amount = Math.max(1, amount);
    }

    public boolean triggeredBy(CounterCountTrigger t) {
        return countTrigger == t;
    }
}
