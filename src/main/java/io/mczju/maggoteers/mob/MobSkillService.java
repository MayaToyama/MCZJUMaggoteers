package io.mczju.maggoteers.mob;

import com.github.mczjuops.mczjugamecore.game.AbstractGame;
import io.mczju.maggoteers.MaggoteersPlugin;
import io.mczju.maggoteers.game.MaggoteersGame;
import io.mczju.maggoteers.wave.WaveEngine;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 怪物技能运行时：attach（生成）→ 事件分发（攻击/受击/被击杀/tick/计数器）→ detach（死亡/清波）。
 * <p>技能 id 解析失败（mob_skills.yml 缺失）时静默跳过；SPAWN 技能在 attach 立即执行。
 */
public final class MobSkillService {

    private static final Map<UUID, List<MobSkillSpec>> BY_ENTITY = new ConcurrentHashMap<>();
    private static final Map<UUID, Map<String, Long>> LAST_FIRE = new ConcurrentHashMap<>();

    private MobSkillService() {}

    public static void start(MaggoteersPlugin plugin) {
        Bukkit.getScheduler().runTaskTimer(plugin, MobSkillService::tickAll, 20L, 20L);
    }

    /** 生成时挂载：技能表 + 计数器 + 隐身 + SPAWN 技能。幂等（重复 attach 覆盖）。 */
    public static void attach(LivingEntity le, List<String> skillIds) {
        if (le == null || skillIds == null || skillIds.isEmpty()) return;
        List<MobSkillSpec> specs = new ArrayList<>();
        for (String id : skillIds) {
            MobSkillRegistry.get(id).ifPresent(specs::add);
        }
        if (specs.isEmpty()) return;
        UUID uuid = le.getUniqueId();
        BY_ENTITY.put(uuid, List.copyOf(specs));
        MobCounterRegistry.load(le, specs);
        MaggoteersGame game = gameOf(le);
        for (MobSkillSpec spec : specs) {
            if (spec.invisible()) applyInvisible(le);
            if (spec.trigger() == MobTrigger.SPAWN) {
                execute(game, le, spec, null, null);
            }
        }
    }

    public static void detach(UUID uuid) {
        BY_ENTITY.remove(uuid);
        LAST_FIRE.remove(uuid);
        MobCounterRegistry.unload(uuid);
        MobTempAttributeService.clearAll(uuid);
    }

    public static List<MobSkillSpec> skillsOf(UUID uuid) {
        List<MobSkillSpec> s = BY_ENTITY.get(uuid);
        return s == null ? List.of() : s;
    }

    /** 事件分发：先推进计数器（计满 id 触发对应 COUNTER 技能），再匹配普通 trigger。 */
    public static void fire(MaggoteersGame game, LivingEntity mob, MobTrigger t,
                            LivingEntity target, LivingEntity source) {
        if (mob == null) return;
        List<MobSkillSpec> specs = BY_ENTITY.get(mob.getUniqueId());
        if (specs == null || specs.isEmpty()) return;

        for (String cid : MobCounterRegistry.signal(mob, t, target, source)) {
            for (MobSkillSpec spec : specs) {
                if (spec.trigger() == MobTrigger.COUNTER && spec.counter() != null
                        && spec.counter().id().equals(cid)) {
                    execute(game, mob, spec, target, source);
                }
            }
        }
        if (t == MobTrigger.COUNTER) return;

        for (MobSkillSpec spec : specs) {
            if (spec.trigger() != t || spec.trigger() == MobTrigger.SPAWN) continue;
            if (spec.condition() != null && !spec.condition().evaluate(game, mob, target, source)) continue;
            execute(game, mob, spec, target, source);
        }
    }

    private static void execute(MaggoteersGame game, LivingEntity mob, MobSkillSpec spec,
                                LivingEntity target, LivingEntity source) {
        for (MobEffectSpec es : spec.effects()) {
            MobEffectExecutor.execute(mob, es, target, source);
        }
    }

    /** 每秒心跳：TICK 光环技能按 cooldown_sec 触发；失效实体 detach。 */
    private static void tickAll() {
        for (var entry : BY_ENTITY.entrySet()) {
            UUID uuid = entry.getKey();
            Entity raw = Bukkit.getEntity(uuid);
            if (!(raw instanceof LivingEntity le) || le.isDead() || !le.isValid()) {
                detach(uuid);
                continue;
            }
            MaggoteersGame game = gameOf(le);
            if (game == null) continue;
            long now = System.currentTimeMillis();
            for (MobSkillSpec spec : entry.getValue()) {
                if (spec.trigger() != MobTrigger.TICK) continue;
                if (!cooldownElapsed(uuid, spec.id(), now, spec.cooldownSec())) continue;
                if (spec.condition() != null && !spec.condition().evaluate(game, le, null, null)) continue;
                execute(game, le, spec, null, null);
                markFired(uuid, spec.id(), now);
            }
        }
    }

    private static boolean cooldownElapsed(UUID uuid, String skillId, long now, int cooldownSec) {
        if (cooldownSec <= 0) return true;
        Map<String, Long> bySkill = LAST_FIRE.get(uuid);
        Long last = bySkill == null ? null : bySkill.get(skillId);
        return last == null || now - last >= cooldownSec * 1000L;
    }

    private static void markFired(UUID uuid, String skillId, long now) {
        LAST_FIRE.computeIfAbsent(uuid, u -> new HashMap<>()).put(skillId, now);
    }

    private static void applyInvisible(LivingEntity le) {
        le.setInvisible(true);
        EntityEquipment eq = le.getEquipment();
        if (eq == null) return;
        ItemStack bottle = new ItemStack(Material.GLASS_BOTTLE);
        eq.setHelmet(bottle);
        eq.setHelmetDropChance(0f);
    }

    private static MaggoteersGame gameOf(LivingEntity le) {
        AbstractGame g = WaveEngine.gameOfEntity(le.getUniqueId());
        return g instanceof MaggoteersGame mg ? mg : null;
    }
}
