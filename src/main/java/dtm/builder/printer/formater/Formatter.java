package dtm.builder.printer.formater;

import dtm.builder.printer.Severity;

public interface Formatter {
    String format(Severity severity, Object o, Object... args);
}
