package io.mczju.maggoteers.effect;

import com.github.mczjuops.mczjugamecore.game.AbstractGame;
import io.mczju.maggoteers.MaggoteersPlugin;
import io.mczju.maggoteers.game.AllyTargeting;
import io.mczju.maggoteers.game.MaggoteersGame;
import io.mczju.maggoteers.item.fx.MagicFxBuilder;
import io.mczju.maggoteers.item.fx.MagicFxConfig;
import io.mczju.maggoteers.item.fx.MagicFxPreset;
import io.mczju.maggoteers.item.fx.MagicFxPresets;
import io.mczju.maggoteers.item.fx.MagicUseFx;
import io.mczju.maggoteers.state.PlayerState;
import io.mczju.maggoteers.state.PlayerStateManager;
import io.mczju.maggoteers.util.GameRegistries;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
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
            WeaponHeldService.sync(p, game);
        } else {
            resyncDerived(p, st);
        }
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
                if (e.isPermanent() && e.effect() != Effect.AURA) applyDerived(p, e);
            }
        } finally {
            BULK_RESYNC.set(false);
            // 即使中途抛错（如非法 NamespacedKey），也要按当前上限恢复血量，避免卡在 strip 后的 20/0
            double maxAfter = maxInst != null ? maxInst.getValue() : 20.0;
            try {
                p.setHealth(finalizeHealthAfterResync(healthBefore, maxBefore, maxAfter));
            } catch (IllegalArgumentException ignored) {
                // 玩家可能已离线/无效
            }
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

    /** 对局结束：剥除派生视图 + 清 PlayerState 效果。 */
    public static void removeAll(Player p) {
        PlayerState st = PlayerStateManager.get(currentGame(p), p.getUniqueId());
        if (st == null) return;
        stripDerived(p, st.effects());
        st.clearEffects();
    }

    /** 剥除单条常驻派生视图（卸载 held 武器时须在从 PlayerState 移除之前调用）。 */
    static void stripOneDerived(Player p, PlayerEffect e) {
        if (p == null || e == null || !e.isPermanent()) return;
        switch (e.effect()) {
            case ADD_ATTRIBUTE -> {
                if (!isVirtualAttribute(e)) {
                    Attribute attr = e.params().get(EffectKeys.ATTR);
                    if (attr == null) {
                        String name = e.params().get(EffectKeys.ATTR_NAME);
                        if (name != null) attr = GameRegistries.attribute(name);
                    }
                    if (attr != null) {
                        AttributeInstance inst = p.getAttribute(attr);
                        if (inst != null) {
                            String prefix = AttributeModifierKeys.sanitizeKeyPath(e.id());
                            for (AttributeModifier m : new ArrayList<>(inst.getModifiers())) {
                                if (m.getKey().namespace().equals(MaggoteersPlugin.getInstance().getName().toLowerCase())
                                        && m.getKey().value().startsWith(prefix)) {
                                    inst.removeModifier(m);
                                }
                            }
                        }
                    }
                }
            }
            case ADD_POTION -> {
                PotionEffectType type = e.params().get(EffectKeys.POTION);
                if (type != null) p.removePotionEffect(type);
            }
            default -> { }
        }
    }

    /** 常驻 ADD_POTION 施加时长：held 用 params.duration_ticks（0→1s 刷新）；STAT 奖励仍 30s。 */
    static int resolvePotionDurationTicks(PlayerEffect e) {
        Integer configured = e.params().get(EffectKeys.DURATION_TICKS);
        if (configured != null && configured > 0) {
            return configured;
        }
        if (e.id() != null && e.id().startsWith(WeaponHeldService.HELD_PREFIX)) {
            return 20; // duration_ticks:0 = 手持期间每 1s 刷新
        }
        return 20 * 30;
    }

    /** 剥除本插件施加的派生视图：插件命名空间下的 AttributeModifier + 常驻 ADD_POTION 药水。 */
    private static void stripDerived(Player p, java.util.List<PlayerEffect> effects) {
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
        for (PlayerEffect e : effects) {
            if (e.isPermanent() && e.effect() == Effect.ADD_POTION) {
                PotionEffectType type = e.params().get(EffectKeys.POTION);
                if (type != null) p.removePotionEffect(type);
            }
        }
    }

    private static final Set<String> FIRING = new HashSet<>();

    /**
     * 对局内某 trigger 触发：对每个冒险模式玩家扫到期 + 执行 fireTrigger==fired 的触发型效果。
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
                var ps = io.mczju.maggoteers.state.PlayerStateManager.get(game, p.getUniqueId());
                if (ps == null || !ps.isAlive()) continue;
                // 1) 到期扫描
                List<String> removed = EffectStacker.sweepExpiry(ps.effects(), fired);
                if (!removed.isEmpty()) {
                    resyncDerived(p, ps);
                    for (String removedId : removed) {
                        io.mczju.maggoteers.reward.CollectibleService.remove(p, removedId);
                        BoundEquipService.remove(p, removedId);
                    }
                }
                // 2) 执行触发型（fireTrigger==fired）；REVOKE_GRANTS 先于同 trigger 其它效果
                dispatchTriggeredEffects(p, ps, game, fired, TriggerContext.empty());
                for (PlayerEffect e : new ArrayList<>(ps.effects())) {
                    // 3) recurring（仅 ON_TICK_1S，基于全局 tick 时钟）
                    if (fired == Trigger.ON_TICK_1S && e.recurringIntervalSec() > 0
                            && e.recurringSpawn() != null
                            && (Bukkit.getCurrentTick() / 20L) % e.recurringIntervalSec() == 0) {
                        apply(p, e.recurringSpawn());
                    }
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
        var ps = io.mczju.maggoteers.state.PlayerStateManager.get(game, p.getUniqueId());
        if (ps == null) return;
        TriggerContext raw = ctx != null ? ctx : TriggerContext.empty();
        TriggerContext useCtx = raw.fired() != null ? raw
                : new TriggerContext(fired, raw.hitTarget(), raw.attacker(), raw.eventLocation());
        if (fired != Trigger.ON_DEATH && fired != Trigger.ON_REVIVE && !ps.isAlive()) {
            return;
        }
        List<String> removed = EffectStacker.sweepExpiry(ps.effects(), fired);
        if (!removed.isEmpty()) {
            resyncDerived(p, ps);
            for (String removedId : removed) {
                io.mczju.maggoteers.reward.CollectibleService.remove(p, removedId);
                BoundEquipService.remove(p, removedId);
            }
        }
        dispatchTriggeredEffects(p, ps, game, fired, useCtx);
    }

    /** Pass 1: REVOKE_GRANTS; pass 2: all other triggered effects on {@code fired}. */
    private static void dispatchTriggeredEffects(Player p, PlayerState ps, MaggoteersGame game,
                                                 Trigger fired, TriggerContext ctx) {
        TriggerContext useCtx = ctx != null ? ctx : TriggerContext.empty();
        var snapshot = new ArrayList<>(ps.effects());
        for (PlayerEffect e : snapshot) {
            if (e.fireTrigger() != fired) continue;
            if (e.effect() == Effect.ADD_ATTRIBUTE && TriggeredGrantAttribute.isRevoke(e.params())) {
                executeTriggeredAddAttribute(p, e, game, useCtx);
            }
        }
        for (PlayerEffect e : snapshot) {
            if (e.fireTrigger() != fired) continue;
            if (e.effect() == Effect.ADD_ATTRIBUTE && TriggeredGrantAttribute.isRevoke(e.params())) continue;
            executeEffect(p, e, game, useCtx);
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
        applySelfPotions(p, ability.selfPotions());
        if (ability.effect() == Effect.ADD_ATTRIBUTE) {
            return applyWeaponTempEffect(p, game, ability);
        }
        if (ability.effect() == Effect.ADD_POTION) {
            if (ability.expiryTrigger() != null) {
                return applyWeaponTempEffect(p, game, ability);
            }
            int dur = ability.params().getOrDefault(EffectKeys.DURATION_TICKS, 0);
            if (dur > 0) {
                return applyInstantPotion(p, ability.params());
            }
            return false;
        }
        return executeMagicEffect(p, game, ability.effect(), ability.params(), ability.fx(),
                TriggerContext.empty(), true);
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
        int amp = params.getOrDefault(EffectKeys.AMP, 0);
        int dur = params.getOrDefault(EffectKeys.DURATION_TICKS, 0);
        if (dur <= 0) return false;
        p.addPotionEffect(new PotionEffect(type, dur, amp, false, true));
        return true;
    }

    private static boolean applyWeaponTempEffect(Player p, MaggoteersGame game, ItemAbility ability) {
        Optional<String> err = WeaponTempEffect.validate(
                ability.effect(), ability.params(), ability.expiryTrigger(), ability.expiryCharges());
        if (err.isPresent()) {
            LOG.warning("use_ability " + ability.effect() + " " + ability.itemId() + ": " + err.get());
            return false;
        }
        Stack stack = ability.stack() != null ? ability.stack() : Stack.REPLACE;
        EffectContext params = ability.params() != null ? ability.params().copy() : new EffectContext();
        PlayerEffect pe = new PlayerEffect(
                WeaponTempEffect.effectId(ability.itemId()),
                ability.effect(),
                params,
                null,
                ability.expiryTrigger(),
                ability.expiryCharges(),
                0,
                null,
                stack,
                0,
                0);
        apply(p, pe, game);
        return true;
    }

    /** 执行一条效果（触发型在 fireTrigger 时调；常驻型在 apply 时已施加）。 */
    private static void executeEffect(Player p, PlayerEffect e, MaggoteersGame game) {
        executeEffect(p, e, game, TriggerContext.empty());
    }

    private static void executeEffect(Player p, PlayerEffect e, MaggoteersGame game, TriggerContext ctx) {
        switch (e.effect()) {
            case HEAL -> {
                double amount = e.params().getOrDefault(EffectKeys.AMOUNT, 0.0);
                var hp = p.getAttribute(Attribute.MAX_HEALTH);
                double max = hp != null ? hp.getValue() : 20.0;
                p.setHealth(Math.min(max, p.getHealth() + amount));
            }
            case ADD_POTION -> {
                PotionEffectType type = e.params().get(EffectKeys.POTION);
                if (type == null) return;
                int amp = e.params().getOrDefault(EffectKeys.AMP, 0);
                int dur = e.params().getOrDefault(EffectKeys.DURATION_TICKS, 0);
                if (dur > 0) p.addPotionEffect(new PotionEffect(type, dur, amp, false, true));
            }
            case DAMAGE_AREA, DAMAGE_BEAM, HEAL_AREA, BUFF_AREA, DISABLE_AI ->
                    executeMagicEffect(p, game, e.effect(), e.params(), null, ctx);
            case GRANT_REVIVE -> {
                int count = e.params().getOrDefault(EffectKeys.COUNT, 1);
                PlayerStateManager.addReviveCount(game, p.getUniqueId(), count);
            }
            case GRANT_ITEM -> executeGrantItem(p, e.params());
            case SUMMON -> executeSummon(p, game, e.params(), ctx, false);
            case ADD_ATTRIBUTE -> executeTriggeredAddAttribute(p, e, game, ctx);
            case AURA -> activateAura(p, e, game);
        }
    }

    private static boolean executeMagicEffect(Player p, MaggoteersGame game, Effect effect,
                                           EffectContext rawParams, MagicUseFx weaponFx,
                                           TriggerContext ctx) {
        return executeMagicEffect(p, game, effect, rawParams, weaponFx, ctx, weaponFx != null);
    }

    private static boolean executeMagicEffect(Player p, MaggoteersGame game, Effect effect,
                                           EffectContext rawParams, MagicUseFx weaponFx,
                                           TriggerContext ctx, boolean weaponPath) {
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
                Particle ringParticle = weaponFx != null && weaponFx.particle() != null
                        ? weaponFx.particle() : Particle.FLAME;
                io.mczju.maggoteers.util.ParticleEffects.playAreaRing(
                        origin.getWorld(), origin, radius, ringParticle,
                        Math.max(32, (int) (radius * 12)));
                List<LivingEntity> targets = TargetResolver.collect(game, p, origin, radius, effect, params);
                List<BuffPotionSpec> potions = params.get(EffectKeys.POTIONS);
                EffectContext markFxCtx = params.get(EffectKeys.MARK_FX);
                MagicUseFx markFx = markFxCtx == null ? null
                        : MagicFxBuilder.fromContext(markFxCtx, MagicUseFx.defaults());
                int markDurationSec = params.getOrDefault(EffectKeys.MARK_DURATION_SEC, 0);
                Runnable action = () -> {
                    for (LivingEntity le : targets) {
                        le.damage(dmg, p);
                        if (potions != null && !potions.isEmpty() && le.isValid() && !le.isDead()) {
                            for (BuffPotionSpec spec : potions) {
                                PotionMerge.applyIfNeeded(le, spec.type(), spec.amp(), spec.durationTicks());
                            }
                        }
                        if (markFx != null && markDurationSec > 0 && le.isValid() && !le.isDead()) {
                            MagicFxPresets.followMarkAbove(le, markFx, markDurationSec);
                        }
                    }
                };
                if (shouldWrapMagicDamage(useCtx)) {
                    MagicDamageContext.run(game, p.getUniqueId(), action);
                } else {
                    action.run();
                }
                return true;
            }
            case DAMAGE_BEAM -> {
                executeDamageBeam(p, game, params, weaponFx);
                return true;
            }
            case HEAL_AREA -> {
                double radius = params.getOrDefault(EffectKeys.RADIUS, 5.0);
                double amount = params.getOrDefault(EffectKeys.AMOUNT, 0.0);
                boolean includeSelf = AuraParams.includeSelf(params);
                org.bukkit.Location origin = TriggerContext.resolveOrigin(p, useCtx);
                for (Player ally : AllyTargeting.alliesNear(game, origin, radius, p, includeSelf)) {
                    var hp = ally.getAttribute(Attribute.MAX_HEALTH);
                    double max = hp != null ? hp.getValue() : 20.0;
                    ally.setHealth(Math.min(max, ally.getHealth() + amount));
                }
                return true;
            }
            case BUFF_AREA -> {
                executeBuffArea(p, game, params, weaponFx, useCtx);
                return true;
            }
            case DISABLE_AI -> {
                executeDisableAi(p, game, params, weaponFx, useCtx);
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    private static boolean executeGrantItem(Player p, EffectContext params) {
        return GrantItemParams.parse(params)
                .map(g -> {
                    if (io.mczju.maggoteers.item.ItemService.createItem(g.itemId(), g.amount()).isEmpty()) {
                        LOG.warning("GRANT_ITEM: unknown item " + g.itemId());
                        return false;
                    }
                    io.mczju.maggoteers.item.ItemService.give(p, g.itemId(), g.amount());
                    return true;
                })
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

    /** Suppress owner ON_DAMAGE_DEALT from magic/summon damage in the same tick. */
    private static boolean shouldWrapMagicDamage(TriggerContext ctx) {
        if (ctx == null || ctx.fired() == null) return false;
        return switch (ctx.fired()) {
            case ON_KILL, ON_DAMAGE_DEALT, ON_DAMAGE_TAKEN, ON_DEATH -> true;
            default -> false;
        };
    }

    private static void executeDisableAi(Player p, MaggoteersGame game, EffectContext params,
                                         MagicUseFx weaponFx, TriggerContext ctx) {
        boolean weaponPath = weaponFx != null;
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
            MobAiLockRegistry.lock(le, duration);
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
                                        MagicUseFx weaponFx, TriggerContext ctx) {
        boolean weaponPath = weaponFx != null;
        if (!weaponPath) {
            EffectContext fxCtx = params.get(EffectKeys.FX);
            if (fxCtx != null) {
                MagicUseFx fx = MagicFxBuilder.fromContext(fxCtx, MagicUseFx.defaults());
                MagicFxPresets.play(p, fx);
            }
        }
        List<BuffPotionSpec> potions = params.get(EffectKeys.POTIONS);
        if (potions == null || potions.isEmpty()) return;

        List<LivingEntity> targets = BuffAreaTargets.resolve(game, p, params, ctx);
        EffectContext markFxCtx = params.get(EffectKeys.MARK_FX);
        MagicUseFx markFx = markFxCtx == null ? null
                : MagicFxBuilder.fromContext(markFxCtx, MagicUseFx.defaults());

        for (LivingEntity le : targets) {
            if (!le.isValid()) continue;
            if (!le.isDead()) {
                for (BuffPotionSpec spec : potions) {
                    PotionMerge.applyIfNeeded(le, spec.type(), spec.amp(), spec.durationTicks());
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

    private static void executeDamageBeam(Player p, MaggoteersGame game, EffectContext params, MagicUseFx weaponFx) {
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

        MagicUseFx fx = weaponFx != null ? weaponFx : MagicFxConfig.forWeapon(null);
        fx = new MagicUseFx(fx.sound(), fx.soundVolume(), fx.soundPitch(), fx.preset(), fx.particle(),
                fx.radius(), length, fx.density(), fx.rippleRings(), fx.expandSteps(), fx.spiralTicks());
        if (weaponFx == null && fx.sound() != null) {
            p.playSound(eye, fx.sound(), fx.soundVolume(), fx.soundPitch());
        }
        MagicFxPresets.playBeamAlong(eye, dir, length, fx);

        Set<UUID> hit = new HashSet<>();
        int steps = Math.max(16, (int) (length * 2));
        MagicDamageContext.run(game, p.getUniqueId(), () -> {
            for (int i = 0; i <= steps; i++) {
                Location pt = eye.clone().add(dir.clone().multiply(length * i / (double) steps));
                for (Entity en : p.getWorld().getNearbyEntities(pt, beamRadius, beamRadius, beamRadius)) {
                    if (!(en instanceof LivingEntity le)) continue;
                    if (!TargetResolver.isTarget(game, p, le, params)) continue;
                    if (!hit.add(le.getUniqueId())) continue;
                    le.damage(dmg, p);
                }
            }
        });
    }

    /** 触发型 AURA：交互后写入 fireTrigger=null 的携带条目（保留 expiry）。 */
    private static void activateAura(Player p, PlayerEffect e, MaggoteersGame game) {
        if (e.effect() != Effect.AURA) return;
        PlayerState st = PlayerStateManager.get(game, p.getUniqueId());
        if (st != null) {
            st.effects().removeIf(x -> x.id().equals(e.id()) && x.fireTrigger() != null);
        }
        PlayerEffect active = new PlayerEffect(
                e.id(), Effect.AURA, e.params().copy(), null,
                e.expiryTrigger(), e.expiryCharges(),
                0, null, e.stack(), e.upgradeMax(), e.cooldownSec());
        active.setLevel(e.level());
        apply(p, active);
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
        AttributeModifier.Operation operation = "PERCENT".equalsIgnoreCase(op)
                ? AttributeModifier.Operation.MULTIPLY_SCALAR_1
                : AttributeModifier.Operation.ADD_NUMBER;
        double maxBefore = attr == Attribute.MAX_HEALTH ? inst.getValue() : 0;
        // D5：唯一 key = id + level + 自增序号；sanitize：ability:/held: 等 id 含冒号非法
        NamespacedKey key = AttributeModifierKeys.pluginKey(MaggoteersPlugin.getInstance(),
                e.id() + "_" + e.level() + "_" + KEY_SEQ.incrementAndGet());
        inst.addModifier(new AttributeModifier(key, value, operation));
        if (attr == Attribute.MAX_HEALTH && !BULK_RESYNC.get()) {
            bumpHealthForMaxGain(p, maxBefore, inst.getValue());
        }
    }

    private static void applyPotionPermanent(Player p, PlayerEffect e) {
        PotionEffectType type = e.params().get(EffectKeys.POTION);
        if (type == null) return;
        int amp = e.params().getOrDefault(EffectKeys.AMP, 0);
        // D1 净化技巧：amp >= 255 视为"免疫该效果"（0s 255 级抵消）——Paper 实测若无效，靠 PurifyListener fallback
        if (amp >= 255) {
            p.addPotionEffect(new PotionEffect(type, resolvePotionDurationTicks(e), 255, false, false, false));
            return;
        }
        int duration = resolvePotionDurationTicks(e);
        p.addPotionEffect(new PotionEffect(type, duration, amp, false, false, true));
    }

    /**
     * 刷新常驻药水派生视图（由 GameplayTickListener 每秒调）。
     * 对 permanent + ADD_POTION 的效果重新施加 30s PotionEffect，
     * 使其在过期前被刷新，从而真正"永久"。
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
                int amp = e.params().getOrDefault(EffectKeys.AMP, 0);
                if (amp >= 255) {
                    // D1 净化技巧：amp >= 255 → 0s 255 级抵消（PurifyListener fallback）
                    p.addPotionEffect(new PotionEffect(type, resolvePotionDurationTicks(e), 255, false, false, false));
                } else {
                    p.addPotionEffect(new PotionEffect(type, resolvePotionDurationTicks(e), amp, false, false, true));
                }
            }
        }
    }

    private static MaggoteersGame currentGame(Player p) {
        if (p == null) return null;
        MaggoteersGame bound = PlayerStateManager.gameForPlayer(p.getUniqueId());
        if (bound != null) return bound;
        var pe = new com.github.mczjuops.mczjugamecore.player.PlayerExt(p);
        AbstractGame g = pe.getGame();
        return g instanceof MaggoteersGame mg ? mg : null;
    }

    private EffectService() {}
}
