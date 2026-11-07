package dtm.builder.build;

import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.LongSupplier;

public final class ResourceManager {
    public static final long MIB = 1L << 20;
    private final int workers;
    private final long budget;
    private final long reserve;
    private final LongSupplier available;
    private int running;
    private long reserved;

    public ResourceManager(int jobs, BuildOptions options) {
        this(jobs, options, Runtime.getRuntime().availableProcessors(), physicalTotal(), ResourceManager::physicalAvailable);
    }

    ResourceManager(int jobs, BuildOptions options, int cpus, long total, LongSupplier available) {
        this.available = available;
        reserve = options.memoryReserve() > 0 ? options.memoryReserve() : Math.max(512 * MIB, total > 0 ? total / 10 : 0);
        long free = available.getAsLong();
        boolean reliable = free > 0 && total > 0;
        long automatic = reliable ? Math.max(128 * MIB, free - reserve) : 512 * MIB;
        budget = options.memoryLimit() > 0 ? Math.min(options.memoryLimit(), automatic) : automatic;
        workers = Math.max(1, Math.min(jobs > 0 ? jobs : cpus, Math.max(1, cpus)));
    }

    public int workers() { return workers; }
    public long budget() { return budget; }
    public boolean canAcquire(long estimate) {
        if (running >= workers) return false;

        if (running == 0) return true;
        long free = available.getAsLong();
        return estimate <= budget - reserved && free > 0 && estimate <= Math.max(0, free - reserve);
    }
    public void acquire(long estimate) { running++; reserved += Math.min(estimate, budget); }
    public void release(long estimate) { running--; reserved -= Math.min(estimate, budget); }

    public static long estimate(Path source, long configured, long observed) {
        long baseline = configured > 0 ? configured : SourceCollector.isCppSources(java.util.List.of(source)) ? 768 * MIB : 256 * MIB;
        long size = 0;
        try { size = Files.size(source); } catch (java.io.IOException ignored) { }

        long heuristic = baseline + Math.min(4L << 30, size * 128);
        return Math.max(heuristic, observed > 0 ? observed + observed / 4 : 0);
    }

    private static long physicalTotal() {
        return ManagementFactory.getOperatingSystemMXBean() instanceof com.sun.management.OperatingSystemMXBean os
                ? os.getTotalMemorySize() : -1;
    }
    private static long physicalAvailable() {
        try {

            if (Files.isRegularFile(Path.of("/proc/meminfo")))
                for (String line : Files.readAllLines(Path.of("/proc/meminfo")))
                    if (line.startsWith("MemAvailable:")) return Long.parseLong(line.replaceAll("[^0-9]", "")) * 1024;
            if (ManagementFactory.getOperatingSystemMXBean() instanceof com.sun.management.OperatingSystemMXBean os)
                return os.getFreeMemorySize();
        } catch (Exception ignored) { }
        return -1;
    }
}
