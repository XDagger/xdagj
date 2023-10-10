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
package io.xdag.core.state;

import org.apache.tuweni.bytes.Bytes;

import io.xdag.core.XAmount;
import io.xdag.utils.SimpleDecoder;
import io.xdag.utils.SimpleEncoder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Account {

    private final byte[] address;
    private XAmount available;
    private XAmount locked;
    private long nonce;

    /**
     * Creates an account instance.
     */
    public Account(byte[] address, XAmount available, XAmount locked, long nonce) {
        this.address = address;
        this.available = available;
        this.locked = locked;
        this.nonce = nonce;
    }

    /**
     * Serializes this account into byte array.
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
     */
    public static Account fromBytes(byte[] address, byte[] bytes) {
        SimpleDecoder dec = new SimpleDecoder(bytes);
        XAmount available = dec.readXAmount();
        XAmount locked = dec.readXAmount();
        long nonce = dec.readLong();

        return new Account(address, available, locked, nonce);
    }

    @Override
    public String toString() {
        return "Account [address=" + Bytes.wrap(address).toHexString() + ", available=" + available + ", locked=" + locked
                + ", nonce=" + nonce + "]";
    }
}
