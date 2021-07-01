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

import io.xdag.utils.HashUtils;
import io.xdag.utils.SimpleDecoder;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.crypto.Hash;
import org.apache.tuweni.crypto.SECP256K1;

public class XdagTransaction {

    private final byte id;

    private final TransactionType type;

    private final Bytes to;

    private final XAmount value;

    private final XAmount fee;

    private final long nonce;

    private final long timestamp;

    private final Bytes data;

    private final Bytes encoded;

    private final Bytes32 hash;

    private SECP256K1.Signature signature;

    private final long gas;
    private final XAmount gasPrice; // nanoSEM per gas

    /**
     * Create a new transaction.
     *
     * @param id
     * @param type
     * @param to
     * @param value
     * @param fee
     * @param nonce
     * @param timestamp
     * @param data
     * @param gas
     * @param gasPrice
     */
    public XdagTransaction(byte id, TransactionType type, Bytes to, XAmount value, XAmount fee, long nonce,
                           long timestamp, Bytes data, long gas, XAmount gasPrice) {
        this.id = id;
        this.type = type;
        this.to = to;
        this.value = value;
        this.fee = fee;
        this.nonce = nonce;
        this.timestamp = timestamp;
        this.data = data;
        this.gas = gas;
        this.gasPrice = gasPrice;

        SimpleEncoder enc = new SimpleEncoder();
        enc.writeByte(id);
        enc.writeByte(type.toByte());
        enc.writeBytes(to);
        enc.writeXAmount(value);
        enc.writeXAmount(fee);
        enc.writeLong(nonce);
        enc.writeLong(timestamp);
        enc.writeBytes(data);

        if (TransactionType.CALL == type || TransactionType.CREATE == type) {
            enc.writeLong(gas);
            enc.writeXAmount(gasPrice);
        }
        this.encoded = Bytes.wrap(enc.toBytes());
        this.hash = Hash.sha2_256(encoded);
    }

    /**
     * Create a signed transaction from raw bytes
     *
     * @param hash
     * @param encoded
     * @param signature
     */
    private XdagTransaction(Bytes32 hash, Bytes encoded, Bytes signature) {
        this.hash = hash;

        XdagTransaction decodedTx = fromEncoded(encoded);
        this.id = decodedTx.id;
        this.type = decodedTx.type;
        this.to = decodedTx.to;
        this.value = decodedTx.value;
        this.fee = decodedTx.fee;
        this.nonce = decodedTx.nonce;
        this.timestamp = decodedTx.timestamp;
        this.data = decodedTx.data;

        this.gas = decodedTx.gas;
        this.gasPrice = decodedTx.gasPrice;

        this.encoded = encoded;

        this.signature = SECP256K1.Signature.fromBytes(signature);
    }

    public XdagTransaction(byte id, TransactionType type, Bytes toAddress, XAmount value, XAmount fee, long nonce,
                       long timestamp, Bytes data) {
        this(id, type, toAddress, value, fee, nonce, timestamp, data, 0, XAmount.ZERO);
    }

    public boolean isVMTransaction() {
        return type == TransactionType.CREATE || type == TransactionType.CALL;
    }

    /**
     * Sign this transaction.
     *
     * @param key
     * @return
     */
    public XdagTransaction sign(SECP256K1.KeyPair key) {
        this.signature = SECP256K1.sign(this.hash, key);
        return this;
    }

    /**
     * <p>
     * Validate transaction format and signature. </>
     *
     * <p>
     * NOTE: this method does not check transaction validity over the state. Use
     * </p>
     *
     * @param config
     *            Whether to verify the transaction signature or not. This is useful
     *            when there are multiple transaction signatures that can be
     *            verified in batch for performance reason.
     * @return true if success, otherwise false
     */
//    public boolean validate(Config config, boolean verifySignature) {
//        return hash != null && hash.size() == Hash.HASH_LEN
//                && id == config.getId()
//                && type != null
//                && to != null && to.size() == Keys.ADDRESS_LEN
//                && (type != TransactionType.CREATE || Arrays.equals(to, EMPTY_ADDRESS))
//                && value.isNotNegative()
//                && fee.isNotNegative()
//                && nonce >= 0
//                && timestamp > 0
//                && data != null
//                && encoded != null
//                && signature != null && !Arrays.equals(signature.getAddress(), EMPTY_ADDRESS)
//
//                && Arrays.equals(Hash.sha256(encoded.toArray()), hash.toArray())
//                && (!verifySignature || Sign.verify(hash, signature))
//
//                // The coinbase key is publicly available. People can use it for transactions.
//                // It won't introduce any fundamental loss to the system but could potentially
//                // cause confusion for block explorer, and thus are prohibited.
//                && (type == TransactionType.COINBASE
//                || (!Arrays.equals(signature.getAddress(), Constants.COINBASE_ADDRESS)
//                && !Arrays.equals(to, Constants.COINBASE_ADDRESS)));
//    }

//    public boolean validate(Config config) {
//        return validate(config, true);
//    }

    /**
     * Returns the transaction Config id.
     *
     * @return
     */
    public byte getId() {
        return id;
    }

    /**
     * Returns the transaction hash.
     *
     * @return
     */
    public Bytes getHash() {
        return hash;
    }

    /**
     * Returns the transaction type.
     *
     * @return
     */
    public TransactionType getType() {
        return type;
    }

    /**
     * Parses the from address from signature.
     *
     * @return an address if the signature is valid, otherwise null
     */

    public Bytes getFrom() {
        //TODO
        return null;
//        return (signature == null) ? null : Keys.getAddress()signature.getAddress();
    }

    /**
     * Returns the recipient address.
     *
     * @return
     */
    public Bytes getTo() {
        return to;
    }

    /**
     * Returns the value.
     *
     * @return
     */
    public XAmount getValue() {
        return value;
    }

    /**
     * Returns the transaction fee.
     *
     * @return
     */
    public XAmount getFee() {
        return fee;
    }

    /**
     * Returns the nonce.
     *
     * @return
     */
    public long getNonce() {
        return nonce;
    }

    /**
     * Returns the timestamp.
     *
     * @return
     */
    public long getTimestamp() {
        return timestamp;
    }

    /**
     * Returns the extra data.
     *
     * @return
     */
    public Bytes getData() {
        return data;
    }

    public Bytes getEncoded() {
        return encoded;
    }

    public long getGas() {
        return gas;
    }

    public XAmount getGasPrice() {
        return gasPrice;
    }

    /**
     * Decodes an byte-encoded transaction that is not yet signed by a private key.
     *
     * @param encoded
     *            the bytes of encoded transaction
     * @return the decoded transaction
     */
    public static XdagTransaction fromEncoded(Bytes encoded) {
        SimpleDecoder decoder = new SimpleDecoder(encoded.toArray());

        byte id = decoder.readByte();
        byte type = decoder.readByte();
        Bytes to = Bytes.wrap(decoder.readBytes());
        XAmount value = decoder.readXAmount();
        XAmount fee = decoder.readXAmount();
        long nonce = decoder.readLong();
        long timestamp = decoder.readLong();
        Bytes data = Bytes.wrap(decoder.readBytes());

        long gas = 0;
        XAmount gasPrice = XAmount.ZERO;

        TransactionType transactionType = TransactionType.of(type);
        if (TransactionType.CALL == transactionType || TransactionType.CREATE == transactionType) {
            gas = decoder.readLong();
            gasPrice = decoder.readXAmount();
        }

        return new XdagTransaction(id, transactionType, to, value, fee, nonce, timestamp, data,
                gas, gasPrice);

    }

    /**
     * Returns the signature.
     *
     * @return
     */
    public SECP256K1.Signature getSignature() {
        return signature;
    }

    /**
     * Converts into a byte array.
     *
     * @return
     */
    public byte[] toBytes() {
        SimpleEncoder enc = new SimpleEncoder();
        enc.writeBytes(hash);
        enc.writeBytes(encoded);
        enc.writeBytes(signature.bytes().toArray());
        return enc.toBytes();
    }

    /**
     * Parses from a byte array.
     *
     * @param bytes
     * @return
     */
    public static XdagTransaction fromBytes(byte[] bytes) {
        SimpleDecoder dec = new SimpleDecoder(bytes);
        Bytes32 hash = Bytes32.wrap(dec.readBytes());
        Bytes encoded = Bytes.wrap(dec.readBytes());
        Bytes signature = Bytes.wrap(dec.readBytes());

        return new XdagTransaction(hash, encoded, signature);
    }

    /**
     * Returns size of the transaction in bytes
     *
     * @return size in bytes
     */
    public int size() {
        return toBytes().length;
    }

    @Override
    public String toString() {
        return "Transaction [type=" + type + ", from=" + getFrom().toHexString() + ", to=" + to.toHexString() + ", value="
                + value + ", fee=" + fee + ", nonce=" + nonce + ", timestamp=" + timestamp + ", data="
                + data.toHexString() + ", hash=" + hash.toHexString() + "]";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;

        if (o == null || getClass() != o.getClass())
            return false;

        XdagTransaction that = (XdagTransaction) o;

        return new EqualsBuilder()
                .append(encoded, that.encoded)
                .append(hash, that.hash)
                .append(signature, that.signature)
                .isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 37)
                .append(encoded)
                .append(hash)
                .append(signature)
                .toHashCode();
    }

}
