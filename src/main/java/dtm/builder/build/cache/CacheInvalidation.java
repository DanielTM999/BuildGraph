package dtm.builder.build.cache;

import dtm.builder.build.BuildDirectoryLock;
import dtm.builder.build.ToolProbe;
import dtm.builder.build.incremental.FileHasher;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

/** Ownership metadata is separate from the content-addressed object format. */
public final class CacheInvalidation {
    private CacheInvalidation() { }

    private static Path index(Path root, Path project) throws IOException {
        String identity = project.toFile().getCanonicalPath();
        if (ToolProbe.isWindows()) identity = identity.toLowerCase(Locale.ROOT);
        return root.resolve("projects").resolve(FileHasher.hashCommand(List.of(identity)) + ".keys");
    }

    static void record(Path root, Path project, String key) throws IOException {
        try (var ignored = BuildDirectoryLock.acquire(root.resolve("projects"))) {
            Path file = index(root, project);
            if (Files.isSymbolicLink(file.getParent()) || Files.isSymbolicLink(file))
                throw new IOException("Indice de cache nao pode ser um link: " + file);
            Files.createDirectories(file.getParent());
            Files.writeString(file, key + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        }
    }

    public static void invalidate(Path directory, Path project, boolean global) throws IOException {
        Path root = directory.toAbsolutePath().normalize();
        if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) return;
        if (Files.isSymbolicLink(root)) throw new IOException("Diretorio de cache nao pode ser um link: " + root);
        if (Files.isSymbolicLink(root.resolve("projects"))) throw new IOException("Indice de cache nao pode ser um link");
        try (var ignored = BuildDirectoryLock.acquire(root.resolve("projects"))) {
            if (global) {
                // Only recognized cache files are removed, even when --cache-dir names a mixed directory.
                try (var paths = Files.list(root)) {
                    for (Path shard : paths.toList()) {
                        if (shard.getFileName().toString().matches("[a-f0-9]{2}")
                                && Files.isDirectory(shard, LinkOption.NOFOLLOW_LINKS)) {
                            try (var entries = Files.list(shard)) {
                                for (Path entry : entries.toList()) {
                                    String name = entry.getFileName().toString();
                                    if (name.matches("[a-f0-9]{64}\\.entry") && name.startsWith(shard.getFileName().toString())
                                            || name.matches("\\.write-.*\\.tmp")) Files.deleteIfExists(entry);
                                }
                            }
                        }
                    }
                }
                Path projects = root.resolve("projects");
                if (Files.isDirectory(projects, LinkOption.NOFOLLOW_LINKS)) {
                    try (var files = Files.list(projects)) {
                        for (Path file : files.toList())
                            if (file.getFileName().toString().matches("[a-f0-9]{64}\\.keys")) Files.deleteIfExists(file);
                    }
                }
            } else {
                Path file = index(root, project);
                Set<String> keys = readKeys(file);
                Path projects = root.resolve("projects");
                if (Files.isDirectory(projects, LinkOption.NOFOLLOW_LINKS)) {
                    try (var files = Files.list(projects)) {
                        for (Path other : files.toList())
                            if (!other.equals(file) && other.getFileName().toString().matches("[a-f0-9]{64}\\.keys"))
                                keys.removeAll(readKeys(other));
                    }
                }
                for (String key : keys) {
                    Path shard = root.resolve(key.substring(0, 2));
                    if (!Files.isSymbolicLink(shard)) Files.deleteIfExists(shard.resolve(key + ".entry"));
                }
                Files.deleteIfExists(file);
            }
        }
    }

    private static Set<String> readKeys(Path file) throws IOException {
        Set<String> keys = new HashSet<>();
        if (Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            for (String key : Files.readAllLines(file)) if (key.matches("[a-f0-9]{64}")) keys.add(key);
        }
        return keys;
    }

    static void compact(Path root, Path project) throws IOException {
        try (var ignored = BuildDirectoryLock.acquire(root.resolve("projects"))) {
            Path file = index(root, project);
            if (Files.isSymbolicLink(file.getParent()) || Files.isSymbolicLink(file)) return;
            if (!Files.isRegularFile(file)) return;
            Set<String> keys = readKeys(file);
            keys.removeIf(key -> !Files.isRegularFile(root.resolve(key.substring(0, 2)).resolve(key + ".entry")));
            Path temporary = Files.createTempFile(file.getParent(), ".index-", ".tmp");
            try {
                Files.write(temporary, new TreeSet<>(keys));
                SharedObjectCache.atomicMove(temporary, file);
            } finally { Files.deleteIfExists(temporary); }
        }
    }

    public static void invalidateLocal(Path buildDir) throws IOException {
        try (var ignored = BuildDirectoryLock.acquire(buildDir)) {
            for (String name : List.of(".buildgraph-state", ".obj")) {
                Path root = buildDir.toAbsolutePath().normalize().resolve(name);
                if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) continue;
                Files.walkFileTree(root, new SimpleFileVisitor<>() {
                    @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        Files.delete(file);
                        return FileVisitResult.CONTINUE;
                    }
                    @Override public FileVisitResult postVisitDirectory(Path dir, IOException error) throws IOException {
                        if (error != null) throw error;
                        Files.delete(dir);
                        return FileVisitResult.CONTINUE;
                    }
                });
            }
        }
    }
}
