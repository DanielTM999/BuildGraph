package dtm.builder.build;

import dtm.builder.build.cache.PreprocessedObjectCache;
import dtm.builder.build.graph.ResolvedTarget;
import dtm.builder.build.graph.TargetGraph;
import dtm.builder.build.graph.TargetResolver;
import dtm.builder.build.incremental.DepFileParser;
import dtm.builder.build.incremental.FileHasher;
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
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public final class NativeTargetBuilder {
    private NativeTargetBuilder() { }

    public static BuildResult build(BuildRequest req, ResolvedTarget target, TargetGraph graph,
                                    Consumer<String> out) {
        return build(req, target, graph, out, (message, started) -> out.accept(message));
    }

    public static BuildResult build(BuildRequest req, ResolvedTarget target, TargetGraph graph,
                                    Consumer<String> out, BiConsumer<String, Long> progress) {
        try {
            try (BuildRuntime runtime = new BuildRuntime(req)) {
                return execute(req, target, graph, runtime.serialize(out), runtime.serialize(progress), runtime);
            }
        } catch (IOException | IllegalArgumentException e) {
            return BuildResult.fail(1, "Target '" + target.id() + "': " + e.getMessage());
        }
    }

    static BuildResult build(BuildRequest req, ResolvedTarget target, TargetGraph graph,
                             Consumer<String> out, BiConsumer<String, Long> progress,
                             BuildRuntime runtime) {
        try { return execute(req, target, graph, runtime.serialize(out), runtime.serialize(progress), runtime); }
        catch (IOException | IllegalArgumentException e) {
            return BuildResult.fail(1, "Target '" + target.id() + "': " + e.getMessage());
        }
    }

    private static BuildResult execute(BuildRequest req, ResolvedTarget target, TargetGraph graph,
                                        Consumer<String> out, BiConsumer<String, Long> progress, BuildRuntime runtime) throws IOException {
        long analysisStarted = runtime.metrics.start();
        NativeArtifacts.requireId(target.id());
        ManifestRootModel m = TargetResolver.perTargetManifest(req.manifest(), target);
        boolean[] invalidPlatform = {false};
        TargetPlatform platform = TargetPlatform.resolve(m.getPlatform(), message -> {
            invalidPlatform[0] = true;
            out.accept(message);
        });

        if (invalidPlatform[0] || "native".equalsIgnoreCase(m.getPlatform())) m.setPlatform(null);
        else if (NativeTools.specified(m.getPlatform())) m.setPlatform(platform.triple());
        List<Path> sources = runtime.sources(target);
        if (sources.isEmpty()) throw new IllegalArgumentException("Nenhuma fonte C/C++/ASM encontrada");
        boolean exclusiveUnits = sources.stream().anyMatch(source -> source.toString().matches("(?i).*\\.(cppm|ixx|mpp|ccm|cxxm)$"))
                || m.getCompileFlags().stream().map(flag -> flag.toLowerCase(Locale.ROOT))
                .anyMatch(flag -> flag.contains("module") || flag.contains("pch") || flag.startsWith("/yc")
                        || flag.startsWith("/yu") || flag.startsWith("/fp"));
        boolean flat = target.type() == TargetType.BINARY && "bin".equals(AssemblyCommandBuilder.format(m, platform));
        if ((flat || target.type() == TargetType.OBJECT) && sources.size() != 1)
            throw new IllegalArgumentException("type object ou NASM bin direto requer uma unica fonte principal");
        if (flat && !SourceCollector.isAssembly(sources.getFirst()))
            throw new IllegalArgumentException("asmFormat bin requer uma fonte NASM");
        if (!flat && "bin".equals(AssemblyCommandBuilder.format(m, platform)))
            throw new IllegalArgumentException("asmFormat bin requer type binary");
        String mode = NativeTools.specified(m.getLinkMode()) ? m.getLinkMode().toLowerCase(Locale.ROOT) : "auto";
        if (!List.of("auto", "driver", "linker").contains(mode)) throw new IllegalArgumentException("linkMode invalido: " + mode);

        List<ResolvedTarget> linkedDeps = linkDependencies(graph, target);
        boolean cpp = SourceCollector.isCppSources(sources);
        boolean c = sources.stream().anyMatch(s -> !SourceCollector.isAssembly(s));
        for (ResolvedTarget dep : usageDependencies(graph, target)) {
            List<Path> depSources = runtime.sources(dep);
            cpp |= SourceCollector.isCppSources(depSources);
            c |= depSources.stream().anyMatch(s -> !SourceCollector.isAssembly(s));
            ManifestRootModel dm = TargetResolver.perTargetManifest(req.manifest(), dep);
            TargetPlatform dp = TargetPlatform.resolve(dm.getPlatform(), null);
            if (!platform.compatible(dp)) throw new IllegalArgumentException("Plataformas incompativeis no link: " + target.id() + " e " + dep.id());
            m.setIncludes(ManifestMerge.mergeAdditive(m.getIncludes(), dep.includes(), null));
        }
        boolean finalLink = target.type() != TargetType.STATIC && target.type() != TargetType.OBJECT && !flat;
        boolean driverLink = finalLink && (mode.equals("driver") || mode.equals("auto") && c);
        boolean needsCompiler = sources.stream().anyMatch(s -> !SourceCollector.isAssembly(s) || SourceCollector.needsPreprocessor(s)) || driverLink;
        Toolchain compiler = needsCompiler ? NativeTools.compiler(m, platform, req.toolchain(), req.projectPath()) : null;
        boolean msvc = compiler != null && compiler.isMsvc();
        Map<Path, NativeTools.Tool> assemblers = new LinkedHashMap<>();
        for (Path source : sources) if (SourceCollector.isAssembly(source))
            assemblers.put(source, NativeTools.assembler(m, platform, source, req.projectPath()));
        if (!NativeTools.specified(m.getAsmKind()) && !assemblers.isEmpty()) m.setAsmKind(assemblers.values().iterator().next().kind());
        for (NativeTools.Tool tool : new LinkedHashSet<>(assemblers.values())) NativeTools.validateAssembler(tool, platform, req, m);
        NativeTools.Tool linker = finalLink && !driverLink ? NativeTools.linker(m, platform, req.projectPath()) : null;
        boolean nativeMsvc = msvc || (linker != null && linker.kind().equals("msvc"))
                || assemblers.values().stream().anyMatch(t -> t.kind().equals("masm"))
                || target.type() == TargetType.STATIC && platform.windows() && req.toolchain() != null && req.toolchain().isMsvc();
        if (nativeMsvc && !NativeTools.specified(m.getLinkerKind())) m.setLinkerKind("msvc");
        Path artifact = artifact(req, target, nativeMsvc);
        Path objDir = req.buildDir().resolve(".obj").resolve(target.id());
        Files.createDirectories(objDir);
        Files.createDirectories(artifact.getParent());
        Path archive = null;
        if (target.type() == TargetType.STATIC) {
            archive = NativeTools.specified(m.getArchiver()) ? NativeTools.executable(m.getArchiver(), "ar", req.projectPath())
                    : platform.nativeDestination() && (compiler != null || nativeMsvc)
                    ? ToolchainDetector.resolveArchiver(compiler != null ? compiler : req.toolchain()) : null;
            if (archive == null) archive = NativeTools.executable(null, nativeMsvc ? "lib" : NativeTools.prefixed(platform, "ar"), req.projectPath());
        }
        Path objcopy = target.type() == TargetType.BINARY && !flat
                ? NativeTools.executable(m.getObjcopy(), NativeTools.prefixed(platform, "objcopy"), req.projectPath()) : null;
        boolean archiveMsvc = nativeMsvc;
        if (archive != null) {
            String name = NativeTools.name(archive);
            if (name.equals("ar") || name.endsWith("-ar")) archiveMsvc = false;
            if (name.equals("lib") || name.endsWith("-lib")) archiveMsvc = true;
        }
        PackagePaths packages = runtime.packages();
        StringBuilder fingerprint = new StringBuilder("|" + platform + "|" + AssemblyCommandBuilder.format(m, platform));
        for (NativeTools.Tool tool : new LinkedHashSet<>(assemblers.values())) fingerprint.append('|').append(tool.kind()).append(runtime.toolFingerprint(tool.executable()));
        if (linker != null) fingerprint.append(runtime.toolFingerprint(linker.executable()));
        fingerprint.append(runtime.toolFingerprint(archive)).append(runtime.toolFingerprint(objcopy));
        fingerprint.append(runtime.compilerFingerprint(compiler, m));
        String environment = runtime.environment(m, compiler);
        IncrementalBuildService incremental = new IncrementalBuildService(req.buildDir(), compiler, req.buildMode(), req.incremental(), fingerprint + "|env=" + environment + "|includes=" + runtime.includeNamespace(m, sources, packages.includeDirs()), runtime.hasher);
        TargetState state = incremental.begin(target.id());
        runtime.metrics.elapsed("analysis", analysisStarted);
        List<Path> owned = new ArrayList<>(List.of(artifact));
        if (nativeMsvc && finalLink && objcopy == null) owned.add(artifact.resolveSibling(artifact.getFileName() + ".pdb"));
        if (nativeMsvc && target.type() == TargetType.SHARED) {
            owned.add(importLibrary(artifact));
            owned.add(artifact.resolveSibling(artifact.getFileName().toString().replaceFirst("(?i)\\.dll$", "") + ".exp"));
        }

        NativeArtifacts.record(req.buildDir(), target.id(), owned);
        try {
            List<Path> objects = new ArrayList<>();
            List<CompletableFuture<BuildResult>> pending = new ArrayList<>();
            boolean drained = false;
            try {
                for (Path source : sources) {
                    Path object = flat || target.type() == TargetType.OBJECT ? artifact
                            : objDir.resolve(NativeArtifacts.objectFileName(req.projectPath(), source, platform.windows()));
                    objects.add(object);
                    Path depFile = objDir.resolve(object.getFileName() + ".d");
                    Path ppDep = objDir.resolve(object.getFileName() + ".cpp.d");
                    List<String> preprocess = List.of();
                    List<String> dependencyScan = List.of();
                    List<String> command;
                    NativeTools.Tool asm = assemblers.get(source);
                    if (asm != null) {
                        Path asmSource = source;
                        if (SourceCollector.needsPreprocessor(source)) {
                            asmSource = objDir.resolve(object.getFileName() + ".s");
                            preprocess = AssemblyCommandBuilder.preprocess(compiler, m, req.projectPath(), source, asmSource, ppDep, packages.includeDirs());
                        }
                        command = AssemblyCommandBuilder.build(asm, platform, m, req.projectPath(), asmSource, object, depFile, packages.includeDirs(), !preprocess.isEmpty());
                        if (asm.kind().equals("nasm"))
                            dependencyScan = AssemblyCommandBuilder.nasmDependencies(asm, platform, m, req.projectPath(), asmSource, object, depFile, packages.includeDirs());
                    } else {
                        CompileSpec spec = new CompileSpec(compiler, SourceCollector.isCppSources(List.of(source)), m,
                                target.type() == TargetType.SHARED, List.of(source), object, packages.includeDirs(),
                                List.of(), List.of(), req.buildMode(), List.of(), req.projectPath(), depFile);
                        command = CompileCommandBuilder.buildCompileOnlyCommand(spec);
                    }
                    List<String> signature = new ArrayList<>(preprocess);
                    signature.addAll(command);
                    signature.addAll(dependencyScan);
                    long checked = runtime.metrics.start();
                    boolean upToDate = incremental.isObjectUpToDate(state, source, object, signature);
                    runtime.metrics.elapsed("incremental", checked);
                    if (upToDate) {
                        runtime.metrics.add("local_reused", 1);
                        progress.accept("Sem mudancas: " + source.getFileName(), 0L);
                        continue;
                    }
                    final ManifestRootModel unitManifest = m;
                    final List<String> unitPreprocess = preprocess, unitDependencyScan = dependencyScan;
                    long estimate = exclusiveUnits ? Long.MAX_VALUE : ResourceManager.estimate(source, req.options().memoryPerJob(),
                            runtime.history.peak(FileHasher.hashCommand(signature)));
                    pending.add(runtime.scheduler.submit(estimate, true,
                            () -> {
                                try {
                                    BuildResult result = compileUnit(req, unitManifest, platform, flat, msvc, asm, source, object, depFile, ppDep,
                                        unitPreprocess, unitDependencyScan, command, signature, incremental, state, out, progress, runtime, environment);
                                    if (!result.success()) runtime.scheduler.cancel();
                                    return result;
                                } catch (Exception e) { runtime.scheduler.cancel(); throw e; }
                            }));

                    if (pending.size() >= runtime.scheduler.workers() * 2) {
                        BuildResult result = await(pending.removeFirst(), runtime);
                        if (!result.success()) return result;
                    }
                }
                for (var future : pending) {
                    BuildResult result = await(future, runtime);
                    if (!result.success()) return result;
                }
                drained = true;
            } finally {
                if (!drained) runtime.scheduler.cancel();

                boolean interrupted = Thread.interrupted();
                for (var future : pending) {
                    boolean done = false;
                    while (!done) {
                        try { future.get(); done = true; }
                        catch (InterruptedException e) { interrupted = true; runtime.scheduler.cancel(); }
                        catch (ExecutionException | CancellationException e) { done = true; }
                    }
                }
                if (interrupted) Thread.currentThread().interrupt();
            }
            if (flat || target.type() == TargetType.OBJECT) return BuildResult.ok(0, artifact, "Artefato gerado: " + artifact);

            List<Path> inputs = new ArrayList<>(objects);
            List<Path> tracked = new ArrayList<>(objects);
            for (ResolvedTarget dep : linkedDeps) {
                Path depArtifact = artifact(req, dep, nativeMsvc);
                if (target.type() != TargetType.STATIC || dep.type() == TargetType.OBJECT)
                    inputs.add(dep.type() == TargetType.SHARED && nativeMsvc ? importLibrary(depArtifact) : depArtifact);
                tracked.add(depArtifact);
            }
            tracked.addAll(packages.linkInputFiles());
            for (String file : m.getLinkDependencies()) {
                Path path = req.projectPath().resolve(file).normalize();
                if (!Files.isRegularFile(path)) throw new IllegalArgumentException("linkDependencies inexistente: " + path);
                tracked.add(path);
            }
            inputs = new ArrayList<>(new LinkedHashSet<>(inputs));
            tracked = new ArrayList<>(new LinkedHashSet<>(tracked));
            Path linkOutput = objcopy == null ? artifact : objDir.resolve("linked-image" + (platform.windows() ? ".exe" : ""));
            List<String> command = target.type() == TargetType.STATIC
                    ? ArchiverCommandBuilder.buildArchiveCommand(archive, artifact, inputs, archiveMsvc)
                    : LinkCommandBuilder.build(driverLink ? compiler.driver(cpp) : linker.executable(),
                            driverLink ? msvc ? "msvc" : "gnu" : linker.kind(), driverLink, cpp, platform, m,
                            req.projectPath(), target.type() == TargetType.SHARED, inputs, linkOutput,
                            packages.libraryDirs(), packages.linkLibraries());
            List<String> conversion = objcopy == null ? List.of()
                    : List.of(objcopy.toString(), "-O", "binary", linkOutput.toString(), artifact.toString());
            List<String> signature = new ArrayList<>(command);
            signature.addAll(conversion);
            long linkChecked = runtime.metrics.start();
            boolean needsLink = incremental.needsLink(state, signature, artifact, tracked);
            runtime.metrics.elapsed("incremental", linkChecked);
            if (!needsLink
                    && (!nativeMsvc || target.type() != TargetType.SHARED || Files.isRegularFile(importLibrary(artifact)))
                    && (objcopy == null || Files.isRegularFile(linkOutput))) {
                progress.accept("Artefato ja atualizado: " + artifact, 0L);
                return BuildResult.ok(0, artifact, "Artefato atualizado: " + artifact);
            }

            if (target.type() == TargetType.STATIC) Files.deleteIfExists(artifact);
            long started = System.nanoTime();
            final ManifestRootModel linkManifest = m;
            int exit = await(runtime.scheduler.submit(Math.max(256 * ResourceManager.MIB, resourcesForLink(objects)), false, () -> {
                long linking = runtime.metrics.start();
                try {
                    int code = run(req, linkManifest, command, out);
                    if (code == 0 && !conversion.isEmpty()) code = run(req, linkManifest, conversion, out);
                    return code;
                } finally { runtime.metrics.elapsed("link", linking); }
            }), runtime, -1);
            if (exit != 0 || !Files.isRegularFile(artifact)) {
                incremental.recordLinkFailed(state);
                return BuildResult.fail(exit == 0 ? 1 : exit, "Link/archive/conversao falhou: " + artifact);
            }
            incremental.recordLinked(state, signature, tracked);
            progress.accept("Artefato gerado: " + artifact, started);
            return BuildResult.ok(0, artifact, "Artefato gerado: " + artifact);
        } finally {
            incremental.finish(state, out);
        }
    }

    private static BuildResult compileUnit(BuildRequest req, ManifestRootModel m, TargetPlatform platform,
            boolean flat, boolean msvc, NativeTools.Tool asm, Path source, Path object, Path depFile, Path ppDep,
            List<String> preprocess, List<String> dependencyScan, List<String> command, List<String> signature,
            IncrementalBuildService incremental, TargetState state, Consumer<String> out,
            BiConsumer<String, Long> progress, BuildRuntime runtime, String environment) throws IOException {
        long compiling = runtime.metrics.start();
        ProcessRunner.beginMemoryTracking();
        PreprocessedObjectCache.Prepared cached = null;
        try {
            incremental.invalidateObject(state, source);
            runtime.hasher.invalidate(object);
            long started = System.nanoTime();
            Files.deleteIfExists(depFile);
            if (asm == null) {
                long caching = runtime.metrics.start();
                try {
                    cached = runtime.cache.prepare(req, m, source, object, depFile, command, environment, runtime.metrics, out);
                    if (cached != null && cached.key() != null && req.incremental()) {
                        if (runtime.cache.restore(cached, object)) {
                            if (!inputsStable(cached, source)) {
                                Files.deleteIfExists(object);
                                return BuildResult.fail(1, "Entrada alterada durante a compilacao: " + source);
                            }
                            incremental.recordCompiled(state, source, object, signature, cached.dependencies());
                            runtime.metrics.add("cache_hits", 1);
                            progress.accept("Cache compartilhado: " + source.getFileName(), started);
                            return BuildResult.ok(0, object, "Objeto restaurado: " + source);
                        }
                        runtime.metrics.add("cache_misses", 1);
                    }
                } finally { runtime.metrics.elapsed("shared_cache", caching); }
            }
            if (Thread.currentThread().isInterrupted()) return BuildResult.fail(1, "Compilacao cancelada: " + source);
            if (!preprocess.isEmpty()) {
                Files.deleteIfExists(ppDep);
                int exit = run(req, m, preprocess, out);
                if (exit != 0) return BuildResult.fail(exit, "Pre-processamento falhou: " + source);
            }
            MsvcIncludeParser includes = asm == null && msvc ? new MsvcIncludeParser() : null;
            Consumer<String> compileOut = includes == null ? out : line -> { if (!includes.offer(line)) out.accept(line); };
            long execution = runtime.metrics.start();
            int exit;
            try { exit = run(req, m, cached == null ? command : cached.command(), compileOut); }
            finally { runtime.metrics.elapsed("compile", execution); }
            if (exit != 0 || Thread.currentThread().isInterrupted()) {
                Files.deleteIfExists(object);
                return BuildResult.fail(exit == 0 ? 1 : exit, "Compilacao/montagem falhou: " + source);
            }
            if (!Files.isRegularFile(object)) return BuildResult.fail(1, "Ferramenta nao gerou " + object);
            if (!flat && req.executor() == ProcessExecutor.REAL) {
                try { ObjectFormatValidator.validate(object, platform); }
                catch (IOException e) { Files.deleteIfExists(object); throw e; }
            }
            long dependencies = runtime.metrics.start();
            if (!dependencyScan.isEmpty() && run(req, m, dependencyScan, out) != 0) Files.deleteIfExists(depFile);
            List<Path> deps = cached != null ? cached.dependencies() : includes != null ? includes.prefix() == null ? null : includes.includes()
                    : asm != null && asm.kind().equals("masm") ? null : DepFileParser.parseSafe(depFile, req.projectPath());
            if (!preprocess.isEmpty() && deps != null) {
                List<Path> ppDeps = DepFileParser.parseSafe(ppDep, req.projectPath());
                if (ppDeps == null) deps = null;
                else { deps = new ArrayList<>(deps); deps.addAll(ppDeps); }
            }
            runtime.metrics.elapsed("dependencies", dependencies);
            if (cached != null) {
                if (!inputsStable(cached, source)) {
                    Files.deleteIfExists(object);
                    return BuildResult.fail(1, "Entrada alterada durante a compilacao: " + source);
                }
                long caching = runtime.metrics.start();
                try { runtime.cache.store(cached, object); }
                finally { runtime.metrics.elapsed("shared_cache", caching); }
            }
            incremental.recordCompiled(state, source, object, signature, cached != null && cached.key() == null ? null : deps);
            progress.accept("Compilado " + source.getFileName(), started);
            runtime.metrics.add("compiled", 1);
            return BuildResult.ok(0, object, "Compilado " + source);
        } finally {
            if (cached != null) cached.close();
            runtime.history.record(FileHasher.hashCommand(signature), ProcessRunner.peakMemoryBytes());
            runtime.metrics.elapsed("unit_total", compiling);
        }
    }

    private static boolean inputsStable(PreprocessedObjectCache.Prepared input, Path source) throws IOException {
        if (!new FileHasher().sha256(source).equals(input.sourceHash())) return false;
        for (Path dependency : input.dependencies())
            if (Files.getLastModifiedTime(dependency).toMillis() > input.scanStartedMillis()) return false;
        return true;
    }

    private static long resourcesForLink(List<Path> objects) {
        long bytes = 0;
        for (Path object : objects) try { bytes += Files.size(object); } catch (IOException ignored) { }
        return Math.min(4L << 30, bytes * 3);
    }

    private static BuildResult await(CompletableFuture<BuildResult> future, BuildRuntime runtime) {
        return await(future.exceptionally(error -> BuildResult.fail(1, "Falha na unidade de compilacao: " + error)),
                runtime, BuildResult.fail(1, "Compilacao cancelada ou falhou"));
    }

    private static <T> T await(CompletableFuture<T> future, BuildRuntime runtime, T failure) {
        try { return future.get(); }
        catch (InterruptedException e) {
            runtime.scheduler.cancel();
            boolean done = false;
            while (!done) {
                try { future.get(); done = true; }
                catch (InterruptedException again) { }
                catch (ExecutionException | CancellationException failed) { done = true; }
            }
            Thread.currentThread().interrupt();
            return failure;
        }
        catch (ExecutionException | CancellationException e) {
            runtime.scheduler.cancel(); return failure;
        }
    }

    private static int run(BuildRequest req, ManifestRootModel m, List<String> command, Consumer<String> out) throws IOException {
        out.accept("+ " + String.join(" ", command));
        if (req.executor() != ProcessExecutor.REAL) return req.executor().run(command, req.projectPath(), m.getEnv(), out);
        try (ResponseFile response = ResponseFile.prepare(command, req.buildDir())) {
            return req.executor().run(response.command(), req.projectPath(), m.getEnv(), out);
        }
    }

    public static Path artifact(BuildRequest req, ResolvedTarget target, boolean msvc) {
        return NativeArtifacts.artifact(req.buildDir(), req.manifest(), target,
                msvc || req.toolchain() != null && req.toolchain().isMsvc());
    }
    public static Path importLibrary(Path dll) {
        return dll.resolveSibling(dll.getFileName().toString().replaceFirst("(?i)\\.dll$", "") + ".lib");
    }

    public static List<ResolvedTarget> linkDependencies(TargetGraph graph, ResolvedTarget target) {
        LinkedHashMap<String, ResolvedTarget> out = new LinkedHashMap<>();
        collect(graph, target, false, out, new HashSet<>());
        Set<String> absorbedObjects = new HashSet<>();
        for (ResolvedTarget dep : out.values()) if (dep.type() == TargetType.STATIC || dep.type() == TargetType.SHARED)
            for (ResolvedTarget nested : usageDependencies(graph, dep))
                if (nested.type() == TargetType.OBJECT) absorbedObjects.add(nested.id());
        absorbedObjects.forEach(out::remove);
        List<ResolvedTarget> ordered = new ArrayList<>(graph.topologicalOrder());
        Collections.reverse(ordered);
        return ordered.stream().filter(t -> out.containsKey(t.id())).toList();
    }

    private static List<ResolvedTarget> usageDependencies(TargetGraph graph, ResolvedTarget target) {
        Map<String, ResolvedTarget> found = new LinkedHashMap<>();
        ArrayDeque<String> pending = new ArrayDeque<>(target.dependsOn());
        while (!pending.isEmpty()) {
            ResolvedTarget dep = graph.target(pending.removeFirst());
            if (dep == null || !(dep.type().isLibrary() || dep.type() == TargetType.OBJECT)
                    || found.putIfAbsent(dep.id(), dep) != null) continue;
            pending.addAll(dep.dependsOn());
        }
        return new ArrayList<>(found.values());
    }
    private static void collect(TargetGraph graph, ResolvedTarget target, boolean absorbed,
                                Map<String, ResolvedTarget> out, Set<String> seen) {
        for (String id : target.dependsOn()) {
            ResolvedTarget dep = graph.target(id);
            if (dep == null || !seen.add(id + ":" + absorbed)) continue;
            if (dep.type().isLibrary() || dep.type() == TargetType.OBJECT && !absorbed) out.putIfAbsent(id, dep);
            if (dep.type().isLibrary() || dep.type() == TargetType.OBJECT)
                collect(graph, dep, absorbed || dep.type() == TargetType.STATIC || dep.type() == TargetType.SHARED, out, seen);
        }
    }
}
