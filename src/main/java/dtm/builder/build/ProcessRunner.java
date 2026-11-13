package dtm.builder.build;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public final class ProcessRunner {

    public static final int COMMAND_NOT_FOUND = 127;

    private ProcessRunner() {
    }

    public static int run(List<String> command, Path workingDir, Map<String, String> env,
                          Consumer<String> output) {
        ProcessBuilder pb = new ProcessBuilder(command);
        if (workingDir != null) {
            pb.directory(workingDir.toFile());
        }
        pb.redirectErrorStream(true);
        if (env != null && !env.isEmpty()) {
            pb.environment().putAll(env);
        }

        Process process;
        try {
            process = pb.start();
        } catch (IOException e) {
            emit(output, "Comando nao encontrado ou falhou ao iniciar: "
                    + String.join(" ", command) + " (" + e.getMessage() + ")");
            return COMMAND_NOT_FOUND;
        }

        ACTIVE.add(process);
        Thread drain = Thread.ofVirtual().name("buildgraph-output").start(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) emit(output, line);
            } catch (IOException ignored) {

            } catch (RuntimeException e) {
                terminate(process);
            }
        });
        try {
            while (!process.waitFor(100, TimeUnit.MILLISECONDS)) {
                long resident = residentBytes(process.toHandle());
                PEAK.set(Math.max(PEAK.get(), resident));
            }
            drain.join();
            return process.exitValue();
        } catch (InterruptedException e) {
            terminate(process);
            Thread.currentThread().interrupt();
            return -1;
        } finally {
            if (process.isAlive()) terminate(process);
            ACTIVE.remove(process);
            try { process.getInputStream().close(); } catch (IOException ignored) { }
            drain.interrupt();
            boolean interrupted = Thread.interrupted();
            try { drain.join(5000); } catch (InterruptedException e) { interrupted = true; }
            if (interrupted) Thread.currentThread().interrupt();
        }
    }

    private static final Set<Process> ACTIVE = ConcurrentHashMap.newKeySet();
    private static final ThreadLocal<Long> PEAK = ThreadLocal.withInitial(() -> 0L);
    static {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> ACTIVE.forEach(ProcessRunner::terminate), "buildgraph-process-cleanup"));
    }
    public static void beginMemoryTracking() { PEAK.set(0L); }
    public static long peakMemoryBytes() { return PEAK.get(); }

    private static void terminate(Process process) {
        var children = process.descendants().toList();
        children.reversed().forEach(child -> { if (child.isAlive()) child.destroyForcibly(); });
        process.destroyForcibly();
        try { process.getInputStream().close(); } catch (IOException ignored) { }
        boolean interrupted = Thread.interrupted();
        try { process.waitFor(5, TimeUnit.SECONDS); }
        catch (InterruptedException e) { interrupted = true; }
        if (interrupted) Thread.currentThread().interrupt();
    }

    private static long residentBytes(ProcessHandle process) {
        if (ToolProbe.isWindows()) return 0;
        long total = rss(process.pid());
        for (ProcessHandle child : process.descendants().toList()) total += rss(child.pid());
        return total;
    }
    private static long rss(long pid) {
        try {
            for (String line : Files.readAllLines(Path.of("/proc", Long.toString(pid), "status")))
                if (line.startsWith("VmRSS:")) return Long.parseLong(line.replaceAll("[^0-9]", "")) * 1024;
        } catch (IOException | NumberFormatException ignored) { }
        return 0;
    }

    private static void emit(Consumer<String> output, String line) {
        if (output != null) {
            output.accept(line);
        }
    }
}
