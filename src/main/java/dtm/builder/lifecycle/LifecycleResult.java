package dtm.builder.lifecycle;

public record LifecycleResult(boolean success, String message) {

    public static LifecycleResult ok(String message) {
        return new LifecycleResult(true, message);
    }

    public static LifecycleResult fail(String message) {
        return new LifecycleResult(false, message);
    }
}
