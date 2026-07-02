package dtm.bulder.build.graph;

import java.util.ArrayList;
import java.util.List;

public record TargetResolution(
        List<ResolvedTarget> targets,
        List<String> errors,
        boolean multiTarget) {

    public TargetResolution {
        targets = targets == null ? new ArrayList<>() : targets;
        errors = errors == null ? new ArrayList<>() : errors;
    }

    public boolean isOk() {
        return errors.isEmpty();
    }
}
