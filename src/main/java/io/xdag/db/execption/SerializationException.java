package io.xdag.db.execption;

/**
 * {@link SerializationException} is thrown when there is a problem with serialization.
 */
public final class SerializationException extends SerDeException {

    public SerializationException(
            final String message,
            final Throwable throwable
    ) {
        super(message, throwable);
    }
}
