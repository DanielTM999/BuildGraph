package dtm.bulder.lifecycle;

import dtm.bulder.build.Artifacts;
import dtm.bulder.build.BuildExecutor;
import dtm.bulder.build.BuildRequest;
import dtm.bulder.build.BuildResult;
import dtm.bulder.build.TestRunner;
import dtm.bulder.manifest.model.LibraryManifest;
import dtm.bulder.manifest.model.ManifestRootModel;
import dtm.bulder.repo.SafeZipExtractor;

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
            if (p.impliesBuild()) {
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
                ctx.buildSystem(), ctx.packagesDir(), ctx.buildDir(), ctx.buildMode(), ctx.output());
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
        String id = idOf(manifest);
        String version = manifest.getVersion();
        boolean library = manifest.isLibrary();
        Path artifact = Artifacts.artifactPath(ctx.projectPath(), manifest, ctx.buildDir(), library);

        Path content = null;
        try {
            content = Files.createTempDirectory("buildgraph-install-");

            LibraryManifest lm = new LibraryManifest();
            lm.setId(id);
            lm.setName(notBlank(manifest.getName()) ? manifest.getName() : id);
            lm.setVersion(version);
            lm.setDescription(manifest.getDescription());
            lm.setKind(LibraryManifest.KIND_SOURCE);

            List<String> includeNames = new ArrayList<>();
            for (String inc : manifest.getIncludePaths()) {
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
            lm.setIncludePaths(includeNames);

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
