package dtm.builder.build;

import java.nio.file.Path;

public record Toolchain(ToolchainKind kind, Path cc, Path cxx) {

    public boolean hasC() {
        return cc != null;
    }

    public boolean hasCxx() {
        return cxx != null;
    }

    public Path driver(boolean cpp) {
        if (cpp) {
            return cxx != null ? cxx : cc;
        }
        return cc != null ? cc : cxx;
    }

    public boolean isMsvc() {
        return kind == ToolchainKind.MSVC;
    }

    public String displayName() {
        Path d = cxx != null ? cxx : cc;
        return kind + (d == null ? "" : " (" + d + ")");
    }
}
