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
package io.xdag.evm.client;

import io.xdag.core.SimpleEncoder;
import io.xdag.core.XAmount;
import io.xdag.evm.program.InternalTransaction;
import io.xdag.utils.EVMUtils;
import io.xdag.utils.SimpleDecoder;
import org.apache.tuweni.bytes.Bytes;

/**
 * Represents a Xdag Evm internal transaction.
 */
public class XdagEvmInternalTransaction {

    private Bytes rootTxHash;
    private boolean rejected;
    private int depth;
    private int index;
    private String type;

    private Bytes from;
    private Bytes to;
    private long nonce;
    private XAmount value;
    private Bytes data;
    private long gas;
    private XAmount gasPrice;

    public XdagEvmInternalTransaction(Bytes rootTxHash, InternalTransaction it) {
        this(rootTxHash, it.isRejected(), it.getDepth(), it.getIndex(), it.getType(),
                it.getFrom(), it.getTo(), it.getNonce(),
                EVMUtils.weiToXAmount(it.getValue()),
                it.getData(), it.getGas(),
                EVMUtils.weiToXAmount(it.getGasPrice()));
    }

    public XdagEvmInternalTransaction(Bytes rootTxHash, boolean rejected, int depth, int index,
                                    String type, Bytes from, Bytes to, long nonce,
                                    XAmount value, Bytes data, long gas, XAmount gasPrice) {
        this.rootTxHash = rootTxHash;
        this.rejected = rejected;
        this.depth = depth;
        this.index = index;
        this.type = type;
        this.from = from;
        this.to = to;
        this.nonce = nonce;
        this.value = value;
        this.data = data;
        this.gas = gas;
        this.gasPrice = gasPrice;
    }

    public Bytes getRootTxHash() {
        return rootTxHash;
    }

    public boolean isRejected() {
        return rejected;
    }

    public int getDepth() {
        return depth;
    }

    public int getIndex() {
        return index;
    }

    public String getType() {
        return type;
    }

    public Bytes getFrom() {
        return from;
    }

    public Bytes getTo() {
        return to;
    }

    public long getNonce() {
        return nonce;
    }

    public XAmount getValue() {
        return value;
    }

    public Bytes getData() {
        return data;
    }

    public long getGas() {
        return gas;
    }

    public XAmount getGasPrice() {
        return gasPrice;
    }

    public byte[] toBytes() {
        SimpleEncoder enc = new SimpleEncoder();
        enc.writeBytes(this.getRootTxHash());
        enc.writeBoolean(this.isRejected());
        enc.writeInt(this.getDepth());
        enc.writeInt(this.getIndex());
        enc.writeString(this.getType());
        enc.writeBytes(this.getFrom());
        enc.writeBytes(this.getTo());
        enc.writeLong(this.getNonce());
        enc.writeXAmount(this.getValue());
        enc.writeBytes(this.getData());
        enc.writeLong(this.getGas());
        enc.writeXAmount(this.getGasPrice());

        return enc.toBytes();
    }

    public static XdagEvmInternalTransaction fromBytes(byte[] bytes) {
        SimpleDecoder dec = new SimpleDecoder(bytes);
        Bytes rootTxHash = Bytes.wrap(dec.readBytes());
        boolean isRejected = dec.readBoolean();
        int depth = dec.readInt();
        int index = dec.readInt();
        String type = dec.readString();
        Bytes from = Bytes.wrap(dec.readBytes());
        Bytes to = Bytes.wrap(dec.readBytes());
        long nonce = dec.readLong();
        XAmount value = dec.readXAmount();
        Bytes data = Bytes.wrap(dec.readBytes());
        long gas = dec.readLong();
        XAmount gasPrice = dec.readXAmount();

        return new XdagEvmInternalTransaction(rootTxHash, isRejected, depth, index,
                type, from, to, nonce, value, data, gas, gasPrice);
    }

    @Override
    public String toString() {
        return "XdagEvmInternalTransaction{" +
                "rejected=" + rejected +
                ", depth=" + depth +
                ", index=" + index +
                ", type=" + type +
                ", from=" + from.toHexString() +
                ", to=" + to.toHexString() +
                ", nonce=" + nonce +
                ", value=" + value +
                ", data=" + data.toHexString() +
                ", gas=" + gas +
                ", gasPrice=" + gasPrice +
                '}';
    }

}
