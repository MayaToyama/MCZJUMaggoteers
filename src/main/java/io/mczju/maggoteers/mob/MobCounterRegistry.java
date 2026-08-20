package io.mczju.maggoteers.mob;

import com.github.mczjuops.mczjugamecore.game.AbstractGame;
import io.mczju.maggoteers.game.MaggoteersGame;
import io.mczju.maggoteers.wave.WaveEngine;
import org.bukkit.entity.LivingEntity;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 怪物计数器生命周期：出生加载、死亡卸载；输入信号推进，计满触发 COUNTER 技能。
 * <p>与玩家 CounterService 对称但独立：count 用 {@link MobTrigger}，条件以怪物为中心。
 * <p>串联语义：本 tick 计满的 id 作为 {@code COUNTER} 信号继续驱动 count==COUNTER 的其它计数器
 *（防自环：跳过自身 id），用队列迭代直到没有新计满。
 */
public final class MobCounterRegistry {

    private static final Map<UUID, Map<String, MobCounterState>> BY_ENTITY = new ConcurrentHashMap<>();

    /** 计数器运行态。current 从 0 起，每次匹配输入 +1，计满清零循环。 */
    public record MobCounterState(MobCounterSpec spec, int current) {
        public MobCounterState {
            if (current < 0) current = 0;
        }
        public MobCounterState increment() { return new MobCounterState(spec, current + 1); }
        public boolean full() { return current >= spec.amount(); }
    }

    /** 纯逻辑：输入信号 input 时该计数器应得的下一计数；计满→0（触发）。非匹配输入→原值。 */
    static int advanceCount(MobCounterSpec spec, MobTrigger input, int current) {
        if (spec == null || !spec.triggeredBy(input)) return current;
        int next = current + 1;
        return next >= spec.amount() ? 0 : next;
    }

    /** 纯逻辑：输入信号 input 是否使该计数器计满（决定是否触发）。 */
    static boolean fullAt(MobCounterSpec spec, MobTrigger input, int current) {
        if (spec == null || !spec.triggeredBy(input)) return false;
        return current + 1 >= spec.amount();
    }

    public static void load(LivingEntity le, List<MobSkillSpec> specs) {
        if (le == null || specs == null || specs.isEmpty()) return;
        Map<String, MobCounterState> counters = new HashMap<>();
        for (MobSkillSpec s : specs) {
            if (s.counter() != null) {
                counters.put(s.counter().id(), new MobCounterState(s.counter(), 0));
            }
        }
        if (!counters.isEmpty()) {
            BY_ENTITY.put(le.getUniqueId(), counters);
        }
    }

    public static void unload(UUID uuid) {
        BY_ENTITY.remove(uuid);
    }

    /**
     * 输入信号推进本怪计数器；返回本 tick 最终应触发的 counter id 列表（含串联驱动）。
     * 条件不通过、count!=input 的计数器不推进。
     */
    public static List<String> signal(LivingEntity mob, MobTrigger t,
                                      LivingEntity target, LivingEntity source) {
        List<String> fired = new ArrayList<>();
        if (mob == null) return fired;
        Map<String, MobCounterState> counters = BY_ENTITY.get(mob.getUniqueId());
        if (counters == null || counters.isEmpty()) return fired;

        Deque<String> queue = new ArrayDeque<>();
        // 初筛：count==t（非 COUNTER 信号，避免自环）且计满者入队
        for (var e : counters.entrySet()) {
            MobCounterState st = e.getValue();
            if (!st.spec().triggeredBy(t) || t == MobTrigger.COUNTER) continue;
            if (!passesCondition(mob, st.spec(), target, source)) continue;
            e.setValue(new MobCounterState(st.spec(), advanceCount(st.spec(), t, st.current())));
            if (fullAt(st.spec(), t, st.current())) {
                queue.add(st.spec().id());
            }
        }
        // 串联：COUNTER 信号驱动其它计数器（跳过自身）
        while (!queue.isEmpty()) {
            String cid = queue.poll();
            fired.add(cid);
            for (var e : counters.entrySet()) {
                MobCounterState st = e.getValue();
                if (!st.spec().triggeredBy(MobTrigger.COUNTER) || cid.equals(st.spec().id())) continue;
                if (!passesCondition(mob, st.spec(), target, source)) continue;
                e.setValue(new MobCounterState(st.spec(),
                        advanceCount(st.spec(), MobTrigger.COUNTER, st.current())));
                if (fullAt(st.spec(), MobTrigger.COUNTER, st.current())) {
                    queue.add(st.spec().id());
                }
            }
        }
        return fired;
    }

    private static boolean passesCondition(LivingEntity mob, MobCounterSpec spec,
                                           LivingEntity target, LivingEntity source) {
        if (spec.condition() == null) return true;
        AbstractGame game = WaveEngine.gameOfEntity(mob.getUniqueId());
        return game instanceof MaggoteersGame mg
                && spec.condition().evaluate(mg, mob, target, source);
    }

    private MobCounterRegistry() {}
}
