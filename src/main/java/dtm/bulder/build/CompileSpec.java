package dtm.bulder.build;

import dtm.bulder.manifest.model.ManifestRootModel;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public record CompileSpec(
        Toolchain toolchain,
        boolean cpp,
        ManifestRootModel manifest,
        boolean library,
        List<Path> sources,
        Path artifact,
        List<Path> extraIncludeDirs,
        List<Path> extraLibDirs,
        List<String> extraLinkLibs,
        String buildMode,
        List<String> extraFlags,
        Path projectPath) {

    public CompileSpec {
        sources = sources == null ? new ArrayList<>() : sources;
        extraIncludeDirs = extraIncludeDirs == null ? new ArrayList<>() : extraIncludeDirs;
        extraLibDirs = extraLibDirs == null ? new ArrayList<>() : extraLibDirs;
        extraLinkLibs = extraLinkLibs == null ? new ArrayList<>() : extraLinkLibs;
        extraFlags = extraFlags == null ? new ArrayList<>() : extraFlags;
        buildMode = (buildMode == null || buildMode.isBlank()) ? "Debug" : buildMode;
    }

    public boolean isRelease() {
        return "release".equalsIgnoreCase(buildMode);
    }
}
