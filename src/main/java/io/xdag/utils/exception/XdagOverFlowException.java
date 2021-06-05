package io.xdag.utils.exception;

public class XdagOverFlowException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public XdagOverFlowException() {
        super("amount overflow!");
    }

    public XdagOverFlowException(
            String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }

    public XdagOverFlowException(String message, Throwable cause) {
        super(message, cause);
    }

    public XdagOverFlowException(String message) {
        super(message);
    }

    public XdagOverFlowException(Throwable cause) {
        super(cause);
    }

}
