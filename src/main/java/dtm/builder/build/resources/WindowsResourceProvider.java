package dtm.builder.build.resources;

import dtm.builder.build.BuildRequest;
import dtm.builder.build.TargetPlatform;
import dtm.builder.build.Toolchain;
import dtm.builder.manifest.model.ManifestRootModel;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;

final class WindowsResourceProvider implements ExecutableResourceProvider {
    public Path tool(TargetPlatform platform, Toolchain compiler, boolean msvcLink, Path project) throws IOException {
        Path driver = compiler == null ? null : compiler.driver(false);
        String prefix = platform.nativeDestination() ? "" : platform.triple() + "-";
        Path found = msvcLink
                ? ExecutableResourceBuilder.findTool(driver, "rc", "llvm-rc")
                : ExecutableResourceBuilder.findTool(driver, prefix + "windres", prefix + "llvm-windres",
                        "windres", "llvm-windres");
        if (found == null) throw new IOException("Compilador de recursos Windows indisponivel para " + platform.triple()
                + " (windres, llvm-windres, rc ou llvm-rc)");
        return found;
    }
    public Output output(Path dir, boolean icon, boolean msvcLink, boolean driverLink) {
        Path compiled = dir.resolve(msvcLink ? "resources.res" : "resources.o");
        List<Path> files = new ArrayList<>(List.of(dir.resolve("resources.rc"), compiled));
        if (icon) files.add(dir.resolve("icon.ico"));
        return new Output(files, List.of(compiled), List.of());
    }
    public void generate(BuildRequest req, ManifestRootModel manifest, TargetPlatform platform,
                         Toolchain compiler, Path tool, Path icon, Map<String, String> metadata,
                         Path dir, Output output, Consumer<String> log) throws IOException {
        if (icon != null) ExecutableResourceBuilder.write(dir.resolve("icon.ico"), IconEncoders.ico(IconConverter.read(icon)));
        Path rc = dir.resolve("resources.rc");
        ExecutableResourceBuilder.write(rc, script(icon != null, metadata));
        Path compiled = output.inputs().getFirst();
        String name = tool.getFileName().toString().toLowerCase();
        List<String> command = new ArrayList<>(List.of(tool.toString()));
        if (name.contains("windres")) {
            command.addAll(List.of("--input-format=rc", "--output-format=" + (compiled.toString().endsWith(".res") ? "res" : "coff"),
                    "--codepage=65001", "--target=" + switch (platform.arch()) {
                        case "x86" -> "pe-i386";
                        case "x86_64" -> "pe-x86-64";
                        case "aarch64" -> "pe-aarch64";
                        default -> throw new IOException("Arquitetura Windows sem recurso COFF: " + platform.arch());
                    }, "-I", dir.toString(), "-i", rc.toString(), "-o", compiled.toString()));
        } else command.addAll(List.of("/nologo", "/i", dir.toString(), "/fo" + compiled, rc.toString()));
        ExecutableResourceBuilder.run(req, manifest, command, log);
    }
    static String script(boolean icon, Map<String, String> metadata) {
        StringBuilder s = new StringBuilder("#pragma code_page(65001)\n");
        if (icon) s.append("1 ICON \"icon.ico\"\n");
        if (metadata.isEmpty()) return s.toString();
        String fv = metadata.getOrDefault("fileVersion", "0");
        String pv = metadata.getOrDefault("productVersion", fv);
        s.append("1 VERSIONINFO\n FILEVERSION ").append(numeric(fv)).append("\n PRODUCTVERSION ")
                .append(numeric(pv)).append("\n FILEFLAGSMASK 0x3fL\n FILEFLAGS 0x0L\n FILEOS 0x40004L\n FILETYPE 0x1L\n FILESUBTYPE 0x0L\nBEGIN\n BLOCK \"StringFileInfo\"\n BEGIN\n BLOCK \"040904B0\"\n BEGIN\n");
        Map<String, String> names = Map.ofEntries(Map.entry("productName", "ProductName"),
                Map.entry("companyName", "CompanyName"), Map.entry("description", "FileDescription"),
                Map.entry("fileVersion", "FileVersion"), Map.entry("productVersion", "ProductVersion"),
                Map.entry("copyright", "LegalCopyright"), Map.entry("trademark", "LegalTrademarks"),
                Map.entry("internalName", "InternalName"), Map.entry("originalFilename", "OriginalFilename"),
                Map.entry("comments", "Comments"));
        new TreeMap<>(metadata).forEach((key, value) -> {
            if (value != null) s.append(" VALUE \"").append(escape(names.getOrDefault(key, key)))
                    .append("\", \"").append(escape(value)).append("\\0\"\n");
        });
        return s.append(" END\n END\n BLOCK \"VarFileInfo\"\n BEGIN\n VALUE \"Translation\", 0x0409, 1200\n END\nEND\n").toString();
    }
    private static String numeric(String version) {
        String[] parts = version.split("\\.");
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < 4; i++) {
            String part = i < parts.length ? parts[i].replaceFirst("[^0-9].*$", "") : "";
            long value = part.isEmpty() ? 0 : Long.parseLong(part);
            if (value > 65535) throw new IllegalArgumentException("Componente de versao acima de 65535: " + version);
            if (i > 0) out.append(',');
            out.append(value);
        }
        return out.toString();
    }
    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }
}
