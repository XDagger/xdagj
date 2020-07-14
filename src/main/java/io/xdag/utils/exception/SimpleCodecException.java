package io.xdag.utils.exception;

public class SimpleCodecException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public SimpleCodecException() {
    }

    public SimpleCodecException(String s) {
        super(s);
    }

    public SimpleCodecException(String s, Throwable throwable) {
        super(s, throwable);
    }

    public SimpleCodecException(Throwable throwable) {
        super(throwable);
    }

    public SimpleCodecException(String s, Throwable throwable, boolean b, boolean b1) {
        super(s, throwable, b, b1);
    }
}
