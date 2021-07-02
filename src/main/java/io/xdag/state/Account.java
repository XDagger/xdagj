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
package io.xdag.state;

import io.xdag.core.SimpleEncoder;
import io.xdag.core.XAmount;
import io.xdag.utils.SimpleDecoder;
import org.bouncycastle.util.encoders.Hex;

import java.util.Arrays;

public class Account {

    private final byte[] address;
    private XAmount available;
    private XAmount locked;
    private long nonce;

    /**
     * Creates an account instance.
     *
     */
    public Account(byte[] address, XAmount available, XAmount locked, long nonce) {
        this.address = address;
        this.available = available;
        this.locked = locked;
        this.nonce = nonce;
    }

    /**
     * Serializes this account into byte array.
     *
     */
    public byte[] toBytes() {
        SimpleEncoder enc = new SimpleEncoder();
        enc.writeXAmount(available);
        enc.writeXAmount(locked);
        enc.writeLong(nonce);

        return enc.toBytes();
    }

    /**
     * Parses an account from byte array.
     *
     */
    public static Account fromBytes(byte[] address, byte[] bytes) {
        SimpleDecoder dec = new SimpleDecoder(bytes);
        XAmount available = dec.readXAmount();
        XAmount locked = dec.readXAmount();
        long nonce = dec.readLong();

        return new Account(address, available, locked, nonce);
    }

    /**
     * Returns the address of this account.
     *
     */
    public byte[] getAddress() {
        return address;
    }

    /**
     * Returns the available balance of this account.
     *
     */
    public XAmount getAvailable() {
        return available;
    }

    /**
     * Sets the available balance of this account.
     *
     */
    void setAvailable(XAmount available) {
        this.available = available;
    }

    /**
     * Returns the locked balance of this account.
     *
     */
    public XAmount getLocked() {
        return locked;
    }

    /**
     * Sets the locked balance of this account.
     *
     */
    void setLocked(XAmount locked) {
        this.locked = locked;
    }

    /**
     * Gets the nonce of this account.
     *
     */
    public long getNonce() {
        return nonce;
    }

    /**
     * Sets the nonce of this account.
     *
     */
    void setNonce(long nonce) {
        this.nonce = nonce;
    }

    @Override
    public String toString() {
        return "Account [address=" + Arrays.toString(Hex.encode(address)) + ", available=" + available + ", locked=" + locked
                + ", nonce=" + nonce + "]";
    }

}
