/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2020-2030 The XdagJ Developers
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package io.xdag.core;

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
     * Trying to connect to the  dev network.
     */
    WDST(0x05),
    /**
     * Trying to connect to the test network.
     */
    WTST(0x06),
    /**
     * Trying to connect to the main network.
     */
    WAIT(0x07),

    /**
     * Connected to the  dev network. Synchronizing.
     */
    CDST(0x08),
    /**
     * Connected to the test network. Synchronizing.
     */
    CTST(0x09),
    /**
     * Connected to the main network. Synchronizing.
     */
    CONN(0x0a),

    /**
     * Synchronized with the  dev network. Normal testing.
     */
    SDST(0x0b),
    /**
     * Synchronized with the test network. Normal testing.
     */
    STST(0x0c),
    /**
     * Synchronized with the main network. Normal operation.
     */
    SYNC(0x0d),

    /**
     * Waiting for transfer to complete.
     */
    XFER(0x0e);

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

    @Override
    public String toString() {
        return switch (cmd) {
            case 0x00 -> "Pool Initializing....";
            case 0x01 -> "Generating keys...";
            case 0x02 -> "The local storage is corrupted. Resetting blocks engine.";
            case 0x03 -> "Loading blocks from the local storage.";
            case 0x04 -> "Blocks loaded. Waiting for 'run' command.";
            case 0x05 -> "Trying to connect to the  dev network.";
            case 0x06 -> "Trying to connect to the test network.";
            case 0x07 -> "Trying to connect to the main network.";
            case 0x08 -> "Connected to the  dev network. Synchronizing.";
            case 0x09 -> "Connected to the test network. Synchronizing.";
            case 0x0a -> "Connected to the main network. Synchronizing.";
            case 0x0b -> "Synchronized with the  dev network. Normal testing.";
            case 0x0c -> "Synchronized with the test network. Normal testing.";
            case 0x0d -> "Synchronized with the main network. Normal operation.";
            case 0x0e -> "Waiting for transfer to complete.";
            default -> "Abnormal State";
        };
    }
}
