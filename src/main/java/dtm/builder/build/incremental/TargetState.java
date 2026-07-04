package dtm.builder.build.incremental;

import java.util.HashSet;
import java.util.Set;

/** Estado incremental de um target durante um build; confinado a thread do target. */
public final class TargetState {

    private final String targetId;
    private final TargetBuildState data;
    private final Set<String> touched = new HashSet<>();
    private boolean anyRecompiled;

    TargetState(String targetId, TargetBuildState data) {
        this.targetId = targetId;
        this.data = data;
    }

    public String targetId() {
        return targetId;
    }

    public boolean anyRecompiled() {
        return anyRecompiled;
    }

    TargetBuildState data() {
        return data;
    }

    Set<String> touched() {
        return touched;
    }

    void markRecompiled() {
        anyRecompiled = true;
    }
}
