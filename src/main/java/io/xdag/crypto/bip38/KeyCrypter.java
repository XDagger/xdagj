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

import org.bouncycastle.crypto.params.KeyParameter;

public interface KeyCrypter {
    /**
     * Return the EncryptionType enum value which denotes the type of encryption/ decryption that this KeyCrypter
     * can understand.
     */
    EncryptionType getUnderstoodEncryptionType();

    /**
     * Create a KeyParameter (which typically contains an AES key)
     * @param password
     * @return KeyParameter The KeyParameter which typically contains the AES key to use for encrypting and decrypting
     * @throws KeyCrypterException
     */
    KeyParameter deriveKey(CharSequence password) throws KeyCrypterException;

    /**
     * Decrypt the provided encrypted bytes, converting them into unencrypted bytes.
     *
     * @throws KeyCrypterException if decryption was unsuccessful.
     */
    byte[] decrypt(EncryptedData encryptedBytesToDecode, KeyParameter aesKey) throws KeyCrypterException;

    /**
     * Encrypt the supplied bytes, converting them into ciphertext.
     *
     * @return encryptedPrivateKey An encryptedPrivateKey containing the encrypted bytes and an initialisation vector.
     * @throws KeyCrypterException if encryption was unsuccessful
     */
    EncryptedData encrypt(byte[] plainBytes, KeyParameter aesKey) throws KeyCrypterException;
}
