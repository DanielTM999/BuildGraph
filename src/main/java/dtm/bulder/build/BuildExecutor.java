package dtm.bulder.build;

import dtm.bulder.build.graph.ResolvedTarget;
import dtm.bulder.build.graph.TargetGraph;
import dtm.bulder.build.graph.TargetResolution;
import dtm.bulder.build.graph.TargetResolver;
import dtm.bulder.build.graph.TargetScheduler;
import dtm.bulder.manifest.ManifestMerge;
import dtm.bulder.manifest.model.ManifestRootModel;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
        if (req.toolchain() == null) {
            return BuildResult.fail(1, "Nenhuma toolchain C/C++ encontrada (instale clang ou gcc)");
        }

        TargetResolution resolution = TargetResolver.resolve(req.manifest(), req.projectPath(),
                req.toolchain().isMsvc());
        if (!resolution.isOk()) {
            return BuildResult.fail(1, "Targets invalidos: "
                    + String.join("; ", resolution.errors()));
        }

        try {
            Files.createDirectories(req.buildDir());
        } catch (IOException e) {
            return BuildResult.fail(1, "Falha ao criar diretorio de build: " + e.getMessage());
        }

        if (!resolution.multiTarget()) {
            TargetGraph legacy = TargetGraph.of(resolution.targets());
            return compileTarget(req, resolution.targets().get(0), legacy, null, req.output());
        }

        TargetGraph graph = TargetGraph.of(resolution.targets());
        List<String> cycle = graph.cycle();
        if (!cycle.isEmpty()) {
            return BuildResult.fail(1, "Ciclo entre targets: " + String.join(" -> ", cycle));
        }

        if (!req.onlyTargets().isEmpty()) {
            for (String id : req.onlyTargets()) {
                if (graph.target(id.trim()) == null) {
                    return BuildResult.fail(1, "Target desconhecido: " + id.trim());
                }
            }
            graph = graph.subsetWithDependencies(req.onlyTargets());
        }

        Path archiver = null;
        boolean hasStatic = graph.targets().stream()
                .anyMatch(t -> t.type() == TargetType.STATIC);
        if (hasStatic) {
            archiver = ToolchainDetector.resolveArchiver(req.toolchain());
            if (archiver == null) {
                return BuildResult.fail(1, "Nenhum archiver encontrado para targets static"
                        + " (instale llvm-ar, ar ou lib.exe)");
            }
        }

        TargetGraph finalGraph = graph;
        Path finalArchiver = archiver;
        TargetScheduler.Result sched = TargetScheduler.run(graph, req.jobs(),
                target -> compileTarget(req, target, finalGraph, finalArchiver,
                        line -> req.output().accept("[" + target.id() + "] " + line)),
                req.output());

        return summarize(req, sched);
    }

    private static BuildResult summarize(BuildRequest req, TargetScheduler.Result sched) {
        List<String> built = new ArrayList<>();
        List<String> failures = new ArrayList<>();
        for (Map.Entry<String, BuildResult> e : sched.results().entrySet()) {
            if (e.getValue().success()) {
                built.add(e.getKey());
            } else {
                failures.add("[" + e.getKey() + "] " + e.getValue().message());
            }
        }
        if (sched.success()) {
            return BuildResult.ok(0, req.buildDir(),
                    built.size() + " target(s) gerado(s): " + String.join(", ", built));
        }
        String skipped = sched.skipped().isEmpty() ? ""
                : " | nao iniciados: " + String.join(", ", sched.skipped());
        return BuildResult.fail(1, String.join("; ", failures) + skipped);
    }

    private static BuildResult compileTarget(BuildRequest req, ResolvedTarget target,
                                             TargetGraph graph, Path archiver,
                                             Consumer<String> out) {
        ManifestRootModel effective = req.manifest();
        Path projectPath = req.projectPath();
        boolean msvc = req.toolchain().isMsvc();

        List<Path> sources = SourceCollector.collectSources(projectPath,
                target.sourceFolders(), target.synthetic());
        if (sources.isEmpty()) {
            return BuildResult.fail(1, "Nenhum arquivo de fonte C/C++ encontrado");
        }

        ManifestRootModel targetManifest = effective == null
                ? new ManifestRootModel()
                : TargetResolver.perTargetManifest(effective, target);

        PackagePaths pkgPaths = PackagePaths.resolve(req.packagesDir());
        List<Path> extraLibDirs = new ArrayList<>(pkgPaths.libraryDirs());
        List<String> extraLinkLibs = new ArrayList<>(pkgPaths.linkLibraries());
        List<Path> extraSources = new ArrayList<>();

        for (ResolvedTarget dep : transitiveDependencies(graph, target)) {
            targetManifest.setIncludePaths(ManifestMerge.mergeAdditive(
                    targetManifest.getIncludePaths(), dep.includePaths(), null));
            Path depArtifact = Artifacts.artifactPath(req.buildDir(), dep.name(), dep.type(), msvc);
            if (dep.type() == TargetType.SHARED) {
                if (msvc) {
                    out.accept("aviso: link MSVC contra DLL '" + dep.name()
                            + "' requer import lib " + req.buildDir().resolve(dep.name() + ".lib"));
                    extraSources.add(req.buildDir().resolve(dep.name() + ".lib"));
                } else {
                    if (!extraLibDirs.contains(req.buildDir())) {
                        extraLibDirs.add(req.buildDir());
                    }
                    extraLinkLibs.add(dep.name());
                }
            } else if (dep.type() == TargetType.STATIC) {
                if (msvc) {
                    extraSources.add(depArtifact);
                } else {
                    List<String> linkFlags = new ArrayList<>(targetManifest.getLinkFlags());
                    linkFlags.add(depArtifact.toString());
                    targetManifest.setLinkFlags(linkFlags);
                }
            }
        }

        boolean cpp = SourceCollector.isCppSources(sources);
        Path artifact = Artifacts.artifactPath(req.buildDir(), target.name(), target.type(), msvc);

        if (target.type() == TargetType.STATIC) {
            return compileStatic(req, target, targetManifest, sources, cpp, artifact,
                    archiver, out);
        }

        List<Path> allSources = new ArrayList<>(sources);
        allSources.addAll(extraSources);
        CompileSpec spec = new CompileSpec(
                req.toolchain(), cpp, targetManifest, target.type() == TargetType.SHARED,
                allSources, artifact, pkgPaths.includeDirs(), extraLibDirs, extraLinkLibs,
                req.buildMode(), new ArrayList<>(), projectPath);

        List<String> cmd = CompileCommandBuilder.buildCompileCommand(spec);
        out.accept("+ " + String.join(" ", cmd));
        int exit = ProcessRunner.run(cmd, projectPath, targetManifest.getEnv(), out);
        if (exit == 0) {
            return BuildResult.ok(exit, artifact, "Artefato gerado: " + artifact);
        }
        return BuildResult.fail(exit, "Compilacao falhou (exit " + exit + ")");
    }

    private static BuildResult compileStatic(BuildRequest req, ResolvedTarget target,
                                             ManifestRootModel targetManifest, List<Path> sources,
                                             boolean cpp, Path artifact, Path archiver,
                                             Consumer<String> out) {
        boolean msvc = req.toolchain().isMsvc();
        Path objDir = req.buildDir().resolve(".obj").resolve(target.id());
        try {
            Files.createDirectories(objDir);
        } catch (IOException e) {
            return BuildResult.fail(1, "Falha ao criar diretorio de objetos: " + e.getMessage());
        }

        List<Path> objects = new ArrayList<>();
        int i = 0;
        for (Path source : sources) {
            String baseName = source.getFileName().toString();
            Path object = objDir.resolve(i + "_" + baseName + (msvc ? ".obj" : ".o"));
            objects.add(object);
            i++;

            CompileSpec spec = new CompileSpec(
                    req.toolchain(), cpp, targetManifest, false,
                    List.of(source), object, PackagePaths.resolve(req.packagesDir()).includeDirs(),
                    List.of(), List.of(), req.buildMode(), new ArrayList<>(), req.projectPath());
            List<String> cmd = CompileCommandBuilder.buildCompileOnlyCommand(spec);
            out.accept("+ " + String.join(" ", cmd));
            int exit = ProcessRunner.run(cmd, req.projectPath(), targetManifest.getEnv(), out);
            if (exit != 0) {
                return BuildResult.fail(exit, "Compilacao falhou em " + source
                        + " (exit " + exit + ")");
            }
        }

        List<String> archive = ArchiverCommandBuilder.buildArchiveCommand(archiver, artifact,
                objects, msvc);
        out.accept("+ " + String.join(" ", archive));
        int exit = ProcessRunner.run(archive, req.projectPath(), targetManifest.getEnv(), out);
        if (exit == 0) {
            return BuildResult.ok(exit, artifact, "Artefato gerado: " + artifact);
        }
        return BuildResult.fail(exit, "Archiver falhou (exit " + exit + ")");
    }

    private static List<ResolvedTarget> transitiveDependencies(TargetGraph graph,
                                                               ResolvedTarget target) {
        Set<String> seen = new LinkedHashSet<>();
        Deque<String> stack = new ArrayDeque<>(target.dependsOn());
        List<ResolvedTarget> out = new ArrayList<>();
        while (!stack.isEmpty()) {
            String id = stack.pop();
            if (!seen.add(id)) {
                continue;
            }
            ResolvedTarget dep = graph.target(id);
            if (dep != null) {
                out.add(dep);
                dep.dependsOn().forEach(stack::push);
            }
        }
        return out;
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
