package com.propfolio.extraction;

/**
 * Reading a bill failed. The message is shown to the landlord, so it is written for them
 * and never contains bill contents or the API key.
 */
public class ExtractionException extends RuntimeException {

    public ExtractionException(String message) {
        super(message);
    }
}
