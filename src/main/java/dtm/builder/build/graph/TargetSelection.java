package dtm.builder.build.graph;

import dtm.builder.manifest.model.ManifestRootModel;
import java.nio.file.Path;
import java.util.List;

/** One selection contract shared by build, clean, test and install. */
public final class TargetSelection {
    private TargetSelection() { }
    public static TargetGraph resolve(ManifestRootModel manifest, Path project, boolean msvc,
                                      List<String> ids) {
        TargetResolution resolution = TargetResolver.resolve(manifest, project, msvc);
        if (!resolution.isOk()) throw new IllegalArgumentException(String.join("; ", resolution.errors()));
        TargetGraph graph = TargetGraph.of(resolution.targets());
        if (ids != null && !ids.isEmpty()) {
            for (String id : ids) {
                if (id == null || graph.target(id.trim()) == null)
                    throw new IllegalArgumentException("Target desconhecido: " + id);
            }
            graph = graph.subsetWithDependencies(ids);
        }
        if (!graph.cycle().isEmpty()) throw new IllegalArgumentException("Ciclo entre targets: " + graph.cycle());
        return graph;
    }
}
