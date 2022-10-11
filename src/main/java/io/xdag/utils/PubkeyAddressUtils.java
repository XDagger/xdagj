package io.xdag.utils;

import io.xdag.crypto.Base58;
import io.xdag.utils.exception.AddressFormatException;

public class PubkeyAddressUtils {

    public static String toBase58(byte[] hash160) {
        return Base58.encodeChecked(hash160);
    }

    public static byte[] formBase58(String base58) throws AddressFormatException {
        byte[] bytes = Base58.decodeChecked(base58);
        if (bytes.length != 20)
            throw new AddressFormatException.InvalidDataLength("Wrong number of bytes: " + bytes.length);
        return bytes;
    }

}
