package dtm.builder.manifest;

import dtm.builder.manifest.model.ManifestPackagesModel;
import dtm.builder.manifest.model.ManifestTargetModel;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Regras de composição compartilhadas entre profiles e targets:
 * scalars sobrescrevem, listas são união ordenada (com exclusões),
 * flags concatenam e maps fazem merge por chave.
 */
public final class ManifestMerge {

    private ManifestMerge() {
    }

    public static String pick(String base, String override) {
        if (override != null && !override.isBlank()) {
            return override;
        }
        return base;
    }

    public static List<String> mergeAdditive(List<String> base, List<String> extra,
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
        addFiltered(out, base, excluded);
        addFiltered(out, extra, excluded);
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

    public static List<String> concat(List<String> base, List<String> extra) {
        List<String> out = new ArrayList<>();
        if (base != null) {
            out.addAll(base);
        }
        if (extra != null) {
            out.addAll(extra);
        }
        return out;
    }

    public static Map<String, String> mergeMap(Map<String, String> base, Map<String, String> extra) {
        Map<String, String> out = new LinkedHashMap<>();
        if (base != null) {
            out.putAll(base);
        }
        if (extra != null) {
            out.putAll(extra);
        }
        return out;
    }

    public static List<ManifestPackagesModel> mergePackages(List<ManifestPackagesModel> base,
                                                            List<ManifestPackagesModel> extra) {
        Map<String, ManifestPackagesModel> byId = new LinkedHashMap<>();
        List<ManifestPackagesModel> noId = new ArrayList<>();
        collectPackages(base, byId, noId);
        collectPackages(extra, byId, noId);
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

    /**
     * União por id: entradas do profile com id existente são combinadas
     * incrementalmente com o target raiz; ids novos viram targets adicionais.
     */
    public static List<ManifestTargetModel> mergeTargets(List<ManifestTargetModel> base,
                                                         List<ManifestTargetModel> extra) {
        Map<String, ManifestTargetModel> byId = new LinkedHashMap<>();
        if (base != null) {
            for (ManifestTargetModel t : base) {
                if (t != null && t.getId() != null && !t.getId().isBlank()) {
                    byId.put(t.getId().trim(), t);
                }
            }
        }
        if (extra != null) {
            for (ManifestTargetModel t : extra) {
                if (t == null || t.getId() == null || t.getId().isBlank()) {
                    continue;
                }
                String id = t.getId().trim();
                ManifestTargetModel current = byId.get(id);
                byId.put(id, current == null ? t : mergeTarget(current, t));
            }
        }
        return new ArrayList<>(byId.values());
    }

    public static ManifestTargetModel mergeTarget(ManifestTargetModel base,
                                                  ManifestTargetModel override) {
        ManifestTargetModel out = new ManifestTargetModel();
        out.setId(pick(base.getId(), override.getId()));
        out.setName(pick(base.getName(), override.getName()));
        out.setType(pick(base.getType(), override.getType()));
        out.setSourceFolders(mergeAdditive(base.getSourceFolders(), override.getSourceFolders(),
                override.getExcludeSourceFolders()));
        out.setExcludeSourceFolders(mergeAdditive(base.getExcludeSourceFolders(),
                override.getExcludeSourceFolders(), null));
        out.setIncludePaths(mergeAdditive(base.getIncludePaths(), override.getIncludePaths(),
                override.getExcludeIncludePaths()));
        out.setExcludeIncludePaths(mergeAdditive(base.getExcludeIncludePaths(),
                override.getExcludeIncludePaths(), null));
        out.setDefines(mergeAdditive(base.getDefines(), override.getDefines(),
                override.getExcludeDefines()));
        out.setExcludeDefines(mergeAdditive(base.getExcludeDefines(),
                override.getExcludeDefines(), null));
        out.setCompileFlags(concat(base.getCompileFlags(), override.getCompileFlags()));
        out.setLinkFlags(concat(base.getLinkFlags(), override.getLinkFlags()));
        out.setLibraryPaths(mergeAdditive(base.getLibraryPaths(), override.getLibraryPaths(), null));
        out.setDependsOn(mergeAdditive(base.getDependsOn(), override.getDependsOn(), null));
        return out;
    }
}
