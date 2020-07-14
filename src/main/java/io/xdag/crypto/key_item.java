package io.xdag.crypto;

import java.util.Arrays;

public class key_item {
    byte[] pubKey;
    /** 0:even 1:odd */
    boolean pubKeyParity;

    public key_item(byte[] pubKey, boolean pubKeyParity) {
        this.pubKey = pubKey;
        this.pubKeyParity = pubKeyParity;
    }

    @Override
    public boolean equals(Object obj) {
        key_item other = null;
        if(obj != null && obj instanceof key_item) {
            other = (key_item) obj;
        } else {
            return false;
        }
        if (other.pubKeyParity != this.pubKeyParity) {
            return false;
        } else {
            if (Arrays.equals(this.pubKey,other.pubKey)) {
                return true;
            }
            return false;
        }
    }
    
}
