package dtm.bulder.build;

import dtm.bulder.manifest.model.ManifestRootModel;
import dtm.bulder.repo.PathSanitizer;

import java.io.IOException;
import java.nio.file.Files;
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
        Path projectPath = req.projectPath();
        ManifestRootModel manifest = req.manifest();

        List<Path> testSources = SourceCollector.collectTestSources(projectPath);
        if (testSources.isEmpty()) {
            return BuildResult.ok(0, null, "Nenhum teste encontrado");
        }
        if (req.toolchain() == null) {
            return BuildResult.fail(1, "Nenhuma toolchain C/C++ encontrada");
        }

        List<Path> sources = new ArrayList<>(testSources);
        for (Path src : SourceCollector.collectSources(projectPath, manifest)) {
            String name = src.getFileName().toString().toLowerCase();
            if (!name.startsWith("main.")) {
                sources.add(src);
            }
        }

        boolean cpp = SourceCollector.isCppSources(sources);
        Path buildDir = req.buildDir();
        try {
            Files.createDirectories(buildDir);
        } catch (IOException e) {
            return BuildResult.fail(1, "Falha ao criar diretorio de build: " + e.getMessage());
        }

        String base = PathSanitizer.sanitizePackageFolderName(
                Artifacts.baseName(projectPath, manifest)) + "_tests";
        Path artifact = buildDir.resolve(ToolProbe.isWindows() ? base + ".exe" : base);

        PackagePaths pkgPaths = PackagePaths.resolve(req.packagesDir());
        CompileSpec spec = new CompileSpec(
                req.toolchain(), cpp, manifest, false, sources, artifact,
                pkgPaths.includeDirs(), pkgPaths.libraryDirs(), pkgPaths.linkLibraries(),
                req.buildMode(), new ArrayList<>(), projectPath);

        List<String> compile = CompileCommandBuilder.buildCompileCommand(spec);
        req.output().accept("+ " + String.join(" ", compile));
        int compiled = ProcessRunner.run(compile, projectPath,
                manifest == null ? null : manifest.getEnv(), req.output());
        if (compiled != 0) {
            return BuildResult.fail(compiled, "Compilacao dos testes falhou (exit " + compiled + ")");
        }

        req.output().accept("+ " + artifact);
        int ran = ProcessRunner.run(List.of(artifact.toString()), projectPath,
                manifest == null ? null : manifest.getEnv(), req.output());
        return ran == 0 ? BuildResult.ok(ran, artifact, "Testes OK")
                : BuildResult.fail(ran, "Testes falharam (exit " + ran + ")");
    }
}
