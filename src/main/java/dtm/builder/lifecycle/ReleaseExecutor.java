package dtm.builder.lifecycle;

import dtm.builder.build.BuildDirectoryLock;
import dtm.builder.build.BuildOptions;
import dtm.builder.build.BuildSystem;
import dtm.builder.build.NativeArtifacts;
import dtm.builder.build.TargetType;
import dtm.builder.build.graph.TargetSelection;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.List;

public final class ReleaseExecutor {
    private ReleaseExecutor() { }

    public static LifecycleResult run(LifecycleContext ctx, Set<Phase> requested) {
        Set<Path> required = new HashSet<>();
        Set<Path> deliverables = new HashSet<>();
        ReleaseCleanup cleanup;
        try {
            if (ctx.buildSystem() != BuildSystem.MANIFEST && ctx.buildSystem() != BuildSystem.DEFAULT)
                return LifecycleResult.fail("--release requer build direto por manifest ou descoberta de fontes; backend "
                        + ctx.buildSystem());
            validateBuildDirectory(ctx);
            cleanup = new ReleaseCleanup(ctx.manifest() == null ? List.of() : ctx.manifest().getReleaseCleanup());
            boolean msvc = ctx.toolchain() != null && ctx.toolchain().isMsvc();
            var graph = TargetSelection.resolve(ctx.manifest(), ctx.projectPath(), msvc, ctx.onlyTargets());
            Set<String> dependencies = new HashSet<>();
            graph.targets().forEach(target -> dependencies.addAll(target.dependsOn()));
            for (var target : graph.targets()) {
                if (target.type() == TargetType.OBJECT && dependencies.contains(target.id())
                        && !ctx.onlyTargets().contains(target.id())) continue;
                Path artifact = NativeArtifacts.artifact(ctx.buildDir(), ctx.manifest(), target, msvc)
                        .toAbsolutePath().normalize();
                required.add(artifact);
                deliverables.add(artifact);
                if (target.type() == TargetType.SHARED) {
                    for (Path output : NativeArtifacts.declaredOutputs(ctx.buildDir(), ctx.manifest(), target, msvc))
                        if (output.getFileName().toString().endsWith(".lib"))
                            deliverables.add(output.toAbsolutePath().normalize());
                }
            }
        } catch (IOException | IllegalArgumentException e) {
            return LifecycleResult.fail("Release: " + e.getMessage());
        }

        BuildOptions options = ctx.options();
        var uncached = new BuildOptions(options.memoryLimit(), options.memoryPerJob(), options.cacheDirectory(),
                options.cacheSize(), false, options.profile(), options.memoryReserve());
        var fresh = new LifecycleContext(ctx.projectPath(), ctx.manifest(), ctx.toolchain(), ctx.buildSystem(),
                ctx.repo(), ctx.packagesDir(), ctx.buildDir(), ctx.buildMode(), ctx.placeholders(),
                ctx.output(), ctx.info(), ctx.jobs(), ctx.onlyTargets(), false, ctx.executor(), uncached);
        EnumSet<Phase> phases = EnumSet.of(Phase.CLEAN, Phase.BUILD, Phase.TEST);
        phases.addAll(requested);

        try (var lock = BuildDirectoryLock.acquire(ctx.buildDir())) {
            LifecycleResult result = LifecycleResult.fail("Release interrompida");
            try {
                prune(ctx.buildDir(), ctx.projectPath(), Set.of());
                result = LifecycleExecutor.run(fresh, phases);
                if (result.success()) {
                    for (Path artifact : required)
                        if (!Files.isRegularFile(artifact, LinkOption.NOFOLLOW_LINKS))
                            throw new IOException("Artefato ausente: " + artifact);
                }
            } catch (IOException | RuntimeException e) {
                result = LifecycleResult.fail("Release: " + e.getMessage());
            } finally {
                try {
                    cleanCompilation(ctx.buildDir(), ctx.projectPath(), result.success() ? deliverables : Set.of());
                    if (result.success()) cleanup.clean(ctx.buildDir());
                } catch (IOException e) {
                    result = LifecycleResult.fail(result.message() + "; limpeza da release falhou: " + e.getMessage());
                }
            }
            if (result.success()) ctx.info().accept("Release pronta: artefatos e recursos preservados; intermediarios removidos de " + ctx.buildDir());
            return result;
        } catch (IOException e) {
            return LifecycleResult.fail("Release: " + e.getMessage());
        }
    }

    private static void validateBuildDirectory(LifecycleContext ctx) throws IOException {
        Path build = ctx.buildDir().toFile().getCanonicalFile().toPath();
        Path project = ctx.projectPath().toRealPath();
        if (project.startsWith(build))
            throw new IOException("outputDir nao pode ser o projeto nem um diretorio ancestral: " + build);
        rejectSymbolicParents(ctx.buildDir(), ctx.projectPath());
    }

    private static void cleanCompilation(Path directory, Path project, Set<Path> keep) throws IOException {
        Path root = directory.toAbsolutePath().normalize();
        rejectSymbolicParents(root, project);
        for (Path output : NativeArtifacts.recordedOutputs(root)) {
            if (keep.contains(output)) continue;
            rejectSymbolicParents(output.getParent(), project);
            Files.deleteIfExists(output);
        }
        for (String name : new String[] {".obj", ".buildgraph-state", ".buildgraph-outputs", ".clangd"})
            deleteTree(root.resolve(name));
        if (Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
            try (var files = Files.list(root)) {
                for (Path file : files.filter(path -> path.getFileName().toString().startsWith(".buildgraph-args-")
                        && path.getFileName().toString().endsWith(".rsp")).toList())
                    Files.delete(file);
            }
        }
    }

    private static void deleteTree(Path path) throws IOException {
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return;
        Files.walkFileTree(path, new SimpleFileVisitor<>() {
            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override public FileVisitResult postVisitDirectory(Path dir, IOException error) throws IOException {
                if (error != null) throw error;
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static void rejectSymbolicParents(Path directory, Path project) throws IOException {
        Path root = directory.toAbsolutePath().normalize();
        Path boundary = project.toAbsolutePath().normalize();
        while (boundary != null && !root.startsWith(boundary)) boundary = boundary.getParent();
        for (Path path = root; path != null && !path.equals(boundary); path = path.getParent())
            if (Files.isSymbolicLink(path))
                throw new IOException("Release recusou diretorio simbolico: " + path);
    }

    private static void prune(Path directory, Path project, Set<Path> keep) throws IOException {
        Path root = directory.toAbsolutePath().normalize();
        rejectSymbolicParents(root, project);
        if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) return;
        Set<Path> keptDirectories = new HashSet<>();
        keptDirectories.add(root);
        for (Path file : keep)
            for (Path parent = file.getParent(); parent != null && parent.startsWith(root); parent = parent.getParent())
                keptDirectories.add(parent);
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (!keep.contains(file) || attrs.isSymbolicLink()) Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override public FileVisitResult postVisitDirectory(Path dir, IOException error) throws IOException {
                if (error != null) throw error;
                if (!keptDirectories.contains(dir)) Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
