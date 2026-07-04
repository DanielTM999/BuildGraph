package dtm.builder.build;

import dtm.builder.build.graph.ResolvedTarget;
import dtm.builder.build.graph.TargetGraph;
import dtm.builder.build.graph.TargetResolution;
import dtm.builder.build.graph.TargetResolver;
import dtm.builder.build.graph.TargetScheduler;
import dtm.builder.build.incremental.DepFileParser;
import dtm.builder.build.incremental.IncrementalBuildService;
import dtm.builder.build.incremental.MsvcIncludeParser;
import dtm.builder.build.incremental.TargetState;
import dtm.builder.manifest.ManifestMerge;
import dtm.builder.manifest.model.ManifestRootModel;

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

        TargetGraph graph = TargetGraph.of(resolution.targets());
        List<String> cycle = graph.cycle();
        if (!cycle.isEmpty()) {
            return BuildResult.fail(1, "Ciclo entre targets: " + String.join(" -> ", cycle));
        }

        if (resolution.multiTarget() && !req.onlyTargets().isEmpty()) {
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

        ActionProgress progress = new ActionProgress(actionCount(req.projectPath(), graph),
                graph.size() > 1, req.output());
        progress.start();

        IncrementalBuildService incremental = new IncrementalBuildService(req.buildDir(),
                req.toolchain(), req.buildMode(), req.incremental());

        TargetGraph finalGraph = graph;
        Path finalArchiver = archiver;
        if (!resolution.multiTarget()) {
            ResolvedTarget target = graph.targets().iterator().next();
            return compileTarget(req, target, graph, archiver, req.output(), progress,
                    incremental);
        }

        TargetScheduler.Result sched = TargetScheduler.run(graph, req.jobs(),
                target -> compileTarget(req, target, finalGraph, finalArchiver,
                        line -> req.output().accept("[" + target.id() + "] " + line), progress,
                        incremental),
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
                                             Consumer<String> out, ActionProgress progress,
                                             IncrementalBuildService incremental) {
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
        List<Path> dependencyArtifacts = new ArrayList<>();

        for (ResolvedTarget dep : transitiveDependencies(graph, target)) {
            targetManifest.setIncludePaths(ManifestMerge.mergeAdditive(
                    targetManifest.getIncludePaths(), dep.includePaths(), null));
            Path depArtifact = Artifacts.artifactPath(req.buildDir(), dep.name(), dep.type(), msvc);
            dependencyArtifacts.add(depArtifact);
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
        Path objDir = req.buildDir().resolve(".obj").resolve(target.id());
        try {
            Files.createDirectories(objDir);
        } catch (IOException e) {
            return BuildResult.fail(1, "Falha ao criar diretorio de objetos: " + e.getMessage());
        }

        TargetState state = incremental.begin(target.id());
        try {
            List<Path> objects = new ArrayList<>();
            int i = 0;
            for (Path source : sources) {
                String baseName = source.getFileName().toString();
                Path object = objDir.resolve(i + "_" + baseName + (msvc ? ".obj" : ".o"));
                objects.add(object);
                i++;

                Path depFile = object.resolveSibling(object.getFileName() + ".d");
                CompileSpec spec = new CompileSpec(
                        req.toolchain(), SourceCollector.isCppSources(List.of(source)),
                        targetManifest, target.type() == TargetType.SHARED, List.of(source),
                        object, pkgPaths.includeDirs(),
                        List.of(), List.of(), req.buildMode(), new ArrayList<>(),
                        req.projectPath(), depFile);
                List<String> cmd = CompileCommandBuilder.buildCompileOnlyCommand(spec);
                if (incremental.isObjectUpToDate(state, source, object, cmd)) {
                    progress.upToDateSource(target, req.projectPath(), source);
                    continue;
                }
                out.accept("+ " + String.join(" ", cmd));
                MsvcIncludeParser includeParser = msvc ? new MsvcIncludeParser() : null;
                Consumer<String> compileOut = includeParser == null ? out
                        : line -> {
                            if (!includeParser.offer(line)) {
                                out.accept(line);
                            }
                        };
                long started = System.nanoTime();
                int exit = req.executor().run(cmd, req.projectPath(), targetManifest.getEnv(),
                        compileOut);
                progress.compiled(target, req.projectPath(), source, started, exit == 0);
                if (exit != 0) {
                    return BuildResult.fail(exit, "Compilacao falhou em " + source
                            + " (exit " + exit + ")");
                }
                List<Path> deps;
                if (msvc) {
                    deps = includeParser.prefix() == null ? null : includeParser.includes();
                } else {
                    deps = DepFileParser.parseSafe(depFile, req.projectPath());
                }
                incremental.recordCompiled(state, source, object, cmd, deps);
            }

            if (target.type() != TargetType.STATIC) {
                List<Path> linkInputs = new ArrayList<>(objects);
                linkInputs.addAll(extraSources);
                List<Path> trackedLinkInputs = new ArrayList<>(linkInputs);
                for (Path dependencyArtifact : dependencyArtifacts) {
                    if (!trackedLinkInputs.contains(dependencyArtifact)) {
                        trackedLinkInputs.add(dependencyArtifact);
                    }
                }
                CompileSpec spec = new CompileSpec(
                        req.toolchain(), cpp, targetManifest, target.type() == TargetType.SHARED,
                        linkInputs, artifact, pkgPaths.includeDirs(), extraLibDirs, extraLinkLibs,
                        req.buildMode(), new ArrayList<>(), projectPath);
                List<String> link = CompileCommandBuilder.buildLinkCommand(spec);
                if (!incremental.needsLink(state, link, artifact, trackedLinkInputs)) {
                    progress.upToDateArtifact(target, artifact);
                    return BuildResult.ok(0, artifact, "Artefato atualizado: " + artifact);
                }
                out.accept("+ " + String.join(" ", link));
                long started = System.nanoTime();
                int exit = req.executor().run(link, projectPath, targetManifest.getEnv(), out);
                progress.linked(target, artifact, started, exit == 0);
                if (exit == 0) {
                    incremental.recordLinked(state, link, trackedLinkInputs);
                    return BuildResult.ok(exit, artifact, "Artefato gerado: " + artifact);
                }
                incremental.recordLinkFailed(state);
                return BuildResult.fail(exit, "Link falhou (exit " + exit + ")");
            }

            List<String> archive = ArchiverCommandBuilder.buildArchiveCommand(archiver, artifact,
                    objects, msvc);
            if (!incremental.needsLink(state, archive, artifact, objects)) {
                progress.upToDateArtifact(target, artifact);
                return BuildResult.ok(0, artifact, "Artefato atualizado: " + artifact);
            }
            out.accept("+ " + String.join(" ", archive));
            long started = System.nanoTime();
            int exit = req.executor().run(archive, req.projectPath(), targetManifest.getEnv(),
                    out);
            progress.archived(target, artifact, started, exit == 0);
            if (exit == 0) {
                incremental.recordLinked(state, archive, objects);
                return BuildResult.ok(exit, artifact, "Artefato gerado: " + artifact);
            }
            incremental.recordLinkFailed(state);
            return BuildResult.fail(exit, "Archiver falhou (exit " + exit + ")");
        } finally {
            incremental.finish(state, out);
        }
    }

    static int actionCount(Path projectPath, TargetGraph graph) {
        int total = 0;
        for (ResolvedTarget target : graph.targets()) {
            total += SourceCollector.collectSources(projectPath, target.sourceFolders(),
                    target.synthetic()).size() + 1;
        }
        return Math.max(1, total);
    }

    private static final class ActionProgress {

        private final int total;
        private final boolean showTarget;
        private final Consumer<String> output;
        private int completed;

        private ActionProgress(int total, boolean showTarget, Consumer<String> output) {
            this.total = total;
            this.showTarget = showTarget;
            this.output = output;
        }

        private synchronized void start() {
            emit("[0/" + total + "] Iniciando build");
        }

        private synchronized void compiled(ResolvedTarget target, Path project, Path source,
                                           long started, boolean success) {
            String path;
            try {
                path = project.toAbsolutePath().normalize()
                        .relativize(source.toAbsolutePath().normalize()).toString();
            } catch (IllegalArgumentException e) {
                path = source.toString();
            }
            completed(success ? "Compilado " + path : "Falhou ao compilar " + path,
                    target, started);
        }

        private synchronized void linked(ResolvedTarget target, Path artifact,
                                         long started, boolean success) {
            completed(success ? "Link concluido: " + artifact.getFileName()
                    : "Link falhou: " + artifact.getFileName(), target, started);
        }

        private synchronized void archived(ResolvedTarget target, Path artifact,
                                           long started, boolean success) {
            completed(success ? "Archive concluido: " + artifact.getFileName()
                    : "Archive falhou: " + artifact.getFileName(), target, started);
        }

        private synchronized void upToDateSource(ResolvedTarget target, Path project,
                                                 Path source) {
            String path;
            try {
                path = project.toAbsolutePath().normalize()
                        .relativize(source.toAbsolutePath().normalize()).toString();
            } catch (IllegalArgumentException e) {
                path = source.toString();
            }
            completedInstant("Sem mudancas: " + path, target);
        }

        private synchronized void upToDateArtifact(ResolvedTarget target, Path artifact) {
            completedInstant("Artefato ja atualizado: " + artifact.getFileName(), target);
        }

        private void completedInstant(String message, ResolvedTarget target) {
            completed++;
            String targetLabel = showTarget ? "[" + target.id() + "] " : "";
            emit("[" + completed + "/" + total + "] " + targetLabel + message);
        }

        private void completed(String message, ResolvedTarget target, long started) {
            completed++;
            String targetLabel = showTarget ? "[" + target.id() + "] " : "";
            emit("[" + completed + "/" + total + "] " + targetLabel + message
                    + " (" + elapsed(started) + ")");
        }

        private String elapsed(long started) {
            double seconds = (System.nanoTime() - started) / 1_000_000_000.0;
            return String.format(java.util.Locale.ROOT, "%.3f s", seconds);
        }

        private void emit(String message) {
            if (output != null) {
                output.accept(message);
            }
        }
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
        progress(req, 0, 2, "Iniciando build");
        List<String> configure = List.of("cmake", "-S", ".", "-B", buildDir.toString(),
                "-DCMAKE_BUILD_TYPE=" + req.buildMode());
        req.output().accept("+ " + String.join(" ", configure));
        int c = req.executor().run(configure, req.projectPath(), null, req.output());
        progress(req, 1, 2, c == 0 ? "CMake configurado" : "CMake configure falhou");
        if (c != 0) {
            return BuildResult.fail(c, "cmake configure falhou (exit " + c + ")");
        }
        List<String> build = List.of("cmake", "--build", buildDir.toString());
        req.output().accept("+ " + String.join(" ", build));
        int b = req.executor().run(build, req.projectPath(), null, req.output());
        progress(req, 2, 2, b == 0 ? "CMake build concluido" : "CMake build falhou");
        return b == 0 ? BuildResult.ok(b, buildDir, "CMake build concluido")
                : BuildResult.fail(b, "cmake build falhou (exit " + b + ")");
    }

    private static BuildResult meson(BuildRequest req) {
        Path buildDir = req.buildDir();
        boolean needsSetup = !Files.exists(buildDir.resolve("build.ninja"));
        int total = needsSetup ? 2 : 1;
        progress(req, 0, total, "Iniciando build");
        if (needsSetup) {
            List<String> setup = List.of("meson", "setup", buildDir.toString());
            req.output().accept("+ " + String.join(" ", setup));
            int s = req.executor().run(setup, req.projectPath(), null, req.output());
            progress(req, 1, total, s == 0 ? "Meson configurado" : "Meson setup falhou");
            if (s != 0) {
                return BuildResult.fail(s, "meson setup falhou (exit " + s + ")");
            }
        }
        List<String> ninja = List.of("ninja", "-C", buildDir.toString());
        req.output().accept("+ " + String.join(" ", ninja));
        int n = req.executor().run(ninja, req.projectPath(), null, req.output());
        progress(req, total, total, n == 0 ? "Ninja concluido" : "Ninja falhou");
        return n == 0 ? BuildResult.ok(n, buildDir, "Meson build concluido")
                : BuildResult.fail(n, "ninja falhou (exit " + n + ")");
    }

    private static BuildResult make(BuildRequest req) {
        progress(req, 0, 1, "Iniciando build");
        List<String> make = List.of("make");
        req.output().accept("+ " + String.join(" ", make));
        int m = req.executor().run(make, req.projectPath(), null, req.output());
        progress(req, 1, 1, m == 0 ? "Make concluido" : "Make falhou");
        return m == 0 ? BuildResult.ok(m, req.projectPath(), "make concluido")
                : BuildResult.fail(m, "make falhou (exit " + m + ")");
    }

    private static void progress(BuildRequest req, int current, int total, String message) {
        if (req.output() != null) {
            req.output().accept("[" + current + "/" + total + "] " + message);
        }
    }
}
