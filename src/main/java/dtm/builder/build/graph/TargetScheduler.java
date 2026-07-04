package dtm.builder.build.graph;

import dtm.builder.build.BuildResult;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Executa os targets do grafo respeitando dependsOn. Targets independentes
 * rodam em paralelo (limitados por jobs); na primeira falha nenhum target
 * novo é iniciado (fail-fast), mas os que já estão rodando terminam.
 */
public final class TargetScheduler {

    public record Result(Map<String, BuildResult> results, List<String> skipped, boolean success) {
    }

    private TargetScheduler() {
    }

    public static Result run(TargetGraph graph, int jobs, TargetBuilder builder,
                             Consumer<String> info) {
        int limit = jobs > 0 ? jobs : Runtime.getRuntime().availableProcessors();
        limit = Math.max(1, Math.min(limit, graph.size()));

        if (limit == 1) {
            return runSerial(graph, builder);
        }
        return runParallel(graph, limit, builder, info);
    }

    private static Result runSerial(TargetGraph graph, TargetBuilder builder) {
        Map<String, BuildResult> results = new LinkedHashMap<>();
        boolean success = true;
        for (ResolvedTarget target : graph.topologicalOrder()) {
            if (!success) {
                continue;
            }
            BuildResult r = safeBuild(builder, target);
            results.put(target.id(), r);
            success = r.success();
        }
        return new Result(results, skippedIds(graph, results), success);
    }

    private static Result runParallel(TargetGraph graph, int limit, TargetBuilder builder,
                                      Consumer<String> info) {
        Object lock = new Object();
        Map<String, BuildResult> results = new LinkedHashMap<>();
        Set<String> done = new HashSet<>();
        Set<String> started = new HashSet<>();
        int[] running = {0};
        boolean[] failed = {false};

        ExecutorService pool = Executors.newFixedThreadPool(limit);
        try {
            synchronized (lock) {
                info.accept("Build paralelo com " + limit + " jobs");
                scheduleReady(graph, pool, builder, lock, results, done, started, running, failed);
                while (running[0] > 0 || (!failed[0] && done.size() < graph.size())) {
                    try {
                        lock.wait();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        failed[0] = true;
                        break;
                    }
                }
            }
        } finally {
            pool.shutdown();
        }

        synchronized (lock) {
            return new Result(new LinkedHashMap<>(results), skippedIds(graph, results),
                    !failed[0]);
        }
    }

    private static void scheduleReady(TargetGraph graph, ExecutorService pool,
                                      TargetBuilder builder, Object lock,
                                      Map<String, BuildResult> results, Set<String> done,
                                      Set<String> started, int[] running, boolean[] failed) {
        if (failed[0]) {
            return;
        }
        for (ResolvedTarget target : graph.ready(done, started)) {
            started.add(target.id());
            running[0]++;
            pool.submit(() -> {
                BuildResult r = safeBuild(builder, target);
                synchronized (lock) {
                    results.put(target.id(), r);
                    running[0]--;
                    if (r.success()) {
                        done.add(target.id());
                        scheduleReady(graph, pool, builder, lock, results, done, started,
                                running, failed);
                    } else {
                        failed[0] = true;
                    }
                    lock.notifyAll();
                }
            });
        }
    }

    private static BuildResult safeBuild(TargetBuilder builder, ResolvedTarget target) {
        try {
            return builder.build(target);
        } catch (Exception e) {
            return BuildResult.fail(1, "Falha inesperada no target '" + target.id() + "': " + e);
        }
    }

    private static List<String> skippedIds(TargetGraph graph, Map<String, BuildResult> results) {
        List<String> skipped = new ArrayList<>();
        for (ResolvedTarget t : graph.targets()) {
            if (!results.containsKey(t.id())) {
                skipped.add(t.id());
            }
        }
        return skipped;
    }

}
