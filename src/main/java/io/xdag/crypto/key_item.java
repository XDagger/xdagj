package io.xdag.crypto;

public class key_item {
    byte[] pubKey;
    /**0:even 1:odd*/
    boolean pubKeyParity;

    public key_item(byte[] pubKey,boolean pubKeyParity){
        this.pubKey = pubKey;
        this.pubKeyParity = pubKeyParity;
    }


    public boolean equals(key_item other) {
        if(other.pubKeyParity != this.pubKeyParity){
            return false;
        }else {
            if (this.pubKey == other.pubKey){
                return true;
            }
            return false;
        }
    }
}