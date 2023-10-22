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

import java.math.BigInteger;
import java.util.Arrays;

import org.apache.tuweni.bytes.Bytes;

import io.xdag.config.Constants;
import io.xdag.crypto.Hash;
import io.xdag.utils.BytesUtils;
import io.xdag.utils.SimpleDecoder;
import io.xdag.utils.SimpleEncoder;
import io.xdag.utils.WalletUtils;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BlockHeader {
    private final byte[] hash;
    private final long number;
    private final byte[] coinbase;
    private final byte[] parentHash;
    private final long timestamp;
    private final byte[] transactionsRoot;
    private final byte[] resultsRoot;
    private final long difficultyTarget;
    private long nonce;
    private final byte[] data;
    private final byte[] encoded;

    public BlockHeader(long number, byte[] coinbase, byte[] prevHash, long timestamp, byte[] transactionsRoot,
            byte[] resultsRoot, long nonce, byte[] data) {
        this.number = number;
        this.coinbase = coinbase;
        this.parentHash = prevHash;
        this.timestamp = timestamp;
        this.transactionsRoot = transactionsRoot;
        this.resultsRoot = resultsRoot;
        this.difficultyTarget = Constants.EASIEST_DIFFICULTY_TARGET;
        this.nonce = nonce;
        this.data = data;

        SimpleEncoder enc = new SimpleEncoder();
        enc.writeLong(number);
        enc.writeBytes(coinbase);
        enc.writeBytes(prevHash);
        enc.writeLong(timestamp);
        enc.writeBytes(transactionsRoot);
        enc.writeBytes(resultsRoot);

        // difficultyTarget
        byte[] difficultyTargetBytes = new byte[4];
        BytesUtils.writeInt32LE(difficultyTarget, difficultyTargetBytes, 0);
        enc.writeBytes(difficultyTargetBytes);

        // nonce
        byte[] nonceBytes = new byte[4];
        BytesUtils.writeInt32LE(nonce, nonceBytes, 0);
        enc.writeBytes(nonceBytes);

        enc.writeBytes(data);
        this.encoded = enc.toBytes();
        this.hash = Hash.h256(encoded);
    }

    /**
     * Creates an instance of block header.
     */
    public BlockHeader(long number, byte[] coinbase, byte[] prevHash, long timestamp, byte[] transactionsRoot,
            byte[] resultsRoot, long difficultyTarget, long nonce, byte[] data) {
        this.number = number;
        this.coinbase = coinbase;
        this.parentHash = prevHash;
        this.timestamp = timestamp;
        this.transactionsRoot = transactionsRoot;
        this.resultsRoot = resultsRoot;
        this.difficultyTarget = difficultyTarget;
        this.nonce = nonce;
        this.data = data;

        SimpleEncoder enc = new SimpleEncoder();
        enc.writeLong(number);
        enc.writeBytes(coinbase);
        enc.writeBytes(prevHash);
        enc.writeLong(timestamp);
        enc.writeBytes(transactionsRoot);
        enc.writeBytes(resultsRoot);

        // difficultyTarget
        byte[] difficultyTargetBytes = new byte[4];
        BytesUtils.writeInt32LE(difficultyTarget, difficultyTargetBytes, 0);
        enc.writeBytes(difficultyTargetBytes);

        // nonce
        byte[] nonceBytes = new byte[4];
        BytesUtils.writeInt32LE(nonce, nonceBytes, 0);
        enc.writeBytes(nonceBytes);

        enc.writeBytes(data);
        this.encoded = enc.toBytes();
        this.hash = Hash.h256(encoded);
    }

    /**
     * Parses block header from byte arrays.
     */
    public BlockHeader(byte[] hash, byte[] encoded) {
        this.hash = hash;

        SimpleDecoder dec = new SimpleDecoder(encoded);
        this.number = dec.readLong();
        this.coinbase = dec.readBytes();
        this.parentHash = dec.readBytes();
        this.timestamp = dec.readLong();
        this.transactionsRoot = dec.readBytes();
        this.resultsRoot = dec.readBytes();

        // difficultyTarget
        byte[] difficultyTargetBytes = dec.readBytes();
        this.difficultyTarget = BytesUtils.readUint32(difficultyTargetBytes, 0 );

        // nonce
        byte[] nonceBytes = dec.readBytes();
        this.nonce = BytesUtils.readUint32(nonceBytes, 0 );

        this.data = dec.readBytes();

        this.encoded = encoded;
    }

    /**
     * Validates block header format.
     *
     * @return true if success, otherwise false
     */
    public boolean validate() {
        return hash != null && hash.length == BytesUtils.EMPTY_HASH.length
                && number >= 0
                && coinbase != null && coinbase.length == BytesUtils.EMPTY_ADDRESS.length
                && parentHash != null && parentHash.length == BytesUtils.EMPTY_HASH.length
                && timestamp >= 0
                && transactionsRoot != null && transactionsRoot.length == BytesUtils.EMPTY_HASH.length
                && resultsRoot != null && resultsRoot.length == BytesUtils.EMPTY_HASH.length
                //&& difficultyTarget != null && difficultyTarget.length == BytesUtils.EMPTY_HASH.length
                //&& nonce != null && nonce.length == BytesUtils.EMPTY_4BYTES.length
                && data != null && data.length <= BlockHeaderData.MAX_SIZE
                && encoded != null
                && Arrays.equals(Hash.h256(encoded), hash);
    }

    public boolean checkProofOfWork() {
        BigInteger target = getDifficultyTargetAsInteger();
        BigInteger h = new BigInteger(1, hash);
        return h.compareTo(target) <= 0;
    }

    public BigInteger getDifficultyTargetAsInteger() {
        return BytesUtils.decodeCompactBits(difficultyTarget);
    }

    public BlockHeaderData getDecodedData() {
        return new BlockHeaderData(data);
    }

    public byte[] toBytes() {
        SimpleEncoder enc = new SimpleEncoder();
        enc.writeBytes(hash);
        enc.writeBytes(encoded);
        return enc.toBytes();
    }

    public static BlockHeader fromBytes(byte[] bytes) {
        SimpleDecoder dec = new SimpleDecoder(bytes);
        byte[] hash = dec.readBytes();
        byte[] encoded = dec.readBytes();

        return new BlockHeader(hash, encoded);
    }

    @Override
    public String toString() {
        StringBuilder s = new StringBuilder();
        s.append(" BlockHeader: \n");
        s.append("   hash: ").append(Bytes.wrap(hash).toHexString()).append('\n');
        s.append("   number: ").append(number).append('\n');
        s.append("   coinbase: ").append(WalletUtils.toBase58(coinbase)).append('\n');
        s.append("   previous block: ").append(Bytes.wrap(parentHash).toHexString()).append("\n");
        s.append("   timestamp: ").append(timestamp).append("\n");
        s.append("   transactions root: ").append(Bytes.wrap(transactionsRoot).toHexString()).append('\n');
        s.append("   result root: ").append(Bytes.wrap(resultsRoot).toHexString()).append('\n');
        s.append("   difficulty target (nBits): ").append(difficultyTarget).append("\n");
        s.append("   nonce: ").append(nonce).append("\n");
        return s.toString();
    }
}
