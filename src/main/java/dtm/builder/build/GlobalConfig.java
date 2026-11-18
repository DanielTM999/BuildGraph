package dtm.builder.build;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Set;

public record GlobalConfig(Path file, Path cacheDirectory, Long cacheSize) {
    public static final String PROPERTY = "buildgraph.config";
    public static final String ENVIRONMENT = "BUILDGRAPH_CONFIG";
    private static final Set<String> KEYS = Set.of("cacheDir", "cacheSize");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static Path location() {
        String explicit = System.getProperty(PROPERTY);
        if (explicit == null || explicit.isBlank()) explicit = System.getenv(ENVIRONMENT);
        if (explicit != null && !explicit.isBlank()) return Path.of(explicit);
        String base = System.getenv("LOCALAPPDATA");
        if (ToolProbe.isWindows() && base != null && !base.isBlank())
            return Path.of(base, "BuildGraph", "config.json");
        String xdg = System.getenv("XDG_CONFIG_HOME");
        Path root = xdg != null && !xdg.isBlank() ? Path.of(xdg) : Path.of(System.getProperty("user.home"), ".config");
        return root.resolve("buildgraph").resolve("config.json");
    }

    public static GlobalConfig load() {
        return load(location());
    }

    public static GlobalConfig load(Path file) {
        if (!Files.isRegularFile(file)) return new GlobalConfig(file, null, null);
        JsonNode root;
        try {
            root = MAPPER.readTree(file.toFile());
        } catch (IOException e) {
            throw invalid(file, "JSON invalido (" + e.getMessage() + ")");
        }
        if (root == null || root.isMissingNode()) return new GlobalConfig(file, null, null);
        if (!root.isObject()) throw invalid(file, "o conteudo deve ser um objeto JSON");
        for (Iterator<String> names = root.fieldNames(); names.hasNext(); ) {
            String name = names.next();
            if (!KEYS.contains(name)) throw invalid(file, "chave desconhecida '" + name + "' (aceitas: cacheDir, cacheSize)");
        }
        return new GlobalConfig(file, directory(file, root.get("cacheDir")), size(file, root.get("cacheSize")));
    }

    private static Path directory(Path file, JsonNode node) {
        if (node == null || node.isNull()) return null;
        if (!node.isTextual() || node.asText().isBlank()) throw invalid(file, "cacheDir deve ser um caminho");
        String text = node.asText().trim();
        try {
            Path path = text.equals("~") || text.startsWith("~/") || text.startsWith("~\\")
                    ? Path.of(System.getProperty("user.home")).resolve(text.length() > 2 ? text.substring(2) : "")
                    : Path.of(text);
            if (path.isAbsolute()) return path.normalize();
            Path parent = file.toAbsolutePath().getParent();
            return (parent == null ? path : parent.resolve(path)).normalize();
        } catch (InvalidPathException e) {
            throw invalid(file, "cacheDir invalido: " + text);
        }
    }

    private static Long size(Path file, JsonNode node) {
        if (node == null || node.isNull()) return null;
        if (node.isIntegralNumber()) {
            if (!node.canConvertToLong() || node.asLong() < 0) throw invalid(file, "cacheSize invalido: " + node);
            return node.asLong();
        }
        if (!node.isTextual()) throw invalid(file, "cacheSize deve ser texto (ex.: \"10G\") ou bytes");
        String text = node.asText().trim();
        if (text.equals("0")) return 0L;
        try {
            return BuildOptions.bytes(text);
        } catch (IllegalArgumentException e) {
            throw invalid(file, "cacheSize invalido: " + text);
        }
    }

    private static IllegalArgumentException invalid(Path file, String message) {
        return new IllegalArgumentException("Config global " + file + ": " + message);
    }
}
