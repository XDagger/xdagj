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
package io.xdag.crypto.jce;

import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.Provider;

public final class ECKeyFactory {

    public static final String ALGORITHM = "EC";

    private static final String algorithmAssertionMsg = "Assumed the JRE supports EC key factories";

    private ECKeyFactory() {
    }

    public static KeyFactory getInstance() {
        return Holder.INSTANCE;
    }

    public static KeyFactory getInstance(final String provider) throws NoSuchProviderException {
        try {
            return KeyFactory.getInstance(ALGORITHM, provider);
        } catch (NoSuchAlgorithmException ex) {
            throw new AssertionError(algorithmAssertionMsg, ex);
        }
    }

    public static KeyFactory getInstance(final Provider provider) {
        try {
            return KeyFactory.getInstance(ALGORITHM, provider);
        } catch (NoSuchAlgorithmException ex) {
            throw new AssertionError(algorithmAssertionMsg, ex);
        }
    }

    private static class Holder {
        private static final KeyFactory INSTANCE;

        static {
            try {
                INSTANCE = KeyFactory.getInstance(ALGORITHM);
            } catch (NoSuchAlgorithmException ex) {
                throw new AssertionError(algorithmAssertionMsg, ex);
            }
        }
    }
}
