package io.xdag.rpc;

public enum ErrorCode {
    SUCCESS(0, "success"),

    // param error
    ERR_PARAM_INVALID(1001, "param invalid"),
    ERR_BALANCE_NOT_ENOUGH(1002, "balance not enough"),
    ERR_VALUE_INVALID(1003, "The transfer amount must be greater than 0"),
    ERR_TO_ADDRESS_INVALID(1004, "To address is illegal"),
    ERR_WALLET_UNLOCK(1005, "wallet unlock failed"),

    ;
    private final int code;
    private final String msg;

    private ErrorCode(int code, String msg) {
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
