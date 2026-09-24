package dtm.builder.build;

import dtm.builder.build.graph.TargetGraph;
import dtm.builder.build.graph.TargetResolver;
import dtm.builder.build.graph.TargetScheduler;
import dtm.builder.manifest.model.ManifestRootModel;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class BuildExecutor {

    private BuildExecutor() {
    }

    public static BuildResult build(BuildRequest req) {
        if (!req.onlyTargets().isEmpty() && req.buildSystem() != BuildSystem.MANIFEST
                && req.buildSystem() != BuildSystem.DEFAULT)
            return BuildResult.fail(1, "--target requer build direto por manifest; backend " + req.buildSystem());
        return switch (req.buildSystem()) {
            case CMAKE -> cmake(req);
            case MESON -> meson(req);
            case MAKE -> make(req);
            case MANIFEST, DEFAULT -> directCompile(req);
        };
    }

    private static BuildResult directCompile(BuildRequest req) {
        TargetGraph graph;
        try {
            graph = dtm.builder.build.graph.TargetSelection.resolve(req.manifest(), req.projectPath(),
                    req.toolchain() != null && req.toolchain().isMsvc(), req.onlyTargets());
            Files.createDirectories(req.buildDir());
        } catch (IOException | IllegalArgumentException e) {
            return BuildResult.fail(1, e.getMessage());
        }
        int total = graph.targets().stream().mapToInt(target -> {
            ManifestRootModel m = TargetResolver.perTargetManifest(req.manifest(), target);
            boolean singleStep = target.type() == TargetType.OBJECT || target.type() == TargetType.BINARY && "bin".equalsIgnoreCase(m.getAsmFormat());
            return SourceCollector.collectSources(req.projectPath(), target.sources(), target.synthetic()).size() + (singleStep ? 0 : 1);
        }).sum();
        req.output().accept("[0/" + total + "] Iniciando build");
        java.util.concurrent.atomic.AtomicInteger completed = new java.util.concurrent.atomic.AtomicInteger();
        TargetScheduler.Result result = TargetScheduler.run(graph, req.jobs(), target -> {
            BuildResult built = NativeTargetBuilder.build(req, target, graph,
                    line -> req.output().accept("[" + target.id() + "] " + line),
                    (message, started) -> {
                        synchronized (completed) {
                            String elapsed = started == 0 ? "" : String.format(java.util.Locale.ROOT, " (%.3f s)", (System.nanoTime() - started) / 1_000_000_000.0);
                            req.output().accept("[" + completed.incrementAndGet() + "/" + total + "] [" + target.id() + "] " + message + elapsed);
                        }
                    });
            return built;
        }, req.output());
        if (req.manifest() == null || req.manifest().getTargets().isEmpty())
            return result.results().values().iterator().next();
        return summarize(req, result);
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

    static int actionCount(Path projectPath, TargetGraph graph) {
        return graph.targets().stream().mapToInt(target ->
                SourceCollector.collectSources(projectPath, target.sources(), target.synthetic()).size()
                        + (target.type() == TargetType.OBJECT ? 0 : 1)).sum();
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
