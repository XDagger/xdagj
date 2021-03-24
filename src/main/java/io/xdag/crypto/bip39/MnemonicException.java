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
package io.xdag.crypto.bip39;

public class MnemonicException extends Exception {
    public MnemonicException() {
        super();
    }

    public MnemonicException(String msg) {
        super(msg);
    }

    /**
     * Thrown when an argument to MnemonicCode is the wrong length.
     */
    public static class MnemonicLengthException extends MnemonicException {
        public MnemonicLengthException(String msg) {
            super(msg);
        }
    }

    /**
     * Thrown when a list of MnemonicCode words fails the checksum check.
     */
    public static class MnemonicChecksumException extends MnemonicException {
        public MnemonicChecksumException() {
            super();
        }
    }

    /**
     * Thrown when a word is encountered which is not in the MnemonicCode's word list.
     */
    public static class MnemonicWordException extends MnemonicException {
        /** Contains the word that was not found in the word list. */
        public final String badWord;

        public MnemonicWordException(String badWord) {
            super();
            this.badWord = badWord;
        }
    }
}
