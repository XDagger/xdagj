package io.xdag.config;

public enum BlockType {
    MAIN(0, "Main"),
    WALLET(1, "Wallet"),
    TRANSACTION(2, "Transaction");

    private final int code;
    private final String desc;

    BlockType(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public int getCode() {
        return this.code;
    }

    public String getDesc() {
        return this.desc;
    }
}
