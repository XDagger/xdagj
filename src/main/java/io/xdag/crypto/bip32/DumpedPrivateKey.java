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
package io.xdag.crypto.bip32;

import com.google.common.base.Preconditions;
import io.xdag.config.Config;
import io.xdag.crypto.Base58;
import io.xdag.crypto.ECKey;
import io.xdag.crypto.PrefixedChecksummedBytes;
import io.xdag.crypto.bip38.AddressFormatException;

import javax.annotation.Nullable;
import java.util.Arrays;

/**
 * Parses and generates private keys in the form used by the Bitcoin "dumpprivkey" command. This is the private key
 * bytes with a header byte and 4 checksum bytes at the end. If there are 33 private key bytes instead of 32, then
 * the last byte is a discriminator value for the compressed pubkey.
 */
public class DumpedPrivateKey extends PrefixedChecksummedBytes {

    /**
     * Construct a private key from its Base58 representation.
     * @param config
     *            The expected NetworkParameters or null if you don't want validation.
     * @param base58
     *            The textual form of the private key.
     * @throws AddressFormatException
     *             if the given base58 doesn't parse or the checksum is invalid
     * @throws AddressFormatException.WrongNetwork
     *             if the given private key is valid but for a different chain (eg testnet vs mainnet)
     */
    public static DumpedPrivateKey fromBase58(@Nullable Config config, String base58)
            throws AddressFormatException, AddressFormatException.WrongNetwork {
        byte[] versionAndDataBytes = Base58.decodeChecked(base58);
        int version = versionAndDataBytes[0] & 0xFF;
        byte[] bytes = Arrays.copyOfRange(versionAndDataBytes, 1, versionAndDataBytes.length);
        if (version != config.dumpedPrivateKeyHeader) {
            throw new AddressFormatException.WrongNetwork(version);
        }
        return new DumpedPrivateKey(config, bytes);
    }

    private DumpedPrivateKey(Config config, byte[] bytes) {
        super(config, bytes);
        if (bytes.length != 32 && bytes.length != 33)
            throw new AddressFormatException.InvalidDataLength(
                    "Wrong number of bytes for a private key (32 or 33): " + bytes.length);
    }

    // Used by ECKey.getPrivateKeyEncoded()
    public DumpedPrivateKey(Config config, byte[] keyBytes, boolean compressed) {
        this(config, encode(keyBytes, compressed));
    }

    /**
     * Returns the base58-encoded textual form, including version and checksum bytes.
     *
     * @return textual form
     */
    public String toBase58() {
        return Base58.encodeChecked(config.dumpedPrivateKeyHeader, bytes);
    }

    private static byte[] encode(byte[] keyBytes, boolean compressed) {
        Preconditions.checkArgument(keyBytes.length == 32, "Private keys must be 32 bytes");
        if (!compressed) {
            return keyBytes;
        } else {
            // Keys that have compressed public components have an extra 1 byte on the end in dumped form.
            byte[] bytes = new byte[33];
            System.arraycopy(keyBytes, 0, bytes, 0, 32);
            bytes[32] = 1;
            return bytes;
        }
    }

    /**
     * Returns an ECKey created from this encoded private key.
     */
    public ECKey getKey() {
        return ECKey.fromPrivate(Arrays.copyOf(bytes, 32), isPubKeyCompressed());
    }

    /**
     * Returns true if the public key corresponding to this private key is compressed.
     */
    public boolean isPubKeyCompressed() {
        return bytes.length == 33 && bytes[32] == 1;
    }

    @Override
    public String toString() {
        return toBase58();
    }
}

