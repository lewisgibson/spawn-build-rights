package com.lewisgibson.spawnbuildrights.core.grant;

/** The store could not be opened, read or written. The message says which and why. */
public final class StorageException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * @param message which operation failed and on which store
     * @param cause the underlying failure, or {@code null} when there is none
     */
    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
