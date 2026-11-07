package dtm.builder.build;

import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class CompilationSchedulerTest {
    private static ResourceManager resources(int workers, long budget) {
        var options = new BuildOptions(budget, 0, Path.of("unused"), 0, false, false);
        return new ResourceManager(workers, options, 8, 32L << 30, () -> 16L << 30);
    }

    @Test void thousandsOfUnitsShareOneGlobalWorkerLimit() throws Exception {
        AtomicInteger active = new AtomicInteger(), peak = new AtomicInteger(), completed = new AtomicInteger();
        CountDownLatch started = new CountDownLatch(4), release = new CountDownLatch(1);
        try (var scheduler = new CompilationScheduler(resources(4, 8L << 30), new BuildMetrics(true))) {
            List<CompletableFuture<Integer>> results = new ArrayList<>();
            for (int i = 0; i < 3000; i++) results.add(scheduler.submit(128L << 20, true, () -> {
                peak.accumulateAndGet(active.incrementAndGet(), Math::max);
                started.countDown();
                try { assertTrue(release.await(10, TimeUnit.SECONDS)); return completed.incrementAndGet(); }
                finally { active.decrementAndGet(); }
            }));
            assertTrue(started.await(10, TimeUnit.SECONDS));
            assertEquals(4, peak.get());
            release.countDown();
            CompletableFuture.allOf(results.toArray(CompletableFuture[]::new)).get(20, TimeUnit.SECONDS);
            assertEquals(3000, completed.get());
            assertEquals(4, scheduler.peak());
        } finally { release.countDown(); }
        assertEquals(0, active.get());
    }

    @Test void memoryBudgetIsFifoAndLargeUnitCannotStarve() throws Exception {
        CountDownLatch first = new CountDownLatch(1), release = new CountDownLatch(1);
        List<Integer> order = new CopyOnWriteArrayList<>();
        try (var scheduler = new CompilationScheduler(resources(4, 1024L << 20), new BuildMetrics(true))) {
            var a = scheduler.submit(768L << 20, true, () -> { first.countDown(); release.await(); order.add(1); return 1; });
            assertTrue(first.await(5, TimeUnit.SECONDS));
            var b = scheduler.submit(1024L << 20, true, () -> { order.add(2); return 2; });
            var c = scheduler.submit(128L << 20, true, () -> { order.add(3); return 3; });
            assertFalse(b.isDone()); assertFalse(c.isDone());
            release.countDown();
            CompletableFuture.allOf(a, b, c).get(10, TimeUnit.SECONDS);
            assertEquals(List.of(1, 2, 3), order);
        } finally { release.countDown(); }
    }

    @Test void oversizedUnitRunsAloneAndMakesProgress() throws Exception {
        try (var scheduler = new CompilationScheduler(resources(4, 64L << 20), new BuildMetrics(false))) {
            var results = new ArrayList<CompletableFuture<Integer>>();
            for (int i = 0; i < 50; i++) results.add(scheduler.submit(1L << 30, true, () -> 7));
            CompletableFuture.allOf(results.toArray(CompletableFuture[]::new)).get(10, TimeUnit.SECONDS);
            assertEquals(1, scheduler.peak());
        }
    }

    @Test void cancellationInterruptsRunningAndCompletesQueuedUnits() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        try (var scheduler = new CompilationScheduler(resources(1, 1L << 30), new BuildMetrics(true))) {
            var active = scheduler.submit(128L << 20, true, () -> { started.countDown(); new CountDownLatch(1).await(); return 0; });
            assertTrue(started.await(5, TimeUnit.SECONDS));
            var queued = scheduler.submit(128L << 20, true, () -> fail("Cancelled work must not start"));
            scheduler.cancel();
            assertThrows(ExecutionException.class, () -> active.get(5, TimeUnit.SECONDS));
            assertThrows(ExecutionException.class, () -> queued.get(5, TimeUnit.SECONDS));
        }
    }

    @Test void unknownPhysicalMemoryIsConservativeAndCpuIsCapped() {
        var options = new BuildOptions(8L << 30, 1L << 20, Path.of("unused"), 0, false, false);
        var resources = new ResourceManager(100, options, 4, -1, () -> -1);
        assertEquals(4, resources.workers());
        assertTrue(resources.canAcquire(32L << 20));
        resources.acquire(32L << 20);
        assertFalse(resources.canAcquire(32L << 20));
        resources.release(32L << 20);
        assertTrue(resources.canAcquire(32L << 20));
    }

    @Test void disabledMetricsDoNotRecordCountersOrTimers() {
        BuildMetrics metrics = new BuildMetrics(false);
        assertEquals(0, metrics.start()); metrics.add("compiled", 1); metrics.elapsed("compile", 0);
        assertEquals(0, metrics.value("compiled")); assertEquals(0, metrics.value("compile_ns"));
    }
}
