package dtm.builder.build;

import java.nio.file.Files;
import java.nio.file.Path;

public final class BuildSystemDetector {

    private BuildSystemDetector() {
    }

    public static BuildSystem detect(Path projectPath, boolean hasManifest) {
        if (isFile(projectPath, "CMakeLists.txt")) {
            return BuildSystem.CMAKE;
        }
        if (isFile(projectPath, "meson.build")) {
            return BuildSystem.MESON;
        }
        if (isFile(projectPath, "Makefile") || isFile(projectPath, "makefile")
                || isFile(projectPath, "GNUmakefile")) {
            return BuildSystem.MAKE;
        }
        if (hasManifest) {
            return BuildSystem.MANIFEST;
        }
        return BuildSystem.DEFAULT;
    }

    private static boolean isFile(Path projectPath, String name) {
        return projectPath != null && Files.isRegularFile(projectPath.resolve(name));
    }
}
