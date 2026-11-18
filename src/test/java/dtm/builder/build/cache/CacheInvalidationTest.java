package dtm.builder.build.cache;

import dtm.builder.UserArgs;
import dtm.builder.cli.CommandDispatcher;
import dtm.builder.build.incremental.FileHasher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.*;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CacheInvalidationTest {
    @TempDir Path temp;
    private String key(String name) { return FileHasher.hashCommand(List.of(name)); }

    @Test void projectCleanupPreservesOtherProjectsAndSharedEntries() throws Exception {
        Path root = temp.resolve("cache"), a = temp.resolve("a"), b = temp.resolve("b");
        var first = new SharedObjectCache(root, 1024 * 1024, a);
        var second = new SharedObjectCache(root, 1024 * 1024, b);
        Path object = Files.writeString(temp.resolve("object.o"), "compiled");
        first.store(key("a"), object);
        first.store(key("shared"), object);
        second.store(key("b"), object);
        assertTrue(second.restore(key("shared"), temp.resolve("restored.o")));
        CacheInvalidation.invalidate(root, a, false);
        assertFalse(Files.exists(first.entry(key("a"))));
        assertTrue(Files.exists(second.entry(key("b"))));
        assertTrue(Files.exists(first.entry(key("shared"))));
        CacheInvalidation.invalidate(root, b, false);
        assertFalse(Files.exists(second.entry(key("b"))));
        assertFalse(Files.exists(first.entry(key("shared"))));
    }

    @Test void globalCleanupIncludesLegacyEntriesButPreservesUnrelatedFiles() throws Exception {
        Path root = temp.resolve("cache");
        var cache = new SharedObjectCache(root, 1024 * 1024);
        cache.store(key("legacy"), Files.writeString(temp.resolve("object.o"), "compiled"));
        Path unrelated = Files.writeString(root.resolve("keep.txt"), "keep");
        Path inShard = Files.writeString(cache.entry(key("legacy")).resolveSibling("keep.txt"), "keep");
        CacheInvalidation.invalidate(root, temp, true);
        assertFalse(Files.exists(cache.entry(key("legacy"))));
        assertTrue(Files.exists(unrelated));
        assertTrue(Files.exists(inShard));
        CacheInvalidation.invalidate(root, temp, true);
        CacheInvalidation.invalidate(temp.resolve("missing"), temp, true);
    }

    @Test void standaloneProjectCommandResolvesOutputAndDoesNotBuild() throws Exception {
        Files.writeString(temp.resolve("Manifest.json"), """
                {"outputDir":"custom-output"}
                """);
        Path output = Files.createDirectories(temp.resolve("custom-output"));
        Path objects = Files.createDirectories(output.resolve(".obj/app"));
        Files.writeString(objects.resolve("a.o"), "compiled");
        Path states = Files.createDirectories(output.resolve(".buildgraph-state"));
        Files.writeString(states.resolve("app.json"), "{}");
        Path artifact = Files.writeString(output.resolve("app.exe"), "keep");
        var args = new UserArgs(new String[]{temp.toString(), "--invalidade-cache",
                "--cache-dir", temp.resolve("cache").toString(), "--cache-size", "1G"});
        assertFalse(args.isInvalidCommand(), args.getInvalidReason());
        assertFalse(args.hasBuild());
        assertEquals(0, new CommandDispatcher().run(args));
        assertFalse(Files.exists(objects));
        assertFalse(Files.exists(states));
        assertTrue(Files.exists(artifact));
    }

    @Test void globalCommandWorksWithoutProjectOrManifestAndAcceptsAliases() throws Exception {
        for (String flag : List.of("--invalidade-cache-global", "--invalidate-cache-global")) {
            var args = new UserArgs(new String[]{temp.resolve("nonexistent-project").toString(), flag,
                    "--cache-dir", temp.resolve("cache").toString(), "--cache-size", "1G"});
            assertFalse(args.isInvalidCommand(), args.getInvalidReason());
            assertFalse(args.hasBuild());
            assertEquals(0, new CommandDispatcher().run(args));
        }
        assertTrue(new UserArgs(new String[]{"build", "--invalidate-cache"}).hasBuild());
        assertTrue(new UserArgs(new String[]{"--invalidate-cache"}).hasInvalidateCache());
    }

    @Test void malformedIndexCannotDeleteFilesOutsideCache() throws Exception {
        Path root = temp.resolve("cache"), project = temp.resolve("project");
        CacheInvalidation.record(root, project, "../../outside");
        Path outside = Files.writeString(temp.resolve("outside.entry"), "keep");
        CacheInvalidation.invalidate(root, project, false);
        assertEquals("keep", Files.readString(outside));
    }

    @Test void invalidManifestAbortsLocalDeletion() throws Exception {
        Files.writeString(temp.resolve("Manifest.json"), "{broken");
        Path objects = Files.createDirectories(temp.resolve("build/.obj"));
        Path object = Files.writeString(objects.resolve("keep.o"), "keep");
        var args = new UserArgs(new String[]{temp.toString(), "--invalidade-cache",
                "--cache-dir", temp.resolve("cache").toString(), "--cache-size", "1G"});
        assertEquals(1, new CommandDispatcher().run(args));
        assertTrue(Files.exists(object));
    }
}
