package dtm.builder.build.resources;

import dtm.builder.build.BuildRequest;
import dtm.builder.build.TargetPlatform;
import dtm.builder.build.Toolchain;
import dtm.builder.manifest.model.ManifestRootModel;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.Locale;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

final class LinuxResourceProvider implements ExecutableResourceProvider {
    public Path tool(TargetPlatform platform, Toolchain compiler, boolean msvcLink, Path project) throws IOException {
        if (compiler == null || compiler.driver(false) == null)
            throw new IOException("Compiler C/ASM necessario para recursos ELF de " + platform.triple());
        return compiler.driver(false);
    }
    public Output output(Path dir, boolean icon, boolean msvcLink, boolean driverLink) {
        List<Path> files = new ArrayList<>(List.of(dir.resolve("metadata.json"), dir.resolve("resources.s"), dir.resolve("resources.o")));
        if (icon) files.add(dir.resolve("icon.png"));
        return new Output(files, List.of(dir.resolve("resources.o")), List.of());
    }
    public void generate(BuildRequest req, ManifestRootModel manifest, TargetPlatform platform,
                         Toolchain compiler, Path tool, Path icon, Map<String, String> metadata,
                         Path dir, Output output, Consumer<String> log) throws IOException {
        Path json = dir.resolve("metadata.json");
        ExecutableResourceBuilder.write(json, ExecutableResourceBuilder.json(metadata));
        Path png = dir.resolve("icon.png");
        if (icon != null) {
            var image = IconConverter.read(icon);
            if (icon.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".png"))
                Files.copy(icon, png, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            else ExecutableResourceBuilder.write(png, IconConverter.png(image, Math.max(image.getWidth(), image.getHeight())));
        }
        StringBuilder asm = new StringBuilder();
        if (!metadata.isEmpty()) section(asm, ".buildgraph.metadata", json);
        if (icon != null) section(asm, ".buildgraph.icon", png);
        asm.append(".section .note.GNU-stack,\"\",@progbits\n");
        Path source = dir.resolve("resources.s");
        ExecutableResourceBuilder.write(source, asm.toString());
        List<String> command = new ArrayList<>(List.of(tool.toString()));
        if (tool.getFileName().toString().contains("clang") && !platform.nativeDestination())
            command.add("--target=" + platform.triple());
        command.addAll(List.of("-c", "-x", "assembler", source.toString(), "-o", output.inputs().getFirst().toString()));
        ExecutableResourceBuilder.run(req, manifest, command, log);
    }
    private static void section(StringBuilder asm, String name, Path file) {
        asm.append(".section ").append(name).append(",\"aR\",@progbits\n.incbin \"")
                .append(file.toAbsolutePath().toString().replace("\\", "/").replace("\"", "\\\""))
                .append("\"\n");
    }
}
