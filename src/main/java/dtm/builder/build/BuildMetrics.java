package dtm.builder.build;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Consumer;

public final class BuildMetrics {
    private final boolean enabled;
    private final ConcurrentHashMap<String, LongAdder> counters = new ConcurrentHashMap<>();
    public BuildMetrics(boolean enabled) { this.enabled = enabled; }
    public long start() { return enabled ? System.nanoTime() : 0; }
    public void elapsed(String key, long start) { if (enabled) add(key + "_ns", System.nanoTime() - start); }
    public void add(String key, long value) {
        if (enabled) counters.computeIfAbsent(key, ignored -> new LongAdder()).add(value);
    }
    public long value(String key) { var counter = counters.get(key); return counter == null ? 0 : counter.sum(); }
    public void report(Consumer<String> output, int peak, long budget, int workers) {
        if (!enabled) return;
        output.accept("[profile] workers=" + workers + " memory_budget_bytes=" + budget + " max_compilations=" + peak);
        counters.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(entry -> output.accept("[profile] " + entry.getKey() + "=" + entry.getValue().sum()));
    }
}
