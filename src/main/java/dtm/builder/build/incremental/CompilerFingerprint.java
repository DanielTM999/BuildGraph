package dtm.builder.build.incremental;

import dtm.builder.build.Toolchain;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;

public final class CompilerFingerprint {

    private CompilerFingerprint() {
    }

    /**
     * Identidade da toolchain via kind + path/tamanho/mtime dos drivers.
     * Atualizacao do compilador muda o binario e invalida o estado sem
     * precisar executar processo algum.
     */
    public static String of(Toolchain toolchain) {
        if (toolchain == null) {
            return "none";
        }
        return toolchain.kind() + "|" + describe(toolchain.cc()) + "|" + describe(toolchain.cxx());
    }

    private static String describe(Path driver) {
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
