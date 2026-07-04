package dtm.builder.build;

import dtm.builder.manifest.model.LibraryManifest;
import dtm.builder.repo.GlobalRepository;
import dtm.builder.repo.RepoJson;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public final class PackagePaths {

    private final List<Path> includeDirs = new ArrayList<>();
    private final List<Path> libraryDirs = new ArrayList<>();
    private final List<String> linkLibraries = new ArrayList<>();

    public List<Path> includeDirs() {
        return includeDirs;
    }

    public List<Path> libraryDirs() {
        return libraryDirs;
    }

    public List<String> linkLibraries() {
        return linkLibraries;
    }

    public static PackagePaths resolve(Path packagesDir) {
        PackagePaths out = new PackagePaths();
        if (packagesDir == null || !Files.isDirectory(packagesDir)) {
            return out;
        }
        try (Stream<Path> children = Files.list(packagesDir)) {
            for (Path pkgDir : children.toList()) {
                if (!Files.isDirectory(pkgDir) || pkgDir.getFileName().toString().startsWith(".tmp-")) {
                    continue;
                }
                Path manifestFile = pkgDir.resolve(GlobalRepository.GLOBAL_MANIFEST_FILE);
                if (!Files.isRegularFile(manifestFile)) {

                    addIfDir(out.includeDirs, pkgDir.resolve("include"));
                    continue;
                }
                try {
                    LibraryManifest lm = RepoJson.read(manifestFile, LibraryManifest.class);
                    for (String inc : lm.getIncludePaths()) {
                        addIfDir(out.includeDirs, pkgDir.resolve(inc).normalize());
                    }
                    for (String lib : lm.getLibraryPaths()) {
                        addIfDir(out.libraryDirs, pkgDir.resolve(lib).normalize());
                    }
                    out.linkLibraries.addAll(lm.getLinkLibraries());
                } catch (IOException ignored) {
                    addIfDir(out.includeDirs, pkgDir.resolve("include"));
                }
            }
        } catch (IOException ignored) {

        }
        return out;
    }

    private static void addIfDir(List<Path> list, Path dir) {
        if (Files.isDirectory(dir) && !list.contains(dir)) {
            list.add(dir);
        }
    }
}
