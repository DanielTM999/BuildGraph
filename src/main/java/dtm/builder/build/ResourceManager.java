package dtm.builder.build;

import com.sun.management.OperatingSystemMXBean;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.LongSupplier;

public final class ResourceManager {
    public static final long MIB = 1L << 20;
    private static final long ACCOUNTED_LIMIT = 1L << 50;
    private final int workers;
    private final long budget;
    private final long limit;
    private final long reserve;
    private final LongSupplier available;
    private int running;
    private long reserved;

    public ResourceManager(int jobs, BuildOptions options) {
        this(jobs, options, Runtime.getRuntime().availableProcessors(), physicalTotal(), ResourceManager::physicalAvailable);
    }

    ResourceManager(int jobs, BuildOptions options, int cpus, long total, LongSupplier available) {
        this.available = available;
        reserve = options.memoryReserve() > 0 ? options.memoryReserve()
                : Math.min(2048 * MIB, Math.max(512 * MIB, total > 0 ? total / 16 : 0));
        long free = available.getAsLong();
        boolean reliable = free > 0 && total > 0;
        long automatic = reliable ? Math.max(128 * MIB, free - reserve) : 512 * MIB;
        limit = options.memoryLimit() > 0 ? options.memoryLimit() : ACCOUNTED_LIMIT;
        budget = Math.min(limit, automatic);
        workers = Math.max(1, Math.min(jobs > 0 ? jobs : cpus, Math.max(1, cpus)));
    }

    public int workers() { return workers; }
    public long budget() { return budget; }
    public boolean canAcquire(long estimate) {
        if (running >= workers) return false;

        if (running == 0) return true;
        long accounted = accounted(estimate);
        if (reserved + accounted > limit) return false;
        long free = available.getAsLong();
        return free > 0 && accounted + reserved / 2 <= free - reserve;
    }
    public void acquire(long estimate) { running++; reserved += accounted(estimate); }
    public void release(long estimate) { running--; reserved -= accounted(estimate); }
    private static long accounted(long estimate) { return Math.min(Math.max(0, estimate), ACCOUNTED_LIMIT); }

    public static long estimate(Path source, long configured, long observed) {
        long measured = observed > 0 ? observed + observed / 4 : 0;
        if (configured <= 0 && measured > 0) return Math.max(64 * MIB, measured);
        boolean cpp = SourceCollector.isCppSources(List.of(source));
        long size = 0;
        try { size = Files.size(source); } catch (IOException ignored) { }

        long heuristic = configured > 0 ? configured + Math.min(4L << 30, size * 128)
                : (cpp ? 256 * MIB : 128 * MIB) + Math.min(4L << 30, size * (cpp ? 512 : 256));
        return Math.max(heuristic, measured);
    }

    private static long physicalTotal() {
        return ManagementFactory.getOperatingSystemMXBean() instanceof OperatingSystemMXBean os
                ? os.getTotalMemorySize() : -1;
    }
    private static long physicalAvailable() {
        try {

            if (Files.isRegularFile(Path.of("/proc/meminfo")))
                for (String line : Files.readAllLines(Path.of("/proc/meminfo")))
                    if (line.startsWith("MemAvailable:")) return Long.parseLong(line.replaceAll("[^0-9]", "")) * 1024;
            if (ManagementFactory.getOperatingSystemMXBean() instanceof OperatingSystemMXBean os)
                return os.getFreeMemorySize();
        } catch (Exception ignored) { }
        return -1;
    }
}
