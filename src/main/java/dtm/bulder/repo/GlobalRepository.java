package dtm.bulder.repo;

import dtm.bulder.manifest.model.LibraryManifest;
import dtm.bulder.manifest.model.ManifestPackagesModel;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

public final class GlobalRepository {

    public static final String LIB_SUBDIR = "lib";
    public static final String GLOBAL_MANIFEST_FILE = "Manifest.json";
    public static final String SOURCE_TOKEN = "source";

    private final List<Path> roots;

    private GlobalRepository(List<Path> roots) {
        if (roots == null || roots.isEmpty()) {
            throw new IllegalArgumentException("Ao menos um repositorio deve ser configurado");
        }
        Set<Path> normalized = new LinkedHashSet<>();
        for (Path root : roots) {
            if (root != null) {
                normalized.add(root.toAbsolutePath().normalize());
            }
        }
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Ao menos um repositorio valido deve ser configurado");
        }
        this.roots = List.copyOf(normalized);
    }

    public static GlobalRepository defaultRepo() {
        return new GlobalRepository(List.of(defaultRoot()));
    }

    public static GlobalRepository at(Path root) {
        return new GlobalRepository(List.of(root));
    }

    public static GlobalRepository at(List<Path> roots) {
        return new GlobalRepository(roots);
    }

    public static GlobalRepository resolve(String explicitPath) {
        List<Path> roots = new ArrayList<>();
        if (explicitPath != null && !explicitPath.isBlank()) {
            roots.add(Paths.get(explicitPath.trim()));
        }
        roots.add(defaultRoot());
        return at(roots);
    }

    public static GlobalRepository resolve(Path projectPath, List<String> manifestRepositories,
                                           String explicitPath) {
        List<Path> roots = new ArrayList<>();
        Path projectRoot = projectPath == null
                ? Paths.get(System.getProperty("user.dir", ".")).toAbsolutePath().normalize()
                : projectPath.toAbsolutePath().normalize();
        if (manifestRepositories != null) {
            for (String configured : manifestRepositories) {
                if (configured == null || configured.isBlank()) {
                    continue;
                }
                Path path = Paths.get(configured.trim());
                roots.add(path.isAbsolute() ? path : projectRoot.resolve(path));
            }
        }
        if (explicitPath != null && !explicitPath.isBlank()) {
            roots.add(Paths.get(explicitPath.trim()));
        }
        roots.add(defaultRoot());
        return at(roots);
    }

    private static Path defaultRoot() {
        Path home = Paths.get(System.getProperty("user.home", "."));
        return home.resolve(".buildgraph").resolve("repository").toAbsolutePath().normalize();
    }

    public List<Path> roots() {
        return roots;
    }

    public Path root() throws IOException {
        Path root = primaryRoot();
        Files.createDirectories(root);
        return root;
    }

    public Path stagingDir() throws IOException {
        Path staging = primaryRoot().resolve(".staging");
        Files.createDirectories(staging);
        return staging;
    }

    public Path libraryVersionDir(String id, String version) {
        return libraryVersionDir(primaryRoot(), id, version);
    }

    private Path libraryVersionDir(Path root, String id, String version) {
        return root.resolve(PathSanitizer.sanitizeId(id))
                .resolve(PathSanitizer.sanitizeVersion(version));
    }

    public boolean isInstalled(ManifestPackagesModel pkg) {
        return resolveVariant(pkg, null).found();
    }

    public boolean isContentDir(Path dir) {
        return dir != null
                && Files.isRegularFile(dir.resolve(GLOBAL_MANIFEST_FILE))
                && Files.isDirectory(dir.resolve(LIB_SUBDIR));
    }

    public VariantResolution resolveVariant(ManifestPackagesModel pkg, String targetToken) {
        for (Path root : roots) {
            VariantResolution resolution = resolveVariant(
                    libraryVersionDir(root, pkg.getId(), pkg.getVersion()), targetToken);
            if (resolution.found()) {
                return resolution;
            }
        }
        return VariantResolution.notFound();
    }

    private VariantResolution resolveVariant(Path versionDir, String targetToken) {
        if (!Files.isDirectory(versionDir)) {
            return VariantResolution.notFound();
        }

        if (targetToken != null && !targetToken.isBlank()) {
            Path match = versionDir.resolve(PathSanitizer.sanitizeToken(targetToken));
            if (isContentDir(match)) {
                return new VariantResolution(match, targetToken);
            }
        }

        Path source = versionDir.resolve(SOURCE_TOKEN);
        if (isContentDir(source)) {
            return new VariantResolution(source, SOURCE_TOKEN);
        }

        try (Stream<Path> children = Files.list(versionDir)) {
            for (Path child : children.toList()) {
                if (isContentDir(child)) {
                    return new VariantResolution(child, child.getFileName().toString());
                }
            }
        } catch (IOException ignored) {

        }
        return VariantResolution.notFound();
    }

    private Path primaryRoot() {
        return roots.get(0);
    }

    public Path storeBundle(Path packageRoot, LibraryManifest manifest,
                            ManifestPackagesModel declared) throws IOException {
        Path variantDir = libraryVersionDir(declared.getId(), declared.getVersion())
                .resolve(SOURCE_TOKEN);
        Path lib = variantDir.resolve(LIB_SUBDIR);

        SafeZipExtractor.deleteTree(variantDir);
        Files.createDirectories(lib);
        SafeZipExtractor.copyTree(packageRoot, lib);
        RepoJson.write(variantDir.resolve(GLOBAL_MANIFEST_FILE), manifest);
        return variantDir;
    }

    public Path publish(String id, String version, Path content, LibraryManifest manifest)
            throws IOException {
        Path variantDir = libraryVersionDir(id, version).resolve(SOURCE_TOKEN);
        Path lib = variantDir.resolve(LIB_SUBDIR);

        SafeZipExtractor.deleteTree(variantDir);
        Files.createDirectories(lib);
        SafeZipExtractor.copyTree(content, lib);
        RepoJson.write(variantDir.resolve(GLOBAL_MANIFEST_FILE), manifest);
        return variantDir;
    }

    public record VariantResolution(Path dir, String token) {
        public static VariantResolution notFound() {
            return new VariantResolution(null, null);
        }

        public boolean found() {
            return dir != null;
        }
    }
}
