package dtm.builder.build;

import dtm.builder.UserArgs;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class GlobalConfigTest {
    @TempDir Path dir;
    private String previous;

    @BeforeEach void remember() { previous = System.getProperty(GlobalConfig.PROPERTY); }

    @AfterEach void restore() {
        if (previous == null) System.clearProperty(GlobalConfig.PROPERTY);
        else System.setProperty(GlobalConfig.PROPERTY, previous);
    }

    private Path config(String json) throws IOException {
        Path file = dir.resolve("config.json");
        Files.writeString(file, json);
        System.setProperty(GlobalConfig.PROPERTY, file.toString());
        return file;
    }

    @Test void missingFileKeepsBuiltinDefaults() {
        System.setProperty(GlobalConfig.PROPERTY, dir.resolve("absent.json").toString());
        assertEquals(BuildOptions.builtin(), BuildOptions.defaults());
        assertEquals(5L << 30, new UserArgs(new String[]{"build"}).getBuildOptions().cacheSize());
    }

    @Test void appliesGlobalCacheDirectoryAndSize() throws IOException {
        Path target = dir.resolve("objects").toAbsolutePath();
        config("{\"cacheDir\": \"" + target.toString().replace("\\", "\\\\") + "\", \"cacheSize\": \"2G\"}");
        var options = new UserArgs(new String[]{"build"}).getBuildOptions();
        assertEquals(target.normalize(), options.cacheDirectory());
        assertEquals(2L << 30, options.cacheSize());
        assertTrue(options.sharedCache());
    }

    @Test void commandLineOverridesEachSetting() throws IOException {
        config("{\"cacheDir\": \"from-config\", \"cacheSize\": \"2G\"}");
        var size = new UserArgs(new String[]{"build", "--cache-size", "1G"}).getBuildOptions();
        assertEquals(1L << 30, size.cacheSize());
        assertEquals(dir.resolve("from-config").toAbsolutePath().normalize(), size.cacheDirectory());
        var path = new UserArgs(new String[]{"build", "--cache-dir", "cli"}).getBuildOptions();
        assertEquals(Path.of("cli"), path.cacheDirectory());
        assertEquals(2L << 30, path.cacheSize());
    }

    @Test void partialConfigFallsBackPerKey() throws IOException {
        config("{\"cacheSize\": 1048576}");
        var options = BuildOptions.defaults();
        assertEquals(1L << 20, options.cacheSize());
        assertEquals(BuildOptions.builtin().cacheDirectory(), options.cacheDirectory());
        config("{}");
        assertEquals(BuildOptions.builtin(), BuildOptions.defaults());
    }

    @Test void relativeDirectoryResolvesAgainstConfigFolder() throws IOException {
        Path file = config("{\"cacheDir\": \"cache/objects\"}");
        assertEquals(file.toAbsolutePath().getParent().resolve("cache/objects").normalize(), GlobalConfig.load(file).cacheDirectory());
    }

    @Test void invalidConfigMakesCommandInvalidWithFileInMessage() throws IOException {
        for (String json : new String[]{"{\"cacheSize\": \"lots\"}", "{\"cacheDir\": 3}", "{\"cachesize\": \"1G\"}", "[1]", "{broken"}) {
            Path file = config(json);
            var args = new UserArgs(new String[]{"build"});
            assertTrue(args.isInvalidCommand(), json);
            assertTrue(args.getInvalidReason().contains(file.toString()), args.getInvalidReason());
        }
    }

    @Test void invalidConfigIsIgnoredWhenBothCacheFlagsAreGiven() throws IOException {
        config("{broken");
        var args = new UserArgs(new String[]{"build", "--cache-dir", "x", "--cache-size", "1G"});
        assertFalse(args.isInvalidCommand());
        assertEquals(1L << 30, args.getBuildOptions().cacheSize());
        assertFalse(new UserArgs(new String[]{"--version"}).isInvalidCommand());
    }
}
