package dtm.builder.manifest;

import dtm.builder.manifest.model.ManifestPackagesModel;
import dtm.builder.manifest.model.ManifestParseResult;
import dtm.builder.manifest.model.ManifestRootModel;
import dtm.builder.manifest.model.ResolvedDependency;
import dtm.builder.repo.GlobalRepository;
import dtm.builder.repo.LibrariesCleaner;
import dtm.builder.repo.LibraryDownloader;
import dtm.builder.repo.LibraryInstaller;
import dtm.builder.repo.LibraryMaterializer;
import dtm.builder.repo.DependencyResolver;
import dtm.builder.repo.DependencyResolutionException;
import dtm.builder.repo.SyncResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

public final class ManifestResolver {

    private final Path projectPath;
    private final GlobalRepository repository;
    private final LibraryInstaller installer;

    public ManifestResolver(Path projectPath, GlobalRepository repository) {
        this.projectPath = ProjectManifestFiles.normalizeRoot(projectPath);
        this.repository = repository;
        this.installer = new LibraryInstaller(repository, new LibraryDownloader());
    }

    public Path getManifestFilePath() {
        return ProjectManifestFiles.firstExistingProjectManifest(projectPath);
    }

    public boolean isManifestFile(Path path) {
        return ProjectManifestFiles.isManifest(path);
    }

    public ManifestParseResult read() {
        Path manifestPath = getManifestFilePath();
        if (manifestPath == null) {
            return new ManifestParseResult(null, List.of());
        }
        return ManifestParser.readManifest(manifestPath);
    }

    public ManifestRootModel readEffective(String profileOverride) {
        ManifestParseResult parse = read();
        ManifestRootModel raw = parse.getManifest();
        if (raw == null) {
            return null;
        }
        if ((raw.getActiveProfile() == null || raw.getActiveProfile().isBlank())
                && profileOverride != null && !profileOverride.isBlank()) {
            raw.setActiveProfile(profileOverride);
        }
        return ManifestProfiles.effective(raw);
    }

    public Path packagesDir(ManifestRootModel effective) {
        String base = effective == null ? null : effective.getPackagesBase();
        if (base != null && !base.isBlank()) {
            return projectPath.resolve(base).normalize();
        }
        return ProjectManifestFiles.defaultPackagesDir(projectPath);
    }

    public SyncResult syncPackages(String profileOverride, Consumer<String> log) {
        return syncPackages(profileOverride, null, log);
    }

    public SyncResult syncPackages(String profileOverride, Path packagesDirOverride,
                                   Consumer<String> log) {
        Path manifestPath = getManifestFilePath();
        if (manifestPath == null) {
            return SyncResult.NO_MANIFEST;
        }
        ManifestParseResult parse = ManifestParser.readManifest(manifestPath);
        if (!parse.isOk()) {
            return SyncResult.INVALID_MANIFEST;
        }
        ManifestRootModel raw = parse.getManifest();
        if ((raw.getActiveProfile() == null || raw.getActiveProfile().isBlank())
                && profileOverride != null && !profileOverride.isBlank()) {
            raw.setActiveProfile(profileOverride);
        }
        ManifestRootModel effective = ManifestProfiles.effective(raw);
        Path packagesDir = packagesDirOverride != null
                ? packagesDirOverride : packagesDir(effective);
        if (!effective.isPackagesDeclared() || effective.getPackages().isEmpty()) {
            // Sem dependencias declaradas: desmaterializa o que restou (drop de dep).
            boolean removed = LibrariesCleaner.clean(packagesDir, Set.of());
            if (removed) {
                info(log, "Nenhuma dependencia no manifest; pacotes desmaterializados em "
                        + packagesDir);
                return SyncResult.APPLIED_CHANGED;
            }
            return SyncResult.NO_PACKAGES;
        }

        List<ResolvedDependency> resolved = new ArrayList<>();
        try {
            for (ManifestPackagesModel pkg : effective.getPackages()) {
                info(log, "Resolvendo package " + pkg.key());
            }
            resolved.addAll(new DependencyResolver(repository, installer)
                    .resolve(effective.getPackages()));

            Files.createDirectories(packagesDir);

            Set<String> keep = new LinkedHashSet<>();
            for (ResolvedDependency dep : resolved) {
                keep.add(LibraryMaterializer.folderNameFor(dep));
            }

            boolean willClean = LibrariesCleaner.wouldClean(packagesDir, keep);
            boolean willMaterialize = false;
            for (ResolvedDependency dep : resolved) {
                Path dest = packagesDir.resolve(LibraryMaterializer.folderNameFor(dep));
                if (!LibraryMaterializer.isUpToDate(dest, dep)) {
                    willMaterialize = true;
                    break;
                }
            }
            if (!willClean && !willMaterialize) {
                return SyncResult.APPLIED_NO_CHANGE;
            }

            boolean changed = LibrariesCleaner.clean(packagesDir, keep);
            for (ResolvedDependency dep : resolved) {
                boolean did = LibraryMaterializer.materialize(dep, packagesDir);
                if (did) {
                    info(log, "Package " + dep.key() + " materializado em "
                            + packagesDir.resolve(LibraryMaterializer.folderNameFor(dep)));
                }
                changed |= did;
            }
            return changed ? SyncResult.APPLIED_CHANGED : SyncResult.APPLIED_NO_CHANGE;
        } catch (DependencyResolutionException | IOException | RuntimeException e) {
            info(log, "Falha ao sincronizar packages: " + e.getMessage());
            return SyncResult.RESOLUTION_FAILED;
        }
    }

    private static void info(Consumer<String> log, String message) {
        if (log != null) {
            log.accept(message);
        }
    }
}
