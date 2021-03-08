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
