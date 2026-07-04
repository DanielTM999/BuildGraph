package dtm.builder.integration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.json.JsonMapper;
import dtm.builder.build.CompileCommandBuilder;
import dtm.builder.build.CompileSpec;
import dtm.builder.build.PackagePaths;
import dtm.builder.build.SourceCollector;
import dtm.builder.build.TargetType;
import dtm.builder.build.Toolchain;
import dtm.builder.build.graph.ResolvedTarget;
import dtm.builder.build.graph.TargetResolution;
import dtm.builder.build.graph.TargetResolver;
import dtm.builder.manifest.model.ManifestRootModel;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CompilationDatabaseGenerator {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private CompilationDatabaseGenerator() {
    }

    public static String generate(Path projectPath, ManifestRootModel manifest,
                                  Toolchain toolchain, Path packagesDir, Path buildDir,
                                  String buildMode) {
        TargetResolution resolution = TargetResolver.resolve(manifest, projectPath,
                toolchain != null && toolchain.isMsvc());

        // clangd espera uma entrada por arquivo; em fontes compartilhadas
        // entre targets, o primeiro target declarado vence.
        Map<Path, ResolvedTarget> sourcesByTarget = new LinkedHashMap<>();
        for (ResolvedTarget target : resolution.targets()) {
            for (Path source : SourceCollector.collectSources(projectPath,
                    target.sourceFolders(), target.synthetic())) {
                sourcesByTarget.putIfAbsent(source.toAbsolutePath().normalize(), target);
            }
        }
        if (sourcesByTarget.isEmpty()) {
            return "[]";
        }
        if (toolchain == null) {
            throw new IllegalStateException("Nenhuma toolchain C/C++ encontrada");
        }

        PackagePaths packagePaths = PackagePaths.resolve(packagesDir);
        List<Entry> entries = new ArrayList<>();
        int index = 0;

        for (Map.Entry<Path, ResolvedTarget> pair : sourcesByTarget.entrySet()) {
            Path source = pair.getKey();
            ResolvedTarget target = pair.getValue();
            ManifestRootModel targetManifest = manifest == null || target.synthetic()
                    ? manifest
                    : TargetResolver.perTargetManifest(manifest, target);
            boolean cpp = SourceCollector.isCppSources(List.of(source));
            String extension = toolchain.isMsvc() ? ".obj" : ".o";
            String base = sanitize(source.getFileName().toString());
            Path output = buildDir.resolve(".clangd")
                    .resolve(index++ + "-" + base + extension)
                    .toAbsolutePath().normalize();
            CompileSpec spec = new CompileSpec(toolchain, cpp, targetManifest,
                    target.type() == TargetType.SHARED,
                    List.of(source), output, packagePaths.includeDirs(),
                    packagePaths.libraryDirs(), packagePaths.linkLibraries(), buildMode,
                    List.of(), projectPath);
            List<String> arguments = CompileCommandBuilder.buildCompileOnlyCommand(spec);
            entries.add(new Entry(projectPath.toString(), source.toString(),
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
