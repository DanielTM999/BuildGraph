package dtm.builder.build;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ResponseFileTest {
    @TempDir Path temp;
    @Test void gnuArgumentsAreQuotedAndTemporaryFileIsRemoved() throws Exception {
        Path path;
        try (var response = ResponseFile.prepare(List.of("g++", "C:\\path with spaces\\unit.o", "-DVALUE=\"hello world\""), temp, 1)) {
            path = Path.of(response.command().getLast().substring(1));
            assertEquals("\"C:\\\\path with spaces\\\\unit.o\"\n\"-DVALUE=\\\"hello world\\\"\"\n", Files.readString(path));
        }
        assertFalse(Files.exists(path));
    }
    @Test void msvcResponseUsesUnicodeAndWindowsBackslashRules() throws Exception {
        try (var response = ResponseFile.prepare(List.of("cl.exe", "C:\\with spaces\\", "/DVALUE=\"a b\""), temp, 1)) {
            byte[] bytes = Files.readAllBytes(Path.of(response.command().getLast().substring(1)));
            assertEquals((byte) 0xff, bytes[0]); assertEquals((byte) 0xfe, bytes[1]);
            assertEquals("\"C:\\with spaces\\\\\"\n\"/DVALUE=\\\"a b\\\"\"\n", new String(bytes, 2, bytes.length - 2, StandardCharsets.UTF_16LE));
        }
    }
}
