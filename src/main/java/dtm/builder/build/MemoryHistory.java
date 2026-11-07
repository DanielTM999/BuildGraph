package dtm.builder.build;

import java.io.IOException;
import java.nio.file.*;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

final class MemoryHistory {
    private final Path file;
    private final ConcurrentHashMap<String, Long> peaks = new ConcurrentHashMap<>();
    private volatile boolean changed;
    MemoryHistory(Path file) {
        this.file = file;
        try (var input = Files.newInputStream(file)) {
            Properties values = new Properties(); values.load(input);
            for (String key : values.stringPropertyNames()) {
                long value = Long.parseLong(values.getProperty(key));
                if (value > 0) peaks.put(key, value);
            }
        } catch (IOException | RuntimeException ignored) { }
    }
    long peak(String key) { return peaks.getOrDefault(key, 0L); }
    void record(String key, long value) {
        if (value > peak(key)) { peaks.merge(key, value, Math::max); changed = true; }
    }
    void save() {
        if (!changed) return;
        Path temporary = null;
        try {
            Files.createDirectories(file.toAbsolutePath().getParent());
            temporary = Files.createTempFile(file.toAbsolutePath().getParent(), ".memory-", ".tmp");
            Properties values = new Properties();

            peaks.entrySet().stream().sorted(java.util.Map.Entry.<String,Long>comparingByValue().reversed()).limit(20_000)
                    .forEach(entry -> values.setProperty(entry.getKey(), entry.getValue().toString()));
            try (var output = Files.newOutputStream(temporary)) { values.store(output, "BuildGraph observed peak RSS bytes"); }
            dtm.builder.build.cache.SharedObjectCache.atomicMove(temporary, file);
        } catch (IOException | RuntimeException ignored) { }
        finally { if (temporary != null) try { Files.deleteIfExists(temporary); } catch (IOException ignored) { } }
    }
}
