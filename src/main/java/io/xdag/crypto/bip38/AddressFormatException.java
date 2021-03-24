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
package io.xdag.crypto.bip38;

import io.xdag.crypto.Base58;
import io.xdag.crypto.PrefixedChecksummedBytes;

public class AddressFormatException extends IllegalArgumentException {
    public AddressFormatException() {
        super();
    }

    public AddressFormatException(String message) {
        super(message);
    }

    /**
     * This exception is thrown by {@link Base58} and the {@link PrefixedChecksummedBytes} hierarchy of
     * classes when you try to decode data and a character isn't valid. You shouldn't allow the user to proceed in this
     * case.
     */
    public static class InvalidCharacter extends AddressFormatException {
        public final char character;
        public final int position;

        public InvalidCharacter(char character, int position) {
            super("Invalid character '" + Character.toString(character) + "' at position " + position);
            this.character = character;
            this.position = position;
        }
    }

    /**
     * This exception is thrown by {@link Base58} and the {@link PrefixedChecksummedBytes} hierarchy of
     * classes when you try to decode data and the data isn't of the right size. You shouldn't allow the user to proceed
     * in this case.
     */
    public static class InvalidDataLength extends AddressFormatException {
        public InvalidDataLength() {
            super();
        }

        public InvalidDataLength(String message) {
            super(message);
        }
    }

    /**
     * This exception is thrown by {@link Base58} and the {@link PrefixedChecksummedBytes} hierarchy of
     * classes when you try to decode data and the checksum isn't valid. You shouldn't allow the user to proceed in this
     * case.
     */
    public static class InvalidChecksum extends AddressFormatException {
        public InvalidChecksum() {
            super("Checksum does not validate");
        }

        public InvalidChecksum(String message) {
            super(message);
        }
    }

    /**
     * This exception is thrown by the {@link PrefixedChecksummedBytes} hierarchy of classes when you try and decode an
     * address or private key with an invalid prefix (version header or human-readable part). You shouldn't allow the
     * user to proceed in this case.
     */
    public static class InvalidPrefix extends AddressFormatException {
        public InvalidPrefix() {
            super();
        }

        public InvalidPrefix(String message) {
            super(message);
        }
    }

    /**
     * This exception is thrown by the {@link PrefixedChecksummedBytes} hierarchy of classes when you try and decode an
     * address with a prefix (version header or human-readable part) that used by another network (usually: mainnet vs
     * testnet). You shouldn't allow the user to proceed in this case as they are trying to send money across different
     * chains, an operation that is guaranteed to destroy the money.
     */
    public static class WrongNetwork extends InvalidPrefix {
        public WrongNetwork(int versionHeader) {
            super("Version code of address did not match acceptable versions for network: " + versionHeader);
        }

        public WrongNetwork(String hrp) {
            super("Human readable part of address did not match acceptable HRPs for network: " + hrp);
        }
    }
}

