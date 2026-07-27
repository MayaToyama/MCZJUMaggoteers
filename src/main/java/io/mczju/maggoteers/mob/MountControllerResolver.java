package io.mczju.maggoteers.mob;

import io.mczju.maggoteers.wave.PassengerSpawn;
import org.bukkit.entity.Camel;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** 骑乘小队 AI 控制实体（§1.7 spec）。 */
public final class MountControllerResolver {

    private record DepthMob(LivingEntity entity, int depth) {}

    private MountControllerResolver() {}

    public static LivingEntity resolve(LivingEntity root,
                                       List<LivingEntity> directPassengersInOrder,
                                       List<PassengerSpawn> passengerSpec) {
        if (passengerSpec == null || passengerSpec.isEmpty()) {
            return root;
        }
        if (root instanceof Camel
                && passengerSpec.size() == 2
                && passengerSpec.get(0).passengers().isEmpty()
                && passengerSpec.get(1).passengers().isEmpty()
                && !directPassengersInOrder.isEmpty()) {
            return directPassengersInOrder.getFirst();
        }
        if (root instanceof Camel && !passengerSpec.isEmpty() && !directPassengersInOrder.isEmpty()) {
            LivingEntity front = directPassengersInOrder.getFirst();
            PassengerSpawn frontSpec = passengerSpec.getFirst();
            LivingEntity deep = deepestMob(front, frontSpec, 1);
            if (deep instanceof Mob) return deep;
        }
        List<DepthMob> all = new ArrayList<>();
        for (int i = 0; i < directPassengersInOrder.size() && i < passengerSpec.size(); i++) {
            collectDepth(directPassengersInOrder.get(i), passengerSpec.get(i), 1, all);
        }
        return all.stream()
                .filter(d -> d.entity() instanceof Mob && d.entity().isValid() && !d.entity().isDead())
                .max(Comparator.comparingInt(DepthMob::depth))
                .map(DepthMob::entity)
                .orElse(root);
    }

    private static void collectDepth(LivingEntity entity, PassengerSpawn spec, int depth, List<DepthMob> out) {
        out.add(new DepthMob(entity, depth));
        List<LivingEntity> children = entity.getPassengers().stream()
                .filter(LivingEntity.class::isInstance)
                .map(LivingEntity.class::cast)
                .toList();
        List<PassengerSpawn> nested = spec.passengers();
        for (int i = 0; i < children.size() && i < nested.size(); i++) {
            collectDepth(children.get(i), nested.get(i), depth + 1, out);
        }
    }

    private static LivingEntity deepestMob(LivingEntity entity, PassengerSpawn spec, int depth) {
        LivingEntity best = entity;
        int bestDepth = depth;
        List<LivingEntity> children = entity.getPassengers().stream()
                .filter(LivingEntity.class::isInstance)
                .map(LivingEntity.class::cast)
                .toList();
        List<PassengerSpawn> nested = spec.passengers();
        for (int i = 0; i < children.size() && i < nested.size(); i++) {
            LivingEntity candidate = deepestMob(children.get(i), nested.get(i), depth + 1);
            int candidateDepth = maxDepth(candidate, nested.get(i), depth + 1);
            if (candidateDepth >= bestDepth) {
                bestDepth = candidateDepth;
                best = candidate;
            }
        }
        return best;
    }

    private static int maxDepth(LivingEntity entity, PassengerSpawn spec, int depth) {
        int d = depth;
        List<LivingEntity> children = entity.getPassengers().stream()
                .filter(LivingEntity.class::isInstance)
                .map(LivingEntity.class::cast)
                .toList();
        List<PassengerSpawn> nested = spec.passengers();
        for (int i = 0; i < children.size() && i < nested.size(); i++) {
            d = Math.max(d, maxDepth(children.get(i), nested.get(i), depth + 1));
        }
        return d;
    }
}
