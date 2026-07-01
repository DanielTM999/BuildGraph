package dtm.bulder.integration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.json.JsonMapper;
import dtm.bulder.build.CompileCommandBuilder;
import dtm.bulder.build.CompileSpec;
import dtm.bulder.build.PackagePaths;
import dtm.bulder.build.SourceCollector;
import dtm.bulder.build.Toolchain;
import dtm.bulder.manifest.model.ManifestRootModel;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class CompilationDatabaseGenerator {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private CompilationDatabaseGenerator() {
    }

    public static String generate(Path projectPath, ManifestRootModel manifest,
                                  Toolchain toolchain, Path packagesDir, Path buildDir,
                                  String buildMode) {
        List<Path> sources = SourceCollector.collectSources(projectPath, manifest);
        if (sources.isEmpty()) {
            return "[]";
        }
        if (toolchain == null) {
            throw new IllegalStateException("Nenhuma toolchain C/C++ encontrada");
        }

        PackagePaths packagePaths = PackagePaths.resolve(packagesDir);
        List<Entry> entries = new ArrayList<>();
        int index = 0;
        for (Path source : sources) {
            boolean cpp = SourceCollector.isCppSources(List.of(source));
            String extension = toolchain.isMsvc() ? ".obj" : ".o";
            String base = sanitize(source.getFileName().toString());
            Path output = buildDir.resolve(".clangd")
                    .resolve(index++ + "-" + base + extension)
                    .toAbsolutePath().normalize();
            CompileSpec spec = new CompileSpec(toolchain, cpp, manifest, manifest.isLibrary(),
                    List.of(source), output, packagePaths.includeDirs(),
                    packagePaths.libraryDirs(), packagePaths.linkLibraries(), buildMode,
                    List.of(), projectPath);
            List<String> arguments = CompileCommandBuilder.buildCompileOnlyCommand(spec);
            entries.add(new Entry(projectPath.toString(), source.toAbsolutePath().normalize().toString(),
                    arguments, output.toString()));
        }
        try {
            return JSON.writeValueAsString(entries);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Falha ao gerar compile_commands.json", e);
        }
    }

    public static String normalize(String json) {
        try {
            return JSON.writeValueAsString(JSON.readTree(json));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("compile_commands.json invalido", e);
        }
    }

    private static String sanitize(String value) {
        return value.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    public record Entry(String directory, String file, List<String> arguments, String output) {
    }
}
