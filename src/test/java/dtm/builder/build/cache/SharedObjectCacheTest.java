package dtm.builder.build.cache;

import dtm.builder.build.incremental.FileHasher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class SharedObjectCacheTest {
    @TempDir Path temp;
    private static String key(String value) { return FileHasher.hashCommand(List.of(value)); }

    @Test void persistsAcrossInstancesAndRejectsCorruptOrTruncatedEntries() throws Exception {
        Path original = Files.writeString(temp.resolve("original.o"), "compiled object");
        Path restored = temp.resolve("restored.o");
        var cache = new SharedObjectCache(temp.resolve("cache"), 1L << 20);
        cache.store(key("object"), original);
        assertTrue(new SharedObjectCache(temp.resolve("cache"), 1L << 20).restore(key("object"), restored));
        assertEquals(Files.readString(original), Files.readString(restored));
        byte[] entry = Files.readAllBytes(cache.entry(key("object"))); entry[entry.length - 1] ^= 1;
        Files.write(cache.entry(key("object")), entry);
        Files.writeString(restored, "keep previous object on failed restore");
        assertFalse(cache.restore(key("object"), restored));
        assertEquals("keep previous object on failed restore", Files.readString(restored));
        Files.write(cache.entry(key("object")), new byte[3]);
        assertFalse(cache.restore(key("object"), restored));
        cache.store(key("object"), original);
        assertTrue(cache.restore(key("object"), restored));
    }

    @Test void simultaneousWritersAndReadersNeverExposePartialObjects() throws Exception {
        var cache = new SharedObjectCache(temp.resolve("cache"), 16L << 20);
        Path source = Files.writeString(temp.resolve("source.o"), "object".repeat(30_000));
        String digest = new FileHasher().sha256(source);
        try (var threads = Executors.newFixedThreadPool(8)) {
            var tasks = new java.util.ArrayList<Callable<Void>>();
            for (int i = 0; i < 40; i++) {
                int index = i;
                tasks.add(() -> {
                    cache.store(key("same"), source);
                    Path output = temp.resolve("output-" + index);

                    if (cache.restore(key("same"), output)) assertEquals(digest, new FileHasher().sha256(output));
                    return null;
                });
            }
            for (var result : threads.invokeAll(tasks)) result.get();
        }
        assertTrue(cache.restore(key("same"), temp.resolve("final.o")));
        try (var paths = Files.walk(temp)) { assertFalse(paths.anyMatch(p -> p.toString().endsWith(".tmp"))); }
    }

    @Test void pruningEvictsLeastRecentlyUsedAndHonorsQuota() throws Exception {
        var cache = new SharedObjectCache(temp.resolve("cache"), 700);
        Path source = Files.writeString(temp.resolve("source.o"), "x".repeat(150));
        for (int i = 0; i < 3; i++) {
            cache.store(key("k" + i), source);
            Files.setLastModifiedTime(cache.entry(key("k" + i)), FileTime.fromMillis(1000 + i));
        }
        cache.prune();
        assertFalse(Files.exists(cache.entry(key("k0"))));
        assertTrue(Files.exists(cache.entry(key("k2"))));
        try (var paths = Files.walk(temp.resolve("cache"))) {
            long bytes = paths.filter(p -> p.toString().endsWith(".entry")).mapToLong(p -> {
                try { return Files.size(p); } catch (Exception e) { throw new RuntimeException(e); }
            }).sum();
            assertTrue(bytes <= 700);
        }
    }

    @Test void unwritableCacheIsJustAMiss() throws Exception {
        Path file = Files.writeString(temp.resolve("file"), "not a directory");
        var cache = new SharedObjectCache(file, 1024);
        cache.store(key("object"), file); cache.prune();
        assertFalse(cache.restore(key("object"), temp.resolve("out.o")));
    }
}
