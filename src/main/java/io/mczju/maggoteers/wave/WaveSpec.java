package io.mczju.maggoteers.wave;

import java.util.ArrayList;
import java.util.List;

/**
 * 一波：steps 按序（含 delayTicks 时序）执行，整体重复 {@code repeat} 次。
 * <p>展开语义：{@link #expand()} 把 steps 串 repeat 份；每份内每步 spawn {@link SpawnStep#count} 只于其 point。
 */
public record WaveSpec(String strategyId, List<SpawnStep> steps, int repeat, List<RewardItem> clearReward) {
    public WaveSpec {
        strategyId = strategyId == null ? "" : strategyId;
        steps = List.copyOf(steps);
        clearReward = clearReward == null ? List.of() : List.copyOf(clearReward);
        repeat = Math.max(1, repeat);
    }

    /** 测试/占位：无 strategy id。 */
    public WaveSpec(List<SpawnStep> steps, int repeat, List<RewardItem> clearReward) {
        this("", steps, repeat, clearReward);
    }

    /** 展平成有序 spawn 序列（steps × repeat）。
     * <p>配置里 {@code delay}（秒）表示<strong>该 step 刷怪前</strong>相对上一时间点（上一步刷怪时刻；
     * 每轮 repeat 首步则相对上一轮末步）的等待，而非距本波开始的绝对时间。
     * 展开后 {@link SpawnStep#delayTicks()} 为距本波开始的绝对 tick，供调度器使用。
     */
    public List<SpawnStep> expand() {
        List<SpawnStep> out = new ArrayList<>(steps.size() * repeat);
        int timeline = 0;
        for (int r = 0; r < repeat; r++) {
            for (SpawnStep s : steps) {
                timeline += s.delayTicks();
                out.add(new SpawnStep(
                        s.point(), s.type(), s.count(),
                        s.hpMult(), s.dmgMult(), s.speedMult(), s.dropMult(),
                        timeline, s.affixes(), s.potions(), s.passengers()));
            }
        }
        return out;
    }
}
