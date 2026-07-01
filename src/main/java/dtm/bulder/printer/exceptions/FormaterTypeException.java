package dtm.bulder.printer.exceptions;

public class FormaterTypeException extends RuntimeException {

    private final String format;

    public FormaterTypeException(String message, String format) {
        super(message);
        this.format = format;
    }

    public String getFormat() {
        return format;
    }
}
