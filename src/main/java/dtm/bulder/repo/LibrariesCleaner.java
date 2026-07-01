package dtm.bulder.repo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Stream;

public final class LibrariesCleaner {

    private LibrariesCleaner() {
    }

    public static boolean wouldClean(Path packagesDir, Set<String> keepFolderNames) {
        return scan(packagesDir, keepFolderNames, false);
    }

    public static boolean clean(Path packagesDir, Set<String> keepFolderNames) {
        return scan(packagesDir, keepFolderNames, true);
    }

    private static boolean scan(Path packagesDir, Set<String> keep, boolean delete) {
        if (packagesDir == null || !Files.isDirectory(packagesDir)) {
            return false;
        }
        boolean changed = false;
        try (Stream<Path> children = Files.list(packagesDir)) {
            for (Path child : children.toList()) {
                String name = child.getFileName().toString();
                boolean orphan = name.startsWith(".tmp-") || !keep.contains(name);
                if (!orphan) {
                    continue;
                }
                changed = true;
                if (delete) {
                    try {
                        SafeZipExtractor.deleteTree(child);
                    } catch (IOException ignored) {

                    }
                }
            }
        } catch (IOException ignored) {
            return changed;
        }
        return changed;
    }
}
