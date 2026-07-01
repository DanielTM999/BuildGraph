package dtm.bulder.manifest.model;

public class ManifestDiagnostic {

    private String code;
    private DiagnosticSeverity severity;
    private String message;
    private int offset;
    private int startLine;
    private int startCol;
    private int endLine;
    private int endCol;

    public ManifestDiagnostic() {
    }

    public ManifestDiagnostic(String code, DiagnosticSeverity severity, String message) {
        this.code = code;
        this.severity = severity;
        this.message = message;
    }

    public static ManifestDiagnostic error(String code, String message) {
        return new ManifestDiagnostic(code, DiagnosticSeverity.ERROR, message);
    }

    public static ManifestDiagnostic warning(String code, String message) {
        return new ManifestDiagnostic(code, DiagnosticSeverity.WARNING, message);
    }

    public boolean isError() {
        return severity == DiagnosticSeverity.ERROR;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public DiagnosticSeverity getSeverity() {
        return severity;
    }

    public void setSeverity(DiagnosticSeverity severity) {
        this.severity = severity;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public int getOffset() {
        return offset;
    }

    public void setOffset(int offset) {
        this.offset = offset;
    }

    public int getStartLine() {
        return startLine;
    }

    public void setStartLine(int startLine) {
        this.startLine = startLine;
    }

    public int getStartCol() {
        return startCol;
    }

    public void setStartCol(int startCol) {
        this.startCol = startCol;
    }

    public int getEndLine() {
        return endLine;
    }

    public void setEndLine(int endLine) {
        this.endLine = endLine;
    }

    public int getEndCol() {
        return endCol;
    }

    public void setEndCol(int endCol) {
        this.endCol = endCol;
    }

    @Override
    public String toString() {
        return "[" + severity + "] " + code + ": " + message;
    }
}
