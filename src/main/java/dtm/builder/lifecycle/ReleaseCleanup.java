package dtm.builder.lifecycle;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

final class ReleaseCleanup {
    private record Entry(PathMatcher matcher, PathMatcher rootMatcher, boolean fileName) {
        boolean matches(Path relative) {
            Path candidate = fileName ? relative.getFileName() : relative;
            return matcher.matches(candidate) || rootMatcher != null && rootMatcher.matches(candidate);
        }
    }

    private final List<Entry> entries = new ArrayList<>();

    ReleaseCleanup(List<String> patterns) {
        for (String raw : patterns) {
            String pattern = raw == null ? "" : raw.trim().replace('\\', '/');
            while (pattern.startsWith("./")) pattern = pattern.substring(2);
            while (pattern.endsWith("/")) pattern = pattern.substring(0, pattern.length() - 1);
            if (pattern.isBlank() || pattern.equals(".") || pattern.startsWith("/")
                    || pattern.indexOf(':') >= 0 || List.of(pattern.split("/")).contains(".."))
                throw new IllegalArgumentException("releaseCleanup requer caminhos relativos dentro de outputDir: " + raw);
            PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + pattern);
            PathMatcher rootMatcher = pattern.startsWith("**/")
                    ? FileSystems.getDefault().getPathMatcher("glob:" + pattern.substring(3)) : null;
            entries.add(new Entry(matcher, rootMatcher, !pattern.contains("/")));
        }
    }

    void clean(Path buildDir) throws IOException {
        Path root = buildDir.toAbsolutePath().normalize();
        if (entries.isEmpty() || !Files.exists(root, LinkOption.NOFOLLOW_LINKS)) return;
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                if (!dir.equals(root) && matches(root.relativize(dir))) {
                    deleteTree(dir);
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (matches(root.relativize(file))) Files.delete(file);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private boolean matches(Path relative) {
        return entries.stream().anyMatch(entry -> entry.matches(relative));
    }

    private static void deleteTree(Path directory) throws IOException {
        Files.walkFileTree(directory, new SimpleFileVisitor<>() {
            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override public FileVisitResult postVisitDirectory(Path dir, IOException error) throws IOException {
                if (error != null) throw error;
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
