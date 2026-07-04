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
    private final List<Path> linkInputFiles = new ArrayList<>();

    public List<Path> includeDirs() {
        return includeDirs;
    }

    public List<Path> libraryDirs() {
        return libraryDirs;
    }

    public List<String> linkLibraries() {
        return linkLibraries;
    }

    /** Arquivos de packages que podem ser selecionados pelo linker. */
    public List<Path> linkInputFiles() {
        return linkInputFiles;
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
                        Path libraryDir = pkgDir.resolve(lib).normalize();
                        addIfDir(out.libraryDirs, libraryDir);
                        collectLinkInputs(out.linkInputFiles, libraryDir);
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

    private static void collectLinkInputs(List<Path> inputs, Path directory) {
        if (!Files.isDirectory(directory)) {
            return;
        }
        try (Stream<Path> files = Files.list(directory)) {
            files.filter(Files::isRegularFile)
                    .filter(PackagePaths::isLinkInput)
                    .map(path -> path.toAbsolutePath().normalize())
                    .sorted()
                    .forEach(path -> {
                        if (!inputs.contains(path)) {
                            inputs.add(path);
                        }
                    });
        } catch (IOException ignored) {

        }
    }

    private static boolean isLinkInput(Path path) {
        String name = path.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
        return name.endsWith(".a") || name.endsWith(".lib") || name.endsWith(".dll")
                || name.endsWith(".dylib") || name.endsWith(".so")
                || name.contains(".so.");
    }
}
