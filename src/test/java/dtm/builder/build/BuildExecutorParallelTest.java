package dtm.builder.build;

import dtm.builder.manifest.model.*;
import dtm.builder.build.incremental.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class BuildExecutorParallelTest {
    @TempDir Path project;
    final AtomicInteger compiles = new AtomicInteger(), links = new AtomicInteger(), active = new AtomicInteger(), peak = new AtomicInteger();
    volatile boolean fail;
    volatile CountDownLatch blocked;
    final CountDownLatch started = new CountDownLatch(1);

    BuildRequest request(ManifestRootModel manifest) throws Exception {
        Path compiler = project.resolve("fake-gcc");
        if (!Files.exists(compiler)) Files.writeString(compiler, "compiler");
        return new BuildRequest(project, manifest, new Toolchain(ToolchainKind.CUSTOM, compiler, compiler), BuildSystem.MANIFEST,
                project.resolve("packages"), project.resolve("build"), "Debug", ignored -> {}, 4, List.of(), true,
                this::execute, new BuildOptions(64L << 20, 1L << 20, project.resolve("cache"), 0, false, true));
    }
    int execute(List<String> command, Path cwd, Map<String,String> env, java.util.function.Consumer<String> out) {
        Path object = Path.of(command.get(command.indexOf("-o") + 1));
        try {
            Files.createDirectories(object.getParent());
            if (command.contains("-c")) {
                peak.accumulateAndGet(active.incrementAndGet(), Math::max);
                try {
                    started.countDown();
                    if (blocked != null) blocked.await();
                    if (fail) { Files.writeString(object, "partial"); return 2; }
                    Path source = command.stream().filter(s -> s.endsWith(".cpp") || s.endsWith(".c")).map(Path::of).findFirst().orElseThrow();
                    Files.writeString(object, Files.readString(source) + env + command);
                    Path header = project.resolve("shared.h");
                    Files.writeString(Path.of(command.get(command.indexOf("-MF") + 1)), escape(object) + ": " + escape(source)
                            + (Files.exists(header) ? " " + escape(header) : "") + "\n");
                    compiles.incrementAndGet();
                } finally { active.decrementAndGet(); }
            } else { Files.writeString(object, "binary"); links.incrementAndGet(); }
            return 0;
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); return -1; }
        catch (IOException e) { throw new UncheckedIOException(e); }
    }
    private static String escape(Path path) { return path.toString().replace('\\', '/').replace(" ", "\\ "); }
    ManifestRootModel manifest() {
        var manifest = new ManifestRootModel(); manifest.setName("app"); manifest.setSources(List.of("src")); return manifest;
    }

    @Test void buildsThousandsIncrementallyWithStableObjectsAndContentInvalidation() throws Exception {
        Path sources = Files.createDirectories(project.resolve("src"));
        for (int i = 0; i < 2000; i++) Files.writeString(sources.resolve("unit-%04d.cpp".formatted(i)), "int f" + i + "(){return 1;}\n");
        Path header = Files.writeString(project.resolve("shared.h"), "#define VALUE 1\n");
        var manifest = manifest();
        assertTrue(BuildExecutor.build(request(manifest)).success()); assertEquals(2000, compiles.get()); assertEquals(1, links.get());
        assertTrue(peak.get() <= 4);
        Path state = BuildStateStore.stateFile(project.resolve("build"), "app");
        FileTime stateTime = FileTime.fromMillis(1000); Files.setLastModifiedTime(state, stateTime);
        assertTrue(BuildExecutor.build(request(manifest)).success()); assertEquals(2000, compiles.get()); assertEquals(1, links.get());
        assertEquals(stateTime, Files.getLastModifiedTime(state), "No-op builds must not rewrite state");
        Path source = sources.resolve("unit-1234.cpp"); FileTime modified = Files.getLastModifiedTime(source);
        Files.writeString(source, Files.readString(source).replace("return 1", "return 2")); Files.setLastModifiedTime(source, modified);
        assertTrue(BuildExecutor.build(request(manifest)).success()); assertEquals(2001, compiles.get());

        Files.writeString(sources.resolve("aaa.cpp"), "int first(){return 0;}\n");
        assertTrue(BuildExecutor.build(request(manifest)).success()); assertEquals(2002, compiles.get());
        FileTime headerTime = Files.getLastModifiedTime(header);
        Files.writeString(header, "#define VALUE 2\n"); Files.setLastModifiedTime(header, headerTime);
        assertTrue(BuildExecutor.build(request(manifest)).success()); assertEquals(4003, compiles.get());
        manifest.setCompileFlags(List.of("-DCHANGED"));
        assertTrue(BuildExecutor.build(request(manifest)).success()); assertEquals(6004, compiles.get());
        manifest.setEnv(Map.of("CUSTOM_CONFIGURATION", "changed"));
        assertTrue(BuildExecutor.build(request(manifest)).success()); assertEquals(8005, compiles.get());
    }

    @Test void failedSubprocessCannotLeaveAnApparentlyValidIncrementalObject() throws Exception {
        Files.createDirectories(project.resolve("src"));
        Path source = Files.writeString(project.resolve("src/main.cpp"), "int main(){return 0;}");
        var manifest = manifest(); assertTrue(BuildExecutor.build(request(manifest)).success());
        Files.writeString(source, "int main(){return 1;}"); fail = true;
        assertFalse(BuildExecutor.build(request(manifest)).success()); assertEquals(0, active.get());
        fail = false;
        assertTrue(BuildExecutor.build(request(manifest)).success()); assertEquals(2, compiles.get()); assertEquals(2, links.get());
    }

    @Test void cancellationDrainsWorkersBeforeReturningAndNextBuildRecovers() throws Exception {
        Files.createDirectories(project.resolve("src"));
        for (int i = 0; i < 20; i++) Files.writeString(project.resolve("src/u" + i + ".cpp"), "int value;");
        blocked = new CountDownLatch(1);
        var req = request(manifest());
        var result = new java.util.concurrent.atomic.AtomicReference<BuildResult>();
        Thread build = Thread.ofPlatform().start(() -> result.set(BuildExecutor.build(req)));
        try {
            assertTrue(started.await(10, TimeUnit.SECONDS)); build.interrupt(); build.join(10000);
            assertFalse(build.isAlive()); assertNotNull(result.get()); assertFalse(result.get().success()); assertEquals(0, active.get());
        } finally { blocked.countDown(); build.interrupt(); build.join(10000); }
        blocked = null;
        assertTrue(BuildExecutor.build(req).success()); assertEquals(20, compiles.get());
    }

    @Test void targetDependenciesAndLinkBarrierArePreserved() throws Exception {
        Files.createDirectories(project.resolve("lib")); Files.createDirectories(project.resolve("app"));
        for (String folder : List.of("lib", "app")) for (int i = 0; i < 30; i++) Files.writeString(project.resolve(folder + "/u" + i + ".cpp"), "int value;");
        var lib = new ManifestTargetModel(); lib.setId("lib"); lib.setType("executable"); lib.setSources(List.of("lib"));
        var app = new ManifestTargetModel(); app.setId("app"); app.setType("executable"); app.setSources(List.of("app")); app.setDependsOn(List.of("lib"));
        var m = manifest(); m.setTargets(List.of(app, lib));
        BuildRequest base = request(m);
        ProcessExecutor checked = (command, cwd, env, output) -> {
            if (command.stream().anyMatch(s -> s.startsWith(project.resolve("app").toString())))
                assertTrue(Files.isRegularFile(project.resolve("build/lib" + (ToolProbe.isWindows() ? ".exe" : ""))));
            if (!command.contains("-c")) assertEquals(0, active.get(), "Link must wait for all its units");
            return execute(command, cwd, env, output);
        };
        var req = new BuildRequest(base.projectPath(), m, base.toolchain(), base.buildSystem(), base.packagesDir(), base.buildDir(),
                base.buildMode(), base.output(), 4, List.of(), true, checked, base.options());
        assertTrue(BuildExecutor.build(req).success()); assertEquals(60, compiles.get()); assertEquals(2, links.get());
    }

    @Test void moduleAndPchFlagsKeepThePreviousSerialOrdering() throws Exception {
        Files.createDirectories(project.resolve("src"));
        for (int i = 0; i < 20; i++) Files.writeString(project.resolve("src/u" + i + ".cpp"), "int value;");
        var m = manifest(); m.setCompileFlags(List.of("-fmodules-ts"));
        assertTrue(BuildExecutor.build(request(m)).success()); assertEquals(20, compiles.get()); assertEquals(1, peak.get());
    }
}
