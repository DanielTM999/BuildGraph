package dtm.builder.build.cache;

import dtm.builder.build.*;
import dtm.builder.build.incremental.*;
import dtm.builder.manifest.model.ManifestRootModel;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public final class PreprocessedObjectCache {
    public record Prepared(String key, Path input, List<String> command, List<Path> dependencies,
                           long scanStartedMillis, String sourceHash) implements AutoCloseable {
        @Override public void close() { try { Files.deleteIfExists(input); } catch (IOException ignored) { } }
    }
    private final SharedObjectCache storage;
    private final FileHasher hasher;
    private final ConcurrentHashMap<String, CompletableFuture<String>> compilers = new ConcurrentHashMap<>();
    private final Set<String> clangCompilers = ConcurrentHashMap.newKeySet();

    public PreprocessedObjectCache(SharedObjectCache storage, FileHasher hasher) { this.storage = storage; this.hasher = hasher; }
    public boolean restore(Prepared prepared, Path object) { return prepared.key() != null && storage.restore(prepared.key(), object); }
    public void store(Prepared prepared, Path object) { if (prepared.key() != null) storage.store(prepared.key(), object); }
    public void prune() { storage.prune(); }
    public String fingerprint(BuildRequest req, ManifestRootModel manifest, String executable, String environment) {
        return compilerIdentity(req, manifest, executable, environment);
    }

    public Prepared prepare(BuildRequest req, ManifestRootModel manifest, Path source, Path object,
                            Path depfile, List<String> original, String environment, BuildMetrics metrics, java.util.function.Consumer<String> output) {
        String extension = source.getFileName().toString().replaceFirst("^.*\\.", "").toLowerCase(Locale.ROOT);

        boolean cpp = !extension.equals("c") || source.getFileName().toString().endsWith(".C");
        if (!req.options().sharedCache() || req.options().cacheSize() == 0 || req.executor() != ProcessExecutor.REAL
                || !Set.of("c", "cpp", "cc", "cxx", "c++").contains(extension)
                || !supported(original, source)) {
            metrics.add("cache_bypassed", 1);
            return null;
        }
        Path input = object.resolveSibling(object.getFileName() + (cpp ? ".cache.ii" : ".cache.i"));
        try {
            String compiler = compilerIdentity(req, manifest, original.getFirst(), environment);
            if (compiler == null || Thread.currentThread().isInterrupted()) { metrics.add("cache_bypassed", 1); return null; }
            List<String> preprocessing = new ArrayList<>(original);
            preprocessing.remove("-c");
            preprocessing.remove("-MMD");
            if (!preprocessing.contains("-MD")) preprocessing.add("-MD");
            preprocessing.set(preprocessing.indexOf("-o") + 1, input.toString());
            preprocessing.add("-E");
            long start = System.currentTimeMillis();
            String sourceHash = new FileHasher().sha256(source);
            long scanning = metrics.start();
            int exit;
            try { exit = req.executor().run(preprocessing, req.projectPath(), manifest.getEnv(), output); }
            finally { metrics.elapsed("dependencies", scanning); }
            if (exit != 0 || Thread.currentThread().isInterrupted()) return discard(input);
            List<Path> dependencies = DepFileParser.parseSafe(depfile, req.projectPath());
            if (dependencies == null || dependencies.isEmpty()) return discard(input);

            List<String> compile = new ArrayList<>();
            for (int i = 0; i < original.size(); i++) {
                String arg = original.get(i);
                if (arg.equals("-MF")) { i++; continue; }
                if (List.of("-MMD", "-MD", "-MP").contains(arg)) continue;
                if (arg.equals(source.toString())) { compile.add(input.toString()); continue; }
                if (List.of("-I", "-D", "-U", "-include", "-imacros", "-isystem", "-iquote", "-idirafter").contains(arg)) { i++; continue; }
                if (arg.startsWith("-I") || arg.startsWith("-D") || arg.startsWith("-U")) continue;
                compile.add(arg);
            }
            compile.add(1, "-x");
            compile.add(2, cpp ? "c++-cpp-output" : "cpp-output");
            boolean mapped = original.contains("-ffile-prefix-map=" + req.projectPath() + "=.")
                    || original.contains("-ffile-prefix-map=" + req.projectPath().toString().replace('\\', '/') + "=.");
            List<String> keyParts = new ArrayList<>(List.of("preprocessed-v2", compiler, environment,
                    System.getProperty("os.name"), System.getProperty("os.arch")));
            if (!mapped) keyParts.add(req.projectPath().toAbsolutePath().normalize().toString());
            for (String arg : compile) {
                if (mapped) arg = arg.replace(req.projectPath().toString(), "<project>")
                        .replace(req.projectPath().toString().replace('\\', '/'), "<project>");
                keyParts.add(arg);
            }
            String preprocessed = preprocessedHash(input, mapped ? req.projectPath() : null);
            if (preprocessed == null) {
                metrics.add("cache_bypassed", 1);
                return new Prepared(null, input, List.copyOf(compile), dependencies, start, sourceHash);
            }
            if (clangCompilers.contains(original.getFirst() + "|" + environment)) {
                List<String> expandedCommand = new ArrayList<>(compile); expandedCommand.add("-###");
                String expanded = probe(req, manifest, expandedCommand);

                if (expanded.matches("(?s).*(-load|plugin|include-pch|fmodule|sanitize|fprofile|coverage|split-dwarf|stack-usage).*"))
                    return discard(input);
                if (mapped) expanded = expanded.replace(req.projectPath().toString().replace("\\", "\\\\"), "<project>")
                        .replace(req.projectPath().toString().replace('\\', '/'), "<project>");
                keyParts.add(expanded);
            }
            keyParts.add(preprocessed);
            keyParts.add(sourceHash);
            for (Path dependency : dependencies.stream().distinct().sorted().toList()) {
                String path = dependency.toString();
                if (mapped) path = path.replace(req.projectPath().toString(), "<project>")
                        .replace(req.projectPath().toString().replace('\\', '/'), "<project>");
                keyParts.add(path);
                keyParts.add(hasher.sha256(dependency));
            }
            return new Prepared(FileHasher.hashCommand(keyParts), input, List.copyOf(compile), dependencies, start, sourceHash);
        } catch (IOException | RuntimeException e) { return discard(input); }
    }

    private static Prepared discard(Path input) {
        try { Files.deleteIfExists(input); } catch (IOException ignored) { }
        return null;
    }

    private String compilerIdentity(BuildRequest req, ManifestRootModel manifest, String executable, String environment) {
        String id = executable + "|" + environment;
        CompletableFuture<String> candidate = new CompletableFuture<>();
        CompletableFuture<String> prior = compilers.putIfAbsent(id, candidate);
        if (prior != null) {
            try { return prior.get(); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); return null; }
            catch (java.util.concurrent.ExecutionException e) { return null; }
        }
        try {
            List<String> pieces = new ArrayList<>();
            pieces.add(hasher.sha256(Path.of(executable)));
            String version = probe(req, manifest, executable, "--version");
            String lower = version.toLowerCase(Locale.ROOT);
            if (lower.contains("clang")) clangCompilers.add(id);
            if (!lower.contains("clang") && !lower.contains("gcc") && !lower.contains("g++")
                    && !lower.contains("free software foundation")) throw new IOException("Unsupported compiler");
            pieces.add(version);
            pieces.add(probe(req, manifest, executable, "-dumpmachine"));
            if (!lower.contains("clang")) {
                pieces.add(probe(req, manifest, executable, "-dumpspecs"));
                for (String name : List.of("cc1", "cc1plus", "as")) {
                    String tool = probe(req, manifest, executable, "-print-prog-name=" + name).trim();
                    Path path = Path.of(tool);
                    if (!path.isAbsolute()) path = ToolProbe.findOnPath(tool);
                    if (path == null || !Files.isRegularFile(path)) throw new IOException("Missing compiler component " + name);
                    pieces.add(hasher.sha256(path));
                }
            }
            String result = FileHasher.hashCommand(pieces);
            candidate.complete(result);
            return result;
        } catch (IOException | RuntimeException e) { candidate.complete(null); return null; }
    }
    private static String probe(BuildRequest req, ManifestRootModel manifest, String compiler, String flag) throws IOException {
        return probe(req, manifest, List.of(compiler, flag));
    }
    private static String probe(BuildRequest req, ManifestRootModel manifest, List<String> command) throws IOException {
        StringBuilder result = new StringBuilder();
        int exit = req.executor().run(command, req.projectPath(), manifest.getEnv(), line -> result.append(line).append('\n'));
        if (exit != 0 || Thread.currentThread().isInterrupted()) throw new IOException("Compiler identity unavailable");
        return result.toString();
    }

    static String preprocessedHash(Path input, Path mappedRoot) throws IOException {
        MessageDigest digest;
        try { digest = MessageDigest.getInstance("SHA-256"); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
        try (BufferedReader reader = Files.newBufferedReader(input, StandardCharsets.UTF_8)) {
            String line;
            AsmSafety assembly = new AsmSafety();
            while ((line = reader.readLine()) != null) {

                if (line.contains(".incbin") || line.contains(".include") || line.contains("pch_preprocess"))
                    return null;
                if (!assembly.accept(line)) return null;
                if (mappedRoot != null && line.startsWith("# ")) {
                    String root = mappedRoot.toString();
                    line = line.replace(root.replace("\\", "\\\\"), ".")
                            .replace(root.replace('\\', '/'), ".");
                }
                digest.update(line.getBytes(StandardCharsets.UTF_8));
                digest.update((byte) '\n');
            }
            if (!assembly.finished()) return null;
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static final class AsmSafety {
        private static final java.util.regex.Pattern ASM = java.util.regex.Pattern.compile("\\b(?:asm|__asm|__asm__)\\b");
        private final StringBuilder clause = new StringBuilder();
        private boolean collecting, opened, quoted, escaped, expectTemplate;
        private int depth;
        boolean accept(String line) {

            if (line.startsWith("# ")) return true;
            int cursor = 0;
            while (cursor < line.length()) {
                if (!collecting) {
                    var match = ASM.matcher(line);
                    if (!match.find(cursor)) return true;
                    cursor = match.end(); collecting = true;
                }
                while (cursor < line.length() && collecting) {
                    char c = line.charAt(cursor++); clause.append(c);
                    if (expectTemplate && !Character.isWhitespace(c)) {
                        if (c != '"') return false;
                        expectTemplate = false;
                    }

                    if (c == '.' || clause.length() > 65536) return false;
                    if (escaped) {
                        if (Character.isDigit(c) || "xuU".indexOf(c) >= 0) return false;
                        escaped = false; continue;
                    }
                    if (quoted && c == '\\') { escaped = true; continue; }
                    if (c == '"') {
                        if (!quoted && clause.length() >= 2 && clause.charAt(clause.length() - 2) == 'R') return false;
                        quoted = !quoted; continue;
                    }
                    if (quoted) continue;
                    if (c == '(') { if (!opened) expectTemplate = true; opened = true; depth++; }
                    if (c == ')' && --depth == 0 && opened) {
                        collecting = false; opened = false; clause.setLength(0);
                    }
                }
            }
            return true;
        }
        boolean finished() { return !collecting; }
    }

    private static boolean supported(List<String> command, Path source) {
        for (int i = 1; i < command.size(); i++) {
            String arg = command.get(i);
            if (arg.equals(source.toString())) continue;
            if (Set.of("-o", "-MF", "-I", "-D", "-U", "-include", "-imacros", "-isystem", "-iquote", "-idirafter").contains(arg)) {
                if (++i >= command.size()) return false;
                continue;
            }
            if (Set.of("-c", "-MMD", "-MD", "-MP", "-pthread", "-pipe", "-ansi", "-pedantic", "-pedantic-errors", "-nostdinc", "-nostdinc++").contains(arg)) continue;
            if (arg.startsWith("-D") || arg.startsWith("-U") || arg.startsWith("-I") || arg.startsWith("-std=")
                    || arg.startsWith("--target=") || arg.startsWith("--sysroot=") || arg.startsWith("--driver-mode=")) continue;
            if (arg.matches("-O([0-3sgz]|fast)") || arg.matches("-g([0-3]|gdb[0-3]?|dwarf-[2-5])?")
                    || arg.matches("-W[a-zA-Z0-9_=+\\-]+")) continue;
            if (arg.matches("-m(32|64|sse[0-9.]*|avx[0-9a-z]*|no-[a-z0-9-]+|(arch|cpu|tune|abi|fpu|float-abi)=[a-zA-Z0-9_.+-]+)")
                    && !arg.endsWith("=native")) continue;
            if (arg.matches("-f(no-)?(exceptions|rtti|strict-aliasing|omit-frame-pointer|common|function-sections|data-sections|wrapv|fast-math|PIC|pic|PIE|pie)")
                    || arg.matches("-fvisibility=(hidden|default|protected|internal)")
                    || arg.startsWith("-ffile-prefix-map=") || arg.startsWith("-fdebug-prefix-map=") || arg.startsWith("-fmacro-prefix-map=")) continue;
            return false;
        }
        return true;
    }
}
