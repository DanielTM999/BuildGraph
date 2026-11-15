package dtm.builder.build.resources;

import com.fasterxml.jackson.databind.ObjectMapper;
import dtm.builder.build.BuildRequest;
import dtm.builder.build.TargetPlatform;
import dtm.builder.build.ToolProbe;
import dtm.builder.build.Toolchain;
import dtm.builder.build.incremental.FileHasher;
import dtm.builder.manifest.model.ManifestRootModel;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;

public final class ExecutableResourceBuilder {
    private static final String GENERATOR_VERSION = "1";
    private static final ObjectMapper JSON = new ObjectMapper();

    public record Result(List<Path> inputs, List<String> linkFlags, String fingerprint) {
        public static Result empty() { return new Result(List.of(), List.of(), "resources=none"); }
    }

    public Result build(BuildRequest req, ManifestRootModel manifest, TargetPlatform platform,
                        Toolchain compiler, boolean msvcLink, boolean driverLink, Path objDir,
                        Consumer<String> log) throws IOException {
        String icon = manifest.getIcon();
        Map<String, String> declared = manifest.getMetadata();
        if ((icon == null || icon.isBlank()) && declared.values().stream().allMatch(v -> v == null))
            return Result.empty();
        Path source = icon == null || icon.isBlank() ? null : req.projectPath().resolve(icon).normalize();
        if (source != null && !Files.isRegularFile(source)) throw new IOException("icon inexistente: " + source);
        Map<String, String> metadata = effectiveMetadata(manifest);
        Path dir = objDir.resolve("resources");
        Files.createDirectories(dir);
        ExecutableResourceProvider provider = platform.windows() ? new WindowsResourceProvider()
                : platform.os().equals("linux") ? new LinuxResourceProvider()
                : platform.darwin() ? new MacOSResourceProvider() : null;
        if (provider == null) throw new IOException("Recursos de executavel indisponiveis para " + platform.triple());
        Path tool = provider.tool(platform, compiler, msvcLink, req.projectPath());
        String fingerprint = hash(List.of(GENERATOR_VERSION, platform.triple(), Boolean.toString(msvcLink),
                compiler == null ? "" : compiler.displayName(), tool == null ? "" : tool.toString(),
                tool == null || !Files.isRegularFile(tool) ? "" : new FileHasher().sha256(tool),
                source == null ? "" : hash(Files.readAllBytes(source)), JSON.writeValueAsString(new TreeMap<>(metadata))));
        Path state = dir.resolve("resource-state.txt");
        ExecutableResourceProvider.Output output = provider.output(dir, source != null, msvcLink, driverLink);
        if (req.incremental() && Files.isRegularFile(state) && fingerprint.equals(Files.readString(state))
                && output.files().stream().allMatch(Files::isRegularFile))
            return new Result(output.inputs(), output.linkFlags(), fingerprint);
        provider.generate(req, manifest, platform, compiler, tool, source, metadata, dir, output, log);
        for (Path file : output.files()) if (!Files.isRegularFile(file))
            throw new IOException("Gerador de recursos nao produziu " + file);
        Files.writeString(state, fingerprint, StandardCharsets.UTF_8);
        return new Result(output.inputs(), output.linkFlags(), fingerprint);
    }

    private static Map<String, String> effectiveMetadata(ManifestRootModel m) {
        Map<String, String> all = new TreeMap<>(m.getMetadata());
        defaultValue(all, "productName", m.getName());
        defaultValue(all, "description", m.getDescription());
        defaultValue(all, "fileVersion", m.getVersion());
        defaultValue(all, "productVersion", m.getVersion());
        all.values().removeIf(v -> v == null);
        return all;
    }
    private static void defaultValue(Map<String, String> map, String key, String value) {
        if (!map.containsKey(key) && value != null && !value.isBlank()) map.put(key, value);
    }
    static Path findTool(Path compiler, String... names) {
        Path parent = compiler == null ? null : compiler.getParent();
        for (String name : names) {
            if (parent != null) {
                Path sibling = parent.resolve(name + (ToolProbe.isWindows() ? ".exe" : ""));
                if (Files.isRegularFile(sibling)) return sibling;
            }
            Path found = ToolProbe.findOnPath(name);
            if (found != null) return found;
        }
        return null;
    }
    static void run(BuildRequest req, ManifestRootModel manifest, List<String> command,
                    Consumer<String> log) throws IOException {
        log.accept("+ " + String.join(" ", command));
        if (req.executor().run(command, req.projectPath(), manifest.getEnv(), log) != 0)
            throw new IOException("Falha ao compilar recursos: " + command.getFirst());
    }
    static void write(Path file, byte[] bytes) throws IOException { Files.write(file, bytes); }
    static void write(Path file, String value) throws IOException { Files.writeString(file, value, StandardCharsets.UTF_8); }
    static String hash(List<String> parts) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (String part : parts) {
                byte[] bytes = part.getBytes(StandardCharsets.UTF_8);
                digest.update((byte) (bytes.length >>> 24)); digest.update((byte) (bytes.length >>> 16));
                digest.update((byte) (bytes.length >>> 8)); digest.update((byte) bytes.length); digest.update(bytes);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    static String hash(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    static byte[] json(Map<String, String> map) throws IOException { return JSON.writeValueAsBytes(new TreeMap<>(map)); }
}
