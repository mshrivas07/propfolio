package com.propfolio.common.error;

/**
 * The request is valid on its own but clashes with existing data
 * (duplicate name, overlapping lease, percentages over 100, dependent records). Maps to 409.
 */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
