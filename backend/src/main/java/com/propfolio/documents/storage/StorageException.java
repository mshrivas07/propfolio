package com.propfolio.documents.storage;

/**
 * A file couldn't be read or written, or a storage key was unsafe.
 */
public class StorageException extends RuntimeException {

    public StorageException(String message) {
        super(message);
    }

    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
