package dtm.builder.build;

import dtm.builder.manifest.model.ManifestRootModel;
import java.nio.file.Path;
import java.util.Locale;

/** Resolves only tools needed by a selected action; never substitutes a native tool for a cross tool. */
public final class NativeTools {
    private NativeTools() { }
    public record Tool(Path executable, String kind) { }
    public static boolean specified(String value) { return value != null && !value.isBlank(); }

    public static Path executable(String configured, String fallback, Path project) {
        String value = specified(configured) ? configured.trim() : fallback;
        Path path = Path.of(value);
        if (!path.isAbsolute() && (value.contains("/") || value.contains("\\"))) path = project.resolve(path).normalize();
        Path found = ToolProbe.findOnPath(path.toString());
        return found == null ? path : found.toAbsolutePath().normalize();
    }

    public static String name(Path path) {
        return path.getFileName().toString().toLowerCase(Locale.ROOT).replaceFirst("\\.exe$", "");
    }

    public static String prefixed(TargetPlatform platform, String tool) {
        return platform.nativeDestination() ? tool : platform.triple().replaceFirst("^x86-", "i686-") + "-" + tool;
    }

    public static Tool assembler(ManifestRootModel m, TargetPlatform p, Path source, Path project) {
        String kind = m.getAsmKind();
        String configured = m.getAsmCompiler();
        if (!specified(kind) && specified(configured)) {
            String name = name(Path.of(configured));
            kind = name.equals("nasm") ? "nasm" : name.equals("ml") || name.equals("ml64") ? "masm"
                    : name.equals("as") || name.endsWith("-as") ? "gas" : null;
            if (kind == null) throw new IllegalArgumentException("asmKind obrigatorio para assembler personalizado: " + configured);
        }
        if (!specified(kind)) kind = source.toString().toLowerCase(Locale.ROOT).endsWith(".asm") ? "nasm" : "gas";
        kind = kind.toLowerCase(Locale.ROOT);
        if (!kind.equals("gas") && !kind.equals("nasm") && !kind.equals("masm"))
            throw new IllegalArgumentException("asmKind desconhecido: " + kind);
        if (!kind.equals("gas") && !p.x86())
            throw new IllegalArgumentException(kind + " nao suporta platform " + p.triple());
        if (kind.equals("masm") && !p.windows())
            throw new IllegalArgumentException("MASM requer destino Windows/COFF: " + p.triple());
        if (SourceCollector.needsPreprocessor(source) && !kind.equals("gas"))
            throw new IllegalArgumentException("Fonte .S requer asmKind gas: " + source);
        String fallback = kind.equals("nasm") ? "nasm" : kind.equals("masm") ? p.bits64() ? "ml64" : "ml"
                : prefixed(p, "as");
        Path exe = executable(configured, fallback, project);
        if (kind.equals("masm") && ((name(exe).equals("ml64") && !p.bits64()) || (name(exe).equals("ml") && p.bits64())))
            throw new IllegalArgumentException("Assembler " + exe + " incompativel com " + p.triple());
        return new Tool(exe, kind);
    }

    public static Tool linker(ManifestRootModel m, TargetPlatform p, Path project) {
        String kind = m.getLinkerKind();
        if (!specified(kind) && specified(m.getLinker())) {
            String name = name(Path.of(m.getLinker()));
            kind = name.equals("link") || name.equals("lld-link") ? "msvc"
                    : name.equals("ld64.lld") ? "darwin"
                    : name.equals("ld") || name.endsWith("-ld") || name.equals("ld.lld") || name.equals("ld.bfd") || name.equals("ld.gold")
                    ? p.darwin() ? "darwin" : "gnu" : null;
            if (kind == null) throw new IllegalArgumentException("linkerKind obrigatorio para linker personalizado: " + m.getLinker());
        }
        if (!specified(kind)) kind = p.windows() && ("masm".equalsIgnoreCase(m.getAsmKind()) || p.triple().contains("msvc"))
                ? "msvc" : p.darwin() ? "darwin" : "gnu";
        kind = kind.toLowerCase(Locale.ROOT);
        if (!kind.equals("gnu") && !kind.equals("msvc") && !kind.equals("darwin"))
            throw new IllegalArgumentException("linkerKind desconhecido: " + kind);
        if (kind.equals("msvc") && !p.windows() || kind.equals("darwin") && !p.darwin())
            throw new IllegalArgumentException("Linker " + kind + " incompativel com " + p.triple());
        return new Tool(executable(m.getLinker(), kind.equals("msvc") ? "link" : prefixed(p, "ld"), project), kind);
    }

    public static Toolchain compiler(ManifestRootModel m, TargetPlatform p, Toolchain fallback, Path project) {
        Toolchain tc = fallback;
        if (specified(m.getCCompiler()) || specified(m.getCxxCompiler())) tc = ToolchainDetector.resolve(m, null);
        if (tc == null && p.nativeDestination()) tc = ToolchainDetector.resolve(m, null);
        if (!p.nativeDestination() && !specified(m.getCCompiler()) && !specified(m.getCxxCompiler())
                && (tc == null || (tc.kind() != ToolchainKind.SYSTEM_CLANG && tc.kind() != ToolchainKind.BUNDLED_LLVM))) {
            tc = new Toolchain(ToolchainKind.GCC, executable(null, prefixed(p, "gcc"), project),
                    executable(null, prefixed(p, "g++"), project));
        }
        if (tc == null) throw new IllegalArgumentException("Nenhuma toolchain C/C++ encontrada para " + p.triple());
        if (specified(m.getCCompiler()) || specified(m.getCxxCompiler())) {
            Path cc = specified(m.getCCompiler()) ? executable(m.getCCompiler(), "gcc", project) : null;
            Path cxx = specified(m.getCxxCompiler()) ? executable(m.getCxxCompiler(), "g++", project) : cc;
            tc = new Toolchain(tc.kind(), cc, cxx);
        }
        if (tc.isMsvc() && !p.windows()) throw new IllegalArgumentException("MSVC incompativel com " + p.triple());
        return tc;
    }

    public static void validateAssembler(Tool tool, TargetPlatform requested, BuildRequest req,
                                         ManifestRootModel manifest) {
        if (!tool.kind().equals("gas") || req.executor() != ProcessExecutor.REAL) return;
        java.util.List<String> output = new java.util.ArrayList<>();
        java.util.Map<String, String> probeEnv = new java.util.LinkedHashMap<>(manifest.getEnv());
        probeEnv.put("LC_ALL", "C");
        int exit = req.executor().run(java.util.List.of(tool.executable().toString(), "--version"),
                req.projectPath(), probeEnv, output::add);
        if (exit != 0) throw new IllegalArgumentException("Assembler indisponivel: " + tool.executable());
        String text = String.join("\n", output);
        var matcher = java.util.regex.Pattern.compile("(?:target of|target:)\\s+[`'\"]?([A-Za-z0-9_.-]+)",
                java.util.regex.Pattern.CASE_INSENSITIVE).matcher(text);
        if (!matcher.find()) throw new IllegalArgumentException("GNU assembler nao informou seu destino: " + tool.executable());
        String triple = matcher.group(1);
        // Tool triples such as avr and xtensa-esp32-elf are valid even without an OS component.
        String architecture = triple.split("-")[0];
        TargetPlatform actual = TargetPlatform.resolve(architecture + "-none-elf", null);
        if (!actual.arch().equals(requested.arch()) && !(actual.x86() && requested.x86()))
            throw new IllegalArgumentException("Assembler para " + triple + " incompativel com " + requested.triple());
    }
}
