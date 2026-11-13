package dtm.builder.lifecycle;

import dtm.builder.Main;
import dtm.builder.build.BuildSystem;
import dtm.builder.manifest.ManifestParser;
import dtm.builder.manifest.ManifestProfiles;
import dtm.builder.manifest.model.ManifestRootModel;
import dtm.builder.manifest.model.ManifestTargetModel;
import dtm.builder.placeholder.PlaceholderContext;
import dtm.builder.placeholder.PlaceholderResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LifecycleExecutorTest {

    @TempDir
    Path projectPath;

    @Test
    void directSingleTargetTestsCompileWithoutSeparateBuildPhase() {
        assertFalse(LifecycleExecutor.testRequiresBuild(context(BuildSystem.MANIFEST,
                new ManifestRootModel())));
    }

    @Test
    void externalBuildSystemsAndManifestTargetsStillRequireBuildPhase() {
        assertTrue(LifecycleExecutor.testRequiresBuild(context(BuildSystem.CMAKE,
                new ManifestRootModel())));

        ManifestTargetModel target = new ManifestTargetModel();
        target.setId("core");
        target.setType("static");
        target.setSources(List.of("src"));
        ManifestRootModel manifest = new ManifestRootModel();
        manifest.setTargets(List.of(target));

        assertTrue(LifecycleExecutor.testRequiresBuild(context(BuildSystem.MANIFEST, manifest)));
    }

    @Test
    void selfInvocationTaskRunsCurrentBuildGraphWithActiveProfile() {
        ManifestRootModel raw = ManifestParser.readManifest("""
                {
                  "id": "app",
                  "profiles": { "linux-x86_64-debug": { "buildType": "Debug" } },
                  "tasks": [ { "id": "fixture", "phase": "test", "when": "before",
                               "command": "${buildgraph.command}",
                               "args": ["fixtures/native", "build", "-p", "${profile.name}"] } ]
                }
                """, false).getManifest();
        raw.setActiveProfile("LINUX-x86_64-debug");
        ManifestRootModel manifest = ManifestProfiles.effective(raw);
        var placeholders = new PlaceholderResolver(
                new PlaceholderContext(projectPath, manifest));
        List<List<String>> commands = new ArrayList<>();
        var context = new LifecycleContext(projectPath, manifest, null, BuildSystem.MANIFEST, null,
                projectPath.resolve("packages"), projectPath.resolve("build"), "Debug", placeholders,
                ignored -> { }, ignored -> { }, 0, List.of(), true,
                (command, dir, env, out) -> { commands.add(command); return 0; });

        assertTrue(TaskExecutor.runPhaseTasks(context, Phase.TEST, "before"));

        List<String> expected = new ArrayList<>(TaskExecutor.selfCommand());
        expected.addAll(List.of("fixtures/native", "build", "-p", "linux-x86_64-debug"));
        assertEquals(List.of(expected), commands);
        assertEquals(Main.class.getName(),
                TaskExecutor.selfCommand().getLast());
    }

    private LifecycleContext context(BuildSystem buildSystem, ManifestRootModel manifest) {
        return new LifecycleContext(projectPath, manifest, null, buildSystem, null,
                projectPath.resolve("packages"), projectPath.resolve("build"), "Debug",
                null, ignored -> { }, ignored -> { });
    }
}
