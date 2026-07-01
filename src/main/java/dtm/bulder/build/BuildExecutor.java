package dtm.bulder.build;

import dtm.bulder.manifest.model.ManifestRootModel;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class BuildExecutor {

    private BuildExecutor() {
    }

    public static BuildResult build(BuildRequest req) {
        return switch (req.buildSystem()) {
            case CMAKE -> cmake(req);
            case MESON -> meson(req);
            case MAKE -> make(req);
            case MANIFEST, DEFAULT -> directCompile(req);
        };
    }

    private static BuildResult directCompile(BuildRequest req) {
        Consumer<String> out = req.output();
        ManifestRootModel manifest = req.manifest();
        Path projectPath = req.projectPath();

        List<Path> sources = SourceCollector.collectSources(projectPath, manifest);
        if (sources.isEmpty()) {
            return BuildResult.fail(1, "Nenhum arquivo de fonte C/C++ encontrado");
        }
        if (req.toolchain() == null) {
            return BuildResult.fail(1, "Nenhuma toolchain C/C++ encontrada (instale clang ou gcc)");
        }

        boolean cpp = SourceCollector.isCppSources(sources);
        boolean library = manifest != null && manifest.isLibrary();
        Path buildDir = req.buildDir();
        try {
            Files.createDirectories(buildDir);
        } catch (IOException e) {
            return BuildResult.fail(1, "Falha ao criar diretorio de build: " + e.getMessage());
        }

        PackagePaths pkgPaths = PackagePaths.resolve(req.packagesDir());
        Path artifact = Artifacts.artifactPath(projectPath, manifest, buildDir, library);

        CompileSpec spec = new CompileSpec(
                req.toolchain(), cpp, manifest, library, sources, artifact,
                pkgPaths.includeDirs(), pkgPaths.libraryDirs(), pkgPaths.linkLibraries(),
                req.buildMode(), new ArrayList<>(), projectPath);

        List<String> cmd = CompileCommandBuilder.buildCompileCommand(spec);
        out.accept("+ " + String.join(" ", cmd));
        int exit = ProcessRunner.run(cmd, projectPath,
                manifest == null ? null : manifest.getEnv(), out);
        if (exit == 0) {
            return BuildResult.ok(exit, artifact, "Artefato gerado: " + artifact);
        }
        return BuildResult.fail(exit, "Compilacao falhou (exit " + exit + ")");
    }

    private static BuildResult cmake(BuildRequest req) {
        Path buildDir = req.buildDir();
        List<String> configure = List.of("cmake", "-S", ".", "-B", buildDir.toString(),
                "-DCMAKE_BUILD_TYPE=" + req.buildMode());
        req.output().accept("+ " + String.join(" ", configure));
        int c = ProcessRunner.run(configure, req.projectPath(), null, req.output());
        if (c != 0) {
            return BuildResult.fail(c, "cmake configure falhou (exit " + c + ")");
        }
        List<String> build = List.of("cmake", "--build", buildDir.toString());
        req.output().accept("+ " + String.join(" ", build));
        int b = ProcessRunner.run(build, req.projectPath(), null, req.output());
        return b == 0 ? BuildResult.ok(b, buildDir, "CMake build concluido")
                : BuildResult.fail(b, "cmake build falhou (exit " + b + ")");
    }

    private static BuildResult meson(BuildRequest req) {
        Path buildDir = req.buildDir();
        if (!Files.exists(buildDir.resolve("build.ninja"))) {
            List<String> setup = List.of("meson", "setup", buildDir.toString());
            req.output().accept("+ " + String.join(" ", setup));
            int s = ProcessRunner.run(setup, req.projectPath(), null, req.output());
            if (s != 0) {
                return BuildResult.fail(s, "meson setup falhou (exit " + s + ")");
            }
        }
        List<String> ninja = List.of("ninja", "-C", buildDir.toString());
        req.output().accept("+ " + String.join(" ", ninja));
        int n = ProcessRunner.run(ninja, req.projectPath(), null, req.output());
        return n == 0 ? BuildResult.ok(n, buildDir, "Meson build concluido")
                : BuildResult.fail(n, "ninja falhou (exit " + n + ")");
    }

    private static BuildResult make(BuildRequest req) {
        List<String> make = List.of("make");
        req.output().accept("+ " + String.join(" ", make));
        int m = ProcessRunner.run(make, req.projectPath(), null, req.output());
        return m == 0 ? BuildResult.ok(m, req.projectPath(), "make concluido")
                : BuildResult.fail(m, "make falhou (exit " + m + ")");
    }
}
