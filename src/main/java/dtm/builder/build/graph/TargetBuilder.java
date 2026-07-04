package dtm.builder.build.graph;

import dtm.builder.build.BuildResult;

@FunctionalInterface
public interface TargetBuilder {

    BuildResult build(ResolvedTarget target);
}
