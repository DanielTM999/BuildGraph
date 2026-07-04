package dtm.builder.build;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public final class ToolProbe {

    private static final boolean WINDOWS =
            System.getProperty("os.name", "").toLowerCase().contains("win");

    private ToolProbe() {
    }

    public static boolean isWindows() {
        return WINDOWS;
    }

    public static Path findOnPath(String executable) {
        if (executable == null || executable.isBlank()) {
            return null;
        }

        Path direct = Paths.get(executable);
        if (direct.isAbsolute() || executable.contains("/") || executable.contains("\\")) {
            if (Files.isRegularFile(direct)) {
                return direct;
            }
        }

        String pathEnv = System.getenv("PATH");
        if (pathEnv == null) {
            return null;
        }
        List<String> candidates = new ArrayList<>();
        candidates.add(executable);
        if (WINDOWS && !hasExtension(executable)) {
            for (String ext : windowsExtensions()) {
                candidates.add(executable + ext);
            }
        }
        for (String dir : pathEnv.split(java.io.File.pathSeparator)) {
            if (dir.isBlank()) {
                continue;
            }
            for (String candidate : candidates) {
                Path p = Paths.get(dir, candidate);
                if (Files.isRegularFile(p)) {
                    return p;
                }
            }
        }
        return null;
    }

    public static boolean exists(String executable) {
        return findOnPath(executable) != null;
    }

    private static boolean hasExtension(String exe) {
        int slash = Math.max(exe.lastIndexOf('/'), exe.lastIndexOf('\\'));
        return exe.indexOf('.', slash + 1) >= 0;
    }

    private static String[] windowsExtensions() {
        String pathext = System.getenv("PATHEXT");
        if (pathext == null || pathext.isBlank()) {
            return new String[]{".EXE", ".CMD", ".BAT", ".COM"};
        }
        return pathext.split(";");
    }
}
