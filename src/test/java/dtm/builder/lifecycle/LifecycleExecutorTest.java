package dtm.builder.lifecycle;

import dtm.builder.build.BuildSystem;
import dtm.builder.manifest.model.ManifestRootModel;
import dtm.builder.manifest.model.ManifestTargetModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

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

    private LifecycleContext context(BuildSystem buildSystem, ManifestRootModel manifest) {
        return new LifecycleContext(projectPath, manifest, null, buildSystem, null,
                projectPath.resolve("packages"), projectPath.resolve("build"), "Debug",
                null, ignored -> { }, ignored -> { });
    }
}
