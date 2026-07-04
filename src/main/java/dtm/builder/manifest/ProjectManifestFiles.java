package dtm.builder.manifest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class ProjectManifestFiles {

    public static final String BUILDGRAPH_DIR = ".buildgraph";
    public static final String MANIFEST_JSON = "Manifest.json";
    public static final String MANIFEST_XML = "Manifest.xml";
    public static final String PACKAGES_DIR = "packages";
    public static final String BUILD_DIR = "build";

    private ProjectManifestFiles() {
    }

    public static Path normalizeRoot(Path projectPath) {
        return projectPath == null ? null : projectPath.toAbsolutePath().normalize();
    }

    public static List<Path> manifestCandidates(Path projectPath) {
        Path root = normalizeRoot(projectPath);
        if (root == null) {
            return List.of();
        }
        List<Path> out = new ArrayList<>(4);
        out.add(root.resolve(MANIFEST_JSON));
        out.add(root.resolve(MANIFEST_XML));
        out.add(root.resolve(BUILDGRAPH_DIR).resolve(MANIFEST_JSON));
        out.add(root.resolve(BUILDGRAPH_DIR).resolve(MANIFEST_XML));
        return out;
    }

    public static Path firstExistingProjectManifest(Path projectPath) {
        for (Path candidate : manifestCandidates(projectPath)) {
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    public static boolean isManifest(Path path) {
        if (path == null || path.getFileName() == null) {
            return false;
        }
        String name = path.getFileName().toString();
        return MANIFEST_JSON.equalsIgnoreCase(name) || MANIFEST_XML.equalsIgnoreCase(name);
    }

    public static boolean isXml(Path path) {
        return path != null
                && path.getFileName() != null
                && path.getFileName().toString().toLowerCase().endsWith(".xml");
    }

    public static Path buildGraphDir(Path projectPath) {
        Path root = normalizeRoot(projectPath);
        return root == null ? null : root.resolve(BUILDGRAPH_DIR);
    }

    public static Path defaultPackagesDir(Path projectPath) {
        Path dir = buildGraphDir(projectPath);
        return dir == null ? null : dir.resolve(PACKAGES_DIR);
    }

    public static Path resolvePackagesDir(Path projectPath, String manifestBase,
                                          String cliFallback) {
        Path root = normalizeRoot(projectPath);
        if (root == null) {
            return null;
        }
        String selected = manifestBase != null && !manifestBase.isBlank()
                ? manifestBase : cliFallback;
        if (selected == null || selected.isBlank()) {
            return defaultPackagesDir(root);
        }
        Path configured = Path.of(selected.trim());
        return configured.isAbsolute()
                ? configured.normalize()
                : root.resolve(configured).normalize();
    }

    public static Path defaultBuildDir(Path projectPath) {
        Path root = normalizeRoot(projectPath);
        return root == null ? null : root.resolve(BUILD_DIR);
    }

    public static Path resolveBuildDir(Path projectPath, String outputDir) {
        Path root = normalizeRoot(projectPath);
        if (root == null) {
            return null;
        }
        if (outputDir == null || outputDir.isBlank()) {
            return defaultBuildDir(root);
        }
        Path configured = Path.of(outputDir.trim());
        return configured.isAbsolute()
                ? configured.normalize()
                : root.resolve(configured).normalize();
    }
}
