package dtm.builder.build.incremental;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extrai os headers reportados por /showIncludes. O prefixo da linha e
 * localizado ("Note: including file:" em ingles, "Observacao: incluindo
 * arquivo:" em pt-BR, possivelmente corrompido pelo encoding OEM), entao ele
 * nunca e comparado com um literal: e detectado na primeira linha cujo resto
 * seja um path absoluto existente e reutilizado nas linhas seguintes.
 */
public final class MsvcIncludeParser {

    private static final Pattern CANDIDATE =
            Pattern.compile("^(\\D{1,120}?:)\\s+([A-Za-z]:[\\\\/].+)$");

    private String prefix;
    private final List<Path> includes = new ArrayList<>();

    public MsvcIncludeParser() {
        this(null);
    }

    public MsvcIncludeParser(String knownPrefix) {
        this.prefix = knownPrefix;
    }

    /** true se a linha era de include (consumida e nao deve ser exibida). */
    public boolean offer(String line) {
        if (line == null || line.isEmpty()) {
            return false;
        }
        if (prefix != null) {
            if (!line.startsWith(prefix)) {
                return false;
            }
            Path path = toPath(line.substring(prefix.length()).trim());
            if (path == null) {
                return false;
            }
            includes.add(path);
            return true;
        }
        Matcher m = CANDIDATE.matcher(line);
        if (!m.matches()) {
            return false;
        }
        Path candidate = toPath(m.group(2).trim());
        if (candidate == null || !Files.isRegularFile(candidate)) {
            return false;
        }
        prefix = m.group(1);
        includes.add(candidate);
        return true;
    }

    public List<Path> includes() {
        return includes;
    }

    public String prefix() {
        return prefix;
    }

    private static Path toPath(String raw) {
        try {
            return Path.of(raw);
        } catch (InvalidPathException e) {
            return null;
        }
    }
}
