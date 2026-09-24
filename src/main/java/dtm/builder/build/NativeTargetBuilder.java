package dtm.builder.build;

import dtm.builder.build.graph.ResolvedTarget;
import dtm.builder.build.graph.TargetGraph;
import dtm.builder.build.graph.TargetResolver;
import dtm.builder.build.incremental.CompilerFingerprint;
import dtm.builder.build.incremental.DepFileParser;
import dtm.builder.build.incremental.IncrementalBuildService;
import dtm.builder.build.incremental.MsvcIncludeParser;
import dtm.builder.build.incremental.TargetState;
import dtm.builder.manifest.ManifestMerge;
import dtm.builder.manifest.model.ManifestRootModel;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Builds one target with a compiler/assembler chosen independently for every source. */
public final class NativeTargetBuilder {
    private NativeTargetBuilder() { }

    public static BuildResult build(BuildRequest req, ResolvedTarget target, TargetGraph graph,
                                    Consumer<String> out) {
        return build(req, target, graph, out, (message, started) -> out.accept(message));
    }

    public static BuildResult build(BuildRequest req, ResolvedTarget target, TargetGraph graph,
                                    Consumer<String> out, java.util.function.BiConsumer<String, Long> progress) {
        try {
            return execute(req, target, graph, out, progress);
        } catch (IOException | IllegalArgumentException e) {
            return BuildResult.fail(1, "Target '" + target.id() + "': " + e.getMessage());
        }
    }

    private static BuildResult execute(BuildRequest req, ResolvedTarget target, TargetGraph graph,
                                        Consumer<String> out, java.util.function.BiConsumer<String, Long> progress) throws IOException {
        NativeArtifacts.requireId(target.id());
        ManifestRootModel m = TargetResolver.perTargetManifest(req.manifest(), target);
        boolean[] invalidPlatform = {false};
        TargetPlatform platform = TargetPlatform.resolve(m.getPlatform(), message -> {
            invalidPlatform[0] = true;
            out.accept(message);
        });
        // Preserve compiler-native ABI defaults unless a platform was explicitly requested.
        if (invalidPlatform[0] || "native".equalsIgnoreCase(m.getPlatform())) m.setPlatform(null);
        else if (NativeTools.specified(m.getPlatform())) m.setPlatform(platform.triple());
        List<Path> sources = SourceCollector.collectSources(req.projectPath(), target.sources(), target.synthetic());
        if (sources.isEmpty()) throw new IllegalArgumentException("Nenhuma fonte C/C++/ASM encontrada");
        boolean flat = target.type() == TargetType.BINARY && "bin".equals(AssemblyCommandBuilder.format(m, platform));
        if ((flat || target.type() == TargetType.OBJECT) && sources.size() != 1)
            throw new IllegalArgumentException("type object ou NASM bin direto requer uma unica fonte principal");
        if (flat && !SourceCollector.isAssembly(sources.getFirst()))
            throw new IllegalArgumentException("asmFormat bin requer uma fonte NASM");
        if (!flat && "bin".equals(AssemblyCommandBuilder.format(m, platform)))
            throw new IllegalArgumentException("asmFormat bin requer type binary");
        String mode = NativeTools.specified(m.getLinkMode()) ? m.getLinkMode().toLowerCase(java.util.Locale.ROOT) : "auto";
        if (!List.of("auto", "driver", "linker").contains(mode)) throw new IllegalArgumentException("linkMode invalido: " + mode);

        List<ResolvedTarget> linkedDeps = linkDependencies(graph, target);
        boolean cpp = SourceCollector.isCppSources(sources);
        boolean c = sources.stream().anyMatch(s -> !SourceCollector.isAssembly(s));
        for (ResolvedTarget dep : usageDependencies(graph, target)) {
            List<Path> depSources = SourceCollector.collectSources(req.projectPath(), dep.sources(), dep.synthetic());
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
        StringBuilder fingerprint = new StringBuilder("|" + platform + "|" + AssemblyCommandBuilder.format(m, platform));
        for (NativeTools.Tool tool : assemblers.values()) fingerprint.append('|').append(tool.kind()).append(CompilerFingerprint.describe(tool.executable()));
        if (linker != null) fingerprint.append(CompilerFingerprint.describe(linker.executable()));
        fingerprint.append(CompilerFingerprint.describe(archive)).append(CompilerFingerprint.describe(objcopy));
        IncrementalBuildService incremental = new IncrementalBuildService(req.buildDir(), compiler, req.buildMode(), req.incremental(), fingerprint.toString());
        TargetState state = incremental.begin(target.id());
        List<Path> owned = new ArrayList<>(List.of(artifact));
        if (nativeMsvc && finalLink && objcopy == null) owned.add(artifact.resolveSibling(artifact.getFileName() + ".pdb"));
        if (nativeMsvc && target.type() == TargetType.SHARED) {
            owned.add(importLibrary(artifact));
            owned.add(artifact.resolveSibling(artifact.getFileName().toString().replaceFirst("(?i)\\.dll$", "") + ".exp"));
        }
        // Register before starting processes so selective clean also handles failed builds.
        NativeArtifacts.record(req.buildDir(), target.id(), owned);
        PackagePaths packages = PackagePaths.resolve(req.packagesDir());
        try {
            List<Path> objects = new ArrayList<>();
            int index = 0;
            for (Path source : sources) {
                Path object = flat || target.type() == TargetType.OBJECT ? artifact
                        : objDir.resolve(index++ + "_" + source.getFileName() + (platform.windows() ? ".obj" : ".o"));
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
                if (incremental.isObjectUpToDate(state, source, object, signature)) {
                    progress.accept("Sem mudancas: " + source.getFileName(), 0L);
                    continue;
                }
                long started = System.nanoTime();
                Files.deleteIfExists(depFile);
                if (!preprocess.isEmpty()) {
                    Files.deleteIfExists(ppDep);
                    int exit = run(req, m, preprocess, out);
                    if (exit != 0) return BuildResult.fail(exit, "Pre-processamento falhou: " + source);
                }
                MsvcIncludeParser includes = asm == null && msvc ? new MsvcIncludeParser() : null;
                Consumer<String> compileOut = includes == null ? out : line -> { if (!includes.offer(line)) out.accept(line); };
                int exit = run(req, m, command, compileOut);
                if (exit != 0) {
                    Files.deleteIfExists(object);
                    return BuildResult.fail(exit, "Compilacao/montagem falhou: " + source);
                }
                if (!Files.isRegularFile(object)) return BuildResult.fail(1, "Ferramenta nao gerou " + object);
                if (!flat && req.executor() == ProcessExecutor.REAL) {
                    try { ObjectFormatValidator.validate(object, platform); }
                    catch (IOException e) { Files.deleteIfExists(object); throw e; }
                }
                if (!dependencyScan.isEmpty() && run(req, m, dependencyScan, out) != 0) Files.deleteIfExists(depFile);
                List<Path> deps = includes != null ? includes.prefix() == null ? null : includes.includes()
                        : asm != null && asm.kind().equals("masm") ? null : DepFileParser.parseSafe(depFile, req.projectPath());
                if (!preprocess.isEmpty() && deps != null) {
                    List<Path> ppDeps = DepFileParser.parseSafe(ppDep, req.projectPath());
                    if (ppDeps == null) deps = null;
                    else { deps = new ArrayList<>(deps); deps.addAll(ppDeps); }
                }
                incremental.recordCompiled(state, source, object, signature, deps);
                progress.accept("Compilado " + source.getFileName(), started);
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
            if (!incremental.needsLink(state, signature, artifact, tracked)
                    && (!nativeMsvc || target.type() != TargetType.SHARED || Files.isRegularFile(importLibrary(artifact)))
                    && (objcopy == null || Files.isRegularFile(linkOutput))) {
                progress.accept("Artefato ja atualizado: " + artifact, 0L);
                return BuildResult.ok(0, artifact, "Artefato atualizado: " + artifact);
            }
            // ar replaces members but does not remove obsolete ones; rebuild archives from scratch.
            if (target.type() == TargetType.STATIC) Files.deleteIfExists(artifact);
            long started = System.nanoTime();
            int exit = run(req, m, command, out);
            if (exit == 0 && !conversion.isEmpty()) exit = run(req, m, conversion, out);
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

    private static int run(BuildRequest req, ManifestRootModel m, List<String> command, Consumer<String> out) {
        out.accept("+ " + String.join(" ", command));
        return req.executor().run(command, req.projectPath(), m.getEnv(), out);
    }

    public static Path artifact(BuildRequest req, ResolvedTarget target, boolean msvc) {
        return NativeArtifacts.artifact(req.buildDir(), req.manifest(), target,
                msvc || req.toolchain() != null && req.toolchain().isMsvc());
    }
    public static Path importLibrary(Path dll) {
        return dll.resolveSibling(dll.getFileName().toString().replaceFirst("(?i)\\.dll$", "") + ".lib");
    }

    /** Objects absorbed into a static archive must not also be linked individually downstream. */
    public static List<ResolvedTarget> linkDependencies(TargetGraph graph, ResolvedTarget target) {
        LinkedHashMap<String, ResolvedTarget> out = new LinkedHashMap<>();
        collect(graph, target, false, out, new java.util.HashSet<>());
        java.util.Set<String> absorbedObjects = new java.util.HashSet<>();
        for (ResolvedTarget dep : out.values()) if (dep.type() == TargetType.STATIC || dep.type() == TargetType.SHARED)
            for (ResolvedTarget nested : usageDependencies(graph, dep))
                if (nested.type() == TargetType.OBJECT) absorbedObjects.add(nested.id());
        absorbedObjects.forEach(out::remove);
        List<ResolvedTarget> ordered = new ArrayList<>(graph.topologicalOrder());
        java.util.Collections.reverse(ordered);
        return ordered.stream().filter(t -> out.containsKey(t.id())).toList();
    }

    /** Includes/language requirements survive object absorption into a library. */
    private static List<ResolvedTarget> usageDependencies(TargetGraph graph, ResolvedTarget target) {
        Map<String, ResolvedTarget> found = new LinkedHashMap<>();
        java.util.ArrayDeque<String> pending = new java.util.ArrayDeque<>(target.dependsOn());
        while (!pending.isEmpty()) {
            ResolvedTarget dep = graph.target(pending.removeFirst());
            if (dep == null || !(dep.type().isLibrary() || dep.type() == TargetType.OBJECT)
                    || found.putIfAbsent(dep.id(), dep) != null) continue;
            pending.addAll(dep.dependsOn());
        }
        return new ArrayList<>(found.values());
    }
    private static void collect(TargetGraph graph, ResolvedTarget target, boolean absorbed,
                                Map<String, ResolvedTarget> out, java.util.Set<String> seen) {
        for (String id : target.dependsOn()) {
            ResolvedTarget dep = graph.target(id);
            if (dep == null || !seen.add(id + ":" + absorbed)) continue;
            if (dep.type().isLibrary() || dep.type() == TargetType.OBJECT && !absorbed) out.putIfAbsent(id, dep);
            if (dep.type().isLibrary() || dep.type() == TargetType.OBJECT)
                collect(graph, dep, absorbed || dep.type() == TargetType.STATIC || dep.type() == TargetType.SHARED, out, seen);
        }
    }
}
