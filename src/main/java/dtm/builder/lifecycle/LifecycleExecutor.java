package dtm.builder.lifecycle;

import dtm.builder.build.Artifacts;
import dtm.builder.build.BuildExecutor;
import dtm.builder.build.BuildRequest;
import dtm.builder.build.BuildResult;
import dtm.builder.build.TestRunner;
import dtm.builder.build.graph.ResolvedTarget;
import dtm.builder.build.graph.TargetResolution;
import dtm.builder.build.graph.TargetResolver;
import dtm.builder.manifest.model.LibraryManifest;
import dtm.builder.manifest.model.ManifestRootModel;
import dtm.builder.repo.SafeZipExtractor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public final class LifecycleExecutor {

    private LifecycleExecutor() {
    }

    public static LifecycleResult run(LifecycleContext ctx, Set<Phase> requested) {
        EnumSet<Phase> phases = EnumSet.noneOf(Phase.class);
        phases.addAll(requested);
        for (Phase p : requested) {
            if (p.impliesBuild() || (p == Phase.TEST && testRequiresBuild(ctx))) {
                phases.add(Phase.BUILD);
            }
        }

        for (Phase phase : Phase.values()) {
            if (!phases.contains(phase)) {
                continue;
            }
            ctx.info().accept("");
            ctx.info().accept("--- " + phase.name().toLowerCase() + " ---");

            if (!TaskExecutor.runPhaseTasks(ctx, phase, "before")) {
                return LifecycleResult.fail("Task 'before' da fase " + phase + " falhou");
            }

            LifecycleResult step = runStep(ctx, phase);
            if (!step.success()) {
                return step;
            }

            if (!TaskExecutor.runPhaseTasks(ctx, phase, "after")) {
                return LifecycleResult.fail("Task 'after' da fase " + phase + " falhou");
            }
        }
        return LifecycleResult.ok("Lifecycle concluido");
    }

    static boolean testRequiresBuild(LifecycleContext ctx) {
        return switch (ctx.buildSystem()) {
            case CMAKE, MESON, MAKE -> true;
            case MANIFEST, DEFAULT -> TargetResolver.resolve(ctx.manifest(), ctx.projectPath(),
                    ctx.toolchain() != null && ctx.toolchain().isMsvc()).multiTarget();
        };
    }

    private static LifecycleResult runStep(LifecycleContext ctx, Phase phase) {
        return switch (phase) {
            case CLEAN -> clean(ctx);
            case BUILD -> toResult(BuildExecutor.build(request(ctx)), "Build");
            case TEST -> toResult(TestRunner.test(request(ctx)), "Test");
            case INSTALL -> install(ctx);
        };
    }

    private static BuildRequest request(LifecycleContext ctx) {
        return new BuildRequest(ctx.projectPath(), ctx.manifest(), ctx.toolchain(),
                ctx.buildSystem(), ctx.packagesDir(), ctx.buildDir(), ctx.buildMode(),
                ctx.output(), ctx.jobs(), ctx.onlyTargets(), ctx.incremental(), null);
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static LifecycleResult toResult(BuildResult r, String label) {
        return r.success()
                ? LifecycleResult.ok(label + ": " + r.message())
                : LifecycleResult.fail(label + ": " + r.message());
    }

    private static LifecycleResult clean(LifecycleContext ctx) {
        try {
            SafeZipExtractor.deleteTree(ctx.buildDir());
            ctx.info().accept("Removido diretorio de build: " + ctx.buildDir());
            return LifecycleResult.ok("Clean concluido");
        } catch (IOException e) {
            return LifecycleResult.fail("Clean falhou: " + e.getMessage());
        }
    }

    private static LifecycleResult install(LifecycleContext ctx) {
        ManifestRootModel manifest = ctx.manifest();
        if (manifest == null || !notBlank(idOf(manifest)) || !notBlank(manifest.getVersion())) {
            return LifecycleResult.fail("Install requer 'id' (ou 'name') e 'version' no manifest");
        }
        String projectId = idOf(manifest);
        String version = manifest.getVersion();
        boolean msvc = ctx.toolchain() != null && ctx.toolchain().isMsvc();

        TargetResolution resolution = TargetResolver.resolve(manifest, ctx.projectPath(), msvc);
        if (!resolution.multiTarget()) {
            Path artifact = Artifacts.artifactPath(ctx.projectPath(), manifest, ctx.buildDir(),
                    manifest.isLibrary());
            return publish(ctx, projectId,
                    notBlank(manifest.getName()) ? manifest.getName() : projectId,
                    version, manifest.getDescription(), manifest.getIncludes(), artifact);
        }

        List<String> published = new ArrayList<>();
        for (ResolvedTarget target : resolution.targets()) {
            if (!target.type().isLibrary()) {
                continue;
            }
            String packageId = projectId + "-" + target.id();
            Path artifact = Artifacts.artifactPath(ctx.buildDir(), target.name(),
                    target.type(), msvc);
            LifecycleResult one = publish(ctx, packageId, target.name(), version,
                    manifest.getDescription(), target.includes(), artifact);
            if (!one.success()) {
                return one;
            }
            published.add(packageId);
        }
        if (published.isEmpty()) {
            return LifecycleResult.ok("Install: nenhum target de biblioteca para publicar");
        }
        return LifecycleResult.ok("Install concluido (" + String.join(", ", published)
                + " : " + version + ")");
    }

    private static LifecycleResult publish(LifecycleContext ctx, String id, String name,
                                           String version, String description,
                                           List<String> includes, Path artifact) {
        Path content = null;
        try {
            content = Files.createTempDirectory("buildgraph-install-");

            LibraryManifest lm = new LibraryManifest();
            lm.setId(id);
            lm.setName(notBlank(name) ? name : id);
            lm.setVersion(version);
            lm.setDescription(description);
            lm.setKind(LibraryManifest.KIND_SOURCE);
            lm.setDependencies(new ArrayList<>(ctx.manifest().getPackages()));

            List<String> includeNames = new ArrayList<>();
            for (String inc : includes) {
                Path src = ctx.projectPath().resolve(inc).normalize();
                if (!Files.exists(src)) {
                    continue;
                }
                String base = src.getFileName().toString();
                Path dest = content.resolve(base);
                if (Files.isDirectory(src)) {
                    SafeZipExtractor.copyTree(src, dest);
                } else {
                    Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING);
                }
                includeNames.add(base);
            }
            lm.setIncludes(includeNames);

            if (Files.isRegularFile(artifact)) {
                Path bin = content.resolve("bin");
                Files.createDirectories(bin);
                Files.copy(artifact, bin.resolve(artifact.getFileName()),
                        StandardCopyOption.REPLACE_EXISTING);
            }

            Path variantDir = ctx.repo().publish(id, version, content, lm);
            ctx.info().accept("Instalado no repo global: " + variantDir);
            return LifecycleResult.ok("Install concluido (" + id + ":" + version + ")");
        } catch (IOException e) {
            return LifecycleResult.fail("Install falhou: " + e.getMessage());
        } finally {
            if (content != null) {
                try {
                    SafeZipExtractor.deleteTree(content);
                } catch (IOException ignored) {

                }
            }
        }
    }

    private static String idOf(ManifestRootModel manifest) {
        if (notBlank(manifest.getId())) {
            return manifest.getId();
        }
        return manifest.getName();
    }
}
