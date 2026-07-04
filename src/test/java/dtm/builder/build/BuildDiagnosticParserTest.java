package dtm.builder.build;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BuildDiagnosticParserTest {

    @Test
    void classifiesBuildProgressAsInformationalOutput() {
        assertEquals(BuildDiagnosticParser.Level.NOTE,
                BuildDiagnosticParser.classify("[0/4] Iniciando build"));
        assertEquals(BuildDiagnosticParser.Level.NOTE,
                BuildDiagnosticParser.classify("[3/4] Compilado src/main.cpp (0.125 s)"));
    }
}
