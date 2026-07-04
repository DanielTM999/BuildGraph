package dtm.builder.build.graph;

import dtm.builder.build.TargetType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TargetGraphTest {

    private static ResolvedTarget target(String id, String... deps) {
        return new ResolvedTarget(id, id, TargetType.SHARED, List.of("src"),
                List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(deps), false);
    }

    @Test
    void topologicalOrderRespectsDependencies() {
        TargetGraph g = TargetGraph.of(List.of(
                target("app", "core", "jit"),
                target("jit", "core"),
                target("core")));
        List<String> order = g.topologicalOrder().stream().map(ResolvedTarget::id).toList();
        assertTrue(order.indexOf("core") < order.indexOf("jit"));
        assertTrue(order.indexOf("jit") < order.indexOf("app"));
    }

    @Test
    void detectsCycle() {
        TargetGraph g = TargetGraph.of(List.of(
                target("a", "b"),
                target("b", "c"),
                target("c", "a"),
                target("solo")));
        List<String> cycle = g.cycle();
        assertEquals(Set.of("a", "b", "c"), Set.copyOf(cycle));
    }

    @Test
    void acyclicGraphHasNoCycle() {
        TargetGraph g = TargetGraph.of(List.of(target("a"), target("b", "a")));
        assertTrue(g.cycle().isEmpty());
    }

    @Test
    void readyReturnsOnlyTargetsWithCompletedDependencies() {
        TargetGraph g = TargetGraph.of(List.of(
                target("core"),
                target("util"),
                target("app", "core", "util")));

        List<String> initial = g.ready(Set.of(), Set.of())
                .stream().map(ResolvedTarget::id).toList();
        assertEquals(List.of("core", "util"), initial);

        List<String> afterCore = g.ready(Set.of("core"), Set.of("core", "util"))
                .stream().map(ResolvedTarget::id).toList();
        assertTrue(afterCore.isEmpty(), "app still waits for util");

        List<String> afterBoth = g.ready(Set.of("core", "util"), Set.of("core", "util"))
                .stream().map(ResolvedTarget::id).toList();
        assertEquals(List.of("app"), afterBoth);
    }

    @Test
    void subsetIncludesTransitiveDependencies() {
        TargetGraph g = TargetGraph.of(List.of(
                target("core"),
                target("jit", "core"),
                target("app", "jit"),
                target("other")));
        TargetGraph subset = g.subsetWithDependencies(List.of("app"));
        assertEquals(3, subset.size());
        List<String> ids = subset.targets().stream().map(ResolvedTarget::id).toList();
        assertEquals(List.of("core", "jit", "app"), ids);
    }
}
