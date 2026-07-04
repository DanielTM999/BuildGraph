package dtm.builder.build;

import dtm.builder.manifest.model.ManifestRootModel;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

final class TestMainResolver {

    private static final Pattern MAIN_FUNCTION = Pattern.compile(
            "(?m)^\\s*(?:int|auto)\\s+main\\s*\\(");

    private TestMainResolver() {
    }

    static Result resolve(Path projectPath, ManifestRootModel manifest, List<Path> testSources) {
        String declared = manifest == null ? null : manifest.getTestMain();
        if (declared != null && !declared.isBlank()) {
            Path selected = declaredPath(projectPath, manifest, declared.trim());
            Path source = findSource(testSources, selected);
            if (source == null) {
                return Result.fail("testMain nao encontrado em testFolder: " + declared.trim());
            }
            if (!definesMain(source)) {
                return Result.fail("Arquivo testMain nao define main(): " + declared.trim());
            }
            return Result.ok(source);
        }

        List<Path> candidates = new ArrayList<>();
        for (Path source : testSources) {
            if (definesMain(source)) {
                candidates.add(source);
            }
        }
        if (candidates.isEmpty()) {
            return Result.fail("Nenhum main() encontrado em testFolder");
        }
        if (candidates.size() > 1) {
            return Result.fail("Mais de um main() encontrado em testFolder; configure testMain");
        }
        return Result.ok(candidates.get(0));
    }

    private static Path declaredPath(Path projectPath, ManifestRootModel manifest,
                                     String declared) {
        Path path = Path.of(declared);
        if (path.isAbsolute()) {
            return path.normalize();
        }
        String folder = manifest == null ? null : manifest.getTestFolder();
        String testFolder = folder == null || folder.isBlank() ? "tests" : folder.trim();
        Path relativeToTests = projectPath.resolve(testFolder).resolve(path).normalize();
        if (Files.isRegularFile(relativeToTests)) {
            return relativeToTests;
        }
        return projectPath.resolve(path).normalize();
    }

    private static Path findSource(List<Path> sources, Path selected) {
        Path normalized = selected.toAbsolutePath().normalize();
        for (Path source : sources) {
            if (source.toAbsolutePath().normalize().equals(normalized)) {
                return source;
            }
        }
        return null;
    }

    private static boolean definesMain(Path source) {
        try {
            return MAIN_FUNCTION.matcher(Files.readString(source)).find();
        } catch (IOException e) {
            return false;
        }
    }

    record Result(Path source, String error) {

        static Result ok(Path source) {
            return new Result(source, null);
        }

        static Result fail(String error) {
            return new Result(null, error);
        }

        boolean success() {
            return source != null;
        }
    }
}
