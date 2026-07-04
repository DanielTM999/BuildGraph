package dtm.builder.build;

import dtm.builder.manifest.model.ManifestRootModel;
import dtm.builder.repo.PathSanitizer;

import java.nio.file.Path;

public final class Artifacts {

    private static final boolean MAC =
            System.getProperty("os.name", "").toLowerCase().contains("mac");

    private Artifacts() {
    }

    public static String baseName(Path projectPath, ManifestRootModel manifest) {
        String name = manifest == null ? null : manifest.getName();
        if (name == null || name.isBlank()) {
            name = manifest == null ? null : manifest.getId();
        }
        if (name == null || name.isBlank()) {
            Path fileName = projectPath.getFileName();
            name = fileName == null ? "app" : fileName.toString();
        }
        return PathSanitizer.sanitizePackageFolderName(name);
    }

    public static String fileName(String base, TargetType type, boolean msvc) {
        return switch (type) {
            case EXECUTABLE -> ToolProbe.isWindows() ? base + ".exe" : base;
            case SHARED -> {
                if (ToolProbe.isWindows()) {
                    yield base + ".dll";
                }
                yield MAC ? "lib" + base + ".dylib" : "lib" + base + ".so";
            }
            case STATIC -> msvc ? base + ".lib" : "lib" + base + ".a";
        };
    }

    public static Path artifactPath(Path buildDir, String base, TargetType type, boolean msvc) {
        return buildDir.resolve(fileName(PathSanitizer.sanitizePackageFolderName(base), type, msvc));
    }

    public static Path artifactPath(Path projectPath, ManifestRootModel manifest, Path buildDir,
                                    boolean library) {
        String base = baseName(projectPath, manifest);
        return buildDir.resolve(fileName(base, library ? TargetType.SHARED : TargetType.EXECUTABLE,
                false));
    }
}
