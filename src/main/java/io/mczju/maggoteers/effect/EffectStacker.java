package io.mczju.maggoteers.effect;

import java.util.ArrayList;
import java.util.List;

/**
 * 效果堆叠/到期纯逻辑（与 Bukkit 解耦，全单测）。
 * <p>由 {@link EffectService} 在 apply（merge）与 fireTrigger（sweepExpiry）时调用。
 */
public final class EffectStacker {

    /**
     * 把 incoming 合并进 existing 列表（按 incoming.stack 策略）。返回新列表。
     * <ul>
     *   <li>IGNORE：同 id 已存在 → 不变。</li>
     *   <li>REPLACE：移除旧、加新。</li>
     *   <li>REFRESH：旧效果 expiryCharges 重置为 incoming 的（续期）。</li>
     *   <li>ADD：直接追加（多层叠加）。</li>
     *   <li>UPGRADE_LEVEL：旧效果 level++（封顶 upgradeMax）；满则 IGNORE。</li>
     * </ul>
     * D7：触发型被动（fireTrigger≠null）默认 stack=IGNORE 时，重复不叠加。
     */
    public static List<PlayerEffect> merge(List<PlayerEffect> existing, PlayerEffect incoming) {
        List<PlayerEffect> out = new ArrayList<>(existing);
        PlayerEffect same = out.stream().filter(e -> e.id().equals(incoming.id())).findFirst().orElse(null);
        if (same == null) { out.add(incoming); return out; }

        switch (incoming.stack()) {
            case IGNORE:    return out;                                   // 已存在，忽略
            case REPLACE:   out.remove(same); out.add(incoming); return out;
            case REFRESH:   same.setExpiryCharges(incoming.expiryCharges()); return out;
            case ADD:       out.add(incoming); return out;
            case UPGRADE_LEVEL:
                if (same.level() < incoming.upgradeMax()) same.setLevel(same.level() + 1);
                return out;                                               // 满级则 IGNORE
            default:        return out;
        }
    }

    /**
     * 扫描列表：对 expiryTrigger==fired 的效果扣电荷/移除。返回移除条数（原地修改 list）。
     */
    public static int sweepExpiry(List<PlayerEffect> list, Trigger fired) {
        int removed = 0;
        var it = list.iterator();
        while (it.hasNext()) {
            PlayerEffect e = it.next();
            if (e.expiryTrigger() == fired) {
                if (e.expiryCharges() == -1) { it.remove(); removed++; }
                else if (e.expiryCharges() > 0) {
                    e.setExpiryCharges(e.expiryCharges() - 1);
                    if (e.expiryCharges() == 0) { it.remove(); removed++; }
                }
            }
        }
        return removed;
    }

    private EffectStacker() {}
}
