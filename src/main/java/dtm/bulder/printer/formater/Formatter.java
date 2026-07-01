package dtm.bulder.printer.formater;

import dtm.bulder.printer.Severity;

public interface Formatter {
    String format(Severity severity, Object o, Object... args);
}
