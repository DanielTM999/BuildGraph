package dtm.builder.build;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public final class CompilationScheduler implements AutoCloseable {
    private record Work<T>(long memory, boolean compilation, Callable<T> action,
                           CompletableFuture<T> result, long queued) { }
    private final ResourceManager resources;
    private final BuildMetrics metrics;
    private final ExecutorService workers;
    private final ArrayDeque<Work<?>> pending = new ArrayDeque<>();
    private final Set<Thread> active = new HashSet<>();
    private final Thread dispatcher;
    private boolean closed;
    private int admitted, compiling, peak;

    public CompilationScheduler(ResourceManager resources, BuildMetrics metrics) {
        this.resources = resources;
        this.metrics = metrics;
        workers = Executors.newFixedThreadPool(resources.workers(), Thread.ofPlatform().name("buildgraph-compile-", 0).factory());
        dispatcher = Thread.ofVirtual().name("buildgraph-admission").start(this::dispatch);
    }
    public int workers() { return resources.workers(); }
    public synchronized int peak() { return peak; }
    public synchronized <T> CompletableFuture<T> submit(long memory, boolean compilation, Callable<T> action) {
        var result = new CompletableFuture<T>();
        if (closed) result.completeExceptionally(new InterruptedException("Build cancelado"));
        else {
            pending.addLast(new Work<>(memory, compilation, action, result, metrics.start()));
            notifyAll();
        }
        return result;
    }
    private void dispatch() {
        try {
            while (true) {
                synchronized (this) {
                    while (!closed && (pending.isEmpty() || !resources.canAcquire(pending.getFirst().memory()))) {
                        long waiting = metrics.start();
                        String reason = pending.isEmpty() ? "worker_idle" : admitted >= resources.workers() ? "worker_wait" : "memory_wait";

                        if (pending.isEmpty() || admitted >= resources.workers()) wait();
                        else wait(250);
                        metrics.elapsed(reason, waiting);
                    }
                    if (closed) return;
                    Work<?> work = pending.removeFirst();
                    resources.acquire(work.memory());
                    admitted++;
                    workers.execute(() -> execute(work));
                }
            }
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
    private <T> void execute(Work<T> work) {
        synchronized (this) {
            active.add(Thread.currentThread());
            if (closed) Thread.currentThread().interrupt();
            if (work.compilation()) peak = Math.max(peak, ++compiling);
        }
        metrics.elapsed("queue_wait", work.queued());
        try {
            if (Thread.currentThread().isInterrupted()) throw new InterruptedException("Build cancelado");
            work.result().complete(work.action().call());
        } catch (Throwable e) { work.result().completeExceptionally(e); }
        finally {
            synchronized (this) {
                active.remove(Thread.currentThread());
                if (work.compilation()) compiling--;
                admitted--;
                resources.release(work.memory());
                notifyAll();
            }
        }
    }
    public synchronized void cancel() {
        closed = true;
        for (Work<?> work : pending) work.result().completeExceptionally(new InterruptedException("Build cancelado"));
        pending.clear();
        active.forEach(Thread::interrupt);
        notifyAll();
    }
    @Override public void close() {
        cancel();
        dispatcher.interrupt();
        workers.shutdown();
        boolean interrupted = Thread.interrupted();
        try {
            while (!workers.isTerminated()) {
                try { workers.awaitTermination(1, TimeUnit.SECONDS); }
                catch (InterruptedException e) { interrupted = true; cancel(); }
            }
            try { dispatcher.join(); } catch (InterruptedException e) { interrupted = true; }
        } finally { if (interrupted) Thread.currentThread().interrupt(); }
    }
}
