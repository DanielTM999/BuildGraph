package dtm.builder.build;

import com.fasterxml.jackson.databind.ObjectMapper;
import dtm.builder.build.graph.ResolvedTarget;
import dtm.builder.build.graph.TargetResolver;
import dtm.builder.manifest.model.ManifestRootModel;
import dtm.builder.repo.SafeZipExtractor;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/** Paths and owned outputs, also retained across outputName changes for selective clean. */
public final class NativeArtifacts {
    private static final ObjectMapper JSON = new ObjectMapper();
    private NativeArtifacts() { }

    public static Path artifact(Path buildDir, ManifestRootModel root, ResolvedTarget target, boolean msvc) {
        ManifestRootModel m = TargetResolver.perTargetManifest(root, target);
        TargetPlatform p = TargetPlatform.resolve(m.getPlatform(), null);
        boolean libMsvc = msvcFormat(m, msvc);
        String file = NativeTools.specified(m.getOutputName()) ? m.getOutputName()
                : p.fileName(target.name(), target.type(), libMsvc);
        Path relative = Path.of(file);
        if (relative.isAbsolute() || relative.normalize().startsWith("..") || file.isBlank())
            throw new IllegalArgumentException("outputName deve estar dentro do diretorio de build: " + file);
        Path output = buildDir.resolve(relative).normalize();
        String first = relative.normalize().getName(0).toString().toLowerCase(java.util.Locale.ROOT);
        if (first.startsWith(".buildgraph") || first.equals(".obj") || first.equals(".clangd")
                || output.toAbsolutePath().equals(buildDir.toAbsolutePath().normalize()))
            throw new IllegalArgumentException("outputName reservado: " + file);
        return output;
    }

    private static boolean msvcFormat(ManifestRootModel m, boolean msvc) {
        TargetPlatform p = TargetPlatform.resolve(m.getPlatform(), null);
        String asmName = NativeTools.specified(m.getAsmCompiler()) ? NativeTools.name(Path.of(m.getAsmCompiler())) : "";
        String linkerName = NativeTools.specified(m.getLinker()) ? NativeTools.name(Path.of(m.getLinker())) : "";
        return p.windows() && (msvc || "msvc".equalsIgnoreCase(m.getLinkerKind())
                || "masm".equalsIgnoreCase(m.getAsmKind()) || p.triple().contains("msvc")
                || asmName.equals("ml") || asmName.equals("ml64") || linkerName.equals("link") || linkerName.equals("lld-link"));
    }

    public static List<Path> declaredOutputs(Path buildDir, ManifestRootModel root, ResolvedTarget target, boolean msvc) {
        Path output = artifact(buildDir, root, target, msvc);
        List<Path> outputs = new ArrayList<>(List.of(output));
        ManifestRootModel m = TargetResolver.perTargetManifest(root, target);
        if (msvcFormat(m, msvc)) {
            if (target.type() == TargetType.EXECUTABLE || target.type() == TargetType.SHARED)
                outputs.add(output.resolveSibling(output.getFileName() + ".pdb"));
            if (target.type() == TargetType.SHARED) {
                outputs.add(NativeTargetBuilder.importLibrary(output));
                outputs.add(output.resolveSibling(output.getFileName().toString().replaceFirst("(?i)\\.dll$", "") + ".exp"));
            }
        }
        return outputs;
    }

    private static Path inventory(Path buildDir, String id) {
        requireId(id);
        return buildDir.resolve(".buildgraph-outputs").resolve(id + ".json");
    }
    public static void requireId(String id) {
        if (id == null || !id.matches("[A-Za-z0-9_][A-Za-z0-9_.-]*") || id.equals(".") || id.equals(".."))
            throw new IllegalArgumentException("Id de target invalido: " + id);
    }

    public static synchronized void record(Path buildDir, String id, List<Path> outputs) throws IOException {
        Path root = buildDir.toAbsolutePath().normalize();
        LinkedHashSet<String> owned = new LinkedHashSet<>(read(buildDir, id));
        for (Path output : outputs) {
            Path path = output.toAbsolutePath().normalize();
            if (!path.startsWith(root) || path.equals(root)) throw new IOException("Saida fora de build: " + output);
            owned.add(root.relativize(path).toString());
        }
        Path file = inventory(buildDir, id);
        Files.createDirectories(file.getParent());
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        JSON.writeValue(tmp.toFile(), owned);
        Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
    }

    private static List<String> read(Path buildDir, String id) throws IOException {
        Path file = inventory(buildDir, id);
        return Files.isRegularFile(file) ? List.of(JSON.readValue(file.toFile(), String[].class)) : List.of();
    }

    public static void clean(Path buildDir, ManifestRootModel root, ResolvedTarget target, boolean msvc) throws IOException {
        clean(buildDir, root, target, msvc, java.util.Set.of());
    }

    public static void clean(Path buildDir, ManifestRootModel root, ResolvedTarget target, boolean msvc,
                             java.util.Set<Path> preserved) throws IOException {
        Path base = buildDir.toAbsolutePath().normalize();
        List<Path> paths = new ArrayList<>();
        for (String stored : read(buildDir, target.id())) paths.add(base.resolve(stored).normalize());
        for (Path output : declaredOutputs(buildDir, root, target, msvc)) paths.add(output.toAbsolutePath().normalize());
        paths.add(base.resolve(".obj").resolve(target.id()).normalize());
        paths.add(dtm.builder.build.incremental.BuildStateStore.stateFile(base, target.id()));
        paths.add(inventory(base, target.id()));
        for (Path path : paths) {
            if (!path.startsWith(base) || path.equals(base)) throw new IOException("Clean recusou caminho fora de build: " + path);
            if (preserved.contains(path)) continue;
            // Never traverse a directory symlink to remove another target's/external output.
            for (Path parent = path.getParent(); parent != null && !parent.equals(base); parent = parent.getParent())
                if (Files.isSymbolicLink(parent)) throw new IOException("Clean recusou diretorio simbolico: " + parent);
            if (Files.isSymbolicLink(path)) Files.deleteIfExists(path);
            else if (Files.isDirectory(path)) SafeZipExtractor.deleteTree(path);
            else Files.deleteIfExists(path);
        }
    }
}
