package io.xdag.config;

public enum BlockState {
    MAIN(0, "Main"),
    REJECTED(1, "Rejected"),
    ACCEPTED(2, "Accepted"),
    PENDING(3, "Pending");

    private final int code;
    private final String desc;

    BlockState(int code, String desc) {
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
