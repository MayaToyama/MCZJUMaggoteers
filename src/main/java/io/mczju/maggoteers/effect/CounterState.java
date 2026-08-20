package io.mczju.maggoteers.effect;

/** 运行期计数器实例（出生/获取时加载，卸载/移除时丢弃）。 */
public final class CounterState {
    private final CounterSpec spec;
    private int progress;

    public CounterState(CounterSpec spec) {
        this.spec = spec;
        this.progress = 0;
    }

    public CounterSpec spec() { return spec; }
    public String id() { return spec.id(); }
    public int progress() { return progress; }

    /** 计满返回 true 并清零（循环语义）；否则 false。 */
    public boolean increment() {
        progress++;
        if (progress >= spec.amount()) {
            progress = 0;
            return true;
        }
        return false;
    }

    public void reset() { progress = 0; }
}
