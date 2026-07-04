package dtm.builder.manifest;

import dtm.builder.manifest.model.ManifestProfileModel;
import dtm.builder.manifest.model.ManifestRootModel;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ManifestProfiles {

    private ManifestProfiles() {
    }

    public static ManifestRootModel effective(ManifestRootModel raw) {
        if (raw == null) {
            return null;
        }

        ManifestProfileModel profile = activeProfile(raw);
        ManifestRootModel out = new ManifestRootModel();

        out.setId(raw.getId());
        out.setName(raw.getName());
        out.setVersion(raw.getVersion());
        out.setDescription(raw.getDescription());

        if (profile == null) {
            copyScalars(out, raw, null);
            out.setSourceFolders(new ArrayList<>(raw.getSourceFolders()));
            out.setIncludePaths(new ArrayList<>(raw.getIncludePaths()));
            out.setDefines(new ArrayList<>(raw.getDefines()));
            out.setLibraryPaths(new ArrayList<>(raw.getLibraryPaths()));
            out.setCompileFlags(new ArrayList<>(raw.getCompileFlags()));
            out.setLinkFlags(new ArrayList<>(raw.getLinkFlags()));
            out.setEnv(new LinkedHashMap<>(raw.getEnv()));
            out.setProperties(new LinkedHashMap<>(raw.getProperties()));
            out.setRepositories(ManifestMerge.mergeAdditive(raw.getRepositories(), null, null));
            out.setPackages(new ArrayList<>(raw.getPackages()));
            out.setProfiles(raw.getProfiles());
            out.setTasks(new ArrayList<>(raw.getTasks()));
            out.setTargets(new ArrayList<>(raw.getTargets()));
            out.setPackagesDeclared(raw.isPackagesDeclared());
            return out;
        }

        copyScalars(out, raw, profile);
        out.setSourceFolders(ManifestMerge.mergeAdditive(raw.getSourceFolders(),
                profile.getSourceFolders(), profile.getExcludeSourceFolders()));
        out.setIncludePaths(ManifestMerge.mergeAdditive(raw.getIncludePaths(),
                profile.getIncludePaths(), profile.getExcludeIncludePaths()));
        out.setDefines(ManifestMerge.mergeAdditive(raw.getDefines(), profile.getDefines(),
                profile.getExcludeDefines()));
        out.setLibraryPaths(ManifestMerge.mergeAdditive(raw.getLibraryPaths(),
                profile.getLibraryPaths(), null));
        out.setCompileFlags(ManifestMerge.concat(raw.getCompileFlags(), profile.getCompileFlags()));
        out.setLinkFlags(ManifestMerge.concat(raw.getLinkFlags(), profile.getLinkFlags()));
        out.setEnv(ManifestMerge.mergeMap(raw.getEnv(), profile.getEnv()));
        out.setProperties(ManifestMerge.mergeMap(raw.getProperties(), profile.getProperties()));
        out.setRepositories(ManifestMerge.mergeAdditive(raw.getRepositories(),
                profile.getRepositories(), null));
        out.setPackages(ManifestMerge.mergePackages(raw.getPackages(), profile.getPackages()));
        out.setProfiles(raw.getProfiles());
        out.setTasks(new ArrayList<>(raw.getTasks()));
        out.setTargets(ManifestMerge.mergeTargets(raw.getTargets(), profile.getTargets()));
        out.setPackagesDeclared(raw.isPackagesDeclared() || !profile.getPackages().isEmpty());
        return out;
    }

    public static ManifestProfileModel activeProfile(ManifestRootModel raw) {
        if (raw == null) {
            return null;
        }
        String name = raw.getActiveProfile();
        if (name == null || name.isBlank()) {
            return null;
        }
        String norm = name.trim().toLowerCase();
        for (Map.Entry<String, ManifestProfileModel> e : raw.getProfiles().entrySet()) {
            if (e.getKey() != null && e.getKey().trim().toLowerCase().equals(norm)) {
                return e.getValue();
            }
        }
        return null;
    }

    private static void copyScalars(ManifestRootModel out, ManifestRootModel raw,
                                    ManifestProfileModel profile) {
        out.setCompilerVersion(ManifestMerge.pick(raw.getCompilerVersion(),
                profile == null ? null : profile.getCompilerVersion()));
        out.setCStandard(ManifestMerge.pick(raw.getCStandard(),
                profile == null ? null : profile.getCStandard()));
        out.setCxxStandard(ManifestMerge.pick(raw.getCxxStandard(),
                profile == null ? null : profile.getCxxStandard()));
        out.setToolchainVersion(ManifestMerge.pick(raw.getToolchainVersion(),
                profile == null ? null : profile.getToolchainVersion()));
        out.setPlatform(ManifestMerge.pick(raw.getPlatform(),
                profile == null ? null : profile.getPlatform()));
        out.setCCompiler(ManifestMerge.pick(raw.getCCompiler(),
                profile == null ? null : profile.getCCompiler()));
        out.setCxxCompiler(ManifestMerge.pick(raw.getCxxCompiler(),
                profile == null ? null : profile.getCxxCompiler()));
        out.setSysroot(ManifestMerge.pick(raw.getSysroot(),
                profile == null ? null : profile.getSysroot()));
        out.setOutputDir(ManifestMerge.pick(raw.getOutputDir(),
                profile == null ? null : profile.getOutputDir()));
        out.setPackagesBase(ManifestMerge.pick(raw.getPackagesBase(),
                profile == null ? null : profile.getPackagesBase()));
        out.setActiveProfile(raw.getActiveProfile());
        out.setTestFolder(raw.getTestFolder());
        out.setTestMain(raw.getTestMain());

        boolean library = raw.isLibrary();
        if (profile != null && profile.getLibrary() != null) {
            library = profile.getLibrary();
        }
        out.setLibrary(library);
    }
}
