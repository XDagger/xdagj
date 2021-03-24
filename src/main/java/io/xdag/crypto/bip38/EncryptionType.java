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

public enum EncryptionType {

    /**
     * <pre>
     * All keys in the wallet are unencrypted
     * </pre>
     *
     * <code>UNENCRYPTED = 1;</code>
     */
    UNENCRYPTED(1),
    /**
     * <pre>
     * All keys are encrypted with a passphrase based KDF of scrypt and AES encryption
     * </pre>
     *
     * <code>ENCRYPTED_SCRYPT_AES = 2;</code>
     */
    ENCRYPTED_SCRYPT_AES(2),
    ;

    /**
     * <pre>
     * All keys in the wallet are unencrypted
     * </pre>
     *
     * <code>UNENCRYPTED = 1;</code>
     */
    public static final int UNENCRYPTED_VALUE = 1;
    /**
     * <pre>
     * All keys are encrypted with a passphrase based KDF of scrypt and AES encryption
     * </pre>
     *
     * <code>ENCRYPTED_SCRYPT_AES = 2;</code>
     */
    public static final int ENCRYPTED_SCRYPT_AES_VALUE = 2;


    public final int getNumber() {
        return value;
    }

    /**
     * @param value The numeric wire value of the corresponding enum entry.
     * @return The enum associated with the given numeric wire value.
     * @deprecated Use {@link #forNumber(int)} instead.
     */
    @java.lang.Deprecated
    public static EncryptionType valueOf(int value) {
        return forNumber(value);
    }

    /**
     * @param value The numeric wire value of the corresponding enum entry.
     * @return The enum associated with the given numeric wire value.
     */
    public static EncryptionType forNumber(int value) {
        switch (value) {
            case 1: return UNENCRYPTED;
            case 2: return ENCRYPTED_SCRYPT_AES;
            default: return null;
        }
    }

//    public static EnumMap<EncryptionType> internalGetValueMap() {
//        return internalValueMap;
//    }
//    private static final EnumMap<EncryptionType> internalValueMap =
//            new EnumMap<EncryptionType>() {
//                public EncryptionType findValueByNumber(int number) {
//                    return EncryptionType.forNumber(number);
//                }
//            };




    private static final EncryptionType[] VALUES = values();

    private final int value;

    private EncryptionType(int value) {
        this.value = value;
    }

}
