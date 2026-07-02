package dtm.bulder.build.graph;

import dtm.bulder.build.BuildResult;

@FunctionalInterface
public interface TargetBuilder {

    BuildResult build(ResolvedTarget target);
}
