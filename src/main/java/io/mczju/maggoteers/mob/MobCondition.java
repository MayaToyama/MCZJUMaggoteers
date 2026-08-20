package io.mczju.maggoteers.mob;

import io.mczju.maggoteers.game.MaggoteersGame;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * 怪物技能条件修饰符（AND/OR/NOT + 叶子），<b>以怪物为中心</b>判断：
 * HoldItemPdc 看怪物手持；PlayerInRadius 以怪物位置为中心查玩家。
 */
public sealed interface MobCondition {

    boolean evaluate(MaggoteersGame game, LivingEntity mob, LivingEntity target, LivingEntity source);

    /** 恒真（纯逻辑组合测试用）。 */
    MobCondition TRUE = new Constant(true);
    /** 恒假（纯逻辑组合测试用）。 */
    MobCondition FALSE = new Constant(false);

    /** 恒值叶子（TRUE/FALSE 常量的载体；sealed 接口不允许 lambda 实现）。 */
    record Constant(boolean value) implements MobCondition {
        @Override public boolean evaluate(MaggoteersGame game, LivingEntity mob,
                                          LivingEntity target, LivingEntity source) {
            return value;
        }
    }

    record And(List<MobCondition> children) implements MobCondition {
        public And {
            children = children == null ? List.of() : List.copyOf(children);
        }
        @Override public boolean evaluate(MaggoteersGame game, LivingEntity mob,
                                          LivingEntity target, LivingEntity source) {
            for (MobCondition c : children) {
                if (!c.evaluate(game, mob, target, source)) {
                    return false;
                }
            }
            return true;
        }
    }

    record Or(List<MobCondition> children) implements MobCondition {
        public Or {
            children = children == null ? List.of() : List.copyOf(children);
        }
        @Override public boolean evaluate(MaggoteersGame game, LivingEntity mob,
                                          LivingEntity target, LivingEntity source) {
            for (MobCondition c : children) {
                if (c.evaluate(game, mob, target, source)) {
                    return true;
                }
            }
            return false;
        }
    }

    record Not(MobCondition child) implements MobCondition {
        @Override public boolean evaluate(MaggoteersGame game, LivingEntity mob,
                                          LivingEntity target, LivingEntity source) {
            return child != null && !child.evaluate(game, mob, target, source);
        }
    }

    /** 怪物手持物品 PDC {@code maggoteers:id} 匹配。 */
    record HoldItemPdc(String pdcValue) implements MobCondition {
        @Override public boolean evaluate(MaggoteersGame game, LivingEntity mob,
                                          LivingEntity target, LivingEntity source) {
            if (mob == null) {
                return false;
            }
            ItemStack held = mob.getEquipment() == null ? null : mob.getEquipment().getItemInMainHand();
            String id = io.mczju.maggoteers.item.ItemService.itemIdOf(held);
            return pdcValue != null && pdcValue.equals(id);
        }
    }

    /** 怪物位置半径内存在存活玩家。 */
    record PlayerInRadius(double radius) implements MobCondition {
        @Override public boolean evaluate(MaggoteersGame game, LivingEntity mob,
                                          LivingEntity target, LivingEntity source) {
            if (mob == null || mob.getWorld() == null) {
                return false;
            }
            double r = Math.max(0.0, radius);
            double rsq = r * r;
            for (Player pl : mob.getWorld().getPlayers()) {
                if (!pl.isValid() || pl.isDead()) {
                    continue;
                }
                if (pl.getLocation().distanceSquared(mob.getLocation()) <= rsq) {
                    return true;
                }
            }
            return false;
        }
    }
}
