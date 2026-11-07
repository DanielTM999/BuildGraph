package dtm.builder.build;

import java.nio.file.Path;
import java.util.Locale;

public record BuildOptions(long memoryLimit, long memoryPerJob, Path cacheDirectory,
                           long cacheSize, boolean sharedCache, boolean profile, long memoryReserve) {
    public BuildOptions(long memoryLimit, long memoryPerJob, Path cacheDirectory,
                        long cacheSize, boolean sharedCache, boolean profile) {
        this(memoryLimit, memoryPerJob, cacheDirectory, cacheSize, sharedCache, profile, 0);
    }
    public static BuildOptions defaults() {
        String base = System.getenv("LOCALAPPDATA");
        String xdg = System.getenv("XDG_CACHE_HOME");
        Path root = ToolProbe.isWindows() && base != null && !base.isBlank()
                ? Path.of(base, "BuildGraph", "cache")
                : (xdg != null && !xdg.isBlank() ? Path.of(xdg) : Path.of(System.getProperty("user.home"), ".cache")).resolve("buildgraph");
        return new BuildOptions(0, 0, root.resolve("objects-v1"), 5L << 30, true, false);
    }

    public BuildOptions {
        if (memoryLimit < 0 || memoryPerJob < 0 || cacheSize < 0 || memoryReserve < 0)
            throw new IllegalArgumentException("Memory/cache limits cannot be negative");
        if (cacheDirectory == null) cacheDirectory = defaults().cacheDirectory();
    }

    public static long bytes(String value) {
        String text = value.trim().toUpperCase(Locale.ROOT).replaceFirst("I?B$", "");
        long factor = 1;
        if (text.matches(".*[KMGT]$")) {
            factor = 1L << (10 * ("KMGT".indexOf(text.charAt(text.length() - 1)) + 1));
            text = text.substring(0, text.length() - 1);
        }
        try {
            long result = Math.multiplyExact(Long.parseLong(text), factor);
            if (result <= 0) throw new NumberFormatException();
            return result;
        } catch (ArithmeticException | NumberFormatException e) {
            throw new IllegalArgumentException("Tamanho invalido: " + value + " (ex.: 768M, 8G)");
        }
    }
}
