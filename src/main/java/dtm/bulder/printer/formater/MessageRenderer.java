package dtm.bulder.printer.formater;

import java.util.ArrayList;
import java.util.List;

public final class MessageRenderer {

    private MessageRenderer() {
    }

    public static String render(Object pattern, Object... args) {
        List<Object> messageArgs = new ArrayList<>();
        if (args != null) {
            for (Object arg : args) {
                if (arg instanceof RawFormater.TextColor) {
                    continue;
                }
                messageArgs.add(arg);
            }
        }
        return substitute(String.valueOf(pattern), messageArgs.toArray());
    }

    private static String substitute(String pattern, Object[] args) {
        if (args == null || args.length == 0) {
            return pattern;
        }
        StringBuilder builder = new StringBuilder();
        int argIndex = 0;
        for (int i = 0; i < pattern.length(); i++) {
            char current = pattern.charAt(i);
            if (current == '{' && i + 1 < pattern.length() && pattern.charAt(i + 1) == '}') {
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
}
