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
