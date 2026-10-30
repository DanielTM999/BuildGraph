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
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestRunnerSkipTest {

    @TempDir Path project;

    private ManifestRootModel manifest() throws Exception {
        Files.writeString(project.resolve("core.c"), "int core(void) { return 0; }");
        ManifestTargetModel core = new ManifestTargetModel();
        core.setId("core");
        core.setType("static");
        core.setSources(List.of("core.c"));
        var m = new ManifestRootModel();
        m.setId("project");
        m.setName("project");
        m.setVersion("1.0");
        m.setCCompiler("fake-gcc");
        m.setCxxCompiler("fake-g++");
        m.setArchiver("fake-ar");
        m.setTargets(List.of(core));
        Files.createDirectories(project.resolve("tests/jit"));
        Files.writeString(project.resolve("tests/main.c"), "int main(void) { return 0; }");
        Files.writeString(project.resolve("tests/falha.c"), "int falha(void) { return 1; }");
        Files.writeString(project.resolve("tests/jit/alloc.c"), "int alloc(void) { return 0; }");
        Files.writeString(project.resolve("tests/jit/calls.c"), "int calls(void) { return 0; }");
        return m;
    }

    private LifecycleContext context(ManifestRootModel m, NativeBuildTest.FakeTools tools, List<String> output) {
        return new LifecycleContext(project, m, null, BuildSystem.MANIFEST,
                GlobalRepository.at(project.resolve("repo")), project.resolve("packages"), project.resolve("build"), "Debug",
                new PlaceholderResolver(new PlaceholderContext(project, m), ignored -> {}),
                output::add, ignored -> {}, 1, List.of(), true, tools);
    }

    private static boolean compiled(NativeBuildTest.FakeTools tools, String file) {
        return tools.commands.stream().anyMatch(c -> c.stream().anyMatch(a -> a.replace('\\', '/').endsWith(file)));
    }

    @Test
    void skipTestsBuildsProjectButDoesNotCompileOrRunSuite() throws Exception {
        var m = manifest();
        m.setSkipTests(true);
        var tools = new NativeBuildTest.FakeTools();
        List<String> output = new ArrayList<>();
        var result = LifecycleExecutor.run(context(m, tools, output), Set.of(Phase.TEST));
        assertTrue(result.success(), result.message());
        assertTrue(compiled(tools, "core.c"));
        assertFalse(compiled(tools, "tests/main.c"));
        assertFalse(tools.commands.stream().anyMatch(c -> c.getFirst().contains("_tests")));
        assertTrue(output.contains("Testes ignorados (skipTests)"));
    }

    @Test
    void excludedTestFilesAreLeftOutOfSuite() throws Exception {
        var m = manifest();
        m.setExcludeTests(List.of("falha.c", "jit/alloc.c", "nada.c"));
        var tools = new NativeBuildTest.FakeTools();
        List<String> output = new ArrayList<>();
        var result = LifecycleExecutor.run(context(m, tools, output), Set.of(Phase.TEST));
        assertTrue(result.success(), result.message());
        assertTrue(compiled(tools, "tests/main.c"));
        assertTrue(compiled(tools, "tests/jit/calls.c"));
        assertFalse(compiled(tools, "tests/falha.c"));
        assertFalse(compiled(tools, "tests/jit/alloc.c"));
        assertTrue(tools.commands.getLast().getFirst().contains("_tests"));
        assertTrue(output.contains("aviso: teste ignorado sem correspondencia em testFolder: nada.c"));
        assertTrue(output.contains("Testes ignorados: 2 arquivo(s)"));
    }

    @Test
    void excludingTestMainFails() throws Exception {
        var m = manifest();
        m.setExcludeTests(List.of("jit", "main.c"));
        var tools = new NativeBuildTest.FakeTools();
        var result = LifecycleExecutor.run(context(m, tools, new ArrayList<>()), Set.of(Phase.TEST));
        assertFalse(result.success());
        assertTrue(result.message().contains("main() dos testes nao pode ser ignorado"), result.message());
    }

    private BuildRequest external(BuildSystem system, ManifestRootModel m, NativeBuildTest.FakeTools tools) {
        return new BuildRequest(project, m, null, system, project.resolve("packages"), project.resolve("build"),
                "Debug", ignored -> {}, 1, List.of(), true, tools);
    }

    @Test
    void externalBackendsReceiveExclusions() {
        var m = new ManifestRootModel();
        m.setExcludeTests(List.of("slow", "net.*"));
        var tools = new NativeBuildTest.FakeTools();
        assertTrue(TestRunner.test(external(BuildSystem.CMAKE, m, tools)).success());
        assertEquals(List.of("ctest", "--test-dir", project.resolve("build").toString(), "--output-on-failure",
                "-E", "slow|net.*"), tools.commands.getLast());
        assertTrue(TestRunner.test(external(BuildSystem.MESON, m, tools)).success());
        assertEquals(List.of("meson", "test", "-C", project.resolve("build").toString(), "--print-errorlogs",
                "--no-suite", "slow", "--no-suite", "net.*"), tools.commands.getLast());
        int count = tools.commands.size();
        var make = TestRunner.test(external(BuildSystem.MAKE, m, tools));
        assertFalse(make.success());
        assertEquals(count, tools.commands.size());
        m.setExcludeTests(List.of());
        m.setSkipTests(true);
        assertTrue(TestRunner.test(external(BuildSystem.CMAKE, m, tools)).success());
        assertEquals(count, tools.commands.size());
    }
}
