package io.mczju.maggoteers.wave;

import java.util.ArrayList;
import java.util.List;

/**
 * 一波：steps 按序（含 delayTicks 时序）执行，整体重复 {@code repeat} 次。
 * <p>展开语义：{@link #expand()} 把 steps 串 repeat 份；每份内每步 spawn {@link SpawnStep#count} 只于其 point。
 */
public record WaveSpec(List<SpawnStep> steps, int repeat, List<RewardItem> clearReward) {
    public WaveSpec {
        steps = List.copyOf(steps);
        clearReward = clearReward == null ? List.of() : List.copyOf(clearReward);
        repeat = Math.max(1, repeat);
    }

    /** 展平成有序 spawn 序列（steps × repeat）。repeat 份之间无额外延迟（每步自带 delayTicks）。 */
    public List<SpawnStep> expand() {
        List<SpawnStep> out = new ArrayList<>(steps.size() * repeat);
        for (int r = 0; r < repeat; r++) out.addAll(steps);
        return out;
    }
}
