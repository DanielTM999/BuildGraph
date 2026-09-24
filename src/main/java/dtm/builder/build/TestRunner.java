package dtm.builder.build;

import dtm.builder.build.graph.ResolvedTarget;
import dtm.builder.manifest.model.ManifestRootModel;
import dtm.builder.repo.PathSanitizer;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class TestRunner {

    private TestRunner() {
    }

    public static BuildResult test(BuildRequest req) {
        return switch (req.buildSystem()) {
            case CMAKE -> ctest(req);
            case MESON -> mesonTest(req);
            case MAKE -> makeTest(req);
            case MANIFEST, DEFAULT -> directTests(req);
        };
    }

    private static BuildResult ctest(BuildRequest req) {
        List<String> cmd = List.of("ctest", "--test-dir", req.buildDir().toString(),
                "--output-on-failure");
        req.output().accept("+ " + String.join(" ", cmd));
        int e = ProcessRunner.run(cmd, req.projectPath(), null, req.output());
        return e == 0 ? BuildResult.ok(e, null, "Testes OK") : BuildResult.fail(e, "ctest falhou");
    }

    private static BuildResult mesonTest(BuildRequest req) {
        List<String> cmd = List.of("meson", "test", "-C", req.buildDir().toString(),
                "--print-errorlogs");
        req.output().accept("+ " + String.join(" ", cmd));
        int e = ProcessRunner.run(cmd, req.projectPath(), null, req.output());
        return e == 0 ? BuildResult.ok(e, null, "Testes OK") : BuildResult.fail(e, "meson test falhou");
    }

    private static BuildResult makeTest(BuildRequest req) {
        List<String> cmd = List.of("make", "test");
        req.output().accept("+ " + String.join(" ", cmd));
        int e = ProcessRunner.run(cmd, req.projectPath(), null, req.output());
        return e == 0 ? BuildResult.ok(e, null, "Testes OK") : BuildResult.fail(e, "make test falhou");
    }

    private static BuildResult directTests(BuildRequest req) {
        ManifestRootModel manifest = req.manifest() == null ? new ManifestRootModel() : req.manifest();
        List<Path> testSources = SourceCollector.collectTestSources(req.projectPath(), manifest);
        if (testSources.isEmpty()) return BuildResult.ok(0, null, "Nenhum teste encontrado");
        TestMainResolver.Result main = TestMainResolver.resolve(req.projectPath(), manifest, testSources);
        if (!main.success()) return BuildResult.fail(1, main.error());
        boolean msvc = req.toolchain() != null && req.toolchain().isMsvc();
        dtm.builder.build.graph.TargetGraph selected;
        try {
            selected = dtm.builder.build.graph.TargetSelection.resolve(manifest, req.projectPath(), msvc, req.onlyTargets());
        } catch (IllegalArgumentException e) {
            return BuildResult.fail(1, e.getMessage());
        }
        List<Path> sources = new ArrayList<>(testSources);
        boolean multi = !manifest.getTargets().isEmpty();
        for (Path source : SourceCollector.collectSources(req.projectPath(), manifest.getSources(), !multi))
            if (!source.getFileName().toString().toLowerCase().startsWith("main.")) sources.add(source);
        String base = PathSanitizer.sanitizePackageFolderName(Artifacts.baseName(req.projectPath(), manifest)) + "_tests";
        List<ResolvedTarget> allTargets = dtm.builder.build.graph.TargetResolver.resolve(manifest, req.projectPath(), msvc).targets();
        java.util.Set<String> ids = new java.util.HashSet<>();
        java.util.Set<Path> outputs = new java.util.HashSet<>();
        for (ResolvedTarget existing : allTargets) {
            ids.add(existing.id());
            for (Path path : NativeArtifacts.declaredOutputs(req.buildDir(), manifest, existing, msvc)) outputs.add(path.toAbsolutePath().normalize());
        }
        TargetPlatform testPlatform = TargetPlatform.resolve(manifest.getPlatform(), null);
        while (ids.contains(base) || outputs.contains(req.buildDir().resolve(testPlatform.fileName(base, TargetType.EXECUTABLE, msvc)).toAbsolutePath().normalize()))
            base += "_";
        String id = base;
        var options = new dtm.builder.manifest.model.ManifestTargetModel();
        options.setOutputName(TargetPlatform.resolve(manifest.getPlatform(), null).fileName(base, TargetType.EXECUTABLE, msvc));
        options.setAsmFormat("auto");
        List<String> dependencies = multi ? selected.targets().stream()
                .filter(t -> t.type().isLibrary() || t.type() == TargetType.OBJECT).map(ResolvedTarget::id).toList() : List.of();
        var suite = new ResolvedTarget(id, base, TargetType.EXECUTABLE,
                sources.stream().distinct().map(Path::toString).toList(), manifest.getIncludes(), manifest.getDefines(),
                manifest.getCompileFlags(), manifest.getLinkFlags(), manifest.getLibraryPaths(), dependencies, false, options);
        List<ResolvedTarget> targets = new ArrayList<>(selected.targets());
        targets.add(suite);
        var graph = dtm.builder.build.graph.TargetGraph.of(targets);
        BuildResult compiled = NativeTargetBuilder.build(req, suite, graph, req.output());
        if (!compiled.success()) return compiled;
        TargetPlatform platform = TargetPlatform.resolve(manifest.getPlatform(), null);
        if (!platform.nativeDestination()) return BuildResult.fail(1, "Testes gerados para " + platform.triple()
                + "; execucao cruzada requer um runner externo");
        Path artifact = NativeArtifacts.artifact(req.buildDir(), manifest, suite, msvc);
        req.output().accept("+ " + artifact);
        int exit = req.executor().run(List.of(artifact.toString()), req.projectPath(), manifest.getEnv(), req.output());
        return exit == 0 ? BuildResult.ok(0, artifact, "Testes OK") : BuildResult.fail(exit, "Testes falharam");
    }

}
