package io.xdag.core;

/**
 * @Classname XdagState
 * @Description XDAG矿池或者矿工的一些状态
 * @Date 2020/6/13 19:06
 * @Created by Myron
 */
public enum XdagState {
    /**
     * The pool is initializing......
     */
    INIT(0x00),
    /**
     * wallet generating keys....
     */
    KEYS(0x01),
    /**
     * The local storage is corrupted. Resetting blocks engine.
     */
    REST(0x02),
    /**
     * Loading blocks from the local storage.
     */
    LOAD(0x03),
    /**
     * Blocks loaded. Waiting for 'run' command.
     */
    STOP(0x04),
    /**
     * Trying to connect to the test network.
     */
    WTST(0x05),
    /**
     * Trying to connect to the main network.
     */
    WAIT(0x06),

    /**
     * Connected to the test network. Synchronizing.
     */
    CTST(0x07),
    /**
     * Connected to the main network. Synchronizing.
     */
    CONN(0x08),

    /**
     * Synchronized with the test network. Normal testing.
     */
    STST(0x09),
    /**
     * Synchronized with the main network. Normal operation.
     */
    SYNC(0x0a),

    /**
     * Waiting for transfer to complete.
     */
    XFER(0x0b);

    private int cmd;
    private int temp;

    XdagState(int cmd) {
        this.cmd = cmd;
        this.temp = -1;
    }


    public byte asByte() {
        return (byte) cmd;
    }

    public void setState(XdagState state) {
        this.cmd = state.asByte();
    }

    public void tempSet(XdagState state) {
        this.temp = this.cmd;
        this.cmd = state.asByte();
    }

    public void rollback() {
        this.cmd = this.temp;
        this.temp = -1;
    }



    public XdagState fromByte(byte i) {
        switch (i) {
        case 0x00:
            return INIT;
        case 0x01:
            return KEYS;
        case 0x02:
            return REST;
        case 0x03:
            return LOAD;
        case 0x04:
            return STOP;
        case 0x05:
            return WTST;
        case 0x06:
            return WAIT;
        case 0x07:
            return CTST;
        case 0x08:
            return CONN;
        case 0x09:
            return STST;
        case 0x0a:
            return SYNC;
        case 0x0b:
            return XFER;
        default:
            return null;

        }
    }


    @Override
    public String toString() {
        switch (cmd) {
        case 0x00:
            return "Pool Initializing....";
        case 0x01:
            return "Generating keys...";
        case 0x02:
            return "The local storage is corrupted. Resetting blocks engine.";
        case 0x03:
            return "Loading blocks from the local storage.";
        case 0x04:
            return "Blocks loaded. Waiting for 'run' command.";
        case 0x05:
            return "Trying to connect to the test network.";
        case 0x06:
            return "Trying to connect to the main network.";
        case 0x07:
            return "Connected to the test network. Synchronizing.";
        case 0x08:
            return "Connected to the main network. Synchronizing.";
        case 0x09:
            return "Synchronized with the test network. Normal testing.";
        case 0x0a:
            return "Synchronized with the main network. Normal operation.";
        case 0x0b:
            return "Waiting for transfer to complete.";
        default:
            return "Abnormal State";
        }
    }
}
