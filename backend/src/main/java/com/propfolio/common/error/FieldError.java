package com.propfolio.common.error;

/**
 * One field-level problem, returned in the "errors" array of a 400 ProblemDetail.
 */
public record FieldError(String field, String message) {
}
