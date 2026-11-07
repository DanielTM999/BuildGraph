package dtm.builder.build;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class ProcessRunnerCancellationTest {
    @TempDir Path temp;
    @Test void interruptKillsSilentProcessAndItsChildWithoutWaitingForOutput() throws Exception {
        Path source = temp.resolve("SleepingTool.java");
        Files.writeString(source, """
                import java.nio.file.Path;
                class SleepingTool {
                    public static void main(String[] args) throws Exception {
                        if (args.length == 1) {
                            String java = Path.of(System.getProperty("java.home"), "bin", System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java").toString();
                            new ProcessBuilder(java, args[0], args[0], "child").inheritIO().start();
                        }
                        System.out.println("PID=" + ProcessHandle.current().pid());
                        Thread.sleep(60000);
                    }
                }
                """);
        String java = Path.of(System.getProperty("java.home"), "bin", ToolProbe.isWindows() ? "java.exe" : "java").toString();
        CountDownLatch started = new CountDownLatch(2);
        List<Long> pids = new CopyOnWriteArrayList<>(); AtomicInteger exit = new AtomicInteger();
        Thread worker = Thread.ofPlatform().start(() -> exit.set(ProcessRunner.run(List.of(java, source.toString(), source.toString()), temp, null, line -> {
            if (line.startsWith("PID=")) { pids.add(Long.parseLong(line.substring(4))); started.countDown(); }
        })));
        try {
            assertTrue(started.await(30, TimeUnit.SECONDS));
            worker.interrupt(); worker.join(10000);
            assertFalse(worker.isAlive()); assertEquals(-1, exit.get());
            for (long pid : pids) assertFalse(ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false), "Leaked process " + pid);
        } finally { worker.interrupt(); worker.join(10000); }
    }
}
