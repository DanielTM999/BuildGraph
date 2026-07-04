package dtm.builder.build.graph;

import dtm.builder.build.Artifacts;
import dtm.builder.build.TargetType;
import dtm.builder.manifest.ManifestMerge;
import dtm.builder.manifest.model.ManifestRootModel;
import dtm.builder.manifest.model.ManifestTargetModel;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Converte o manifesto efetivo (raiz + profile já compostos) na lista final
 * de targets. Cada target herda as listas da raiz de forma aditiva, com as
 * mesmas regras de composição dos profiles.
 */
public final class TargetResolver {

    private TargetResolver() {
    }

    public static TargetResolution resolve(ManifestRootModel effective, Path projectPath,
                                           boolean msvc) {
        List<String> errors = new ArrayList<>();

        if (effective == null || effective.getTargets().isEmpty()) {
            ResolvedTarget legacy = syntheticTarget(effective, projectPath);
            return new TargetResolution(List.of(legacy), errors, false);
        }

        List<ResolvedTarget> targets = new ArrayList<>();
        for (ManifestTargetModel t : effective.getTargets()) {
            if (t == null || t.getId() == null || t.getId().isBlank()) {
                continue;
            }
            String id = t.getId().trim();
            String name = (t.getName() == null || t.getName().isBlank()) ? id : t.getName().trim();
            TargetType type = TargetType.parse(t.getType());

            List<String> sources = ManifestMerge.mergeAdditive(effective.getSourceFolders(),
                    t.getSourceFolders(), t.getExcludeSourceFolders());
            if (sources.isEmpty()) {
                errors.add("target '" + id + "' sem sourceFolders efetivos"
                        + " (declare no target ou na raiz do manifest)");
            }

            targets.add(new ResolvedTarget(
                    id,
                    name,
                    type,
                    sources,
                    ManifestMerge.mergeAdditive(effective.getIncludePaths(),
                            t.getIncludePaths(), t.getExcludeIncludePaths()),
                    ManifestMerge.mergeAdditive(effective.getDefines(),
                            t.getDefines(), t.getExcludeDefines()),
                    ManifestMerge.concat(effective.getCompileFlags(), t.getCompileFlags()),
                    ManifestMerge.concat(effective.getLinkFlags(), t.getLinkFlags()),
                    ManifestMerge.mergeAdditive(effective.getLibraryPaths(),
                            t.getLibraryPaths(), null),
                    trimmed(t.getDependsOn()),
                    false));
        }

        validateDependencies(targets, errors);
        validateArtifactNames(targets, msvc, errors);
        return new TargetResolution(targets, errors, true);
    }

    /**
     * Cópia do manifesto efetivo com as listas substituídas pelas listas
     * finais do target, para reaproveitar CompileCommandBuilder e afins
     * sem mudanças.
     */
    public static ManifestRootModel perTargetManifest(ManifestRootModel effective,
                                                      ResolvedTarget target) {
        ManifestRootModel out = new ManifestRootModel();
        out.setId(effective.getId());
        out.setName(target.name());
        out.setVersion(effective.getVersion());
        out.setDescription(effective.getDescription());
        out.setCompilerVersion(effective.getCompilerVersion());
        out.setCStandard(effective.getCStandard());
        out.setCxxStandard(effective.getCxxStandard());
        out.setToolchainVersion(effective.getToolchainVersion());
        out.setPlatform(effective.getPlatform());
        out.setCCompiler(effective.getCCompiler());
        out.setCxxCompiler(effective.getCxxCompiler());
        out.setSysroot(effective.getSysroot());
        out.setLibrary(target.type().isLibrary());
        out.setOutputDir(effective.getOutputDir());
        out.setPackagesBase(effective.getPackagesBase());
        out.setActiveProfile(effective.getActiveProfile());
        out.setTestFolder(effective.getTestFolder());
        out.setTestMain(effective.getTestMain());
        out.setRepositories(new ArrayList<>(effective.getRepositories()));
        out.setSourceFolders(new ArrayList<>(target.sourceFolders()));
        out.setIncludePaths(new ArrayList<>(target.includePaths()));
        out.setDefines(new ArrayList<>(target.defines()));
        out.setCompileFlags(new ArrayList<>(target.compileFlags()));
        out.setLinkFlags(new ArrayList<>(target.linkFlags()));
        out.setLibraryPaths(new ArrayList<>(target.libraryPaths()));
        out.setEnv(effective.getEnv());
        out.setProperties(effective.getProperties());
        out.setPackages(effective.getPackages());
        out.setTasks(effective.getTasks());
        out.setPackagesDeclared(effective.isPackagesDeclared());
        return out;
    }

    public static ResolvedTarget byId(TargetResolution resolution, String id) {
        if (id == null) {
            return null;
        }
        for (ResolvedTarget t : resolution.targets()) {
            if (t.id().equals(id.trim())) {
                return t;
            }
        }
        return null;
    }

    private static ResolvedTarget syntheticTarget(ManifestRootModel effective, Path projectPath) {
        String base = Artifacts.baseName(projectPath, effective);
        boolean library = effective != null && effective.isLibrary();
        return new ResolvedTarget(
                base,
                base,
                library ? TargetType.SHARED : TargetType.EXECUTABLE,
                effective == null ? List.of() : effective.getSourceFolders(),
                effective == null ? List.of() : effective.getIncludePaths(),
                effective == null ? List.of() : effective.getDefines(),
                effective == null ? List.of() : effective.getCompileFlags(),
                effective == null ? List.of() : effective.getLinkFlags(),
                effective == null ? List.of() : effective.getLibraryPaths(),
                List.of(),
                true);
    }

    private static void validateDependencies(List<ResolvedTarget> targets, List<String> errors) {
        Map<String, ResolvedTarget> byId = new LinkedHashMap<>();
        for (ResolvedTarget t : targets) {
            byId.put(t.id(), t);
        }
        for (ResolvedTarget t : targets) {
            for (String dep : t.dependsOn()) {
                ResolvedTarget resolved = byId.get(dep);
                if (resolved == null) {
                    errors.add("target '" + t.id() + "' depende de target inexistente: " + dep);
                } else if (resolved.type() == TargetType.EXECUTABLE) {
                    errors.add("target '" + t.id() + "' nao pode depender do executavel '"
                            + dep + "' (dependencias devem ser shared ou static)");
                }
            }
        }
    }

    private static void validateArtifactNames(List<ResolvedTarget> targets, boolean msvc,
                                              List<String> errors) {
        Map<String, String> byFile = new LinkedHashMap<>();
        for (ResolvedTarget t : targets) {
            String file = Artifacts.fileName(t.name(), t.type(), msvc);
            String other = byFile.putIfAbsent(file, t.id());
            if (other != null) {
                errors.add("targets '" + other + "' e '" + t.id()
                        + "' geram o mesmo artefato: " + file);
            }
        }
    }

    private static List<String> trimmed(List<String> src) {
        List<String> out = new ArrayList<>();
        if (src != null) {
            for (String s : src) {
                if (s != null && !s.isBlank()) {
                    out.add(s.trim());
                }
            }
        }
        return out;
    }
}
