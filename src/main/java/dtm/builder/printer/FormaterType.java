package dtm.builder.printer;

import dtm.builder.printer.exceptions.FormaterTypeException;

public enum FormaterType {
    JSON,
    XML,
    RAW;

    public static FormaterType ofString(String name) {
        if (name == null || name.isBlank()) {
            throw new FormaterTypeException("Invalid format", "null");
        }

        for (FormaterType type : values()) {
            if (type.name().equalsIgnoreCase(name.trim())) {
                return type;
            }
        }

        throw new FormaterTypeException("Invalid format", name);
    }
}
