package io.mczju.maggoteers.mob;

import io.mczju.maggoteers.config.InfernalCfg;
import io.mczju.maggoteers.wave.PassengerSpawn;
import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MountControllerResolverTest {

    private static PassengerSpawn node(boolean controller, PassengerSpawn... nested) {
        return new PassengerSpawn(EntityType.ZOMBIE, 1.0, 1.0, 1.0, 1.0, 1.0,
                List.of(), List.of(), InfernalCfg.NONE, List.of(), List.of(),
                List.of(nested), null, false, controller);
    }

    @Test
    void emptySpecYieldsNoCandidates() {
        assertEquals(List.of(), MountControllerResolver.controllerCandidates(List.of(), false));
    }

    @Test
    void nonCamelTakesDeepestLeaf() {
        var spec = List.of(node(false, node(false, node(false))));
        assertEquals(List.of(List.of(0, 0, 0)),
                MountControllerResolver.controllerCandidates(spec, false));
    }

    @Test
    void camelDoubleLeafPrefersFirstSeat() {
        var spec = List.of(node(false), node(false));
        assertEquals(List.of(List.of(0)),
                MountControllerResolver.controllerCandidates(spec, true));
    }

    @Test
    void camelNestedPrefersFirstSubtreeEvenIfShallower() {
        // 前座子树浅、后座深：规则 2.5 仍先前座子树，整树最深兜底
        var spec = List.of(node(false, node(false)), node(false, node(false, node(false))));
        assertEquals(List.of(List.of(0, 0), List.of(1, 0, 0)),
                MountControllerResolver.controllerCandidates(spec, true));
    }

    @Test
    void nonCamelDeepestAcrossAllSubtrees() {
        var spec = List.of(node(false, node(false)), node(false, node(false, node(false))));
        assertEquals(List.of(List.of(1, 0, 0)),
                MountControllerResolver.controllerCandidates(spec, false));
    }

    @Test
    void explicitControllerBecomesFirstCandidate() {
        var spec = List.of(node(true), node(false, node(false, node(false))));
        assertEquals(List.of(List.of(0), List.of(1, 0, 0)),
                MountControllerResolver.controllerCandidates(spec, false));
    }

    @Test
    void multipleExplicitControllersInDfsOrder() {
        var spec = List.of(node(true, node(true)), node(false, node(false)));
        assertEquals(List.of(List.of(0), List.of(0, 0)),
                MountControllerResolver.controllerCandidates(spec, false));
    }

    @Test
    void explicitControllerOverridesCamelDoubleLeaf() {
        var spec = List.of(node(false), node(true));
        assertEquals(List.of(List.of(1), List.of(0)),
                MountControllerResolver.controllerCandidates(spec, true));
    }

    @Test
    void defaultFallbackDedupedAgainstExplicit() {
        // 显式 [0]，默认回落整树最深 [0,0] → 保留
        var spec = List.of(node(true, node(false)));
        assertEquals(List.of(List.of(0), List.of(0, 0)),
                MountControllerResolver.controllerCandidates(spec, false));
    }
}
