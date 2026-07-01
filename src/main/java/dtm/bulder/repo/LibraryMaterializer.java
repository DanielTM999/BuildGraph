package dtm.bulder.repo;

import dtm.bulder.manifest.model.LibraryManifest;
import dtm.bulder.manifest.model.PackageLock;
import dtm.bulder.manifest.model.ResolvedDependency;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.UUID;

public final class LibraryMaterializer {

    private LibraryMaterializer() {
    }

    public static String folderNameFor(ResolvedDependency dep) {
        return PathSanitizer.sanitizePackageFolderName(dep.preferredName());
    }

    public static boolean isUpToDate(Path finalDir, ResolvedDependency dep) {
        Path lockFile = finalDir.resolve(PackageLock.FILE_NAME);
        if (!Files.isRegularFile(lockFile)) {
            return false;
        }
        try {
            PackageLock lock = RepoJson.read(lockFile, PackageLock.class);
            return Objects.equals(lock.getId(), dep.id())
                    && Objects.equals(lock.getVersion(), version(dep))
                    && Objects.equals(lock.getPlatformToken(), dep.platformToken())
                    && Objects.equals(lock.getGlobalManifestPath(),
                            dep.globalManifestFile().toString());
        } catch (IOException e) {
            return false;
        }
    }

    public static boolean materialize(ResolvedDependency dep, Path packagesDir) throws IOException {
        String folderName = folderNameFor(dep);
        Path finalDir = packagesDir.resolve(folderName);
        if (isUpToDate(finalDir, dep)) {
            return false;
        }

        Path tempDir = packagesDir.resolve(".tmp-" + folderName + "-" + UUID.randomUUID());
        Files.createDirectories(tempDir);
        try {
            Path globalContent = dep.globalLibraryDir().resolve(GlobalRepository.LIB_SUBDIR);
            if (!Files.isDirectory(globalContent)) {
                throw new IOException("Repo global de " + dep.key()
                        + " sem subpasta lib/: " + globalContent);
            }
            SafeZipExtractor.copyTree(globalContent, tempDir);
            Files.copy(dep.globalManifestFile(),
                    tempDir.resolve(GlobalRepository.GLOBAL_MANIFEST_FILE),
                    StandardCopyOption.REPLACE_EXISTING);
            RepoJson.write(tempDir.resolve(PackageLock.FILE_NAME), buildLock(dep));

            if (Files.exists(finalDir)) {
                SafeZipExtractor.deleteTree(finalDir);
            }
            try {
                Files.move(tempDir, finalDir, StandardCopyOption.ATOMIC_MOVE);
            } catch (Exception atomicFail) {
                Files.createDirectories(finalDir);
                SafeZipExtractor.copyTree(tempDir, finalDir);
                SafeZipExtractor.deleteTree(tempDir);
            }
            return true;
        } catch (IOException | RuntimeException e) {
            SafeZipExtractor.deleteTree(tempDir);
            throw e;
        }
    }

    private static PackageLock buildLock(ResolvedDependency dep) {
        PackageLock lock = new PackageLock();
        lock.setId(dep.id());
        lock.setVersion(version(dep));
        LibraryManifest lm = dep.libraryManifest();
        lock.setName(lm == null ? dep.id() : lm.getName());
        lock.setKind(lm == null ? LibraryManifest.KIND_SOURCE : lm.getKind());
        lock.setSource("Manifest");
        lock.setPlatformToken(dep.platformToken());
        lock.setManagedBy(PackageLock.MANAGED_BY);
        lock.setGlobalManifestPath(dep.globalManifestFile().toString());
        return lock;
    }

    private static String version(ResolvedDependency dep) {
        return dep.declared() == null ? null : dep.declared().getVersion();
    }
}
