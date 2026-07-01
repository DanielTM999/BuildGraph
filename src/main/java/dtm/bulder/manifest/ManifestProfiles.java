package dtm.bulder.manifest;

import dtm.bulder.manifest.model.ManifestPackagesModel;
import dtm.bulder.manifest.model.ManifestProfileModel;
import dtm.bulder.manifest.model.ManifestRootModel;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
            out.setRepositories(mergeAdditive(raw.getRepositories(), null, null));
            out.setPackages(new ArrayList<>(raw.getPackages()));
            out.setProfiles(raw.getProfiles());
            out.setTasks(new ArrayList<>(raw.getTasks()));
            out.setPackagesDeclared(raw.isPackagesDeclared());
            return out;
        }

        copyScalars(out, raw, profile);
        out.setSourceFolders(mergeAdditive(raw.getSourceFolders(), profile.getSourceFolders(),
                profile.getExcludeSourceFolders()));
        out.setIncludePaths(mergeAdditive(raw.getIncludePaths(), profile.getIncludePaths(),
                profile.getExcludeIncludePaths()));
        out.setDefines(mergeAdditive(raw.getDefines(), profile.getDefines(),
                profile.getExcludeDefines()));
        out.setLibraryPaths(mergeAdditive(raw.getLibraryPaths(), profile.getLibraryPaths(), null));
        out.setCompileFlags(concat(raw.getCompileFlags(), profile.getCompileFlags()));
        out.setLinkFlags(concat(raw.getLinkFlags(), profile.getLinkFlags()));
        out.setEnv(mergeMap(raw.getEnv(), profile.getEnv()));
        out.setProperties(mergeMap(raw.getProperties(), profile.getProperties()));
        out.setRepositories(mergeAdditive(raw.getRepositories(), profile.getRepositories(), null));
        out.setPackages(mergePackages(raw.getPackages(), profile.getPackages()));
        out.setProfiles(raw.getProfiles());
        out.setTasks(new ArrayList<>(raw.getTasks()));
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
        out.setCompilerVersion(pick(raw.getCompilerVersion(),
                profile == null ? null : profile.getCompilerVersion()));
        out.setCStandard(pick(raw.getCStandard(), profile == null ? null : profile.getCStandard()));
        out.setCxxStandard(pick(raw.getCxxStandard(),
                profile == null ? null : profile.getCxxStandard()));
        out.setToolchainVersion(pick(raw.getToolchainVersion(),
                profile == null ? null : profile.getToolchainVersion()));
        out.setPlatform(pick(raw.getPlatform(), profile == null ? null : profile.getPlatform()));
        out.setCCompiler(pick(raw.getCCompiler(), profile == null ? null : profile.getCCompiler()));
        out.setCxxCompiler(pick(raw.getCxxCompiler(),
                profile == null ? null : profile.getCxxCompiler()));
        out.setSysroot(pick(raw.getSysroot(), profile == null ? null : profile.getSysroot()));
        out.setOutputDir(pick(raw.getOutputDir(), profile == null ? null : profile.getOutputDir()));
        out.setPackagesBase(pick(raw.getPackagesBase(),
                profile == null ? null : profile.getPackagesBase()));
        out.setActiveProfile(raw.getActiveProfile());

        boolean library = raw.isLibrary();
        if (profile != null && profile.getLibrary() != null) {
            library = profile.getLibrary();
        }
        out.setLibrary(library);
    }

    private static String pick(String root, String profile) {
        if (profile != null && !profile.isBlank()) {
            return profile;
        }
        return root;
    }

    private static List<String> mergeAdditive(List<String> root, List<String> profile,
                                              List<String> exclude) {
        Set<String> excluded = new LinkedHashSet<>();
        if (exclude != null) {
            for (String s : exclude) {
                if (s != null) {
                    excluded.add(s.trim());
                }
            }
        }
        Set<String> out = new LinkedHashSet<>();
        addFiltered(out, root, excluded);
        addFiltered(out, profile, excluded);
        return new ArrayList<>(out);
    }

    private static void addFiltered(Set<String> out, List<String> src, Set<String> excluded) {
        if (src == null) {
            return;
        }
        for (String s : src) {
            if (s == null) {
                continue;
            }
            String t = s.trim();
            if (!t.isEmpty() && !excluded.contains(t)) {
                out.add(t);
            }
        }
    }

    private static List<String> concat(List<String> root, List<String> profile) {
        List<String> out = new ArrayList<>();
        if (root != null) {
            out.addAll(root);
        }
        if (profile != null) {
            out.addAll(profile);
        }
        return out;
    }

    private static Map<String, String> mergeMap(Map<String, String> root, Map<String, String> profile) {
        Map<String, String> out = new LinkedHashMap<>();
        if (root != null) {
            out.putAll(root);
        }
        if (profile != null) {
            out.putAll(profile);
        }
        return out;
    }

    private static List<ManifestPackagesModel> mergePackages(List<ManifestPackagesModel> root,
                                                             List<ManifestPackagesModel> profile) {
        Map<String, ManifestPackagesModel> byId = new LinkedHashMap<>();
        List<ManifestPackagesModel> noId = new ArrayList<>();
        collectPackages(root, byId, noId);
        collectPackages(profile, byId, noId);
        List<ManifestPackagesModel> out = new ArrayList<>(byId.values());
        out.addAll(noId);
        return out;
    }

    private static void collectPackages(List<ManifestPackagesModel> src,
                                        Map<String, ManifestPackagesModel> byId,
                                        List<ManifestPackagesModel> noId) {
        if (src == null) {
            return;
        }
        for (ManifestPackagesModel p : src) {
            if (p == null) {
                continue;
            }
            if (p.getId() == null || p.getId().isBlank()) {
                noId.add(p);
            } else {
                byId.put(p.getId(), p);
            }
        }
    }
}
