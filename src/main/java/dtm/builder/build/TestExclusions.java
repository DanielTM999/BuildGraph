package dtm.builder.build;

import dtm.builder.manifest.model.ManifestRootModel;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

final class TestExclusions {

    private final Path testDir;
    private final List<Entry> entries = new ArrayList<>();
    private final Set<String> matched = new LinkedHashSet<>();

    private TestExclusions(Path testDir, List<String> patterns) {
        this.testDir = testDir.toAbsolutePath().normalize();
        for (String raw : patterns) {
            String pattern = normalize(absolute(raw));
            if (!pattern.isEmpty()) entries.add(new Entry(raw.trim(), pattern, glob(pattern)));
        }
    }

    static TestExclusions of(Path projectPath, ManifestRootModel manifest) {
        String declared = manifest == null ? null : manifest.getTestFolder();
        String folder = declared == null || declared.isBlank() ? "tests" : declared.trim();
        List<String> patterns = manifest == null ? List.of() : manifest.getExcludeTests();
        return new TestExclusions(projectPath.resolve(folder), patterns == null ? List.of() : patterns);
    }

    boolean isEmpty() {
        return entries.isEmpty();
    }

    List<Path> filter(List<Path> sources) {
        List<Path> out = new ArrayList<>();
        for (Path source : sources) if (!excludes(source)) out.add(source);
        return out;
    }

    boolean excludes(Path source) {
        String relative = relative(source);
        boolean excluded = false;
        for (Entry entry : entries) {
            if (entry.matches(relative)) {
                matched.add(entry.raw());
                excluded = true;
            }
        }
        return excluded;
    }

    List<String> unmatched() {
        List<String> out = new ArrayList<>();
        for (Entry entry : entries) if (!matched.contains(entry.raw()) && !out.contains(entry.raw())) out.add(entry.raw());
        return out;
    }

    private String relative(Path source) {
        Path absolute = source.toAbsolutePath().normalize();
        Path path = absolute.startsWith(testDir) ? testDir.relativize(absolute) : absolute;
        return path.toString().replace('\\', '/');
    }

    private String absolute(String raw) {
        if (raw == null || raw.isBlank()) return raw;
        try {
            Path path = Path.of(raw.trim());
            if (!path.isAbsolute()) return raw;
            Path normalized = path.normalize();
            return normalized.startsWith(testDir) ? testDir.relativize(normalized).toString() : raw;
        } catch (java.nio.file.InvalidPathException e) {
            return raw;
        }
    }

    private static String normalize(String raw) {
        if (raw == null) return "";
        String value = raw.trim().replace('\\', '/');
        while (value.startsWith("./")) value = value.substring(2);
        while (value.endsWith("/")) value = value.substring(0, value.length() - 1);
        return value;
    }

    private static Pattern glob(String pattern) {
        if (pattern.indexOf('*') < 0 && pattern.indexOf('?') < 0) return null;
        StringBuilder regex = new StringBuilder();
        for (int i = 0; i < pattern.length(); i++) {
            char c = pattern.charAt(i);
            if (c == '*' && i + 1 < pattern.length() && pattern.charAt(i + 1) == '*') {
                boolean slash = i + 2 < pattern.length() && pattern.charAt(i + 2) == '/';
                regex.append(slash ? "(?:.*/)?" : ".*");
                i += slash ? 2 : 1;
            } else if (c == '*') {
                regex.append("[^/]*");
            } else if (c == '?') {
                regex.append("[^/]");
            } else {
                regex.append(Pattern.quote(String.valueOf(c)));
            }
        }
        return Pattern.compile(regex.toString());
    }

    private record Entry(String raw, String pattern, Pattern glob) {

        boolean matches(String relative) {
            if (glob == null) return relative.equals(pattern) || relative.startsWith(pattern + "/");
            if (glob.matcher(relative).matches()) return true;
            if (pattern.indexOf('/') >= 0) return false;
            int slash = relative.lastIndexOf('/');
            return slash >= 0 && glob.matcher(relative.substring(slash + 1)).matches();
        }
    }
}
