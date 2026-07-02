package dtm.bulder.build;

import dtm.bulder.build.graph.ResolvedTarget;
import dtm.bulder.build.graph.TargetResolution;
import dtm.bulder.build.graph.TargetResolver;
import dtm.bulder.manifest.ManifestMerge;
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

        boolean msvc = req.toolchain().isMsvc();
        TargetResolution resolution = TargetResolver.resolve(manifest, projectPath, msvc);
        boolean multiTarget = resolution.multiTarget();

        // No modo multi-target os testes compilam junto apenas as fontes
        // compartilhadas da raiz e linkam contra os artefatos das libs.
        List<Path> sources = new ArrayList<>(testSources);
        List<Path> projectSources = multiTarget
                ? SourceCollector.collectSources(projectPath,
                        manifest == null ? List.of() : manifest.getSourceFolders(), false)
                : SourceCollector.collectSources(projectPath, manifest);
        for (Path src : projectSources) {
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
        List<Path> extraLibDirs = new ArrayList<>(pkgPaths.libraryDirs());
        List<String> extraLinkLibs = new ArrayList<>(pkgPaths.linkLibraries());
        ManifestRootModel testManifest = manifest;

        if (multiTarget && manifest != null) {
            testManifest = copyForTests(manifest);
            for (ResolvedTarget target : resolution.targets()) {
                if (!target.type().isLibrary()) {
                    continue;
                }
                testManifest.setIncludePaths(ManifestMerge.mergeAdditive(
                        testManifest.getIncludePaths(), target.includePaths(), null));
                Path libArtifact = Artifacts.artifactPath(buildDir, target.name(),
                        target.type(), msvc);
                if (target.type() == TargetType.SHARED && !msvc) {
                    if (!extraLibDirs.contains(buildDir)) {
                        extraLibDirs.add(buildDir);
                    }
                    extraLinkLibs.add(target.name());
                } else if (target.type() == TargetType.STATIC && !msvc) {
                    List<String> linkFlags = new ArrayList<>(testManifest.getLinkFlags());
                    linkFlags.add(libArtifact.toString());
                    testManifest.setLinkFlags(linkFlags);
                } else {
                    sources.add(msvc && target.type() == TargetType.SHARED
                            ? buildDir.resolve(target.name() + ".lib") : libArtifact);
                }
            }
        }

        CompileSpec spec = new CompileSpec(
                req.toolchain(), cpp, testManifest, false, sources, artifact,
                pkgPaths.includeDirs(), extraLibDirs, extraLinkLibs,
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

    private static ManifestRootModel copyForTests(ManifestRootModel manifest) {
        ManifestRootModel out = new ManifestRootModel();
        out.setId(manifest.getId());
        out.setName(manifest.getName());
        out.setVersion(manifest.getVersion());
        out.setCompilerVersion(manifest.getCompilerVersion());
        out.setCStandard(manifest.getCStandard());
        out.setCxxStandard(manifest.getCxxStandard());
        out.setPlatform(manifest.getPlatform());
        out.setCCompiler(manifest.getCCompiler());
        out.setCxxCompiler(manifest.getCxxCompiler());
        out.setSysroot(manifest.getSysroot());
        out.setSourceFolders(new ArrayList<>(manifest.getSourceFolders()));
        out.setIncludePaths(new ArrayList<>(manifest.getIncludePaths()));
        out.setDefines(new ArrayList<>(manifest.getDefines()));
        out.setCompileFlags(new ArrayList<>(manifest.getCompileFlags()));
        out.setLinkFlags(new ArrayList<>(manifest.getLinkFlags()));
        out.setLibraryPaths(new ArrayList<>(manifest.getLibraryPaths()));
        out.setEnv(manifest.getEnv());
        out.setProperties(manifest.getProperties());
        return out;
    }
}
