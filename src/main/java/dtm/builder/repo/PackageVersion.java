package dtm.builder.repo;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Comparador de versoes semanticas tolerante a segmentos ausentes (1 == 1.0.0). */
public final class PackageVersion implements Comparable<PackageVersion> {

    private final String value;
    private final List<BigInteger> core;
    private final List<String> prerelease;

    private PackageVersion(String value, List<BigInteger> core, List<String> prerelease) {
        this.value = value;
        this.core = core;
        this.prerelease = prerelease;
    }

    public static PackageVersion parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Versao vazia");
        }
        String value = raw.trim();
        String normalized = value;
        if ((normalized.startsWith("v") || normalized.startsWith("V"))
                && normalized.length() > 1 && Character.isDigit(normalized.charAt(1))) {
            normalized = normalized.substring(1);
        }
        int plus = normalized.indexOf('+');
        if (plus >= 0) {
            normalized = normalized.substring(0, plus);
        }
        String[] split = normalized.split("-", 2);
        String[] numbers = split[0].split("\\.", -1);
        List<BigInteger> core = new ArrayList<>();
        for (String number : numbers) {
            if (number.isBlank() || !number.chars().allMatch(Character::isDigit)) {
                throw new IllegalArgumentException("Versao invalida: " + raw);
            }
            core.add(new BigInteger(number));
        }
        List<String> prerelease = split.length == 1
                ? List.of() : List.of(split[1].split("\\."));
        if (prerelease.stream().anyMatch(String::isBlank)) {
            throw new IllegalArgumentException("Versao invalida: " + raw);
        }
        return new PackageVersion(value, List.copyOf(core), prerelease);
    }

    public String value() {
        return value;
    }

    public BigInteger segment(int index) {
        return index < core.size() ? core.get(index) : BigInteger.ZERO;
    }

    @Override
    public int compareTo(PackageVersion other) {
        int length = Math.max(core.size(), other.core.size());
        for (int i = 0; i < length; i++) {
            int compared = segment(i).compareTo(other.segment(i));
            if (compared != 0) {
                return compared;
            }
        }
        if (prerelease.isEmpty() != other.prerelease.isEmpty()) {
            return prerelease.isEmpty() ? 1 : -1;
        }
        for (int i = 0; i < Math.max(prerelease.size(), other.prerelease.size()); i++) {
            if (i >= prerelease.size()) {
                return -1;
            }
            if (i >= other.prerelease.size()) {
                return 1;
            }
            String left = prerelease.get(i);
            String right = other.prerelease.get(i);
            boolean leftNumber = left.chars().allMatch(Character::isDigit);
            boolean rightNumber = right.chars().allMatch(Character::isDigit);
            int compared;
            if (leftNumber && rightNumber) {
                compared = new BigInteger(left).compareTo(new BigInteger(right));
            } else if (leftNumber != rightNumber) {
                compared = leftNumber ? -1 : 1;
            } else {
                compared = left.toLowerCase(Locale.ROOT)
                        .compareTo(right.toLowerCase(Locale.ROOT));
            }
            if (compared != 0) {
                return compared;
            }
        }
        return 0;
    }

    @Override
    public String toString() {
        return value;
    }
}
