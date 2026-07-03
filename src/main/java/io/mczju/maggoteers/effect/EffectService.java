package io.mczju.maggoteers.effect;

import io.mczju.maggoteers.MaggoteersPlugin;
import io.mczju.maggoteers.state.PlayerState;
import io.mczju.maggoteers.state.PlayerStateManager;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

/**
 * 效果引擎服务（§10）。PlayerState 为真相源，Bukkit 属性/药水为派生视图。
 * <p>D5：常驻 ADD_ATTRIBUTE 每层 AttributeModifier 用唯一 NamespacedKey（id + level + 序号），
 * 1.21 起同 key 重复添加会抛异常。
 */
public final class EffectService {
    private static final Logger LOG = Logger.getLogger("Maggoteers");
    private static final AtomicLong KEY_SEQ = new AtomicLong();   // 保证 key 唯一

    /** 应用一条效果：merge 进 PlayerState；常驻型立即施加派生视图。 */
    public static void apply(Player p, PlayerEffect incoming) {
        PlayerState st = PlayerStateManager.get(currentGame(p), p.getUniqueId());
        if (st == null) { LOG.warning("EffectService.apply: 无 PlayerState " + p.getName()); return; }
        // merge（堆叠/合并）
        var merged = EffectStacker.merge(st.effects(), incoming);
        st.effects().clear();
        st.effects().addAll(merged);
        // 常驻型 → 立即施加派生视图
        if (incoming.isPermanent()) {
            applyDerived(p, incoming);
        }
    }

    /** 复活后重施加：把 PlayerState 所有常驻型重新写回 Bukkit（死亡清掉的药水补回）。 */
    public static void resync(Player p) {
        PlayerState st = PlayerStateManager.get(currentGame(p), p.getUniqueId());
        if (st == null) return;
        for (PlayerEffect e : new ArrayList<>(st.effects())) {
            if (e.isPermanent()) applyDerived(p, e);
        }
    }

    /** 对局结束：清 PlayerState 效果 + 移除本插件加的属性修改器/药水（粗粒度：清插件 key 的 modifier + 清药水）。 */
    public static void removeAll(Player p) {
        PlayerState st = PlayerStateManager.get(currentGame(p), p.getUniqueId());
        if (st == null) return;
        // 移除插件命名空间下的所有 AttributeModifier
        for (Attribute attr : new Attribute[]{Attribute.MAX_HEALTH, Attribute.ATTACK_DAMAGE,
                Attribute.MOVEMENT_SPEED, Attribute.ATTACK_SPEED}) {
            AttributeInstance inst = p.getAttribute(attr);
            if (inst == null) continue;
            for (AttributeModifier m : new ArrayList<>(inst.getModifiers())) {
                if (m.getKey().namespace().equals(MaggoteersPlugin.getInstance().getName().toLowerCase())) {
                    inst.removeModifier(m);
                }
            }
        }
        st.clearEffects();
    }

    // —— 派生视图施加（常驻型）——
    private static void applyDerived(Player p, PlayerEffect e) {
        switch (e.effect()) {
            case ADD_ATTRIBUTE -> applyAttribute(p, e);
            case ADD_POTION    -> applyPotionPermanent(p, e);
            default -> { /* HEAL/DAMAGE_AREA/GRANT_REVIVE 不是常驻型，触发时执行（Task 4） */ }
        }
    }

    private static void applyAttribute(Player p, PlayerEffect e) {
        Attribute attr = e.params().get(EffectKeys.ATTR);
        if (attr == null) return;
        AttributeInstance inst = p.getAttribute(attr);
        if (inst == null) return;
        String op = e.params().getOrDefault(EffectKeys.OP, "FLAT");
        double value = e.params().getOrDefault(EffectKeys.VALUE, 0.0);
        AttributeModifier.Operation operation = "PERCENT".equalsIgnoreCase(op)
                ? AttributeModifier.Operation.MULTIPLY_SCALAR_1
                : AttributeModifier.Operation.ADD_NUMBER;
        // D5：唯一 key = id + level + 自增序号
        NamespacedKey key = new NamespacedKey(MaggoteersPlugin.getInstance(),
                e.id() + "_" + e.level() + "_" + KEY_SEQ.incrementAndGet());
        inst.addModifier(new AttributeModifier(key, value, operation));
    }

    private static void applyPotionPermanent(Player p, PlayerEffect e) {
        PotionEffectType type = e.params().get(EffectKeys.POTION);
        if (type == null) return;
        int amp = e.params().getOrDefault(EffectKeys.AMP, 0);
        // 常驻药水：用足够长时长模拟 infinite（定期由 ON_TICK_1S 刷新，见 Task 5）；这里先施加长时长
        p.addPotionEffect(new PotionEffect(type, 20 * 30, amp, false, false, true));
    }

    /** 由 Task 5 监听器持有当前对局引用；这里临时用玩家所在游戏反查。 */
    private static io.mczju.maggoteers.game.MaggoteersGame currentGame(Player p) {
        var pe = new com.github.mczjuops.mczjugamecore.player.PlayerExt(p);
        return pe.isInGame() ? (io.mczju.maggoteers.game.MaggoteersGame) pe.getGame() : null;
    }

    private EffectService() {}
}
