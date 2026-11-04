package com.propfolio.common.error;

import java.util.List;

/**
 * One or more field values break a business rule that annotations cannot express
 * (e.g. end date before start date). Maps to 400 with field-level errors.
 */
public class InvalidRequestException extends RuntimeException {

    private final List<FieldError> errors;

    public InvalidRequestException(String field, String message) {
        this(List.of(new FieldError(field, message)));
    }

    public InvalidRequestException(List<FieldError> errors) {
        super(errors.isEmpty() ? "Invalid request" : errors.getFirst().message());
        this.errors = List.copyOf(errors);
    }

    /** The first field with a problem. */
    public String getField() {
        return errors.isEmpty() ? null : errors.getFirst().field();
    }

    public List<FieldError> getErrors() {
        return errors;
    }
}
