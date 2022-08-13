package io.xdag.err;

public class BusinessException extends Exception{
    private int code;

    public BusinessException(int code, String msg) {
        super(msg);
        this.code = code;
    }

    public BusinessException(int code, String msg, Throwable throwable) {
        super(msg, throwable);
        this.code = code;
    }

    public int getCode() {
        return this.code;
    }

    /** @deprecated */
    @Deprecated
    public String getMsg() {
        return this.getMessage();
    }
}
