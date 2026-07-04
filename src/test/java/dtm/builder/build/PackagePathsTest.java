package dtm.builder.build;

import dtm.builder.manifest.model.LibraryManifest;
import dtm.builder.repo.GlobalRepository;
import dtm.builder.repo.RepoJson;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PackagePathsTest {

    @TempDir
    Path temp;

    @Test
    void exposesPrecompiledLibrariesAsIncrementalLinkInputs() throws Exception {
        Path pkg = Files.createDirectories(temp.resolve("fmt"));
        Path lib = Files.createDirectories(pkg.resolve("lib"));
        Path staticLibrary = Files.writeString(lib.resolve("libfmt.a"), "archive");
        Path sharedLibrary = Files.writeString(lib.resolve("libfmt.so.1"), "shared");
        Files.writeString(lib.resolve("README.txt"), "ignored");
        LibraryManifest manifest = new LibraryManifest();
        manifest.setLibraryPaths(List.of("lib"));
        manifest.setLinkLibraries(List.of("fmt"));
        RepoJson.write(pkg.resolve(GlobalRepository.GLOBAL_MANIFEST_FILE), manifest);

        PackagePaths paths = PackagePaths.resolve(temp);

        assertEquals(List.of(staticLibrary.toAbsolutePath().normalize(),
                sharedLibrary.toAbsolutePath().normalize()), paths.linkInputFiles());
    }
}
