package dtm.builder.build;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/** Checks actual outputs, catching a native assembler/compiler accidentally used for a cross target. */
public final class ObjectFormatValidator {
    private ObjectFormatValidator() { }
    private static final Map<String, Integer> ELF_MACHINES = Map.ofEntries(
            Map.entry("x86", 3), Map.entry("x86_64", 62), Map.entry("arm", 40), Map.entry("aarch64", 183),
            Map.entry("riscv32", 243), Map.entry("riscv64", 243), Map.entry("mips", 8), Map.entry("mips64", 8),
            Map.entry("powerpc", 20), Map.entry("powerpc64", 21), Map.entry("powerpc64le", 21),
            Map.entry("s390x", 22), Map.entry("loongarch64", 258), Map.entry("sparc", 2), Map.entry("sparc64", 43),
            Map.entry("avr", 83), Map.entry("msp430", 105), Map.entry("bpf", 247), Map.entry("xtensa", 94),
            Map.entry("hexagon", 164), Map.entry("m68k", 4), Map.entry("alpha", 0x9026));

    public static void validate(Path object, TargetPlatform p) throws IOException {
        byte[] header;
        try (var input = Files.newInputStream(object)) { header = input.readNBytes(64); }
        if (header.length < 20) throw new IOException("Objeto invalido: " + object);
        ByteBuffer bytes = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);
        if (header[0] == 0x7f && header[1] == 'E' && header[2] == 'L' && header[3] == 'F') {
            if (p.windows() || p.darwin()) mismatch(object, p);
            bytes.order(header[5] == 2 ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN);
            Integer machine = ELF_MACHINES.get(p.arch());
            if (machine == null && p.arch().startsWith("arm")) machine = 40;
            if (machine == null && p.arch().startsWith("mips")) machine = 8;
            if (machine != null && Short.toUnsignedInt(bytes.getShort(18)) != machine) mismatch(object, p);
            if (machine != null && (header[4] == 2) != p.bits64()) mismatch(object, p);
            return;
        }
        int magic = bytes.getInt(0);
        if (magic == 0xfeedface || magic == 0xfeedfacf) {
            if (!p.darwin()) mismatch(object, p);
            int expected = p.arch().equals("aarch64") ? 0x100000c : p.arch().equals("x86_64") ? 0x1000007
                    : p.arch().equals("x86") ? 7 : 0;
            if (expected != 0 && bytes.getInt(4) != expected) mismatch(object, p);
            return;
        }
        if (header[0] == 0 && header[1] == 'a' && header[2] == 's' && header[3] == 'm') {
            if (!p.arch().startsWith("wasm")) mismatch(object, p);
            return;
        }
        int machine = Short.toUnsignedInt(bytes.getShort(0));
        int expected = p.arch().equals("x86_64") ? 0x8664 : p.arch().equals("x86") ? 0x14c
                : p.arch().equals("aarch64") ? 0xaa64 : p.arch().startsWith("arm") ? 0x1c4 : 0;
        // Other GNU architectures may use non-ELF formats; their backend remains authoritative.
        if (p.windows() && expected != 0 && machine == expected) return;
        if (expected != 0) mismatch(object, p);
    }
    private static void mismatch(Path object, TargetPlatform p) throws IOException {
        throw new IOException("Objeto " + object + " incompativel com platform " + p.triple()
                + "; configure uma toolchain para o destino");
    }
}
