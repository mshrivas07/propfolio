package com.propfolio.common.error;

/**
 * The requested record does not exist for the signed-in landlord.
 * Also used for records owned by another landlord, so ids from other accounts
 * are indistinguishable from ids that never existed (404, never 403).
 */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String resource) {
        super(resource + " not found");
    }
}
