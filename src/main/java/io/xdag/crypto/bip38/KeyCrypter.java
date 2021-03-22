package io.xdag.crypto.bip38;

import org.spongycastle.crypto.params.KeyParameter;

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
