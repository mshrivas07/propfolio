package com.propfolio.common.error;

/**
 * A field value breaks a business rule that annotations cannot express
 * (e.g. end date before start date). Maps to 400 with a field-level error.
 */
public class InvalidRequestException extends RuntimeException {

    private final String field;

    public InvalidRequestException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
