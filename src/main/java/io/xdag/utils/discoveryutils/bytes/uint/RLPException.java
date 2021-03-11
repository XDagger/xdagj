package io.xdag.utils.discoveryutils.bytes.uint;

public class RLPException extends RuntimeException {
    public RLPException(final String message) {
        this(message, null);
    }

    public RLPException(final String message, final Throwable throwable) {
        super(message, throwable);
    }
}
