package dtm.builder.repo;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

/** Restricoes exatas e ranges simples: Maven, comparadores, ^, ~ e wildcards. */
public final class VersionConstraint {

    private final String expression;
    private final List<Bound> bounds;

    private VersionConstraint(String expression, List<Bound> bounds) {
        this.expression = expression;
        this.bounds = bounds;
    }

    public static VersionConstraint exact(String version) {
        PackageVersion parsed = PackageVersion.parse(version);
        return new VersionConstraint(version.trim(), List.of(Bound.exact(parsed)));
    }

    public static VersionConstraint parse(String raw) {
        if (raw == null || raw.isBlank() || "*".equals(raw.trim())) {
            return new VersionConstraint("*", List.of());
        }
        String expression = raw.trim();
        if ((expression.startsWith("[") || expression.startsWith("("))
                && (expression.endsWith("]") || expression.endsWith(")"))) {
            return mavenRange(expression);
        }
        if (expression.startsWith("^")) {
            return caret(expression);
        }
        if (expression.startsWith("~")) {
            return tilde(expression);
        }
        if (expression.matches(".*[xX*].*")) {
            return wildcard(expression);
        }
        if (expression.matches(".+\\s+-\\s+.+")) {
            String[] ends = expression.split("\\s+-\\s+", 2);
            return new VersionConstraint(expression, List.of(
                    Bound.lower(PackageVersion.parse(ends[0]), true),
                    Bound.upper(PackageVersion.parse(ends[1]), true)));
        }
        if (expression.startsWith(">") || expression.startsWith("<")
                || expression.startsWith("=")) {
            List<Bound> bounds = new ArrayList<>();
            for (String token : expression.replace(',', ' ').trim().split("\\s+")) {
                if (!token.isBlank()) {
                    bounds.add(comparator(token));
                }
            }
            return new VersionConstraint(expression, List.copyOf(bounds));
        }
        return exact(expression);
    }

    public boolean matches(String version) {
        return matches(PackageVersion.parse(version));
    }

    public boolean matches(PackageVersion version) {
        for (Bound bound : bounds) {
            int compared = version.compareTo(bound.version());
            if (bound.exact()) {
                if (compared != 0) {
                    return false;
                }
            } else if (bound.isLower()) {
                if (compared < 0 || (compared == 0 && !bound.inclusive())) {
                    return false;
                }
            } else if (compared > 0 || (compared == 0 && !bound.inclusive())) {
                return false;
            }
        }
        return true;
    }

    @Override
    public String toString() {
        return expression;
    }

    private static VersionConstraint mavenRange(String expression) {
        String body = expression.substring(1, expression.length() - 1);
        String[] ends = body.split(",", -1);
        if (ends.length != 2) {
            throw new IllegalArgumentException("Range invalido: " + expression);
        }
        List<Bound> bounds = new ArrayList<>();
        if (!ends[0].isBlank()) {
            bounds.add(Bound.lower(PackageVersion.parse(ends[0].trim()),
                    expression.startsWith("[")));
        }
        if (!ends[1].isBlank()) {
            bounds.add(Bound.upper(PackageVersion.parse(ends[1].trim()),
                    expression.endsWith("]")));
        }
        return new VersionConstraint(expression, List.copyOf(bounds));
    }

    private static VersionConstraint caret(String expression) {
        PackageVersion lower = PackageVersion.parse(expression.substring(1));
        BigInteger major = lower.segment(0);
        BigInteger minor = lower.segment(1);
        String upper;
        if (major.signum() > 0) {
            upper = major.add(BigInteger.ONE) + ".0.0";
        } else if (minor.signum() > 0) {
            upper = "0." + minor.add(BigInteger.ONE) + ".0";
        } else {
            upper = "0.0." + lower.segment(2).add(BigInteger.ONE);
        }
        return range(expression, lower, PackageVersion.parse(upper));
    }

    private static VersionConstraint tilde(String expression) {
        String raw = expression.substring(1);
        PackageVersion lower = PackageVersion.parse(raw);
        int segments = raw.split("-", 2)[0].split("\\.").length;
        String upper = segments <= 1
                ? lower.segment(0).add(BigInteger.ONE) + ".0.0"
                : lower.segment(0) + "." + lower.segment(1).add(BigInteger.ONE) + ".0";
        return range(expression, lower, PackageVersion.parse(upper));
    }

    private static VersionConstraint wildcard(String expression) {
        String[] parts = expression.split("\\.");
        int wildcard = -1;
        for (int i = 0; i < parts.length; i++) {
            if (parts[i].equals("*") || parts[i].equalsIgnoreCase("x")) {
                wildcard = i;
                break;
            }
        }
        if (wildcard <= 0) {
            return new VersionConstraint(expression, List.of());
        }
        StringBuilder lowerText = new StringBuilder();
        for (int i = 0; i < wildcard; i++) {
            if (i > 0) lowerText.append('.');
            lowerText.append(parts[i]);
        }
        PackageVersion lower = PackageVersion.parse(lowerText.toString());
        int bump = wildcard - 1;
        StringBuilder upper = new StringBuilder();
        for (int i = 0; i <= bump; i++) {
            if (i > 0) upper.append('.');
            BigInteger value = lower.segment(i);
            upper.append(i == bump ? value.add(BigInteger.ONE) : value);
        }
        while (upper.toString().split("\\.").length < 3) {
            upper.append(".0");
        }
        return range(expression, lower, PackageVersion.parse(upper.toString()));
    }

    private static VersionConstraint range(String expression, PackageVersion lower,
                                           PackageVersion upper) {
        return new VersionConstraint(expression, List.of(
                Bound.lower(lower, true), Bound.upper(upper, false)));
    }

    private static Bound comparator(String token) {
        String operator;
        if (token.startsWith(">=") || token.startsWith("<=")) {
            operator = token.substring(0, 2);
        } else if (token.startsWith(">") || token.startsWith("<") || token.startsWith("=")) {
            operator = token.substring(0, 1);
        } else {
            throw new IllegalArgumentException("Comparador invalido: " + token);
        }
        PackageVersion version = PackageVersion.parse(token.substring(operator.length()));
        return switch (operator) {
            case ">=" -> Bound.lower(version, true);
            case ">" -> Bound.lower(version, false);
            case "<=" -> Bound.upper(version, true);
            case "<" -> Bound.upper(version, false);
            default -> Bound.exact(version);
        };
    }

    private record Bound(PackageVersion version, int kind, boolean inclusive) {
        private static Bound exact(PackageVersion version) {
            return new Bound(version, 0, true);
        }

        private static Bound lower(PackageVersion version, boolean inclusive) {
            return new Bound(version, 1, inclusive);
        }

        private static Bound upper(PackageVersion version, boolean inclusive) {
            return new Bound(version, 2, inclusive);
        }

        private boolean exact() {
            return kind == 0;
        }

        private boolean isLower() {
            return kind == 1;
        }
    }
}
