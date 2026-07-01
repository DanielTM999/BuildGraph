package dtm.bulder.lifecycle;

public enum Phase {
    CLEAN,
    BUILD,
    TEST,
    INSTALL;

    public static Phase fromString(String s) {
        if (s == null) {
            return null;
        }
        for (Phase p : values()) {
            if (p.name().equalsIgnoreCase(s.trim())) {
                return p;
            }
        }
        return null;
    }

    public boolean impliesBuild() {
        return this == TEST || this == INSTALL;
    }
}
