package dtm.builder.manifest;

import dtm.builder.manifest.model.DependencyLock;
import dtm.builder.manifest.model.LibraryManifest;
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
import dtm.builder.repo.DependencyLockStore;
import dtm.builder.repo.RepoJson;
import dtm.builder.repo.PackageVersion;
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
        return synchronize(profileOverride, packagesDirOverride, SyncMode.UPDATE_AND_MATERIALIZE,
                log);
    }

    public SyncResult lockPackages(String profileOverride, Consumer<String> log) {
        return synchronize(profileOverride, null, SyncMode.UPDATE_LOCK_ONLY, log);
    }

    public SyncResult syncPackagesForBuild(String profileOverride, Path packagesDirOverride,
                                           Consumer<String> log) {
        return synchronize(profileOverride, packagesDirOverride, SyncMode.USE_LOCK_OR_RESOLVE,
                log);
    }

    private SyncResult synchronize(String profileOverride, Path packagesDirOverride,
                                   SyncMode mode, Consumer<String> log) {
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
            boolean removed = mode != SyncMode.UPDATE_LOCK_ONLY
                    && LibrariesCleaner.clean(packagesDir, Set.of());
            boolean lockRemoved = false;
            if (mode != SyncMode.USE_LOCK_OR_RESOLVE) {
                try {
                    lockRemoved = DependencyLockStore.delete(projectPath);
                } catch (IOException e) {
                    info(log, "Falha ao remover lock sem packages: " + e.getMessage());
                    return SyncResult.RESOLUTION_FAILED;
                }
            }
            if (removed) {
                info(log, "Nenhuma dependencia no manifest; pacotes desmaterializados em "
                        + packagesDir);
            }
            return removed || lockRemoved ? SyncResult.APPLIED_CHANGED : SyncResult.NO_PACKAGES;
        }

        try {
            List<ResolvedDependency> resolved;
            if (mode == SyncMode.USE_LOCK_OR_RESOLVE) {
                DependencyLock lock = DependencyLockStore.read(projectPath);
                String fingerprint = DependencyLockStore.fingerprint(effective.getPackages());
                if (lock == null) {
                    info(log, "Aviso: " + DependencyLock.FILE_NAME
                            + " ausente; resolvendo pelo manifest (build nao reproduzivel)");
                    resolved = resolveDeclared(effective.getPackages(), log);
                } else if (!fingerprint.equals(lock.getPackagesFingerprint())) {
                    info(log, "Aviso: " + DependencyLock.FILE_NAME
                            + " desatualizado; resolvendo pelo manifest sem altera-lo");
                    resolved = resolveDeclared(effective.getPackages(), log);
                } else {
                    info(log, "Usando dependencias de " + DependencyLock.FILE_NAME);
                    resolved = resolveLocked(lock);
                }
            } else {
                resolved = resolveDeclared(effective.getPackages(), log);
            }

            if (mode == SyncMode.UPDATE_LOCK_ONLY) {
                DependencyLock lock = DependencyLockStore.fromResolved(effective.getPackages(),
                        resolved);
                boolean changed = DependencyLockStore.write(projectPath, lock);
                info(log, changed ? "Lock atualizado: " + DependencyLockStore.path(projectPath)
                        : "Lock ja atualizado: " + DependencyLockStore.path(projectPath));
                return changed ? SyncResult.APPLIED_CHANGED : SyncResult.APPLIED_NO_CHANGE;
            }

            boolean changed = materialize(resolved, packagesDir, log);
            if (mode == SyncMode.UPDATE_AND_MATERIALIZE) {
                DependencyLock lock = DependencyLockStore.fromResolved(effective.getPackages(),
                        resolved);
                boolean lockChanged = DependencyLockStore.write(projectPath, lock);
                if (lockChanged) {
                    info(log, "Lock atualizado: " + DependencyLockStore.path(projectPath));
                }
                changed |= lockChanged;
            }
            return changed ? SyncResult.APPLIED_CHANGED : SyncResult.APPLIED_NO_CHANGE;
        } catch (DependencyResolutionException | IOException | RuntimeException e) {
            info(log, "Falha ao sincronizar packages: " + e.getMessage());
            return SyncResult.RESOLUTION_FAILED;
        }
    }

    private List<ResolvedDependency> resolveDeclared(List<ManifestPackagesModel> packages,
                                                     Consumer<String> log)
            throws DependencyResolutionException {
        for (ManifestPackagesModel pkg : packages) {
            info(log, "Resolvendo package " + pkg.key());
        }
        return new DependencyResolver(repository, installer).resolve(packages);
    }

    private List<ResolvedDependency> resolveLocked(DependencyLock lock) throws IOException {
        List<ResolvedDependency> resolved = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (DependencyLock.LockedDependency item : lock.getPackages()) {
            if (item.getId() == null || item.getId().isBlank()
                    || item.getVersion() == null || item.getVersion().isBlank()
                    || item.getVariant() == null || item.getVariant().isBlank()) {
                throw new IOException("Entrada incompleta em " + DependencyLock.FILE_NAME);
            }
            if (!seen.add(item.getId())) {
                throw new IOException("Package duplicado em " + DependencyLock.FILE_NAME + ": "
                        + item.getId());
            }
            ManifestPackagesModel declared = new ManifestPackagesModel();
            declared.setId(item.getId());
            declared.setVersion(item.getVersion());
            declared.setDownloadUrl(item.getDownloadUrl());
            declared.setTransitive(false);
            installer.ensureInstalled(declared);
            GlobalRepository.VariantResolution variant = repository.resolveVariant(declared,
                    item.getVariant());
            if (!variant.found() || !item.getVariant().equals(variant.token())) {
                throw new IOException("Variante travada nao encontrada: " + item.getId() + ":"
                        + item.getVersion() + "/" + item.getVariant());
            }
            Path manifestFile = variant.dir().resolve(GlobalRepository.GLOBAL_MANIFEST_FILE);
            LibraryManifest libraryManifest = RepoJson.read(manifestFile, LibraryManifest.class);
            if (libraryManifest.getVersion() != null && !libraryManifest.getVersion().isBlank()
                    && PackageVersion.parse(item.getVersion()).compareTo(
                    PackageVersion.parse(libraryManifest.getVersion())) != 0) {
                throw new IOException("Versao divergente no package travado " + item.getId());
            }
            resolved.add(new ResolvedDependency(declared, libraryManifest, variant.dir(),
                    manifestFile, variant.token()));
        }
        return resolved;
    }

    private boolean materialize(List<ResolvedDependency> resolved, Path packagesDir,
                                Consumer<String> log) throws IOException {
        Files.createDirectories(packagesDir);
        Set<String> keep = new LinkedHashSet<>();
        for (ResolvedDependency dep : resolved) {
            keep.add(LibraryMaterializer.folderNameFor(dep));
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
        return changed;
    }

    private static void info(Consumer<String> log, String message) {
        if (log != null) {
            log.accept(message);
        }
    }

    private enum SyncMode {
        UPDATE_LOCK_ONLY,
        UPDATE_AND_MATERIALIZE,
        USE_LOCK_OR_RESOLVE
    }
}
