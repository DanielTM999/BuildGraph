package dtm.bulder.build;

import dtm.bulder.manifest.model.ManifestRootModel;
import dtm.bulder.repo.PathSanitizer;

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

    public static Path artifactPath(Path projectPath, ManifestRootModel manifest, Path buildDir,
                                    boolean library) {
        String base = baseName(projectPath, manifest);
        String file;
        if (library) {
            if (ToolProbe.isWindows()) {
                file = base + ".dll";
            } else if (MAC) {
                file = "lib" + base + ".dylib";
            } else {
                file = "lib" + base + ".so";
            }
        } else {
            file = ToolProbe.isWindows() ? base + ".exe" : base;
        }
        return buildDir.resolve(file);
    }
}
