package dtm.builder.build;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.List;

final class ResponseFile implements AutoCloseable {
    private final Path file;
    private final List<String> command;
    private ResponseFile(Path file, List<String> command) { this.file = file; this.command = command; }
    List<String> command() { return command; }

    static ResponseFile prepare(List<String> command, Path directory) throws IOException {
        return prepare(command, directory, ToolProbe.isWindows() ? 24000 : 128000);
    }
    static ResponseFile prepare(List<String> command, Path directory, int threshold) throws IOException {
        long length = command.stream().mapToLong(arg -> arg.length() * 2L + 3).sum();
        String name = NativeTools.name(Path.of(command.getFirst()));
        boolean msvc = List.of("cl", "link", "lib", "lld-link", "llvm-lib").contains(name);
        boolean gnu = name.matches("(?:.*-)?(?:gcc|g\\+\\+|cc|c\\+\\+|clang|clang\\+\\+|ar|llvm-ar|ld|ld.lld|lld|objcopy|llvm-objcopy)(?:-[0-9.]+)?");
        if (length < threshold || !(msvc || gnu)) return new ResponseFile(null, command);
        Files.createDirectories(directory);
        Path file = Files.createTempFile(directory, ".buildgraph-args-", ".rsp").toAbsolutePath();
        try {
            StringBuilder arguments = new StringBuilder();
            for (String argument : command.subList(1, command.size())) {
                arguments.append(msvc ? windowsQuote(argument) : '"' + argument.replace("\\", "\\\\").replace("\"", "\\\"") + '"').append('\n');
            }
            if (msvc) {
                try (var stream = Files.newOutputStream(file)) {
                    stream.write(new byte[]{(byte) 0xff, (byte) 0xfe});
                    stream.write(arguments.toString().getBytes(StandardCharsets.UTF_16LE));
                }
            } else Files.writeString(file, arguments, StandardCharsets.UTF_8);
            return new ResponseFile(file, List.of(command.getFirst(), "@" + file));
        } catch (IOException | RuntimeException e) { Files.deleteIfExists(file); throw e; }
    }

    private static String windowsQuote(String argument) {
        StringBuilder result = new StringBuilder("\"");
        int slashes = 0;
        for (char c : argument.toCharArray()) {
            if (c == '\\') { slashes++; continue; }
            result.append("\\".repeat(c == '"' ? slashes * 2 + 1 : slashes)).append(c);
            slashes = 0;
        }
        return result.append("\\".repeat(slashes * 2)).append('"').toString();
    }
    @Override public void close() throws IOException { if (file != null) Files.deleteIfExists(file); }
}
