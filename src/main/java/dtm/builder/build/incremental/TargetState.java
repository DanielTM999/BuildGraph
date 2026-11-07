package dtm.builder.build.incremental;

import java.util.Set;

public final class TargetState {

    private final String targetId;
    private final TargetBuildState data;
    private final Set<String> touched = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private volatile boolean anyRecompiled;
    private volatile boolean dirty;

    TargetState(String targetId, TargetBuildState data) {
        this.targetId = targetId;
        this.data = data;
        data.setObjects(new java.util.concurrent.ConcurrentHashMap<>(data.getObjects()));
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
        dirty = true;
    }

    void markDirty() { dirty = true; }
    boolean dirty() { return dirty; }
}
