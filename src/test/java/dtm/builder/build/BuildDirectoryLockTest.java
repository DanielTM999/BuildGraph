package dtm.builder.build;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;

class BuildDirectoryLockTest {
    @TempDir Path temp;
    @Test void sameOutputWritersAreSerializedButDifferentOutputsRemainIndependent() throws Exception {
        AtomicBoolean entered = new AtomicBoolean();
        CountDownLatch attempting = new CountDownLatch(1);
        try (var threads = Executors.newSingleThreadExecutor()) {
            Future<?> next;
            try (var first = BuildDirectoryLock.acquire(temp.resolve("build"))) {
                next = threads.submit(() -> {
                    attempting.countDown();
                    try (var second = BuildDirectoryLock.acquire(temp.resolve("build"))) { entered.set(true); }
                    catch (Exception e) { throw new RuntimeException(e); }
                });
                assertTrue(attempting.await(5, TimeUnit.SECONDS));
                try (var other = BuildDirectoryLock.acquire(temp.resolve("different"))) { assertFalse(entered.get()); }
            }
            next.get(5, TimeUnit.SECONDS); assertTrue(entered.get());
        }
    }
}
