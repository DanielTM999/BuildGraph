package dtm.builder.lifecycle;

public enum Phase {
    CLEAN,
    BUILD,
    TEST,
    INSTALL;

    public static Phase fromString(String s) {
        if (s == null) {
            return null;
        }
        if ("package".equalsIgnoreCase(s.trim())) return BUILD;
        for (Phase p : values()) {
            if (p.name().equalsIgnoreCase(s.trim())) {
                return p;
            }
        }
        return null;
    }

    public boolean impliesBuild() {
        return this == INSTALL;
    }
}
