package dtm.builder.build;

import dtm.builder.build.incremental.FileHasher;

import java.io.IOException;
import java.nio.channels.*;
import java.nio.file.*;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

public final class BuildDirectoryLock implements AutoCloseable {
    private static final class Local {
        final ReentrantLock lock = new ReentrantLock(true);
        int users;
    }
    private static final ConcurrentHashMap<String, Local> LOCAL = new ConcurrentHashMap<>();
    private final String key;
    private final Local local;
    private final FileChannel channel;
    private final FileLock lock;

    private BuildDirectoryLock(String key, Local local, FileChannel channel, FileLock lock) {
        this.key = key; this.local = local; this.channel = channel; this.lock = lock;
    }

    public static BuildDirectoryLock acquire(Path directory) throws IOException {
        String path = directory.toFile().getCanonicalPath();
        if (ToolProbe.isWindows()) path = path.toLowerCase(Locale.ROOT);
        String key = FileHasher.hashCommand(List.of(path));
        Local local = LOCAL.compute(key, (ignored, current) -> { if (current == null) current = new Local(); current.users++; return current; });
        boolean acquired = false;
        FileChannel channel = null;
        try {
            local.lock.lockInterruptibly(); acquired = true;
            Path root = Files.createDirectories(Path.of(System.getProperty("java.io.tmpdir"), "buildgraph-locks"));
            channel = FileChannel.open(root.resolve(key + ".lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            FileLock lock = channel.lock();
            return new BuildDirectoryLock(key, local, channel, lock);
        } catch (IOException | RuntimeException | InterruptedException e) {
            if (channel != null) channel.close();
            if (acquired) local.lock.unlock();
            releaseReference(key, local);
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new IOException("Nao foi possivel bloquear o diretorio de build: " + directory, e);
        }
    }
    private static void releaseReference(String key, Local local) {
        LOCAL.compute(key, (ignored, current) -> --local.users == 0 ? null : current);
    }
    @Override public void close() {
        try { lock.release(); } catch (IOException ignored) { }
        try { channel.close(); } catch (IOException ignored) { }
        local.lock.unlock();
        releaseReference(key, local);

    }
}
