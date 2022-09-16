package io.xdag.err;

public enum ErrCode{

    ERR_UNKNOWN(1000001, "unknow error"),
    ERR_STACK_SIZE_OVER(1000002,"stack size over than target"),
    ERR_TRANSACTION(1000003,"invalid transaction"),
    ERR_BLOCK_HAVE_APPLIED(1000004,"block have applied"),

    ;

    private final int code;
    private final String msg;

    ErrCode(int code, String msg) {
        this.code = code;
        this.msg = msg;
    }

    public int code() {
        return this.code;
    }

    public String msg() {
        return this.msg;
    }
}
