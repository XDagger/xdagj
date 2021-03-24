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

public class KeyCrypterException extends RuntimeException {

    public KeyCrypterException(String message) {
        super(message);
    }

    public KeyCrypterException(String message, Throwable throwable) {
        super(message, throwable);
    }

    /**
     * This exception is thrown when a private key or seed is decrypted, it doesn't match its public key any
     * more. This likely means the wrong decryption key has been used.
     */
    public static class PublicPrivateMismatch extends KeyCrypterException {
        public PublicPrivateMismatch(String message) {
            super(message);
        }

        public PublicPrivateMismatch(String message, Throwable throwable) {
            super(message, throwable);
        }
    }

    /**
     * This exception is thrown when a private key or seed is decrypted, the decrypted message is damaged
     * (e.g. the padding is damaged). This likely means the wrong decryption key has been used.
     */
    public static class InvalidCipherText extends KeyCrypterException {
        public InvalidCipherText(String message) {
            super(message);
        }

        public InvalidCipherText(String message, Throwable throwable) {
            super(message, throwable);
        }
    }

}
