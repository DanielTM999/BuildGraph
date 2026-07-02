package dtm.bulder.build;

import java.util.regex.Pattern;

public final class BuildDiagnosticParser {

    public enum Level {
        ERROR,
        WARNING,
        NOTE,
        PLAIN
    }

    private static final Pattern GCC_CLANG =
            Pattern.compile(".+:\\d+:\\d+:\\s*(fatal error|error|warning|note)\\b.*");

    private static final Pattern MSVC =
            Pattern.compile(".+\\(\\d+(,\\d+)?\\):\\s*(error|warning)\\b.*");

    private static final Pattern CMAKE = Pattern.compile("\\s*CMake (Error|Warning)\\b.*");
    private static final Pattern PROGRESS = Pattern.compile("\\s*\\[\\d+/\\d+]\\s+.*");

    private BuildDiagnosticParser() {
    }

    public static Level classify(String line) {
        if (line == null) {
            return Level.PLAIN;
        }
        String lower = line.toLowerCase();
        if (PROGRESS.matcher(line).matches()) {
            return Level.NOTE;
        }
        if (GCC_CLANG.matcher(line).matches() || MSVC.matcher(line).matches()
                || CMAKE.matcher(line).matches()) {
            if (lower.contains("error")) {
                return Level.ERROR;
            }
            if (lower.contains("warning")) {
                return Level.WARNING;
            }
            return Level.NOTE;
        }

        if (lower.contains("undefined reference") || lower.contains(": fatal error")
                || lower.contains("ld: ") && lower.contains("error")) {
            return Level.ERROR;
        }
        return Level.PLAIN;
    }
}
