package dtm.builder.build.graph;

import dtm.builder.build.TargetType;
import dtm.builder.manifest.model.ManifestTargetModel;

import java.util.ArrayList;
import java.util.List;

public record ResolvedTarget(
        String id,
        String name,
        TargetType type,
        List<String> sources,
        List<String> includes,
        List<String> defines,
        List<String> compileFlags,
        List<String> linkFlags,
        List<String> libraryPaths,
        List<String> dependsOn,
        boolean synthetic,
        ManifestTargetModel options) {

    public ResolvedTarget(String id, String name, TargetType type, List<String> sources,
                          List<String> includes, List<String> defines, List<String> compileFlags,
                          List<String> linkFlags, List<String> libraryPaths, List<String> dependsOn,
                          boolean synthetic) {
        this(id, name, type, sources, includes, defines, compileFlags, linkFlags, libraryPaths,
                dependsOn, synthetic, null);
    }

    public ResolvedTarget {
        sources = sources == null ? new ArrayList<>() : sources;
        includes = includes == null ? new ArrayList<>() : includes;
        defines = defines == null ? new ArrayList<>() : defines;
        compileFlags = compileFlags == null ? new ArrayList<>() : compileFlags;
        linkFlags = linkFlags == null ? new ArrayList<>() : linkFlags;
        libraryPaths = libraryPaths == null ? new ArrayList<>() : libraryPaths;
        dependsOn = dependsOn == null ? new ArrayList<>() : dependsOn;
    }
}
