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

import org.apache.tuweni.bytes.Bytes;
import org.junit.Test;
import static org.junit.Assert.assertArrayEquals;

import java.nio.charset.StandardCharsets;

import io.xdag.crypto.Aes;

public class AesTest {

    private static Bytes raw = Bytes.wrap("test".getBytes(StandardCharsets.UTF_8));
    private static Bytes key = Bytes.fromHexString("1122334455667788112233445566778811223344556677881122334455667788");
    private static Bytes iv = Bytes.fromHexString("11223344556677881122334455667788");
    private static Bytes encrypted = Bytes.fromHexString("182b93aa58d6291381660e5bad673dd4");

    @Test
    public void testEncrypt() {
        byte[] bytes = Aes.encrypt(raw.toArray(), key.toArray(), iv.toArray());
        assertArrayEquals(encrypted.toArray(), bytes);
    }

    @Test
    public void testDecrypt() {
        byte[] bytes = Aes.decrypt(encrypted.toArray(), key.toArray(), iv.toArray());
        assertArrayEquals(raw.toArray(), bytes);
    }

}
