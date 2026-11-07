package dtm.builder.build.incremental;

import dtm.builder.build.Toolchain;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;

public final class CompilerFingerprint {

    private CompilerFingerprint() {
    }

    public static String of(Toolchain toolchain) {
        if (toolchain == null) {
            return "none";
        }
        return toolchain.kind() + "|" + describe(toolchain.cc()) + "|" + describe(toolchain.cxx());
    }

    public static String describe(Path driver) {
        if (driver == null) {
            return "-";
        }
        try {
            BasicFileAttributes attrs = Files.readAttributes(driver, BasicFileAttributes.class);
            return driver.toAbsolutePath().normalize() + ":" + attrs.size() + ":"
                    + attrs.lastModifiedTime().toMillis();
        } catch (IOException e) {
            return driver.toString();
        }
    }
}
