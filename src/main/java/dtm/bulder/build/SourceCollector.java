package dtm.bulder.build;

import dtm.bulder.manifest.model.ManifestRootModel;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class SourceCollector {

    private static final Set<String> SOURCE_EXTS = Set.of(
            ".c", ".cpp", ".cc", ".cxx", ".c++", ".cppm", ".ixx", ".mpp", ".ccm", ".cxxm",
            ".m", ".mm");

    private static final Set<String> CPP_EXTS = Set.of(
            ".cpp", ".cc", ".cxx", ".c++", ".cppm", ".ixx", ".mpp", ".ccm", ".cxxm", ".mm");

    private static final Set<String> IGNORED_DIRS = Set.of(
            ".git", ".buildgraph", ".orion", ".idea", "build", "out");

    private SourceCollector() {
    }

    public static List<Path> collectSources(Path projectPath, ManifestRootModel manifest) {
        List<String> folders = new ArrayList<>();
        if (manifest != null && !manifest.getSourceFolders().isEmpty()) {
            folders.addAll(manifest.getSourceFolders());
        } else if (Files.isDirectory(projectPath.resolve("src"))) {
            folders.add("src");
        } else {
            folders.add(".");
        }

        List<Path> out = new ArrayList<>();
        for (String folder : folders) {
            Path dir = projectPath.resolve(folder).normalize();
            if (Files.isDirectory(dir)) {
                collect(dir, out);
            }
        }
        return out;
    }

    public static List<Path> collectTestSources(Path projectPath) {
        List<Path> out = new ArrayList<>();
        for (String folder : List.of("test", "tests")) {
            Path dir = projectPath.resolve(folder);
            if (Files.isDirectory(dir)) {
                collect(dir, out);
            }
        }
        return out;
    }

    public static boolean isCppSources(List<Path> sources) {
        for (Path p : sources) {
            if (CPP_EXTS.contains(extensionOf(p))) {
                return true;
            }
        }
        return false;
    }

    private static void collect(Path dir, List<Path> out) {
        try {
            Files.walkFileTree(dir, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path d, BasicFileAttributes attrs) {
                    String name = d.getFileName() == null ? "" : d.getFileName().toString();
                    if (IGNORED_DIRS.contains(name) || name.startsWith("cmake-build-")) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (SOURCE_EXTS.contains(extensionOf(file))) {
                        out.add(file);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException ignored) {

        }
    }

    private static String extensionOf(Path p) {
        String name = p.getFileName().toString().toLowerCase();
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot);
    }
}
