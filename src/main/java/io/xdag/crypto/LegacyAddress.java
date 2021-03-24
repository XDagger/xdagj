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
package io.xdag.crypto;

import io.xdag.config.Config;
import io.xdag.crypto.bip38.AddressFormatException;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Objects;

public class LegacyAddress extends PrefixedChecksummedBytes {

    /**
     * An address is a RIPEMD160 hash of a public key, therefore is always 160 bits or 20 bytes.
     */
    public static final int LENGTH = 20;

    /** True if P2SH, false if P2PKH. */
//    public final boolean p2sh;

    /**
     * Private constructor. Use {@link #fromBase58(Config, String)},
     * {@link #fromPubKeyHash(Config, byte[])}, {@link #fromScriptHash(Config, byte[])} or
     * {@link #fromKey(Config, ECKey)}.
     *
     * @param config
     *            network this address is valid for
     * @param hash160
     *            20-byte hash of pubkey or script
     */
    private LegacyAddress(Config config, byte[] hash160) throws AddressFormatException {
        super(config, hash160);
        if (hash160.length != 20)
            throw new AddressFormatException.InvalidDataLength(
                    "Legacy addresses are 20 byte (160 bit) hashes, but got: " + hash160.length);
    }

    /**
     * Construct a {@link LegacyAddress} that represents the given pubkey hash. The resulting address will be a P2PKH type of
     * address.
     *
     * @param config
     *            network this address is valid for
     * @param hash160
     *            20-byte pubkey hash
     * @return constructed address
     */
    public static LegacyAddress fromPubKeyHash(Config config, byte[] hash160) throws AddressFormatException {
        return new LegacyAddress(config, hash160);
    }

    /**
     * Construct a {@link LegacyAddress} that represents the public part of the given {@link ECKey}. Note that an address is
     * derived from a hash of the public key and is not the public key itself.
     *
     * @param config
     *            network this address is valid for
     * @param key
     *            only the public part is used
     * @return constructed address
     */
    public static LegacyAddress fromKey(Config config, ECKey key) {
        return fromPubKeyHash(config, key.getPubKeyHash());
    }

    /**
     * Construct a {@link LegacyAddress} that represents the given P2SH script hash.
     *
     * @param config
     *            network this address is valid for
     * @param hash160
     *            P2SH script hash
     * @return constructed address
     */
    public static LegacyAddress fromScriptHash(Config config, byte[] hash160) throws AddressFormatException {
        return new LegacyAddress(config, hash160);
    }

    /**
     * Construct a {@link LegacyAddress} from its base58 form.
     *
     * @param config
     *            expected network this address is valid for, or null if if the network should be derived from the
     *            base58
     * @param base58
     *            base58-encoded textual form of the address
     * @throws AddressFormatException
     *             if the given base58 doesn't parse or the checksum is invalid
     * @throws AddressFormatException.WrongNetwork
     *             if the given address is valid but for a different chain (eg testnet vs mainnet)
     */
    public static LegacyAddress fromBase58(@Nullable Config config, String base58)
            throws AddressFormatException, AddressFormatException.WrongNetwork {
        byte[] versionAndDataBytes = Base58.decodeChecked(base58);
        int version = versionAndDataBytes[0] & 0xFF;
        byte[] bytes = Arrays.copyOfRange(versionAndDataBytes, 1, versionAndDataBytes.length);
        if (config == null) {
            throw new AddressFormatException.InvalidPrefix("No network found for " + base58);
        } else {
            return new LegacyAddress(config, bytes);
        }
    }

    public byte[] getHash() {
        return bytes;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        LegacyAddress other = (LegacyAddress) o;
        return super.equals(other);
    }

    /**
     * Returns the base58-encoded textual form, including version and checksum bytes.
     *
     * @return textual form
     */
    public String toBase58() {
        return Base58.encodeChecked(config.addressHeader, bytes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode());
    }

    @Override
    public String toString() {
        return toBase58();
    }

    @Override
    public LegacyAddress clone() throws CloneNotSupportedException {
        return (LegacyAddress) super.clone();
    }

}
