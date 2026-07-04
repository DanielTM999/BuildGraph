package dtm.builder.build.incremental;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class DepFileParser {

    private DepFileParser() {
    }

    /** Dependencias do primeiro alvo de um .d estilo Make; null se ilegivel. */
    public static List<Path> parseSafe(Path depFile) {
        return parseSafe(depFile, null);
    }

    public static List<Path> parseSafe(Path depFile, Path workingDirectory) {
        try {
            List<Path> dependencies = parse(depFile);
            if (workingDirectory == null) {
                return dependencies;
            }
            List<Path> resolved = new ArrayList<>(dependencies.size());
            for (Path dependency : dependencies) {
                resolved.add(dependency.isAbsolute() ? dependency
                        : workingDirectory.resolve(dependency).normalize());
            }
            return resolved;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    public static List<Path> parse(Path depFile) throws IOException {
        String text = Files.readString(depFile);
        text = text.replace("\\\r\n", " ").replace("\\\n", " ");
        List<Path> deps = new ArrayList<>();
        for (String line : text.split("\r?\n")) {
            if (line.isBlank()) {
                continue;
            }
            int sep = targetSeparator(line);
            if (sep < 0) {
                continue;
            }
            for (String token : tokenize(line.substring(sep + 1))) {
                deps.add(Path.of(token));
            }
            // a primeira regra e a real; as demais sao alvos phony gerados por -MP
            break;
        }
        return deps;
    }

    /** Primeiro ':' seguido de whitespace ou fim; drive letters ("C:\") nao separam. */
    private static int targetSeparator(String line) {
        for (int i = 0; i < line.length(); i++) {
            if (line.charAt(i) != ':') {
                continue;
            }
            if (i + 1 >= line.length()) {
                return i;
            }
            char next = line.charAt(i + 1);
            if (next == ' ' || next == '\t') {
                return i;
            }
        }
        return -1;
    }

    private static List<String> tokenize(String deps) {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < deps.length(); i++) {
            char c = deps.charAt(i);
            if (c == '\\' && i + 1 < deps.length() && deps.charAt(i + 1) == ' ') {
                current.append(' ');
                i++;
            } else if (c == ' ' || c == '\t') {
                flush(tokens, current);
            } else {
                current.append(c);
            }
        }
        flush(tokens, current);
        return tokens;
    }

    private static void flush(List<String> tokens, StringBuilder current) {
        if (!current.isEmpty()) {
            tokens.add(current.toString());
            current.setLength(0);
        }
    }
}
