package dtm.builder.repo;

public enum SyncResult {
    NO_MANIFEST,
    INVALID_MANIFEST,
    NO_PACKAGES,
    RESOLUTION_FAILED,
    APPLIED_CHANGED,
    APPLIED_NO_CHANGE;

    public boolean isFailure() {
        return this == INVALID_MANIFEST || this == RESOLUTION_FAILED;
    }
}
