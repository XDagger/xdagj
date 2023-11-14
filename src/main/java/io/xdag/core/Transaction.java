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

import static io.xdag.utils.BytesUtils.EMPTY_ADDRESS;

import java.util.Arrays;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.crypto.KeyPair;
import org.hyperledger.besu.crypto.SECPSignature;

import io.xdag.Network;
import io.xdag.config.Constants;
import io.xdag.crypto.Hash;
import io.xdag.crypto.Sign;
import io.xdag.utils.SimpleDecoder;
import io.xdag.utils.SimpleEncoder;
import io.xdag.utils.WalletUtils;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Transaction {

    /**
     *  Returns the transaction network id.
     */
    private final byte networkId;

    /**
     *  Returns the transaction type.
     */
    private final TransactionType type;

    /**
     *  Returns the recipient address.
     */
    private final byte[] to;

    /**
     *  Returns the value.
     */
    private final XAmount value;

    /**
     *  Returns the transaction fee.
     */
    private final XAmount fee;

    /**
     *  Returns the nonce.
     */
    private final long nonce;

    /**
     *  Returns the timestamp.
     */
    private final long timestamp;

    /**
     *  Returns the extra data.
     */
    private final byte[] data;

    private final byte[] encoded;

    /**
     *  Returns the transaction hash.
     */
    private final byte[] hash;

    /**
     *  Returns the signature.
     */
    private SECPSignature signature;

    /**
     * Create a new TransactionBlock.
     */
    public Transaction(Network network, TransactionType type, byte[] to, XAmount value, XAmount fee, long nonce,
            long timestamp, byte[] data) {
        this.networkId = network.id();
        this.type = type;
        this.to = to;
        this.value = value;
        this.fee = fee;
        this.nonce = nonce;
        this.timestamp = timestamp;
        this.data = data;

        SimpleEncoder enc = new SimpleEncoder();
        enc.writeByte(networkId);
        enc.writeByte(type.toByte());
        enc.writeBytes(to);
        enc.writeXAmount(value);
        enc.writeXAmount(fee);
        enc.writeLong(nonce);
        enc.writeLong(timestamp);
        enc.writeBytes(data);

        this.encoded = enc.toBytes();
        this.hash = Hash.h256(encoded);
    }

    /**
     * Create a signed TransactionBlock from raw bytes
     */
    private Transaction(byte[] hash, byte[] encoded, byte[] signature) {
        this.hash = hash;

        Transaction decodedTx = fromEncoded(encoded);
        this.networkId = decodedTx.networkId;
        this.type = decodedTx.type;
        this.to = decodedTx.to;
        this.value = decodedTx.value;
        this.fee = decodedTx.fee;
        this.nonce = decodedTx.nonce;
        this.timestamp = decodedTx.timestamp;
        this.data = decodedTx.data;

        this.encoded = encoded;

        this.signature = Sign.decode(signature);
    }

    /**
     * Sign this transaction.
     */
    public Transaction sign(KeyPair key) {
        this.signature = Sign.sign(Bytes32.wrap(this.hash), key);
        return this;
    }

    /**
     * <p>
     * Validate transaction format and signature. </>
     *
     * <p>
     * NOTE: this method does not check transaction validity over the state. Use
     * {@link TransactionExecutor} for that purpose
     * </p>
     *
     * @param network
     *            mainnet/testnet/devnet
     * @param verifySignature
     *            Whether to verify the transaction signature or not. This is useful
     *            when there are multiple transaction signatures that can be
     *            verified in batch for performance reason.
     * @return true if success, otherwise false
     */
    public boolean validate(Network network, boolean verifySignature) {
        return hash != null && hash.length == 32
                && networkId == network.id()
                && type != null
                && to != null && to.length == 20
                && value.isNotNegative()
                && fee.isNotNegative()
                && nonce >= 0
                && timestamp > 0
                && data != null
                && encoded != null
                && signature != null && !Arrays.equals(Sign.toAddress(hash, signature), EMPTY_ADDRESS)

                && Arrays.equals(Hash.h256(encoded), hash)
                && (!verifySignature || Sign.verify(Bytes32.wrap(hash), signature))
                && (type == TransactionType.COINBASE
                        || (!Arrays.equals(Sign.toAddress(hash, signature), Constants.COINBASE_ADDRESS)
                                && !Arrays.equals(to, Constants.COINBASE_ADDRESS)));
    }

    public boolean validate(Network network) {
        return validate(network, true);
    }

    /**
     * Parses the from address from signature.
     *
     * @return an address if the signature is valid, otherwise null
     */
    public byte[] getFrom() {
        return (signature == null) ? null : Sign.toAddress(this.hash, signature);
    }

    /**
     * Decodes a byte-encoded TransactionBlock that is not yet signed by a private key.
     *
     * @param encoded
     *            the bytes of encoded transaction
     * @return the decoded transaction
     */
    public static Transaction fromEncoded(byte[] encoded) {
        SimpleDecoder decoder = new SimpleDecoder(encoded);

        byte networkId = decoder.readByte();
        byte type = decoder.readByte();
        byte[] to = decoder.readBytes();
        XAmount value = decoder.readXAmount();
        XAmount fee = decoder.readXAmount();
        long nonce = decoder.readLong();
        long timestamp = decoder.readLong();
        byte[] data = decoder.readBytes();

        TransactionType transactionType = TransactionType.of(type);

        return new Transaction(Network.of(networkId), transactionType, to, value, fee, nonce, timestamp, data);
    }

    /**
     * Converts into a byte array.
     */
    public byte[] toBytes() {
        SimpleEncoder enc = new SimpleEncoder();
        enc.writeBytes(hash);
        enc.writeBytes(encoded);
        enc.writeBytes(Sign.encode(signature));

        return enc.toBytes();
    }

    /**
     * Parses from a byte array.
     */
    public static Transaction fromBytes(byte[] bytes) {
        SimpleDecoder dec = new SimpleDecoder(bytes);
        byte[] hash = dec.readBytes();
        byte[] encoded = dec.readBytes();
        byte[] signature = dec.readBytes();

        return new Transaction(hash, encoded, signature);
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
        return String.format("Transaction [type=%s, hash=%s, from=%s, to=%s, value=%s, fee=%s, nonce=%s, timestamp=%s, data=%s]",
                type,
                Bytes.wrap(getHash()).toHexString(),
                WalletUtils.toBase58(getFrom()),
                WalletUtils.toBase58(to),
                value.toDecimal(2, XUnit.XDAG).toPlainString(),
                fee.toDecimal(2, XUnit.XDAG).toPlainString(),
                nonce,
                timestamp,
                Bytes.wrap(data).toHexString());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;

        if (o == null || getClass() != o.getClass())
            return false;

        Transaction that = (Transaction) o;

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
