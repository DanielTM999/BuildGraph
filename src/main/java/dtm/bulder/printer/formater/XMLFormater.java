package dtm.bulder.printer.formater;

import dtm.bulder.printer.Severity;

public final class XMLFormater implements Formatter {

    @Override
    public String format(Severity severity, Object o, Object... args) {
        Severity sev = severity != null ? severity : Severity.INFO;
        String message = MessageRenderer.render(o, args);
        return "<log severity=\"" + sev.name() + "\" ts=\"" + System.currentTimeMillis() + "\">"
                + escape(message) + "</log>";
    }

    private static String escape(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '&' -> sb.append("&amp;");
                case '<' -> sb.append("&lt;");
                case '>' -> sb.append("&gt;");
                case '"' -> sb.append("&quot;");
                case '\'' -> sb.append("&apos;");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }
}
