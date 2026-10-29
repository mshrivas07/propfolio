package com.propfolio.common.error;

import java.util.Map;

/**
 * The request is valid on its own but clashes with existing data
 * (duplicate name, overlapping lease, percentages over 100, dependent records). Maps to 409.
 *
 * <p>Optional {@code properties} are added to the ProblemDetail body, e.g. the id of the
 * existing record a duplicate upload matches.
 */
public class ConflictException extends RuntimeException {

    private final Map<String, Object> properties;

    public ConflictException(String message) {
        this(message, Map.of());
    }

    public ConflictException(String message, Map<String, Object> properties) {
        super(message);
        this.properties = Map.copyOf(properties);
    }

    public Map<String, Object> getProperties() {
        return properties;
    }
}
