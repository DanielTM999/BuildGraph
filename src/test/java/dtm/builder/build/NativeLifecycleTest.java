package dtm.builder.build;

import dtm.builder.lifecycle.LifecycleContext;
import dtm.builder.lifecycle.LifecycleExecutor;
import dtm.builder.lifecycle.Phase;
import dtm.builder.manifest.model.ManifestRootModel;
import dtm.builder.manifest.model.ManifestTargetModel;
import dtm.builder.placeholder.PlaceholderContext;
import dtm.builder.placeholder.PlaceholderResolver;
import dtm.builder.repo.GlobalRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class NativeLifecycleTest {
    @TempDir Path project;
    private ManifestTargetModel target(String id, String type, String... dependencies) throws Exception {
        Files.writeString(project.resolve(id + ".c"), "int " + id + "(void) { return 0; }");
        ManifestTargetModel t = new ManifestTargetModel(); t.setId(id); t.setType(type);
        t.setSources(List.of(id + ".c")); t.setDependsOn(List.of(dependencies)); return t;
    }
    private ManifestRootModel manifest() throws Exception {
        var m = new ManifestRootModel(); m.setId("project"); m.setName("project"); m.setVersion("1.0");
        m.setCCompiler("fake-gcc"); m.setCxxCompiler("fake-g++"); m.setArchiver("fake-ar");
        m.setTargets(List.of(target("base", "static"), target("core", "static", "base"),
                target("app", "executable", "core"), target("other", "static")));
        return m;
    }
    private LifecycleContext context(ManifestRootModel m, NativeBuildTest.FakeTools tools, String... ids) {
        return new LifecycleContext(project, m, null, BuildSystem.MANIFEST,
                GlobalRepository.at(project.resolve("repo")), project.resolve("packages"), project.resolve("build"), "Debug",
                new PlaceholderResolver(new PlaceholderContext(project, m), ignored -> {}),
                ignored -> {}, ignored -> {}, 1, List.of(ids), true, tools);
    }
    @Test void cleanRemovesOnlyChainIncludingOldOutputNames() throws Exception {
        var m = manifest(); var tools = new NativeBuildTest.FakeTools();
        assertTrue(LifecycleExecutor.run(context(m, tools), Set.of(Phase.BUILD)).success());
        m.getTargets().get(2).setOutputName("renamed-app");
        assertTrue(LifecycleExecutor.run(context(m, tools, "app"), Set.of(Phase.BUILD)).success());
        int processes = tools.commands.size();
        var result = LifecycleExecutor.run(context(m, tools, "app"), Set.of(Phase.CLEAN));
        assertTrue(result.success(), result.message()); assertEquals(processes, tools.commands.size());
        assertFalse(Files.exists(project.resolve("build/renamed-app")));
        assertFalse(Files.exists(project.resolve("build/.obj/core")));
        assertFalse(Files.exists(project.resolve("build/.buildgraph-state/base.json")));
        assertTrue(Files.exists(project.resolve("build/libother.a")));
        assertTrue(Files.exists(project.resolve("build/.obj/other")));
        assertTrue(Files.exists(project.resolve("build/.buildgraph-state/other.json")));
        assertFalse(Files.exists(project.resolve("build/" + TargetPlatform.host().fileName("app", TargetType.EXECUTABLE, false))));
    }
    @Test void installPublishesOnlySelectedLibrariesAndDependencies() throws Exception {
        var m = manifest(); var tools = new NativeBuildTest.FakeTools();
        var result = LifecycleExecutor.run(context(m, tools, "core"), Set.of(Phase.INSTALL));
        assertTrue(result.success(), result.message());
        try (var files = Files.walk(project.resolve("repo"))) {
            List<String> paths = files.map(Path::toString).toList();
            assertTrue(paths.stream().anyMatch(p -> p.contains("project-core")));
            assertTrue(paths.stream().anyMatch(p -> p.contains("project-base")));
            assertFalse(paths.stream().anyMatch(p -> p.contains("project-other")));
        }
        assertFalse(Files.exists(project.resolve("build/libother.a")));
    }
    @Test void testsLinkOnlySelectedLibraries() throws Exception {
        var m = manifest(); var tools = new NativeBuildTest.FakeTools();
        Files.createDirectories(project.resolve("tests"));
        Files.writeString(project.resolve("tests/main.c"), "int main(void) { return 0; }");
        var result = LifecycleExecutor.run(context(m, tools, "core"), Set.of(Phase.TEST));
        assertTrue(result.success(), result.message());
        var link = tools.commands.get(tools.commands.size() - 2);
        assertTrue(link.stream().anyMatch(p -> p.endsWith("libcore.a")), link.toString());
        assertTrue(link.stream().anyMatch(p -> p.endsWith("libbase.a")), link.toString());
        assertFalse(link.stream().anyMatch(p -> p.contains("other")), link.toString());
    }
    @Test void invalidSelectionDoesNotRunCleanOrHooks() throws Exception {
        var m = manifest(); var tools = new NativeBuildTest.FakeTools();
        Path sentinel = Files.createDirectories(project.resolve("build")).resolve("keep");
        Files.writeString(sentinel, "unchanged");
        var result = LifecycleExecutor.run(context(m, tools, "missing"), Set.of(Phase.CLEAN, Phase.BUILD));
        assertFalse(result.success()); assertTrue(Files.exists(sentinel)); assertTrue(tools.commands.isEmpty());
    }
    @Test void oldOutputClaimedByAnotherTargetIsPreservedDuringClean() throws Exception {
        var m = manifest(); var tools = new NativeBuildTest.FakeTools();
        m.getTargets().get(2).setOutputName("shared-name");
        assertTrue(LifecycleExecutor.run(context(m, tools, "app"), Set.of(Phase.BUILD)).success());
        m.getTargets().get(2).setOutputName("new-name"); m.getTargets().get(3).setOutputName("shared-name");
        assertTrue(LifecycleExecutor.run(context(m, tools, "other"), Set.of(Phase.BUILD)).success());
        assertTrue(LifecycleExecutor.run(context(m, tools, "app"), Set.of(Phase.CLEAN)).success());
        assertTrue(Files.exists(project.resolve("build/shared-name")));
    }
}
