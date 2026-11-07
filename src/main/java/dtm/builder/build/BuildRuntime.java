package dtm.builder.build;

import dtm.builder.build.graph.ResolvedTarget;
import dtm.builder.build.incremental.FileHasher;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

final class BuildRuntime implements AutoCloseable {
    final BuildMetrics metrics;
    final ResourceManager resources;
    final CompilationScheduler scheduler;
    final FileHasher hasher = new FileHasher();
    final dtm.builder.build.cache.PreprocessedObjectCache cache;
    final MemoryHistory history;
    private final BuildDirectoryLock directoryLock;
    private final BuildRequest request;
    private final Object outputLock = new Object();
    final java.util.function.Consumer<String> output;
    private final ConcurrentHashMap<String, List<Path>> sources = new ConcurrentHashMap<>();
    private volatile PackagePaths packages;

    BuildRuntime(BuildRequest request) {
        this.request = request;
        output = serialize(request.output());
        try { directoryLock = BuildDirectoryLock.acquire(request.buildDir()); }
        catch (java.io.IOException e) { throw new IllegalArgumentException(e.getMessage(), e); }
        metrics = new BuildMetrics(request.options().profile());
        resources = new ResourceManager(request.jobs(), request.options());
        scheduler = new CompilationScheduler(resources, metrics);
        cache = new dtm.builder.build.cache.PreprocessedObjectCache(
                new dtm.builder.build.cache.SharedObjectCache(request.options().cacheDirectory(), request.options().cacheSize()), hasher);
        history = new MemoryHistory(request.options().cacheDirectory().resolveSibling("memory-history.properties"));
    }

    java.util.function.Consumer<String> serialize(java.util.function.Consumer<String> consumer) {
        return line -> { synchronized (outputLock) { if (consumer != null) consumer.accept(line); } };
    }

    java.util.function.BiConsumer<String, Long> serialize(java.util.function.BiConsumer<String, Long> consumer) {
        return (message, started) -> { synchronized (outputLock) { consumer.accept(message, started); } };
    }

    List<Path> sources(ResolvedTarget target) {
        return sources.computeIfAbsent(target.id(), ignored -> {
            long start = metrics.start();
            try { return SourceCollector.collectSources(request.projectPath(), target.sources(), target.synthetic()); }
            finally { metrics.elapsed("discovery", start); }
        });
    }

    String environment(java.util.Map<String, String> overrides) {
        return BuildEnvironment.signature(System.getenv(), overrides);
    }

    String environment(java.util.Map<String, String> overrides, Toolchain compiler) {

        return compiler != null && compiler.kind() == ToolchainKind.CUSTOM
                ? BuildEnvironment.customSignature(System.getenv(), overrides) : environment(overrides);
    }

    String toolFingerprint(Path executable) {
        if (executable == null) return "-";
        try { return dtm.builder.build.incremental.CompilerFingerprint.describe(executable) + ":" + hasher.sha256(executable); }
        catch (java.io.IOException e) { return dtm.builder.build.incremental.CompilerFingerprint.describe(executable); }
    }

    String compilerFingerprint(Toolchain compiler, dtm.builder.manifest.model.ManifestRootModel manifest) {
        if (compiler == null) return "none";
        var fingerprints = new java.util.ArrayList<String>();
        for (Path driver : java.util.stream.Stream.of(compiler.cc(), compiler.cxx()).filter(java.util.Objects::nonNull).distinct().toList()) {
            String value = request.executor() == ProcessExecutor.REAL && !compiler.isMsvc()
                    ? cache.fingerprint(request, manifest, driver.toString(), environment(manifest.getEnv(), compiler)) : null;
            fingerprints.add(value == null ? toolFingerprint(driver) : value);
        }
        return FileHasher.hashCommand(fingerprints);
    }

    PackagePaths packages() {
        if (packages == null) synchronized (this) {
            if (packages == null) packages = PackagePaths.resolve(request.packagesDir());
        }
        return packages;
    }

    private final ConcurrentHashMap<Path, String> namespaces = new ConcurrentHashMap<>();
    String includeNamespace(dtm.builder.manifest.model.ManifestRootModel manifest, List<Path> units, List<Path> packages) {
        var roots = new java.util.TreeSet<Path>();
        for (String include : manifest.getIncludes()) roots.add(request.projectPath().resolve(include).toAbsolutePath().normalize());
        roots.addAll(packages);
        for (Path source : units) roots.add(source.toAbsolutePath().getParent());
        var flags = manifest.getCompileFlags();
        for (int i = 0; i < flags.size(); i++) {
            String arg = flags.get(i);
            String include = null;
            if (java.util.List.of("-I", "-isystem", "-iquote", "-idirafter", "/I").contains(arg) && i + 1 < flags.size()) include = flags.get(++i);
            else if (arg.startsWith("-I") || arg.startsWith("/I")) include = arg.substring(2);
            if (include != null) roots.add(request.projectPath().resolve(include).toAbsolutePath().normalize());
        }
        var env = new java.util.TreeMap<>(System.getenv());
        env.putAll(manifest.getEnv());
        for (String name : java.util.List.of("CPATH", "C_INCLUDE_PATH", "CPLUS_INCLUDE_PATH", "INCLUDE"))
            for (String path : env.getOrDefault(name, "").split(java.util.regex.Pattern.quote(java.io.File.pathSeparator)))
                if (!path.isBlank()) roots.add(request.projectPath().resolve(path).toAbsolutePath().normalize());
        var nested = new java.util.ArrayList<Path>();
        for (Path root : roots)
            if (roots.stream().anyMatch(other -> !other.equals(root) && root.startsWith(other))) nested.add(root);
        roots.removeAll(nested);
        var stamps = new java.util.ArrayList<String>();
        for (Path root : roots) stamps.add(namespaces.computeIfAbsent(root, this::namespace));
        return FileHasher.hashCommand(stamps);
    }

    private String namespace(Path root) {

        try {
            var names = new java.util.ArrayList<String>();
            var sourceNames = new java.util.ArrayList<String>();
            boolean[] mayIncludeSource = {false};
            Path build = request.buildDir().toAbsolutePath().normalize();
            Path cacheRoot = request.options().cacheDirectory().toAbsolutePath().normalize();
            java.nio.file.Files.walkFileTree(root, java.util.EnumSet.of(java.nio.file.FileVisitOption.FOLLOW_LINKS), Integer.MAX_VALUE,
                    new java.nio.file.SimpleFileVisitor<>() {
                @Override public java.nio.file.FileVisitResult preVisitDirectory(Path path, java.nio.file.attribute.BasicFileAttributes attrs) {
                    String name = path.getFileName() == null ? "" : path.getFileName().toString();
                    return !path.equals(root) && (name.equals(".git") || name.equals(".buildgraph-state") || name.equals(".obj"))
                            || path.startsWith(build) && !root.startsWith(build)
                            || path.startsWith(cacheRoot) && !root.startsWith(cacheRoot)
                            ? java.nio.file.FileVisitResult.SKIP_SUBTREE : java.nio.file.FileVisitResult.CONTINUE;
                }
                @Override public java.nio.file.FileVisitResult visitFile(Path path, java.nio.file.attribute.BasicFileAttributes attrs) {
                    if (attrs.isRegularFile()) {
                        (SourceCollector.isSource(path) ? sourceNames : names).add(path.toString());
                        if (!mayIncludeSource[0]) mayIncludeSource[0] = mayIncludeSource(path);
                    }
                    return java.nio.file.FileVisitResult.CONTINUE;
                }
            });
            if (mayIncludeSource[0]) names.addAll(sourceNames);
            names.sort(String::compareTo);
            return FileHasher.hashCommand(names);
        } catch (java.nio.file.NoSuchFileException e) { return root + ":missing"; }
        catch (java.io.IOException e) { return root + ":unreadable:" + System.nanoTime(); }
    }

    private static final java.util.regex.Pattern INCLUDE = java.util.regex.Pattern.compile("^\\s*#\\s*(?:include|include_next|import|embed)\\s*(.*)");
    private static boolean mayIncludeSource(Path path) {

        try (var reader = java.nio.file.Files.newBufferedReader(path)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains("__has_include") || line.contains("__has_embed")) return true;
                var include = INCLUDE.matcher(line);
                if (!include.find()) continue;
                String value = include.group(1).trim();
                if (!(value.startsWith("\"") || value.startsWith("<"))) return true;
                int end = value.indexOf(value.startsWith("\"") ? '"' : '>', 1);
                if (end < 0 || SourceCollector.isSource(Path.of(value.substring(1, end)))) return true;
            }
            return false;
        } catch (java.io.IOException | RuntimeException e) { return true; }
    }

    @Override public void close() {
        scheduler.close();
        directoryLock.close();
        cache.prune();
        history.save();
        metrics.report(output, scheduler.peak(), resources.budget(), resources.workers());
    }
}
