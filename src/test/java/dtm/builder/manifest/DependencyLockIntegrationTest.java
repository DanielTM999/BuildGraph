package dtm.builder.manifest;

import dtm.builder.manifest.model.DependencyLock;
import dtm.builder.manifest.model.LibraryManifest;
import dtm.builder.manifest.model.PackageLock;
import dtm.builder.repo.DependencyLockStore;
import dtm.builder.repo.GlobalRepository;
import dtm.builder.repo.RepoJson;
import dtm.builder.repo.SyncResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DependencyLockIntegrationTest {

    @TempDir
    Path temp;

    @Test
    void explicitLockDoesNotMaterializeAndBuildUsesLockedVersion() throws Exception {
        Path project = Files.createDirectories(temp.resolve("project"));
        Path repositoryRoot = Files.createDirectories(temp.resolve("repository"));
        Path packages = project.resolve("packages");
        writeManifest(project, "[1.0,2.0)");
        publish(repositoryRoot, "core", "1.0.0");
        ManifestResolver resolver = new ManifestResolver(project,
                GlobalRepository.at(repositoryRoot));

        assertEquals(SyncResult.APPLIED_CHANGED, resolver.lockPackages(null, null));
        assertTrue(Files.isRegularFile(project.resolve(DependencyLock.FILE_NAME)));
        assertFalse(Files.exists(packages));

        publish(repositoryRoot, "core", "1.5.0");
        assertEquals(SyncResult.APPLIED_CHANGED,
                resolver.syncPackagesForBuild(null, packages, null));

        PackageLock materialized = RepoJson.read(
                packages.resolve("core").resolve(PackageLock.FILE_NAME), PackageLock.class);
        assertEquals("1.0.0", materialized.getVersion());
    }

    @Test
    void missingOrStaleLockFallsBackWithoutWritingIt() throws Exception {
        Path project = Files.createDirectories(temp.resolve("project"));
        Path repositoryRoot = Files.createDirectories(temp.resolve("repository"));
        Path packages = project.resolve("packages");
        publish(repositoryRoot, "core", "1.0.0");
        publish(repositoryRoot, "core", "1.5.0");
        writeManifest(project, "[1.0,1.1)");
        ManifestResolver resolver = new ManifestResolver(project,
                GlobalRepository.at(repositoryRoot));
        List<String> log = new ArrayList<>();

        assertEquals(SyncResult.APPLIED_CHANGED,
                resolver.syncPackagesForBuild(null, packages, log::add));
        assertFalse(Files.exists(project.resolve(DependencyLock.FILE_NAME)));
        assertTrue(log.stream().anyMatch(line -> line.contains("nao reproduzivel")));

        assertEquals(SyncResult.APPLIED_CHANGED, resolver.lockPackages(null, null));
        byte[] originalLock = Files.readAllBytes(project.resolve(DependencyLock.FILE_NAME));
        writeManifest(project, "[1.5,2.0)");
        log.clear();
        assertEquals(SyncResult.APPLIED_CHANGED,
                resolver.syncPackagesForBuild(null, packages, log::add));
        assertTrue(log.stream().anyMatch(line -> line.contains("desatualizado")));
        assertTrue(java.util.Arrays.equals(originalLock,
                Files.readAllBytes(project.resolve(DependencyLock.FILE_NAME))));
        PackageLock materialized = RepoJson.read(
                packages.resolve("core").resolve(PackageLock.FILE_NAME), PackageLock.class);
        assertEquals("1.5.0", materialized.getVersion());
    }

    @Test
    void corruptLockFailsInsteadOfBeingIgnored() throws Exception {
        Path project = Files.createDirectories(temp.resolve("project"));
        Path repositoryRoot = Files.createDirectories(temp.resolve("repository"));
        writeManifest(project, "1.0.0");
        publish(repositoryRoot, "core", "1.0.0");
        Files.writeString(project.resolve(DependencyLock.FILE_NAME), "{invalid");
        ManifestResolver resolver = new ManifestResolver(project,
                GlobalRepository.at(repositoryRoot));

        assertEquals(SyncResult.RESOLUTION_FAILED,
                resolver.syncPackagesForBuild(null, project.resolve("packages"), null));
    }

    @Test
    void refreshUpdatesLockAndMaterializes() throws Exception {
        Path project = Files.createDirectories(temp.resolve("project"));
        Path repositoryRoot = Files.createDirectories(temp.resolve("repository"));
        writeManifest(project, "^1.0.0");
        publish(repositoryRoot, "core", "1.2.0");
        ManifestResolver resolver = new ManifestResolver(project,
                GlobalRepository.at(repositoryRoot));

        assertEquals(SyncResult.APPLIED_CHANGED,
                resolver.syncPackages(null, project.resolve("packages"), null));

        DependencyLock lock = DependencyLockStore.read(project);
        assertEquals("1.2.0", lock.getPackages().get(0).getVersion());
        assertTrue(Files.isDirectory(project.resolve("packages/core")));
    }

    private static void writeManifest(Path project, String constraint) throws Exception {
        Files.writeString(project.resolve("Manifest.json"), """
                {
                  "id":"app",
                  "version":"1.0.0",
                  "packagesBase":"packages",
                  "packages":[{"id":"core","versionConstraint":"%s"}]
                }
                """.formatted(constraint));
    }

    private static void publish(Path repositoryRoot, String id, String version) throws Exception {
        Path variant = repositoryRoot.resolve(id).resolve(version).resolve("source");
        Files.createDirectories(variant.resolve("lib/include"));
        Files.writeString(variant.resolve("lib/include/core.h"), version);
        LibraryManifest manifest = new LibraryManifest();
        manifest.setId(id);
        manifest.setName(id);
        manifest.setVersion(version);
        manifest.setIncludePaths(List.of("include"));
        RepoJson.write(variant.resolve(GlobalRepository.GLOBAL_MANIFEST_FILE), manifest);
    }
}
