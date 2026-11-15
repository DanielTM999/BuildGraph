package dtm.builder.lifecycle;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ReleaseCleanupTest {
    @TempDir Path root;

    @Test void defaultDoesNotRemoveStaticLibrariesOrResources() throws Exception {
        Files.writeString(root.resolve("libcore.a"), "library");
        Files.writeString(root.resolve("app.exe"), "app");
        new ReleaseCleanup(List.of()).clean(root);
        assertTrue(Files.exists(root.resolve("libcore.a")));
        assertTrue(Files.exists(root.resolve("app.exe")));
    }

    @Test void patternsRemoveOnlyRequestedFilesAndDirectories() throws Exception {
        Files.createDirectories(root.resolve("nested"));
        Files.createDirectories(root.resolve("temporary/sub"));
        Files.createDirectories(root.resolve("stdlib"));
        Files.writeString(root.resolve("libcore.a"), "library");
        Files.writeString(root.resolve("nested/libextra.a"), "library");
        Files.writeString(root.resolve("temporary/sub/data.txt"), "temporary");
        Files.writeString(root.resolve("stdlib/runtime.vpk"), "runtime");
        Files.writeString(root.resolve("app.exe"), "app");
        new ReleaseCleanup(List.of("*.a", "temporary")).clean(root);
        assertFalse(Files.exists(root.resolve("libcore.a")));
        assertFalse(Files.exists(root.resolve("nested/libextra.a")));
        assertFalse(Files.exists(root.resolve("temporary")));
        assertTrue(Files.exists(root.resolve("stdlib/runtime.vpk")));
        assertTrue(Files.exists(root.resolve("app.exe")));
    }

    @Test void recursivePatternsIncludeRootFiles() throws Exception {
        Files.createDirectories(root.resolve("nested"));
        Files.writeString(root.resolve("libcore.a"), "library");
        Files.writeString(root.resolve("nested/libextra.a"), "library");
        new ReleaseCleanup(List.of("**/*.a")).clean(root);
        assertFalse(Files.exists(root.resolve("libcore.a")));
        assertFalse(Files.exists(root.resolve("nested/libextra.a")));
    }

    @Test void refusesPathsOutsideTheBuildDirectory() {
        for (String pattern : List.of("../outside", "nested/../../outside", "/absolute", "C:/absolute", ".", "", "["))
            assertThrows(IllegalArgumentException.class, () -> new ReleaseCleanup(List.of(pattern)), pattern);
    }
}
