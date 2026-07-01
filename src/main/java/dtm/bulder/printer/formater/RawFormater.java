package dtm.bulder.printer.formater;

import dtm.bulder.printer.Severity;

import java.util.ArrayList;
import java.util.List;

public final class RawFormater implements Formatter {

    private static final String RESET = "\u001B[0m";

    private static final String INFO_COLOR = "\u001B[32m";    
    private static final String WARNING_COLOR = "\u001B[33m"; 
    private static final String ERROR_COLOR = "\u001B[31m";   
    private static final String DEBUG_COLOR = "\u001B[36m";   
    private static final String TRACE_COLOR = "\u001B[90m";   

    @Override
    public String format(Severity severity, Object o, Object... args) {
        Severity currentSeverity = severity != null ? severity : Severity.INFO;

        ParsedArgs parsedArgs = parseArgs(args);

        String message = formatMessage(o, parsedArgs.messageArgs);

        if (parsedArgs.textColor != null) {
            message = parsedArgs.textColor.ansi() + message + RESET;
        }

        if (currentSeverity == Severity.NONE) {
            return message;
        }

        return colorOf(currentSeverity)
                + "["
                + padSeverity(currentSeverity)
                + "]"
                + RESET
                + " "
                + message;
    }

    private ParsedArgs parseArgs(Object... args) {
        if (args == null || args.length == 0) {
            return new ParsedArgs(null, new Object[0]);
        }

        TextColor textColor = null;
        List<Object> messageArgs = new ArrayList<>();

        for (Object arg : args) {
            if (arg instanceof TextColor color) {
                textColor = color;
                continue;
            }

            messageArgs.add(arg);
        }

        return new ParsedArgs(textColor, messageArgs.toArray());
    }

    private String formatMessage(Object object, Object... args) {
        String pattern = String.valueOf(object);

        if (args == null || args.length == 0) {
            return pattern;
        }

        StringBuilder builder = new StringBuilder();
        int argIndex = 0;

        for (int i = 0; i < pattern.length(); i++) {
            char current = pattern.charAt(i);

            if (current == '{'
                    && i + 1 < pattern.length()
                    && pattern.charAt(i + 1) == '}') {

                if (argIndex < args.length) {
                    builder.append(String.valueOf(args[argIndex++]));
                } else {
                    builder.append("{}");
                }

                i++;
                continue;
            }

            builder.append(current);
        }

        return builder.toString();
    }

    private String colorOf(Severity severity) {
        return switch (severity) {
            case INFO -> INFO_COLOR;
            case WARNING -> WARNING_COLOR;
            case ERROR -> ERROR_COLOR;
            case DEBUG -> DEBUG_COLOR;
            case TRACE -> TRACE_COLOR;
            case NONE -> "";
        };
    }

    private String padSeverity(Severity severity) {
        return switch (severity) {
            case INFO -> "INFO";
            case ERROR -> "ERROR";
            case DEBUG -> "DEBUG";
            case TRACE -> "TRACE";
            case WARNING -> "WARNING";
            case NONE -> "";
        };
    }

    private static final class ParsedArgs {

        private final TextColor textColor;
        private final Object[] messageArgs;

        private ParsedArgs(TextColor textColor, Object[] messageArgs) {
            this.textColor = textColor;
            this.messageArgs = messageArgs;
        }
    }

    public enum TextColor {
        BLACK("\u001B[30m"),
        RED("\u001B[31m"),
        GREEN("\u001B[32m"),
        YELLOW("\u001B[33m"),
        BLUE("\u001B[34m"),
        MAGENTA("\u001B[35m"),
        CYAN("\u001B[36m"),
        WHITE("\u001B[37m"),
        GRAY("\u001B[90m");

        private final String ansi;

        TextColor(String ansi) {
            this.ansi = ansi;
        }

        public String ansi() {
            return ansi;
        }
    }
}
