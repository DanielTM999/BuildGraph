package dtm.builder.build;

import java.util.Locale;
import java.util.function.Consumer;

/** Destination information, independent of the OS running BuildGraph. */
public record TargetPlatform(String arch, String os, String triple) {
    public static TargetPlatform host() {
        String arch = architecture(System.getProperty("os.arch", "unknown"));
        String name = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String os = name.contains("win") && !name.contains("darwin") ? "windows"
                : name.contains("mac") || name.contains("darwin") ? "darwin"
                : name.contains("linux") ? "linux" : "unknown";
        return new TargetPlatform(arch, os, (arch.equals("x86") ? "i686" : arch) + (os.equals("windows") ? "-w64-mingw32"
                : os.equals("darwin") ? "-apple-darwin" : "-unknown-" + os));
    }

    public static TargetPlatform resolve(String raw, Consumer<String> warning) {
        if (raw == null || raw.isBlank() || raw.equalsIgnoreCase("native")) return host();
        String value = raw.trim().toLowerCase(Locale.ROOT);
        String[] parts = value.split("-");
        if (parts.length == 2 && java.util.Set.of("windows", "linux", "macos", "darwin", "freebsd").contains(parts[0])) {
            String os = parts[0].equals("macos") ? "darwin" : parts[0];
            String arch = architecture(parts[1]);
            if (!knownArchitecture(arch)) return fallback(raw, warning);
            String triple = (arch.equals("x86") ? "i686" : arch) + (os.equals("windows") ? "-pc-windows-msvc"
                    : os.equals("darwin") ? "-apple-darwin" : "-unknown-" + os);
            return new TargetPlatform(arch, os, triple);
        }
        String arch = architecture(parts[0]);
        // Triples keep an open architecture vocabulary: the selected tool validates support.
        if (parts.length >= 2 && value.matches("[a-z0-9_]+(?:-[a-z0-9_.]+)+")) {
            String os = value.contains("mingw") || value.contains("windows") || value.contains("win32")
                    ? "windows" : value.contains("darwin") || value.contains("apple") || value.contains("macos")
                    ? "darwin" : value.contains("linux") ? "linux"
                    : value.contains("freebsd") ? "freebsd" : value.contains("none") || value.endsWith("-elf")
                    ? "none" : null;
            if (os != null) return new TargetPlatform(arch, os, (arch.equals("x86") ? "i686" : arch) + value.substring(parts[0].length()));
        }
        if (parts.length == 1 && knownArchitecture(arch)) {
            TargetPlatform host = host();
            return new TargetPlatform(arch, host.os, (arch.equals("x86") ? "i686" : arch) + host.triple.substring(host.triple.indexOf('-')));
        }
        return fallback(raw, warning);
    }

    private static TargetPlatform fallback(String raw, Consumer<String> warning) {
        if (warning != null) warning.accept("aviso: platform invalida '" + raw + "'; usando " + host().triple);
        return host();
    }

    private static boolean knownArchitecture(String arch) {
        return arch.matches("x86|x86_64|aarch64|arm[0-9a-z_]*|riscv(?:32|64)|mips(?:el|64|64el)?|powerpc(?:64|64le)?|sparc(?:64)?|s390x?|loongarch(?:32|64)|wasm(?:32|64)|avr|msp430|m68k|bpf|xtensa|hexagon|alpha|hppa[0-9_]*|sh[0-9]*|microblaze(?:el)?|nios2|or1k|arc(?:64)?|amdgcn");
    }

    private static String architecture(String arch) {
        return switch (arch.toLowerCase(Locale.ROOT)) {
            case "amd64", "x64" -> "x86_64";
            case "i386", "i486", "i586", "i686", "i86pc" -> "x86";
            case "arm64" -> "aarch64";
            case "sparcv9" -> "sparc64";
            case "ppc", "ppc64", "ppc64le" -> arch.toLowerCase(Locale.ROOT).replace("ppc", "powerpc");
            default -> arch.toLowerCase(Locale.ROOT);
        };
    }

    public boolean windows() { return os.equals("windows"); }
    public boolean darwin() { return os.equals("darwin"); }
    public boolean x86() { return arch.equals("x86") || arch.equals("x86_64"); }
    public boolean bits64() { return arch.contains("64") || arch.equals("s390x") || arch.equals("alpha")
            || arch.equals("bpf") || arch.equals("amdgcn"); }
    public boolean nativeDestination() { return arch.equals(host().arch) && os.equals(host().os); }
    public boolean compatible(TargetPlatform other) {
        return arch.equals(other.arch) && os.equals(other.os) && abi().equals(other.abi());
    }
    private String abi() {
        if (triple.contains("msvc")) return "msvc";
        if (triple.contains("musl")) return "musl";
        if (triple.contains("eabihf")) return "eabihf";
        if (triple.contains("eabi")) return "eabi";
        return "default";
    }
    public String objectFormat() {
        return (windows() ? "win" : darwin() ? "macho" : "elf") + (bits64() ? "64" : "32");
    }
    public String fileName(String base, TargetType type, boolean msvc) {
        return switch (type) {
            case EXECUTABLE -> base + (windows() ? ".exe" : "");
            case BINARY -> base + ".bin";
            case OBJECT -> base + (windows() ? ".obj" : ".o");
            case STATIC -> msvc ? base + ".lib" : "lib" + base + ".a";
            case SHARED -> windows() ? base + ".dll" : "lib" + base + (darwin() ? ".dylib" : ".so");
        };
    }
}
