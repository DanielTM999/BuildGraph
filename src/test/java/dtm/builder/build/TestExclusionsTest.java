package dtm.builder.build;

import dtm.builder.manifest.model.ManifestRootModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestExclusionsTest {

    @TempDir Path project;

    private TestExclusions exclusions(String folder, String... patterns) {
        ManifestRootModel manifest = new ManifestRootModel();
        manifest.setTestFolder(folder);
        manifest.setExcludeTests(List.of(patterns));
        return TestExclusions.of(project, manifest);
    }

    private Path test(String folder, String relative) {
        return project.resolve(folder).resolve(relative);
    }

    @Test
    void excludesFilesFoldersAndGlobs() {
        TestExclusions ex = exclusions(null, "jit/alloc_test.cpp", "gc/", "**/*_bench.cpp", "./util\\a?.c");
        List<Path> sources = List.of(test("tests", "main.cpp"), test("tests", "jit/alloc_test.cpp"),
                test("tests", "jit/alloc_test2.cpp"), test("tests", "gc/young/a.cpp"),
                test("tests", "core/x_bench.cpp"), test("tests", "util/ab.c"), test("tests", "util/abc.c"),
                test("tests", "gcx/keep.cpp"));
        assertEquals(List.of(test("tests", "main.cpp"), test("tests", "jit/alloc_test2.cpp"),
                test("tests", "util/abc.c"), test("tests", "gcx/keep.cpp")), ex.filter(sources));
        assertEquals(List.of(), ex.unmatched());
    }

    @Test
    void globWithoutSlashMatchesFileNamesInAnyFolder() {
        TestExclusions ex = exclusions("specs", "*_slow.c");
        assertTrue(ex.excludes(test("specs", "a_slow.c")));
        assertTrue(ex.excludes(test("specs", "deep/b_slow.c")));
        assertFalse(ex.excludes(test("specs", "deep/b_fast.c")));
    }

    @Test
    void acceptsAbsolutePathsInsideTestFolderAndReportsUnmatched() {
        Path absolute = test("tests", "jit/a.c").toAbsolutePath();
        TestExclusions ex = exclusions(null, absolute.toString(), "missing.c", " ");
        assertFalse(ex.isEmpty());
        assertEquals(List.of(test("tests", "jit/b.c")), ex.filter(List.of(test("tests", "jit/a.c"), test("tests", "jit/b.c"))));
        assertEquals(List.of("missing.c"), ex.unmatched());
        assertTrue(exclusions(null).isEmpty());
    }
}
