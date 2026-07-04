package dtm.builder.interactive;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.function.Predicate;

public final class ManifestWatcher implements AutoCloseable {

    private static final long DEBOUNCE_MS = 250;

    private final Path directory;
    private final Predicate<Path> filter;
    private final Runnable onChange;

    private WatchService watchService;
    private Thread thread;
    private volatile boolean running;
    private volatile long lastFire;

    public ManifestWatcher(Path directory, Predicate<Path> filter, Runnable onChange) {
        this.directory = directory;
        this.filter = filter;
        this.onChange = onChange;
    }

    public void start() throws IOException {
        watchService = FileSystems.getDefault().newWatchService();
        directory.register(watchService,
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_MODIFY);
        running = true;
        thread = new Thread(this::loop, "manifest-watcher");
        thread.setDaemon(true);
        thread.start();
    }

    private void loop() {
        while (running) {
            WatchKey key;
            try {
                key = watchService.take();
            } catch (InterruptedException | RuntimeException e) {
                return;
            }
            boolean matched = false;
            for (WatchEvent<?> event : key.pollEvents()) {
                Object context = event.context();
                if (!(context instanceof Path name)) {
                    continue;
                }
                if (filter.test(directory.resolve(name))) {
                    matched = true;
                }
            }
            key.reset();

            if (matched) {
                long now = System.currentTimeMillis();
                if (now - lastFire >= DEBOUNCE_MS) {
                    lastFire = now;
                    try {
                        onChange.run();
                    } catch (RuntimeException ignored) {

                    }
                }
            }
        }
    }

    @Override
    public void close() {
        running = false;
        if (thread != null) {
            thread.interrupt();
        }
        if (watchService != null) {
            try {
                watchService.close();
            } catch (IOException ignored) {

            }
        }
    }
}
