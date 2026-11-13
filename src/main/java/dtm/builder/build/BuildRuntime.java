package dtm.builder.build;

import dtm.builder.build.cache.PreprocessedObjectCache;
import dtm.builder.build.cache.SharedObjectCache;
import dtm.builder.build.graph.ResolvedTarget;
import dtm.builder.build.incremental.CompilerFingerprint;
import dtm.builder.build.incremental.FileHasher;
import dtm.builder.manifest.model.ManifestRootModel;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Stream;

final class BuildRuntime implements AutoCloseable {
    final BuildMetrics metrics;
    final ResourceManager resources;
    final CompilationScheduler scheduler;
    final FileHasher hasher = new FileHasher();
    final PreprocessedObjectCache cache;
    final MemoryHistory history;
    private final BuildDirectoryLock directoryLock;
    private final BuildRequest request;
    private final Object outputLock = new Object();
    final Consumer<String> output;
    private final ConcurrentHashMap<String, List<Path>> sources = new ConcurrentHashMap<>();
    private volatile PackagePaths packages;

    BuildRuntime(BuildRequest request) {
        this.request = request;
        output = serialize(request.output());
        try { directoryLock = BuildDirectoryLock.acquire(request.buildDir()); }
        catch (IOException e) { throw new IllegalArgumentException(e.getMessage(), e); }
        metrics = new BuildMetrics(request.options().profile());
        resources = new ResourceManager(request.jobs(), request.options());
        scheduler = new CompilationScheduler(resources, metrics);
        cache = new PreprocessedObjectCache(
                new SharedObjectCache(request.options().cacheDirectory(), request.options().cacheSize()), hasher);
        history = new MemoryHistory(request.options().cacheDirectory().resolveSibling("memory-history.properties"));
    }

    Consumer<String> serialize(Consumer<String> consumer) {
        return line -> { synchronized (outputLock) { if (consumer != null) consumer.accept(line); } };
    }

    BiConsumer<String, Long> serialize(BiConsumer<String, Long> consumer) {
        return (message, started) -> { synchronized (outputLock) { consumer.accept(message, started); } };
    }

    List<Path> sources(ResolvedTarget target) {
        return sources.computeIfAbsent(target.id(), ignored -> {
            long start = metrics.start();
            try { return SourceCollector.collectSources(request.projectPath(), target.sources(), target.synthetic()); }
            finally { metrics.elapsed("discovery", start); }
        });
    }

    String environment(Map<String, String> overrides) {
        return BuildEnvironment.signature(System.getenv(), overrides);
    }

    String environment(Map<String, String> overrides, Toolchain compiler) {

        return compiler != null && compiler.kind() == ToolchainKind.CUSTOM
                ? BuildEnvironment.customSignature(System.getenv(), overrides) : environment(overrides);
    }

    String environment(ManifestRootModel manifest, Toolchain compiler) {
        String full = environment(manifest.getEnv(), compiler);
        if (compiler == null || compiler.isMsvc() || compiler.kind() == ToolchainKind.CUSTOM
                || request.executor() != ProcessExecutor.REAL) return full;
        List<String> drivers = Stream.of(compiler.cc(), compiler.cxx())
                .filter(Objects::nonNull).distinct().map(Path::toString).toList();
        for (String driver : drivers)
            if (!cache.resolvesTools(request, manifest, driver, full)) return full;
        String reduced = BuildEnvironment.resolvedToolSignature(System.getenv(), manifest.getEnv(), ToolProbe.isWindows());
        for (String driver : drivers) cache.alias(driver, full, reduced);
        return reduced;
    }

    String toolFingerprint(Path executable) {
        if (executable == null) return "-";
        try { return CompilerFingerprint.describe(executable) + ":" + hasher.sha256(executable); }
        catch (IOException e) { return CompilerFingerprint.describe(executable); }
    }

    String compilerFingerprint(Toolchain compiler, ManifestRootModel manifest) {
        if (compiler == null) return "none";
        var fingerprints = new ArrayList<String>();
        for (Path driver : Stream.of(compiler.cc(), compiler.cxx()).filter(Objects::nonNull).distinct().toList()) {
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
    String includeNamespace(ManifestRootModel manifest, List<Path> units, List<Path> packages) {
        var roots = new TreeSet<Path>();
        for (String include : manifest.getIncludes()) roots.add(request.projectPath().resolve(include).toAbsolutePath().normalize());
        roots.addAll(packages);
        for (Path source : units) roots.add(source.toAbsolutePath().getParent());
        var flags = manifest.getCompileFlags();
        for (int i = 0; i < flags.size(); i++) {
            String arg = flags.get(i);
            String include = null;
            if (List.of("-I", "-isystem", "-iquote", "-idirafter", "/I").contains(arg) && i + 1 < flags.size()) include = flags.get(++i);
            else if (arg.startsWith("-I") || arg.startsWith("/I")) include = arg.substring(2);
            if (include != null) roots.add(request.projectPath().resolve(include).toAbsolutePath().normalize());
        }
        var env = new TreeMap<>(System.getenv());
        env.putAll(manifest.getEnv());
        for (String name : List.of("CPATH", "C_INCLUDE_PATH", "CPLUS_INCLUDE_PATH", "INCLUDE"))
            for (String path : env.getOrDefault(name, "").split(Pattern.quote(File.pathSeparator)))
                if (!path.isBlank()) roots.add(request.projectPath().resolve(path).toAbsolutePath().normalize());
        var nested = new ArrayList<Path>();
        for (Path root : roots)
            if (roots.stream().anyMatch(other -> !other.equals(root) && root.startsWith(other))) nested.add(root);
        roots.removeAll(nested);
        var stamps = new ArrayList<String>();
        for (Path root : roots) stamps.add(namespaces.computeIfAbsent(root, this::namespace));
        return FileHasher.hashCommand(stamps);
    }

    private String namespace(Path root) {

        try {
            var names = new ArrayList<String>();
            var sourceNames = new ArrayList<String>();
            boolean[] mayIncludeSource = {false};
            Path build = request.buildDir().toAbsolutePath().normalize();
            Path cacheRoot = request.options().cacheDirectory().toAbsolutePath().normalize();
            Files.walkFileTree(root, EnumSet.of(FileVisitOption.FOLLOW_LINKS), Integer.MAX_VALUE,
                    new SimpleFileVisitor<>() {
                @Override public FileVisitResult preVisitDirectory(Path path, BasicFileAttributes attrs) {
                    String name = path.getFileName() == null ? "" : path.getFileName().toString();
                    return !path.equals(root) && (name.equals(".git") || name.equals(".buildgraph-state") || name.equals(".obj"))
                            || path.startsWith(build) && !root.startsWith(build)
                            || path.startsWith(cacheRoot) && !root.startsWith(cacheRoot)
                            ? FileVisitResult.SKIP_SUBTREE : FileVisitResult.CONTINUE;
                }
                @Override public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) {
                    if (attrs.isRegularFile()) {
                        (SourceCollector.isSource(path) ? sourceNames : names).add(path.toString());
                        if (!mayIncludeSource[0]) mayIncludeSource[0] = mayIncludeSource(path);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
            if (mayIncludeSource[0]) names.addAll(sourceNames);
            names.sort(String::compareTo);
            return FileHasher.hashCommand(names);
        } catch (NoSuchFileException e) { return root + ":missing"; }
        catch (IOException e) { return root + ":unreadable:" + System.nanoTime(); }
    }

    private static final Pattern INCLUDE = Pattern.compile("^\\s*#\\s*(?:include|include_next|import|embed)\\s*(.*)");
    private static boolean mayIncludeSource(Path path) {

        try (var reader = Files.newBufferedReader(path)) {
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
        } catch (IOException | RuntimeException e) { return true; }
    }

    @Override public void close() {
        scheduler.close();
        directoryLock.close();
        cache.prune();
        history.save();
        metrics.report(output, scheduler.peak(), resources.budget(), resources.workers());
    }
}
