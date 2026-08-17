package io.mczju.maggoteers.effect;

import io.mczju.maggoteers.MaggoteersPlugin;
import io.mczju.maggoteers.game.AllyTargeting;
import io.mczju.maggoteers.game.MaggoteersGame;
import io.mczju.maggoteers.item.fx.MagicFxBuilder;
import io.mczju.maggoteers.item.fx.MagicFxConfig;
import io.mczju.maggoteers.item.fx.MagicFxPreset;
import io.mczju.maggoteers.item.fx.MagicFxPresets;
import io.mczju.maggoteers.item.fx.MagicFxService;
import io.mczju.maggoteers.item.fx.MagicUseFx;
import io.mczju.maggoteers.state.PlayerState;
import io.mczju.maggoteers.state.PlayerStateManager;
import io.mczju.maggoteers.util.GameRegistries;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import org.bukkit.Bukkit;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
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
        apply(p, incoming, currentGame(p));
    }

    public static void apply(Player p, PlayerEffect incoming, MaggoteersGame game) {
        if (game == null) {
            LOG.warning("EffectService.apply: game null " + p.getName());
            return;
        }
        PlayerState st = PlayerStateManager.get(game, p.getUniqueId());
        if (st == null) {
            LOG.warning("EffectService.apply: 无 PlayerState " + p.getName());
            return;
        }
        var merged = EffectStacker.merge(st.effects(), incoming);
        st.effects().clear();
        st.effects().addAll(merged);
        PlayerEffect mergedPe = st.effects().stream()
                .filter(e -> e.id().equals(incoming.id())).findFirst().orElse(null);
        if (mergedPe != null && mergedPe.effect() == Effect.BOUND_EQUIP) {
            BoundEquipService.sync(p, mergedPe);
        } else if (incoming.effect() == Effect.AURA && incoming.fireTrigger() == null) {
            AuraService.refresh(game);
        } else if (incoming.isPermanent() && incoming.effect() == Effect.ADD_ATTRIBUTE) {
            // Full resync avoids REPLACE/REFRESH leaving orphan AttributeModifiers (unique KEY_SEQ).
            resyncDerived(p, st);
        } else if (incoming.isPermanent() && incoming.effect() != Effect.AURA) {
            applyDerived(p, incoming);
        }
    }

    /** 复活后重施加（幂等）：held 武器被动 + 派生视图 + 护符。 */
    public static void resync(Player p) {
        MaggoteersGame game = currentGame(p);
        PlayerState st = PlayerStateManager.get(game, p.getUniqueId());
        if (st == null) return;
        if (game != null) {
            // sync may early-return when main-hand unchanged; still must strip/reapply derived views
            WeaponHeldService.sync(p, game);
        }
        resyncDerived(p, st);
        if (game != null) {
            io.mczju.maggoteers.reward.CollectibleService.resync(p, game);
            BoundEquipService.resync(p, game);
        }
    }

    private static final ThreadLocal<Boolean> BULK_RESYNC = ThreadLocal.withInitial(() -> false);

    /** Strip + reapply permanent attribute/potion derived views (no collectible sync). */
    static void resyncDerived(Player p, PlayerState st) {
        if (p == null || st == null) return;
        var maxInst = p.getAttribute(Attribute.MAX_HEALTH);
        double healthBefore = p.getHealth();
        double maxBefore = maxInst != null ? maxInst.getValue() : 20.0;

        BULK_RESYNC.set(true);
        try {
            stripDerived(p, st.effects());
            for (PlayerEffect e : new ArrayList<>(st.effects())) {
                if (e.isPermanent() && e.effect() != Effect.AURA) {
                    try {
                        applyDerived(p, e);
                    } catch (RuntimeException ex) {
                        LOG.warning("resyncDerived apply " + e.id() + ": " + ex.getMessage());
                    }
                }
            }
        } finally {
            BULK_RESYNC.set(false);
            double maxAfter = maxInst != null ? maxInst.getValue() : 20.0;
            try {
                p.setHealth(finalizeHealthAfterResync(healthBefore, maxBefore, maxAfter));
            } catch (IllegalArgumentException ex) {
                LOG.warning("resyncDerived setHealth: " + ex.getMessage());
            }
        }
        // stripDerived 会按常驻 ADD_POTION 类型清药水，须立刻重刷 AURA 派生，否则光环药水会空窗甚至被低 amp 常驻盖住
        MaggoteersGame game = currentGame(p);
        if (game != null) {
            AuraService.refresh(game);
        }
    }

    /**
     * strip/reapply 后确定当前血量：净增上限 → 等量加当前血；否则保留比例（切换武器/重算属性）。
     */
    static double finalizeHealthAfterResync(double healthBefore, double maxBefore, double maxAfter) {
        double maxDelta = maxAfter - maxBefore;
        if (maxDelta > 0) {
            return Math.min(maxAfter, healthBefore + maxDelta);
        }
        return preserveHealthAcrossResync(healthBefore, maxBefore, maxAfter);
    }

    /** MAX_HEALTH 实际升高时，给玩家补上同等当前血量（上限与血量同步增长）。 */
    static void bumpHealthForMaxGain(Player p, double maxBefore, double maxAfter) {
        if (p == null || maxAfter <= maxBefore) return;
        p.setHealth(Math.min(maxAfter, p.getHealth() + (maxAfter - maxBefore)));
    }

    /**
     * strip/reapply 属性时保留血量比例；避免 MC 在移除 MAX_HEALTH 修饰符时把当前血 clamp 到 20，
     * 导致刚用补给/heal 恢复的血量在切换手持物后被“打回”基础上限。
     */
    static double preserveHealthAcrossResync(double healthBefore, double maxBefore, double maxAfter) {
        if (maxAfter <= 0) return Math.max(0, healthBefore);
        if (maxBefore <= 0) return Math.min(maxAfter, healthBefore);
        return Math.min(maxAfter, healthBefore * (maxAfter / maxBefore));
    }

    /** 对局结束：剥除派生视图 + 清 PlayerState 效果 + 剥离绑定装备/护符视图。 */
    public static void removeAll(Player p) {
        removeAllFor(p, currentGame(p));
    }

    /**
     * 按显式对局清理单个玩家的派生效果。中途退出玩家已不在 MGC game map（{@code currentGame} 查不到），
     * 必须把对局对象显式传进来才能拿到 PlayerState 并剥除 MAX_HEALTH 等属性加成/常驻药水。
     */
    public static void removeAllFor(Player p, MaggoteersGame game) {
        PlayerState st = PlayerStateManager.get(game, p.getUniqueId());
        if (st == null) return;
        stripDerived(p, st.effects());
        st.clearEffects();
        if (game != null) {
            io.mczju.maggoteers.reward.CollectibleService.resync(p, game);
            BoundEquipService.resync(p, game);
        }
    }

    /** 剥除本插件施加的派生视图：插件命名空间下的 AttributeModifier + 常驻 ADD_POTION 药水。 */
    private static void stripDerived(Player p, java.util.List<PlayerEffect> effects) {
        DerivedViewAttributes.stripPluginModifiers(
                p, MaggoteersPlugin.getInstance().getName().toLowerCase(), null);
        for (PlayerEffect e : effects) {
            if (e.isPermanent() && e.effect() == Effect.ADD_POTION) {
                PotionEffectType type = e.params().get(EffectKeys.POTION);
                if (type != null) p.removePotionEffect(type);
            }
        }
    }

    private static final Set<String> FIRING = new HashSet<>();

    /**
     * 对局内某 trigger 触发：对每个冒险模式玩家先分发 fireTrigger==fired，再扫到期。
     * @param game        当前对局
     * @param fired       触发的 trigger
     * @param tickSeconds 仅 ON_TICK_1S 传>0（用于 recurring 累计）；其余传 0
     */
    public static void fireTrigger(MaggoteersGame game, Trigger fired, int tickSeconds) {
        if (game == null) return;
        String key = System.identityHashCode(game) + ":" + fired.name();
        if (!FIRING.add(key)) return;   // 同 (game,trigger) 正在分发 → 防递归
        try {
            for (var pe : game.getPlayers()) {
                Player p = pe.player();
                try {
                    var ps = io.mczju.maggoteers.state.PlayerStateManager.get(game, p.getUniqueId());
                    if (ps == null || !ps.isAlive()) continue;
                    processTriggerEffects(ps, p, game, fired, TriggerContext.empty());
                    for (PlayerEffect e : new ArrayList<>(ps.effects())) {
                        // recurring（仅 ON_TICK_1S，基于全局 tick 时钟）— after dispatch+sweep
                        if (fired == Trigger.ON_TICK_1S && e.recurringIntervalSec() > 0
                                && e.recurringSpawn() != null
                                && (Bukkit.getCurrentTick() / 20L) % e.recurringIntervalSec() == 0) {
                            apply(p, e.recurringSpawn());
                        }
                    }
                } catch (RuntimeException ex) {
                    LOG.warning("fireTrigger " + fired + " player=" + p.getName() + ": " + ex.getMessage());
                }
            }
        } finally {
            FIRING.remove(key);
        }
    }

    /** 便捷重载（无 recurring 计时）。 */
    public static void fireTrigger(MaggoteersGame game, Trigger fired) {
        fireTrigger(game, fired, 0);
    }

    /** 单玩家触发（战斗/交互：只触发该玩家；不做 recurring 计时）。 */
    public static void fireTriggerPlayer(MaggoteersGame game, Player p, Trigger fired) {
        fireTriggerPlayer(game, p, fired, TriggerContext.empty());
    }

    public static void fireTriggerPlayer(MaggoteersGame game, Player p, Trigger fired, TriggerContext ctx) {
        if (game == null || p == null) return;
        try {
            var ps = io.mczju.maggoteers.state.PlayerStateManager.get(game, p.getUniqueId());
            if (ps == null) return;
            TriggerContext raw = ctx != null ? ctx : TriggerContext.empty();
            TriggerContext useCtx = raw.fired() != null ? raw : raw.withFired(fired);
            if (fired != Trigger.ON_DEATH && fired != Trigger.ON_REVIVE && !ps.isAlive()) {
                return;
            }
            processTriggerEffects(ps, p, game, fired, useCtx);
        } catch (RuntimeException ex) {
            LOG.warning("fireTriggerPlayer " + fired + " player=" + p.getName() + ": " + ex.getMessage());
        }
    }

    /**
     * Spec 4.0 + 4.4: dispatch fireTrigger==T, then sweepExpiry; empower FX once per pack itemId on victim.
     */
    static void processTriggerEffects(PlayerState ps, Player p, MaggoteersGame game,
                                      Trigger fired, TriggerContext useCtx) {
        Set<String> touched = new HashSet<>();
        List<String> removed = TriggerFireOrder.run(
                () -> touched.addAll(dispatchTriggeredEffectsCollecting(p, ps, game, fired, useCtx)),
                () -> {
                    List<String> r = EffectStacker.sweepExpiry(ps.effects(), fired);
                    for (String id : r) {
                        if (id != null && id.startsWith(WeaponAbilityIds.PREFIX)) {
                            touched.add(id);
                        }
                    }
                    return r;
                });
        if (!removed.isEmpty()) {
            resyncDerived(p, ps);
            for (String removedId : removed) {
                io.mczju.maggoteers.reward.CollectibleService.remove(p, removedId);
                BoundEquipService.remove(p, removedId);
            }
        }
        playEmpowerFxIfNeeded(useCtx, touched);
    }

    private static void playEmpowerFxIfNeeded(TriggerContext useCtx, Set<String> touched) {
        LivingEntity victim = useCtx != null ? useCtx.hitTarget() : null;
        if (victim == null || touched == null || touched.isEmpty()) {
            return;
        }
        for (String itemId : EmpowerFxCoalesce.itemIdsTouched(touched)) {
            ItemAbility ab = ItemAbilityRegistry.get(itemId).orElse(null);
            if (ab == null) {
                continue;
            }
            MagicUseFx empower = MagicFxConfig.resolveEmpowerFx(ab);
            if (empower == null) {
                continue;
            }
            MagicFxService.playOnEntity(victim, empower);
        }
    }

    /** Executes triggered effects on {@code fired}; returns executed {@code ability:*} effect ids. */
    private static Set<String> dispatchTriggeredEffectsCollecting(Player p, PlayerState ps, MaggoteersGame game,
                                                                  Trigger fired, TriggerContext ctx) {
        TriggerContext useCtx = ctx != null ? ctx : TriggerContext.empty();
        Set<String> executed = new HashSet<>();
        var snapshot = new ArrayList<>(ps.effects());
        for (PlayerEffect e : snapshot) {
            if (e.fireTrigger() != fired) continue;
            if (e.effect() == Effect.ADD_ATTRIBUTE && TriggeredGrantAttribute.isRevoke(e.params())) {
                executeTriggeredAddAttribute(p, e, game, useCtx);
                noteAbilityTouched(executed, e.id());
            }
        }
        for (PlayerEffect e : snapshot) {
            if (e.fireTrigger() != fired) continue;
            if (e.effect() == Effect.ADD_ATTRIBUTE && TriggeredGrantAttribute.isRevoke(e.params())) continue;
            executeEffect(p, e, game, useCtx);
            noteAbilityTouched(executed, e.id());
        }
        return executed;
    }

    private static void noteAbilityTouched(Set<String> out, String effectId) {
        if (out != null && effectId != null && effectId.startsWith(WeaponAbilityIds.PREFIX)) {
            out.add(effectId);
        }
    }

    private static void executeTriggeredAddAttribute(Player p, PlayerEffect e, MaggoteersGame game,
                                                     TriggerContext ctx) {
        if (TriggeredGrantAttribute.isRevoke(e.params())) {
            PlayerState st = PlayerStateManager.get(game, p.getUniqueId());
            if (st == null) return;
            String sourceId = e.params().get(EffectKeys.SOURCE_ID);
            if (sourceId == null || sourceId.isBlank()) return;
            if (TriggeredGrantAttribute.revokeGrants(st, sourceId)) {
                resyncDerived(p, st);
            }
            return;
        }
        if (e.fireTrigger() != null) {
            TriggeredGrantAttribute.applyGrant(p, game, e);
            return;
        }
        applyAttribute(p, e);
    }

    /** Weapon entry: FX already played by {@link io.mczju.maggoteers.item.interact.ConfigMagicHandler}. */
    public static boolean executeAbility(Player p, MaggoteersGame game, ItemAbility ability) {
        if (p == null || game == null || ability == null) return false;
        boolean didSide = false;
        List<org.bukkit.potion.PotionEffectType> selfClear = ability.selfClearPotions();
        if (selfClear != null && !selfClear.isEmpty()) {
            PotionClear.apply(p, selfClear);
            didSide = true;
        }
        List<BuffPotionSpec> selfPots = ability.selfPotions();
        if (selfPots != null && !selfPots.isEmpty()) {
            applySelfPotions(p, selfPots);
            didSide = true;
        }

        TriggerContext ctx = TriggerContext.empty();
        AtomicReference<LivingEntity> beamHit = new AtomicReference<>();
        boolean anyStep = false;
        List<AbilityStep> steps = ability.steps();
        for (int i = 0; i < steps.size(); i++) {
            AbilityStep step = steps.get(i);
            if (step.isDeferred()) {
                if (writeDeferredStep(p, game, ability, step, i)) {
                    anyStep = true;
                }
                continue;
            }
            boolean ok;
            if (step.effect() == Effect.ADD_ATTRIBUTE) {
                LOG.warning("use_ability " + ability.itemId() + " immediate ADD_ATTRIBUTE unsupported");
                ok = false;
            } else if (step.effect() == Effect.ADD_POTION) {
                int dur = step.params().getOrDefault(EffectKeys.DURATION_TICKS, 0);
                ok = dur > 0 && applyInstantPotion(p, step.params());
            } else if (step.effect() == Effect.HEAL) {
                ok = applyHealOrSelfDamage(p, step.params().getOrDefault(EffectKeys.AMOUNT, 0.0));
            } else {
                ok = executeMagicEffect(p, game, step.effect(), step.params(),
                        ability.fx(), ctx, true, beamHit);
                if (beamHit.get() != null) {
                    ctx = ctx.withHitTarget(beamHit.get());
                    beamHit.set(null);
                }
            }
            if (ok) {
                anyStep = true;
            }
        }
        boolean sideConfigured = (selfClear != null && !selfClear.isEmpty())
                || (selfPots != null && !selfPots.isEmpty());
        return anyStep || (sideConfigured && didSide);
    }

    /** Deferred step → PlayerEffect (ADD_* fireTrigger=null; magic fireTrigger=expiryTrigger). */
    private static boolean writeDeferredStep(Player p, MaggoteersGame game, ItemAbility ability,
                                             AbilityStep step, int stepIndex) {
        Optional<String> err = WeaponTempEffect.validate(
                step.effect(), step.params(), step.expiryTrigger(), step.expiryCharges());
        if (err.isPresent()) {
            LOG.warning("use_ability deferred " + ability.itemId() + " step " + stepIndex + ": " + err.get());
            return false;
        }
        Stack stack = step.stack() != null ? step.stack() : Stack.REPLACE;
        boolean addFamily = step.effect() == Effect.ADD_ATTRIBUTE || step.effect() == Effect.ADD_POTION;
        boolean legacySingleAdd = addFamily && ability.steps().size() == 1;
        String id = legacySingleAdd
                ? WeaponAbilityIds.effectId(ability.itemId())
                : WeaponAbilityIds.effectId(ability.itemId(), stepIndex);
        Trigger fire = addFamily ? null : step.expiryTrigger();
        EffectContext params = step.params() != null ? step.params().copy() : new EffectContext();
        PlayerEffect pe = new PlayerEffect(
                id, step.effect(), params, fire,
                step.expiryTrigger(), step.expiryCharges(),
                0, null, stack, 0, 0);
        apply(p, pe, game);
        return true;
    }

    private static void applySelfPotions(Player p, java.util.List<BuffPotionSpec> potions) {
        if (p == null || potions == null || potions.isEmpty()) return;
        for (BuffPotionSpec spec : potions) {
            PotionMerge.applyIfNeeded(p, spec.type(), spec.amp(), spec.durationTicks());
        }
    }

    private static boolean applyInstantPotion(Player p, EffectContext params) {
        if (p == null || params == null) return false;
        PotionEffectType type = params.get(EffectKeys.POTION);
        if (type == null) return false;
        if (PotionImmunity.isImmune(p, type)) return false;
        int amp = params.getOrDefault(EffectKeys.AMP, 0);
        int dur = params.getOrDefault(EffectKeys.DURATION_TICKS, 0);
        if (dur <= 0) return false;
        p.addPotionEffect(new PotionEffect(type, dur, amp, false, true));
        return true;
    }

    /**
     * 治疗：上限按 MAX_HEALTH（缺省 20.0），返回实际增加的血量。
     * 集中散落各处的 {min(max, health+amount)}（applyHealOrSelfDamage 正分支 / HEAL_AREA / AuraService / SUPPLY_HEALING）。
     */
    public static double heal(Player p, double amount) {
        if (p == null || amount <= 0) return 0.0;
        var hp = p.getAttribute(Attribute.MAX_HEALTH);
        double max = hp != null ? hp.getValue() : 20.0;
        double before = p.getHealth();
        double after = Math.min(max, before + amount);
        p.setHealth(after);
        return after - before;
    }

    /**
     * Positive amount heals; negative amount is an exact HP cost via {@link Player#setHealth}
     * (not {@link Player#damage}) so armor / resistance / invuln frames cannot cancel or dilute it.
     */
    static boolean applyHealOrSelfDamage(Player p, double amount) {
        if (p == null || amount == 0) return false;
        if (amount < 0) {
            // Exact cost: no Attribute lookup (keeps unit tests free of RegistryAccess).
            p.setHealth(Math.max(0.0, p.getHealth() + amount));
            return true;
        }
        heal(p, amount);
        return true;
    }

    /**
     * 执行一条效果（触发型在 fireTrigger 时调；常驻型在 apply 时已施加）。
     * <p>PlayerEffect 路径：params 在解析期已带默认值，这里<b>不</b>套 withDefaults。
     * 因此 GRANT_ITEM/SUMMON/GRANT_REVIVE 与 {@link #executeMagicEffect} 里的双份分支<b>有意保留</b>——
     * 那边先套 {@code MagicEffectParams.withDefaults}（武器 use_ability 路径），合并会把默认值引入本路径，改变行为。
     */
    private static void executeEffect(Player p, PlayerEffect e, MaggoteersGame game, TriggerContext ctx) {
        switch (e.effect()) {
            case HEAL -> applyHealOrSelfDamage(p, e.params().getOrDefault(EffectKeys.AMOUNT, 0.0));
            case ADD_POTION -> {
                PotionEffectType type = e.params().get(EffectKeys.POTION);
                if (type == null) return;
                if (Boolean.TRUE.equals(e.params().get(EffectKeys.IMMUNITY))) {
                    return;
                }
                if (PotionImmunity.isImmune(p, type)) return;
                int amp = e.params().getOrDefault(EffectKeys.AMP, 0);
                int dur = e.params().getOrDefault(EffectKeys.DURATION_TICKS, 0);
                if (dur > 0) p.addPotionEffect(new PotionEffect(type, dur, amp, false, true));
            }
            case DAMAGE_AREA, DAMAGE_BEAM, HEAL_AREA, BUFF_AREA, DISABLE_AI -> {
                boolean weaponPack = e.id() != null && e.id().startsWith(WeaponAbilityIds.PREFIX);
                MagicUseFx weaponFx = null;
                if (weaponPack) {
                    String itemId = WeaponAbilityIds.itemIdOf(e.id()).orElse(null);
                    ItemAbility ab = itemId == null ? null : ItemAbilityRegistry.get(itemId).orElse(null);
                    weaponFx = ab != null ? ab.fx() : null;
                }
                executeMagicEffect(p, game, e.effect(), e.params(), weaponFx, ctx, weaponPack);
            }
            case GRANT_REVIVE -> {
                int count = e.params().getOrDefault(EffectKeys.COUNT, 1);
                PlayerStateManager.addReviveCount(game, p.getUniqueId(), count);
            }
            case GRANT_ITEM -> executeGrantItem(p, e.params());
            case SUMMON -> executeSummon(p, game, e.params(), ctx, false);
            case ADD_ATTRIBUTE -> executeTriggeredAddAttribute(p, e, game, ctx);
        }
    }

    private static boolean executeMagicEffect(Player p, MaggoteersGame game, Effect effect,
                                           EffectContext rawParams, MagicUseFx weaponFx,
                                           TriggerContext ctx) {
        return executeMagicEffect(p, game, effect, rawParams, weaponFx, ctx, weaponFx != null, null);
    }

    private static boolean executeMagicEffect(Player p, MaggoteersGame game, Effect effect,
                                           EffectContext rawParams, MagicUseFx weaponFx,
                                           TriggerContext ctx, boolean weaponPath) {
        return executeMagicEffect(p, game, effect, rawParams, weaponFx, ctx, weaponPath, null);
    }

    /**
     * 武器 use_ability/held 路径：先 {@code MagicEffectParams.withDefaults} 套默认值再分发。
     * <p>GRANT_ITEM/SUMMON/GRANT_REVIVE 与 {@link #executeEffect} 里的双份分支<b>有意保留</b>——
     * 那边（PlayerEffect 路径）不套默认值，合并会把本路径默认值引入普通效果执行，改变行为。
     */
    private static boolean executeMagicEffect(Player p, MaggoteersGame game, Effect effect,
                                           EffectContext rawParams, MagicUseFx weaponFx,
                                           TriggerContext ctx, boolean weaponPath,
                                           AtomicReference<LivingEntity> hitTargetOut) {
        EffectContext params = MagicEffectParams.withDefaults(effect, rawParams);
        TriggerContext useCtx = ctx != null ? ctx : TriggerContext.empty();
        switch (effect) {
            case GRANT_ITEM -> {
                return executeGrantItem(p, params);
            }
            case SUMMON -> {
                return executeSummon(p, game, params, useCtx, weaponPath);
            }
            case DAMAGE_AREA -> {
                double radius = params.getOrDefault(EffectKeys.RADIUS, 3.0);
                double baseDmg = params.getOrDefault(EffectKeys.DAMAGE, 0.0);
                double dmg = baseDmg * PlayerCombatStats.magicDamageMultiplier(game, p.getUniqueId());
                org.bukkit.Location origin = TriggerContext.resolveOrigin(p, useCtx);
                if (origin == null || origin.getWorld() == null) return false;
                io.mczju.maggoteers.util.ParticleEffects.playAreaRing(origin.getWorld(), origin, radius);
                List<LivingEntity> targets = TargetResolver.collect(game, p, origin, radius, effect, params);
                List<BuffPotionSpec> potions = params.get(EffectKeys.POTIONS);
                List<PotionEffectType> clears = params.get(EffectKeys.CLEAR_POTIONS);
                Runnable action = () -> {
                    for (LivingEntity le : targets) {
                        MagicDamage.applyIndependent(le, dmg, p);
                        if (!le.isValid() || le.isDead()) {
                            continue;
                        }
                        PotionClear.apply(le, clears);
                        if (potions != null && !potions.isEmpty()) {
                            for (BuffPotionSpec spec : potions) {
                                PotionMerge.applyIfNeeded(le, spec.type(), spec.amp(), spec.durationTicks());
                            }
                        }
                    }
                };
                // Spec 4.3.1: wrap when shouldWrapMagicDamage — not when weaponFx != null alone
                if (shouldWrapMagicDamage(useCtx)) {
                    MagicDamageContext.run(game, p.getUniqueId(), action);
                } else {
                    action.run();
                }
                return true;
            }
            case DAMAGE_BEAM -> {
                LivingEntity beamHit = executeDamageBeam(p, game, params, weaponFx);
                if (beamHit != null && hitTargetOut != null) {
                    hitTargetOut.set(beamHit);
                }
                return true;
            }
            case HEAL_AREA -> {
                double radius = params.getOrDefault(EffectKeys.RADIUS, 5.0);
                double amount = params.getOrDefault(EffectKeys.AMOUNT, 0.0);
                boolean includeSelf = AuraParams.includeSelf(params);
                org.bukkit.Location origin = TriggerContext.resolveOrigin(p, useCtx);
                for (Player ally : AllyTargeting.alliesNear(game, origin, radius, p, includeSelf)) {
                    heal(ally, amount);
                }
                return true;
            }
            case BUFF_AREA -> {
                executeBuffArea(p, game, params, weaponFx, useCtx, weaponPath);
                return true;
            }
            case DISABLE_AI -> {
                executeDisableAi(p, game, params, weaponFx, useCtx, weaponPath);
                return true;
            }
            case GRANT_REVIVE -> {
                int count = params.getOrDefault(EffectKeys.COUNT, 1);
                PlayerStateManager.addReviveCount(game, p.getUniqueId(), count);
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    private static boolean executeGrantItem(Player p, EffectContext params) {
        return GrantItemParams.parse(params)
                .map(g -> io.mczju.maggoteers.item.ItemService.give(p, g.itemId(), g.amount()))
                .orElse(false);
    }

    private static boolean executeSummon(Player p, MaggoteersGame game, EffectContext raw,
                                         TriggerContext ctx, boolean weaponPath) {
        var parsed = SummonParamsParser.parse(raw, weaponPath);
        if (parsed.isEmpty()) return false;
        SummonParams sp = parsed.get();
        int[] spawned = {0};
        Runnable action = () -> spawned[0] = SummonExecutor.spawn(p, game, sp, ctx);
        if (isCombatSummon(ctx)) {
            MagicDamageContext.run(game, p.getUniqueId(), action);
        } else {
            action.run();
        }
        return spawned[0] > 0;
    }

    private static boolean isCombatSummon(TriggerContext ctx) {
        return shouldWrapMagicDamage(ctx);
    }

    /** Wrap magic/summon damage so same-tick owner DEALT/TAKEN combat triggers are suppressed. */
    private static boolean shouldWrapMagicDamage(TriggerContext ctx) {
        if (ctx == null || ctx.fired() == null) return false;
        return switch (ctx.fired()) {
            case ON_KILL, ON_DAMAGE_DEALT, ON_DAMAGE_TAKEN, ON_DEATH -> true;
            default -> false;
        };
    }

    private static void executeDisableAi(Player p, MaggoteersGame game, EffectContext params,
                                         MagicUseFx weaponFx, TriggerContext ctx, boolean weaponPath) {
        if (!weaponPath) {
            EffectContext fxCtx = params.get(EffectKeys.FX);
            if (fxCtx != null) {
                MagicUseFx fx = MagicFxBuilder.fromContext(fxCtx, MagicUseFx.defaults());
                MagicFxPresets.play(p, fx);
            }
        }

        int duration = params.getOrDefault(EffectKeys.DURATION_TICKS, 0);
        if (duration <= 0) return;

        List<LivingEntity> targets = DisableAiTargets.resolve(game, p, params, ctx);
        EffectContext markFxCtx = params.get(EffectKeys.MARK_FX);
        MagicUseFx markFx = markFxCtx == null ? null
                : MagicFxBuilder.fromContext(markFxCtx, MagicUseFx.defaults());

        for (LivingEntity le : targets) {
            MobAiLockRegistry.lock(le, duration, game);
            if (weaponPath && markFx != null) {
                playMarkFx(le, markFx);
            }
        }
        if (!weaponPath && markFx != null) {
            LivingEntity markTarget = resolveStatMarkTarget(params, ctx);
            if (markTarget != null) {
                playMarkFx(markTarget, markFx);
            }
        }
    }

    private static void executeBuffArea(Player p, MaggoteersGame game, EffectContext params,
                                        MagicUseFx weaponFx, TriggerContext ctx, boolean weaponPath) {
        if (!weaponPath) {
            EffectContext fxCtx = params.get(EffectKeys.FX);
            if (fxCtx != null) {
                MagicUseFx fx = MagicFxBuilder.fromContext(fxCtx, MagicUseFx.defaults());
                MagicFxPresets.play(p, fx);
            }
        }
        List<BuffPotionSpec> potions = params.get(EffectKeys.POTIONS);
        List<PotionEffectType> clears = params.get(EffectKeys.CLEAR_POTIONS);
        boolean hasPotions = potions != null && !potions.isEmpty();
        boolean hasClear = clears != null && !clears.isEmpty();
        if (!hasPotions && !hasClear) return;

        List<LivingEntity> targets = BuffAreaTargets.resolve(game, p, params, ctx);
        EffectContext markFxCtx = params.get(EffectKeys.MARK_FX);
        MagicUseFx markFx = markFxCtx == null ? null
                : MagicFxBuilder.fromContext(markFxCtx, MagicUseFx.defaults());

        for (LivingEntity le : targets) {
            if (!le.isValid()) continue;
            if (!le.isDead()) {
                PotionClear.apply(le, clears);
                if (hasPotions) {
                    for (BuffPotionSpec spec : potions) {
                        PotionMerge.applyIfNeeded(le, spec.type(), spec.amp(), spec.durationTicks());
                    }
                }
            }
            if (weaponPath && markFx != null) {
                playMarkFx(le, markFx);
            }
        }
        if (!weaponPath && markFx != null) {
            LivingEntity markTarget = resolveStatMarkTarget(params, ctx);
            if (markTarget != null) {
                playMarkFx(markTarget, markFx);
            }
        }
    }

    private static LivingEntity resolveStatMarkTarget(EffectContext params, TriggerContext ctx) {
        String markOn = params.getOrDefault(EffectKeys.MARK_ON, "none");
        return switch (markOn.toLowerCase(java.util.Locale.ROOT)) {
            case "hit_target" -> ctx == null ? null : ctx.hitTarget();
            case "attacker" -> ctx == null ? null : ctx.attacker();
            default -> null;
        };
    }

    private static void playMarkFx(LivingEntity le, MagicUseFx markFx) {
        if (le == null || markFx == null) return;
        Location loc = le.getLocation();
        if (loc.getWorld() == null) return;
        if (markFx.preset() == MagicFxPreset.THICK_RING) {
            io.mczju.maggoteers.util.ParticleEffects.playThickRing(
                    loc.getWorld(), loc, markFx.radius(), markFx.particle(), markFx.density());
        } else {
            MagicFxPresets.dotAbove(loc, markFx);
        }
    }

    /** @return first damaged LivingEntity (for shared TriggerContext hitTarget), or null */
    private static LivingEntity executeDamageBeam(Player p, MaggoteersGame game, EffectContext params,
                                                  MagicUseFx weaponFx) {
        double maxRange = params.getOrDefault(EffectKeys.RAY_LENGTH, 32.0);
        double beamRadius = params.getOrDefault(EffectKeys.BEAM_RADIUS, 1.25);
        double baseDmg = params.getOrDefault(EffectKeys.DAMAGE, 0.0);
        double dmg = baseDmg * PlayerCombatStats.magicDamageMultiplier(game, p.getUniqueId());

        Location eye = p.getEyeLocation();
        Vector dir = eye.getDirection().normalize();
        Entity target = p.getTargetEntity((int) maxRange);
        Double targetDist = null;
        if (target != null) {
            targetDist = eye.distance(target.getLocation().add(0, target.getHeight() * 0.5, 0));
        }
        double length = BeamLogic.resolveLength(maxRange, targetDist);

        MagicUseFx fx = weaponFx != null ? weaponFx : MagicFxConfig.currentDefaults();
        fx = new MagicUseFx(fx.sound(), fx.soundVolume(), fx.soundPitch(), fx.preset(), fx.particle(),
                fx.radius(), length, fx.density(), fx.rippleRings(), fx.expandSteps(), fx.spiralTicks());
        if (fx.sound() != null) {
            p.playSound(eye, fx.sound(), fx.soundVolume(), fx.soundPitch());
        }
        MagicFxPresets.playBeamAlong(eye, dir, length, fx);

        Set<UUID> hit = new HashSet<>();
        AtomicReference<LivingEntity> first = new AtomicReference<>();
        int steps = Math.max(16, (int) (length * 2));
        MagicDamageContext.run(game, p.getUniqueId(), () -> {
            for (int i = 0; i <= steps; i++) {
                Location pt = eye.clone().add(dir.clone().multiply(length * i / (double) steps));
                for (Entity en : p.getWorld().getNearbyEntities(pt, beamRadius, beamRadius, beamRadius)) {
                    if (!(en instanceof LivingEntity le)) continue;
                    if (!TargetResolver.isTarget(game, p, le, params)) continue;
                    if (!hit.add(le.getUniqueId())) continue;
                    first.compareAndSet(null, le);
                    MagicDamage.applyIndependent(le, dmg, p);
                }
            }
        });
        return first.get();
    }

    // —— 派生视图施加（常驻型）——
    private static void applyDerived(Player p, PlayerEffect e) {
        switch (e.effect()) {
            case ADD_ATTRIBUTE -> {
                if (!isVirtualAttribute(e)) applyAttribute(p, e);
            }
            case ADD_POTION -> applyPotionPermanent(p, e);
            default -> { }
        }
    }

    private static boolean isVirtualAttribute(PlayerEffect e) {
        String name = e.params().get(EffectKeys.ATTR_NAME);
        return name != null && GameRegistries.isVirtualAttribute(name);
    }

    private static void applyAttribute(Player p, PlayerEffect e) {
        if (isVirtualAttribute(e)) return;
        Attribute attr = e.params().get(EffectKeys.ATTR);
        if (attr == null) {
            String name = e.params().get(EffectKeys.ATTR_NAME);
            if (name != null) attr = GameRegistries.attribute(name);
        }
        if (attr == null) return;
        AttributeInstance inst = p.getAttribute(attr);
        if (inst == null) return;
        String op = e.params().getOrDefault(EffectKeys.OP, "FLAT");
        double value = e.params().getOrDefault(EffectKeys.VALUE, 0.0);
        AttributeModifier.Operation operation = AttributeModifierKeys.attributeOperation(op);
        double maxBefore = attr == Attribute.MAX_HEALTH ? inst.getValue() : 0;
        // D5：唯一 key = effect/<sanitize(id)_level_seq>（与物品 maggoteers:xxx_attack_damage 隔离，防 strip 剥武器伤）
        NamespacedKey key = AttributeModifierKeys.effectKey(MaggoteersPlugin.getInstance(),
                e.id() + "_" + e.level() + "_" + KEY_SEQ.incrementAndGet());
        inst.addModifier(new AttributeModifier(key, value, operation));
        if (attr == Attribute.MAX_HEALTH && !BULK_RESYNC.get()) {
            bumpHealthForMaxGain(p, maxBefore, inst.getValue());
        }
    }

    private static void applyPotionPermanent(Player p, PlayerEffect e) {
        PotionEffectType type = e.params().get(EffectKeys.POTION);
        if (type == null) return;
        if (Boolean.TRUE.equals(e.params().get(EffectKeys.IMMUNITY))) {
            if (PotionImmunity.matchesImmunityEffect(e, type)) {
                p.removePotionEffect(type);
            }
            return;
        }
        int amp = e.params().getOrDefault(EffectKeys.AMP, 0);
        if (PotionImmunity.isImmune(p, type)) {
            return;
        }
        // Permanent potions: long duration refreshed by ON_TICK_1S
        p.addPotionEffect(new PotionEffect(type, 20 * 30, amp, false, false, true));
    }

    /**
     * Refresh permanent potion derived view (called each second by GameplayTickListener).
     */
    public static void refreshPermanentPotions(Player p) {
        io.mczju.maggoteers.game.MaggoteersGame game = currentGame(p);
        if (game == null) return;
        var st = PlayerStateManager.get(game, p.getUniqueId());
        if (st == null) return;
        for (PlayerEffect e : st.effects()) {
            if (e.isPermanent() && e.effect() == Effect.ADD_POTION) {
                PotionEffectType type = e.params().get(EffectKeys.POTION);
                if (type == null) continue;
                if (Boolean.TRUE.equals(e.params().get(EffectKeys.IMMUNITY))) {
                    if (PotionImmunity.matchesImmunityEffect(e, type)) {
                        p.removePotionEffect(type);
                    }
                    continue;
                }
                int amp = e.params().getOrDefault(EffectKeys.AMP, 0);
                if (PotionImmunity.isImmune(p, type)) {
                    continue;
                }
                p.addPotionEffect(new PotionEffect(type, 20 * 30, amp, false, false, true));
            }
        }
    }

    private static MaggoteersGame currentGame(Player p) {
        return PlayerStateManager.gameOf(p);
    }

    private EffectService() {}
}
