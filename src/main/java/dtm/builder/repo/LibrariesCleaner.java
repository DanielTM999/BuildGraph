package dtm.builder.repo;

import dtm.builder.manifest.model.PackageLock;

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
                if (keep.contains(name)) {
                    continue;
                }
                // Nunca remove conteudo alheio: apenas temporarios interrompidos
                // e pastas de fato materializadas pelo BuildGraph (com lock proprio).
                boolean orphan = name.startsWith(".tmp-") || isManagedByBuildGraph(child);
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

    private static boolean isManagedByBuildGraph(Path child) {
        Path lock = child.resolve(PackageLock.FILE_NAME);
        if (!Files.isRegularFile(lock)) {
            return false;
        }
        try {
            PackageLock parsed = RepoJson.read(lock, PackageLock.class);
            return PackageLock.MANAGED_BY.equals(parsed.getManagedBy());
        } catch (IOException e) {
            return false;
        }
    }
}
