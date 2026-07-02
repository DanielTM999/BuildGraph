package dtm.bulder.build.graph;

import dtm.bulder.build.TargetType;

import java.util.ArrayList;
import java.util.List;

/**
 * Target com todas as listas já compostas (raiz -> profile -> target ->
 * override do profile), pronto para o build. Em modo legado existe um
 * único target sintético derivado de 'library'/'name'.
 */
public record ResolvedTarget(
        String id,
        String name,
        TargetType type,
        List<String> sourceFolders,
        List<String> includePaths,
        List<String> defines,
        List<String> compileFlags,
        List<String> linkFlags,
        List<String> libraryPaths,
        List<String> dependsOn,
        boolean synthetic) {

    public ResolvedTarget {
        sourceFolders = sourceFolders == null ? new ArrayList<>() : sourceFolders;
        includePaths = includePaths == null ? new ArrayList<>() : includePaths;
        defines = defines == null ? new ArrayList<>() : defines;
        compileFlags = compileFlags == null ? new ArrayList<>() : compileFlags;
        linkFlags = linkFlags == null ? new ArrayList<>() : linkFlags;
        libraryPaths = libraryPaths == null ? new ArrayList<>() : libraryPaths;
        dependsOn = dependsOn == null ? new ArrayList<>() : dependsOn;
    }
}
