package dtm.builder.build;

import dtm.builder.lifecycle.LifecycleContext;
import dtm.builder.lifecycle.LifecycleExecutor;
import dtm.builder.lifecycle.Phase;
import dtm.builder.lifecycle.ReleaseExecutor;
import dtm.builder.manifest.model.ManifestRootModel;
import dtm.builder.manifest.model.ManifestTargetModel;
import dtm.builder.manifest.model.ManifestTaskModel;
import dtm.builder.placeholder.PlaceholderContext;
import dtm.builder.placeholder.PlaceholderResolver;
import dtm.builder.repo.GlobalRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class NativeReleaseTest {
    @TempDir Path project;

    private ManifestTargetModel target(String id, String type, String... dependencies) throws Exception {
        Files.writeString(project.resolve(id + ".c"), "int " + id + "(void) { return 0; }");
        var target = new ManifestTargetModel();
        target.setId(id);
        target.setType(type);
        target.setSources(List.of(id + ".c"));
        target.setDependsOn(List.of(dependencies));
        return target;
    }

    private ManifestRootModel manifest() throws Exception {
        var manifest = new ManifestRootModel();
        manifest.setId("sample");
        manifest.setName("sample");
        manifest.setVersion("1.0");
        manifest.setCCompiler("fake-gcc");
        manifest.setCxxCompiler("fake-g++");
        manifest.setArchiver("fake-ar");
        manifest.setTargets(List.of(target("base", "static"), target("app", "executable", "base")));
        Files.createDirectories(project.resolve("tests"));
        Files.writeString(project.resolve("tests/main.c"), "int main(void) { return 0; }");
        return manifest;
    }

    private LifecycleContext context(ManifestRootModel manifest, ProcessExecutor tools, String... ids) {
        var options = new BuildOptions(0, 0, project.resolve("cache/objects"), 1024 * 1024, true, false);
        return new LifecycleContext(project, manifest, null, BuildSystem.MANIFEST,
                GlobalRepository.at(project.resolve("repo")), project.resolve("packages"), project.resolve("build"),
                "Release", new PlaceholderResolver(new PlaceholderContext(project, manifest)),
                ignored -> { }, ignored -> { }, 1, List.of(ids), true, tools, options);
    }

    private Set<String> files() throws Exception {
        try (var paths = Files.walk(project.resolve("build"))) {
            return paths.filter(Files::isRegularFile).map(project.resolve("build")::relativize)
                    .map(Path::toString).collect(Collectors.toSet());
        }
    }

    private String appName() {
        return TargetPlatform.host().fileName("app", TargetType.EXECUTABLE, false);
    }

    @Test void rebuildsEveryTimeRunsTestsAndLeavesOnlyDeliverables() throws Exception {
        var manifest = manifest();
        manifest.getTargets().get(1).setOutputName("dist/" + appName());
        var tools = new NativeBuildTest.FakeTools();
        var ctx = context(manifest, tools);
        assertTrue(LifecycleExecutor.run(ctx, Set.of(Phase.BUILD)).success());
        assertTrue(Files.isDirectory(project.resolve("build/.obj")));
        Files.writeString(project.resolve("build/stale-tests.exe"), "old output");
        tools.commands.clear();

        for (int run = 0; run < 2; run++) {
            var result = ReleaseExecutor.run(ctx, Set.of(Phase.BUILD));
            assertTrue(result.success(), result.message());
            assertEquals(Set.of("libbase.a", Path.of("dist", appName()).toString()), files());
            assertFalse(Files.exists(project.resolve("build/.obj")));
            assertFalse(Files.exists(project.resolve("build/.buildgraph-state")));
            assertFalse(Files.exists(project.resolve("build/.buildgraph-outputs")));
            assertEquals(3, tools.commands.stream().filter(c -> c.contains("-c")).count());
            assertTrue(tools.commands.getLast().getFirst().contains("sample_tests"));
            assertTrue(tools.commands.stream().filter(c -> c.contains("-c")).allMatch(c -> c.contains("-O2")));
            tools.commands.clear();
        }
        assertFalse(Files.exists(project.resolve("cache/objects")));
    }

    @Test void failedTestsRemoveAllOutputAndDoNotInstall() throws Exception {
        var manifest = manifest();
        var tools = new NativeBuildTest.FakeTools();
        ProcessExecutor failTests = (command, cwd, env, out) -> {
            int code = tools.run(command, cwd, env, out);
            return command.size() == 1 && command.getFirst().contains("sample_tests") ? 42 : code;
        };
        var result = ReleaseExecutor.run(context(manifest, failTests), Set.of(Phase.INSTALL));
        assertFalse(result.success());
        assertTrue(result.message().contains("Testes falharam"), result.message());
        assertEquals(Set.of(), files());
        assertFalse(Files.exists(project.resolve("repo/packages")));
        assertFalse(Files.exists(project.resolve("build/.obj")));
    }

    @Test void skippedTestsStillRebuildCleanOutputAndAllowInstall() throws Exception {
        var manifest = manifest();
        manifest.setSkipTests(true);
        Files.writeString(project.resolve("tests/main.c"), "invalid test source");
        Files.createDirectories(project.resolve("build/test-reports"));
        Files.writeString(project.resolve("build/test-reports/stale.txt"), "stale");
        Files.writeString(project.resolve("build/sample_tests.exe"), "stale");
        var tools = new NativeBuildTest.FakeTools();
        var ctx = context(manifest, tools);
        for (int run = 0; run < 2; run++) {
            var result = ReleaseExecutor.run(ctx, Set.of(Phase.INSTALL));
            assertTrue(result.success(), result.message());
            assertEquals(Set.of("libbase.a", appName()), files());
            assertEquals(2, tools.commands.stream().filter(c -> c.contains("-c")).count());
            assertTrue(tools.commands.stream().flatMap(List::stream)
                    .noneMatch(arg -> arg.contains("sample_tests") || arg.contains("tests/main.c")
                            || arg.contains("tests\\main.c")));
            assertFalse(Files.exists(project.resolve("build/.obj")));
            assertFalse(Files.exists(project.resolve("build/.buildgraph-state")));
            assertFalse(Files.exists(project.resolve("build/test-reports")));
            tools.commands.clear();
        }
        assertFalse(Files.exists(project.resolve("cache/objects")));
        try (var installed = Files.walk(project.resolve("repo"))) {
            assertTrue(installed.anyMatch(path -> path.getFileName().toString().equals("libbase.a")));
        }
    }

    @Test void selectedReleaseRemovesOldUnselectedArtifactsAndCleansAfterHooks() throws Exception {
        var manifest = manifest();
        var hook = new ManifestTaskModel();
        hook.setId("after-test");
        hook.setPhase("test");
        hook.setWhen("after");
        hook.setCommand("make-junk");
        manifest.setTasks(List.of(hook));
        var tools = new NativeBuildTest.FakeTools();
        Files.createDirectories(project.resolve("build"));
        Files.writeString(project.resolve("build/old-app.exe"), "stale");
        ProcessExecutor executor = (command, cwd, env, out) -> {
            if (command.getFirst().equals("make-junk")) {
                try {
                    Files.createDirectories(project.resolve("build/test-reports"));
                    Files.writeString(project.resolve("build/test-reports/log.txt"), "temporary");
                } catch (Exception e) { throw new RuntimeException(e); }
                return 0;
            }
            return tools.run(command, cwd, env, out);
        };
        var result = ReleaseExecutor.run(context(manifest, executor, "base"), Set.of(Phase.BUILD));
        assertTrue(result.success(), result.message());
        assertEquals(Set.of("libbase.a"), files());
        assertFalse(Files.exists(project.resolve("build/test-reports")));
    }

    @Test void invalidSelectionAndExternalBackendsDoNotClean() throws Exception {
        var manifest = manifest();
        var tools = new NativeBuildTest.FakeTools();
        Files.createDirectories(project.resolve("build"));
        Files.writeString(project.resolve("build/keep"), "existing");
        assertFalse(ReleaseExecutor.run(context(manifest, tools, "unknown"), Set.of(Phase.BUILD)).success());
        var ctx = context(manifest, tools);
        var external = new LifecycleContext(project, manifest, null, BuildSystem.CMAKE, ctx.repo(), ctx.packagesDir(),
                ctx.buildDir(), "Release", ctx.placeholders(), ctx.output(), ctx.info());
        assertFalse(ReleaseExecutor.run(external, Set.of(Phase.BUILD)).success());
        assertEquals(Set.of("keep"), files());
        assertTrue(tools.commands.isEmpty());
    }

    @Test void refusesProjectRootAsOutputDirectory() throws Exception {
        var manifest = manifest();
        var ctx = context(manifest, new NativeBuildTest.FakeTools());
        var unsafe = new LifecycleContext(project, manifest, null, BuildSystem.MANIFEST, ctx.repo(), ctx.packagesDir(),
                project, "Release", ctx.placeholders(), ctx.output(), ctx.info());
        assertFalse(ReleaseExecutor.run(unsafe, Set.of(Phase.BUILD)).success());
        assertTrue(Files.exists(project.resolve("app.c")));
    }

    @Test void removesObjectDependenciesButKeepsImportLibraries() throws Exception {
        var manifest = manifest();
        manifest.setTargets(List.of(target("helper", "object"), target("plugin", "shared", "helper")));
        manifest.setPlatform("x86_64-pc-windows-msvc");
        manifest.setCCompiler("cl");
        manifest.setCxxCompiler("cl");
        manifest.setLinker("fake-link");
        manifest.setLinkerKind("msvc");
        Files.delete(project.resolve("tests/main.c"));
        var tools = new NativeBuildTest.FakeTools();
        ProcessExecutor executor = (command, cwd, env, out) -> {
            int code = tools.run(command, cwd, env, out);
            for (String arg : command) {
                if (arg.startsWith("/IMPLIB:") || arg.startsWith("/PDB:")) {
                    try { Files.writeString(Path.of(arg.substring(arg.indexOf(':') + 1)), "sidecar"); }
                    catch (Exception e) { throw new RuntimeException(e); }
                }
            }
            return code;
        };
        var result = ReleaseExecutor.run(context(manifest, executor), Set.of(Phase.BUILD));
        assertTrue(result.success(), result.message());
        assertEquals(Set.of("plugin.dll", "plugin.lib"), files());
    }
}
