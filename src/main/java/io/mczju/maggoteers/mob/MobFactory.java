package io.mczju.maggoteers.mob;

import com.github.mczjuops.mczjugamecore.game.AbstractGame;
import io.mczju.maggoteers.MaggoteersPlugin;
import io.mczju.maggoteers.item.ItemService;
import io.mczju.maggoteers.wave.*;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Hoglin;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.PiglinAbstract;
import org.bukkit.entity.Slime;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.logging.Logger;

/**
 * 生成原版怪 + 骑乘步（延迟挂载、嵌套乘客）+ 尺寸/寻敌/装备/死亡召唤配置。
 */
public final class MobFactory {
    private static final Logger LOG = Logger.getLogger("Maggoteers");

    /** 3×3 九宫格第 {@code index % 9} 格（相对 base 整格偏移 -1/0/+1）。 */
    public static Location spawnPadLocation(Location base, int index) {
        int cell = Math.floorMod(index, 9);
        return base.clone().add(cell % 3 - 1, 0, cell / 3 - 1);
    }

    /** 生成一步的全部根坐骑；乘客 tick+1 挂载，每实体 {@code track} 回调。 */
    public static void spawnStepGroup(AbstractGame game, Location baseLoc, SpawnStep step,
                                      BiConsumer<LivingEntity, MobSpawnProfile> track) {
        for (int i = 0; i < step.count(); i++) {
            Location loc = spawnPadLocation(baseLoc, i);
            LivingEntity mount = spawnMount(loc, step);
            if (mount == null) continue;
            track.accept(mount, MobSpawnProfile.fromStep(step));
            if (!step.passengers().isEmpty()) {
                List<PassengerSpawn> passengerTree = step.passengers();
                Bukkit.getScheduler().runTaskLater(MaggoteersPlugin.getInstance(), () -> {
                    if (!mount.isValid() || mount.isDead()) return;
                    List<LivingEntity> direct = new ArrayList<>();
                    mountPassengerTree(mount, passengerTree, track, direct);
                    LivingEntity controller = MountControllerResolver.resolve(mount, direct, passengerTree);
                    MountedSquadRegistry.register(game, mount, controller);
                }, 1L);
            }
        }
    }

    private static void mountPassengerTree(LivingEntity carrier, List<PassengerSpawn> nodes,
                                           BiConsumer<LivingEntity, MobSpawnProfile> track,
                                           List<LivingEntity> directChildrenOut) {
        int slots = MountPassengerLimits.directPassengerLimit(carrier)
                - carrier.getPassengers().size();
        for (PassengerSpawn node : nodes) {
            if (slots <= 0) {
                LOG.warning("坐骑 " + carrier.getType() + " 直接乘客已满，跳过 " + node.type());
                break;
            }
            LivingEntity child = spawnPassengerEntity(carrier.getLocation(), node);
            if (child == null) continue;
            track.accept(child, MobSpawnProfile.fromPassenger(node));
            mountPassengerTree(child, node.passengers(), track, null);
            if (!carrier.addPassenger(child)) {
                // L1：child 已 remove 但仍被 livingMobs 追踪，由 5-tick 扫描回收；孙乘客随 child 移除弹飞成散怪
                LOG.warning("addPassenger 失败：" + carrier.getType() + " <- " + child.getType());
                child.remove();
                continue;
            }
            slots--;
            if (directChildrenOut != null) directChildrenOut.add(child);
        }
    }

    /** 刷怪后统一配置：防卸载 → 系数 → 装备 → 名字锁定 → 名字再断言 → 挂载限制。技能挂载由 WaveEngine.trackSpawned 做（Task 6）。 */
    private static void configureSpawnedLiving(LivingEntity le, double hpMult, double dmgMult, double speedMult,
                                               double scaleMult, double followRangeMult,
                                               List<MobEquipment> equipment, String name) {
        le.setRemoveWhenFarAway(false);
        applyMobStats(le, hpMult, dmgMult, speedMult, scaleMult, followRangeMult);
        applyEquipment(le, equipment);
        MobDisplayNames.lock(le, name);
        scheduleNameReassert(le);
        MountPassengerLimits.prepareMount(le);
    }

    public static LivingEntity spawnDeathMob(Location loc, DeathSpawn ds) {
        if (loc.getWorld() == null) return null;
        try {
            if (ds.type() == EntityType.TNT) {
                var raw = loc.getWorld().spawnEntity(loc, EntityType.TNT);
                if (raw instanceof TNTPrimed tnt) {
                    tnt.setFuseTicks(40);
                }
                return null;
            }
            var raw = loc.getWorld().spawnEntity(loc, ds.type());
            if (!(raw instanceof LivingEntity le)) {
                raw.remove();
                return null;
            }
            configureSpawnedLiving(le, ds.hpMult(), ds.dmgMult(), ds.speedMult(), ds.scaleMult(),
                    ds.followRangeMult(), ds.equipment(), ds.name());
            if (le instanceof Mob m) m.setAware(true);
            return le;
        } catch (Exception e) {
            LOG.warning("死亡召唤失败 " + ds.type() + "：" + e.getMessage());
            return null;
        }
    }

    /** 亡语生成物延迟挂载乘客（与 {@link #spawnStepGroup} 同 tick+1 语义）。 */
    public static void mountPassengersDelayed(AbstractGame game, LivingEntity mount, List<PassengerSpawn> passengers,
                                              BiConsumer<LivingEntity, MobSpawnProfile> track) {
        if (mount == null || passengers == null || passengers.isEmpty()) return;
        Bukkit.getScheduler().runTaskLater(MaggoteersPlugin.getInstance(), () -> {
            if (!mount.isValid() || mount.isDead()) return;
            List<LivingEntity> direct = new ArrayList<>();
            mountPassengerTree(mount, passengers, track, direct);
            LivingEntity controller = MountControllerResolver.resolve(mount, direct, passengers);
            MountedSquadRegistry.register(game, mount, controller);
        }, 1L);
    }

    private static LivingEntity spawnMount(Location loc, SpawnStep step) {
        if (loc.getWorld() == null) return null;
        try {
            var raw = loc.getWorld().spawnEntity(loc, step.type());
            if (!(raw instanceof LivingEntity le)) {
                raw.remove();
                return null;
            }
            configureSpawnedLiving(le, step.hpMult(), step.dmgMult(), step.speedMult(), step.scaleMult(),
                    step.followRangeMult(), step.equipment(), step.name());
            return le;
        } catch (Exception e) {
            LOG.warning("生成怪物失败 " + step.type() + "：" + e.getMessage());
            return null;
        }
    }

    public static LivingEntity spawnPassengerEntity(Location loc, PassengerSpawn ps) {
        if (loc.getWorld() == null) return null;
        try {
            var raw = loc.getWorld().spawnEntity(loc, ps.type());
            if (!(raw instanceof LivingEntity le)) {
                raw.remove();
                return null;
            }
            configureSpawnedLiving(le, ps.hpMult(), ps.dmgMult(), ps.speedMult(), ps.scaleMult(),
                    ps.followRangeMult(), ps.equipment(), ps.name());
            if (le instanceof Mob m) m.setAware(true);
            return le;
        } catch (Exception e) {
            LOG.warning("生成乘客失败 " + ps.type() + "：" + e.getMessage());
            return null;
        }
    }

    /** 调试用：在玩家位置生成带技能的怪。 */
    public static LivingEntity spawnDebugMob(Location loc, org.bukkit.entity.EntityType type,
                                             double hpMult, double dmgMult, double spdMult,
                                             List<String> skillIds) {
        SpawnStep fake = new SpawnStep(
                new Vec3(loc.getX(), loc.getY(), loc.getZ()),
                type, 1, hpMult, dmgMult, spdMult, 1.0, 1.0,
                0, skillIds, List.of(), List.of(), List.of());
        return spawnMount(loc, fake);
    }

    private static void scheduleNameReassert(LivingEntity le) {
        var plugin = MaggoteersPlugin.getInstance();
        if (plugin == null) return;
        Runnable r = () -> {
            if (le.isValid()) MobDisplayNames.reassertIfNeeded(le);
        };
        Bukkit.getScheduler().runTask(plugin, r);
        Bukkit.getScheduler().runTaskLater(plugin, r, 1L);
        Bukkit.getScheduler().runTaskLater(plugin, r, 2L);
    }

    private static void applyMobStats(LivingEntity le, double hpMult, double dmgMult, double speedMult,
                                      double scaleMult, double followRangeMult) {
        // setSize 会重置 hp/dmg/speed，必须在 applyMultipliers 之前
        ensureCombatSlimeSize(le);
        double gScale = globalScaleMult();
        double gFollow = globalFollowRangeMult();
        applyMultipliers(le, hpMult, dmgMult, speedMult);
        applyScale(le, scaleMult * gScale);
        applyFollowRange(le, followRangeMult * gFollow);
        preventOverworldZombification(le);
    }

    /** Size≤1 不伤人；局内一律至少 Size 2。分裂由 {@code SlimeSplitEvent} 取消。 */
    private static void ensureCombatSlimeSize(LivingEntity le) {
        if (le instanceof Slime slime && slime.getSize() < 2) {
            slime.setSize(2);
        }
    }

    /** 主世界虚空图里猪灵/疣猪兽默认会转化；局内刷怪一律免疫。 */
    private static void preventOverworldZombification(LivingEntity le) {
        if (le instanceof PiglinAbstract piglin) piglin.setImmuneToZombification(true);
        else if (le instanceof Hoglin hoglin) hoglin.setImmuneToZombification(true);
    }

    private static double globalScaleMult() {
        return MaggoteersPlugin.getInstance().getConfig().getDouble("mob_attributes.scale", 1.0);
    }

    private static double globalFollowRangeMult() {
        return MaggoteersPlugin.getInstance().getConfig().getDouble("mob_attributes.follow_range", 1.0);
    }

    private static void applyMultipliers(LivingEntity le, double hpMult, double dmgMult, double spdMult) {
        AttributeInstance hp = le.getAttribute(Attribute.MAX_HEALTH);
        if (hp != null) {
            double scaled = hp.getBaseValue() * Math.max(0.0001, hpMult);
            hp.setBaseValue(scaled);
            le.setHealth(Math.min(scaled, 1024.0));
        }
        AttributeInstance dmg = le.getAttribute(Attribute.ATTACK_DAMAGE);
        if (dmg != null) dmg.setBaseValue(dmg.getBaseValue() * Math.max(0.0001, dmgMult));
        AttributeInstance spd = le.getAttribute(Attribute.MOVEMENT_SPEED);
        if (spd != null) spd.setBaseValue(spd.getBaseValue() * Math.max(0.0001, spdMult));
    }

    private static void applyScale(LivingEntity le, double mult) {
        if (Math.abs(mult - 1.0) < 1.0e-6) return;
        AttributeInstance scale = le.getAttribute(Attribute.SCALE);
        if (scale == null) {
            LOG.fine(le.getType() + " 无 SCALE 属性，忽略 scale 配置");
            return;
        }
        scale.setBaseValue(scale.getBaseValue() * Math.max(0.05, mult));
    }

    private static void applyFollowRange(LivingEntity le, double mult) {
        if (Math.abs(mult - 1.0) < 1.0e-6) return;
        AttributeInstance fr = le.getAttribute(Attribute.FOLLOW_RANGE);
        if (fr == null) return;
        fr.setBaseValue(fr.getBaseValue() * Math.max(0.1, mult));
    }

    static void applyEquipment(LivingEntity le, List<MobEquipment> equipment) {
        if (equipment == null || equipment.isEmpty()) return;
        EntityEquipment eq = le.getEquipment();
        if (eq == null) return;
        for (MobEquipment entry : equipment) {
            if (entry.itemId().isBlank()) continue;
            ItemStack stack = ItemService.createItem(entry.itemId(), 1).orElse(null);
            if (stack == null) {
                LOG.warning("怪物装备物品不存在（ItemCreator）：" + entry.itemId());
                continue;
            }
            EquipmentSlot slot = parseSlot(entry.slot());
            if (slot == null) {
                LOG.warning("未知装备槽 " + entry.slot() + "，物品 " + entry.itemId());
                continue;
            }
            setSlot(eq, slot, stack);
        }
    }

    private static EquipmentSlot parseSlot(String raw) {
        if (raw == null || raw.isBlank()) return EquipmentSlot.HAND;
        return switch (raw.toUpperCase()) {
            case "HEAD", "HELMET" -> EquipmentSlot.HEAD;
            case "CHEST", "CHESTPLATE" -> EquipmentSlot.CHEST;
            case "LEGS", "LEGGINGS" -> EquipmentSlot.LEGS;
            case "FEET", "BOOTS" -> EquipmentSlot.FEET;
            case "MAIN_HAND", "HAND", "MAIN" -> EquipmentSlot.HAND;
            case "OFF_HAND", "OFFHAND" -> EquipmentSlot.OFF_HAND;
            default -> null;
        };
    }

    private static void setSlot(EntityEquipment eq, EquipmentSlot slot, ItemStack stack) {
        switch (slot) {
            case HEAD -> {
                eq.setHelmet(stack);
                eq.setHelmetDropChance(0f);
            }
            case CHEST -> {
                eq.setChestplate(stack);
                eq.setChestplateDropChance(0f);
            }
            case LEGS -> {
                eq.setLeggings(stack);
                eq.setLeggingsDropChance(0f);
            }
            case FEET -> {
                eq.setBoots(stack);
                eq.setBootsDropChance(0f);
            }
            case HAND -> {
                eq.setItemInMainHand(stack);
                eq.setItemInMainHandDropChance(0f);
            }
            case OFF_HAND -> {
                eq.setItemInOffHand(stack);
                eq.setItemInOffHandDropChance(0f);
            }
            default -> { }
        }
    }

    /** 战斗触发型词缀已退役；技能改为 Task 6 的 MobSkillService 挂载。 */
    private MobFactory() {}
}
