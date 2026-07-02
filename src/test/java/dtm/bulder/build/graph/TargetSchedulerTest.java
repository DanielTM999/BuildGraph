package dtm.bulder.build.graph;

import dtm.bulder.build.BuildResult;
import dtm.bulder.build.TargetType;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TargetSchedulerTest {

    private static ResolvedTarget target(String id, String... deps) {
        return new ResolvedTarget(id, id, TargetType.SHARED, List.of("src"),
                List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(deps), false);
    }

    private static BuildResult ok(String id) {
        return BuildResult.ok(0, Path.of(id), id);
    }

    @Test
    void serialModeBuildsInTopologicalOrder() {
        TargetGraph graph = TargetGraph.of(List.of(
                target("app", "core"),
                target("core")));
        List<String> order = new java.util.ArrayList<>();
        TargetScheduler.Result r = TargetScheduler.run(graph, 1, t -> {
            order.add(t.id());
            return ok(t.id());
        }, line -> {
        });
        assertTrue(r.success());
        assertEquals(List.of("core", "app"), order);
    }

    @Test
    void serialModeReportsProgressFromZeroToTotal() {
        TargetGraph graph = TargetGraph.of(List.of(
                target("app", "core"),
                target("core")));
        List<String> progress = new java.util.ArrayList<>();

        TargetScheduler.Result r = TargetScheduler.run(graph, 1,
                t -> ok(t.id()), progress::add);

        assertTrue(r.success());
        assertEquals(List.of(
                "[0/2] Iniciando build",
                "[1/2] Target core concluido",
                "[2/2] Target app concluido"), progress);
    }

    @Test
    void serialFailFastSkipsRemainingTargets() {
        TargetGraph graph = TargetGraph.of(List.of(
                target("core"),
                target("app", "core")));
        TargetScheduler.Result r = TargetScheduler.run(graph, 1,
                t -> BuildResult.fail(1, "boom " + t.id()), line -> {
                });
        assertFalse(r.success());
        assertEquals(1, r.results().size());
        assertEquals(List.of("app"), r.skipped());
    }

    @Test
    void independentTargetsRunConcurrently() throws Exception {
        TargetGraph graph = TargetGraph.of(List.of(target("a"), target("b")));
        CountDownLatch bothStarted = new CountDownLatch(2);
        TargetScheduler.Result r = TargetScheduler.run(graph, 2, t -> {
            bothStarted.countDown();
            try {
                // só termina quando os dois estiverem rodando ao mesmo tempo
                if (!bothStarted.await(5, TimeUnit.SECONDS)) {
                    return BuildResult.fail(1, "targets nao rodaram em paralelo");
                }
            } catch (InterruptedException e) {
                return BuildResult.fail(1, "interrompido");
            }
            return ok(t.id());
        }, line -> {
        });
        assertTrue(r.success(), () -> String.valueOf(r.results()));
        assertEquals(2, r.results().size());
    }

    @Test
    void parallelProgressRemainsMonotonic() {
        TargetGraph graph = TargetGraph.of(List.of(target("a"), target("b")));
        ConcurrentLinkedQueue<String> output = new ConcurrentLinkedQueue<>();

        TargetScheduler.Result r = TargetScheduler.run(graph, 2,
                t -> ok(t.id()), output::add);

        assertTrue(r.success());
        List<String> progress = output.stream()
                .filter(line -> line.startsWith("["))
                .toList();
        assertEquals(3, progress.size());
        assertTrue(progress.get(0).startsWith("[0/2]"));
        assertTrue(progress.get(1).startsWith("[1/2]"));
        assertTrue(progress.get(2).startsWith("[2/2]"));
    }

    @Test
    void dependentTargetOnlyStartsAfterDependencies() {
        TargetGraph graph = TargetGraph.of(List.of(
                target("core"),
                target("util"),
                target("app", "core", "util")));
        Set<String> done = java.util.concurrent.ConcurrentHashMap.newKeySet();
        ConcurrentLinkedQueue<String> violations = new ConcurrentLinkedQueue<>();
        TargetScheduler.Result r = TargetScheduler.run(graph, 4, t -> {
            for (String dep : t.dependsOn()) {
                if (!done.contains(dep)) {
                    violations.add(t.id() + " iniciou antes de " + dep);
                }
            }
            done.add(t.id());
            return ok(t.id());
        }, line -> {
        });
        assertTrue(r.success());
        assertTrue(violations.isEmpty(), violations::toString);
        assertEquals(3, r.results().size());
    }

    @Test
    void parallelFailFastDoesNotStartDependents() {
        TargetGraph graph = TargetGraph.of(List.of(
                target("core"),
                target("app", "core")));
        AtomicInteger builds = new AtomicInteger();
        TargetScheduler.Result r = TargetScheduler.run(graph, 4, t -> {
            builds.incrementAndGet();
            return BuildResult.fail(1, "boom");
        }, line -> {
        });
        assertFalse(r.success());
        assertEquals(1, builds.get(), "app nao deve iniciar apos falha de core");
        assertEquals(List.of("app"), r.skipped());
    }

    @Test
    void failedTargetIsIncludedInProgress() {
        TargetGraph graph = TargetGraph.of(List.of(
                target("core"),
                target("app", "core")));
        List<String> progress = new java.util.ArrayList<>();

        TargetScheduler.Result r = TargetScheduler.run(graph, 1,
                t -> BuildResult.fail(1, "boom"), progress::add);

        assertFalse(r.success());
        assertEquals(List.of(
                "[0/2] Iniciando build",
                "[1/2] Target core falhou"), progress);
    }

    @Test
    void builderExceptionBecomesFailedResult() {
        TargetGraph graph = TargetGraph.of(List.of(target("a")));
        TargetScheduler.Result r = TargetScheduler.run(graph, 1, t -> {
            throw new IllegalStateException("kaput");
        }, line -> {
        });
        assertFalse(r.success());
        Map.Entry<String, BuildResult> e = r.results().entrySet().iterator().next();
        assertTrue(e.getValue().message().contains("kaput"));
    }
}
