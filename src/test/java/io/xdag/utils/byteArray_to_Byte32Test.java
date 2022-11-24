package io.xdag.utils;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.MutableBytes32;
import org.junit.Test;

/**
 * @author Dcj_Cory
 * @date 2022/11/7 4:21 PM
 */
public class byteArray_to_Byte32Test {
    @Test
    public void testToByte32(){
        byte[] addressbyte = PubkeyAddressUtils.fromBase58("7pWm5FZaNVV61wb4vQapqVixPaLC7Dh2C");
        MutableBytes32 address= ByteArrayToByte32.arrayToByte32(addressbyte);
        String res = PubkeyAddressUtils.toBase58(ByteArrayToByte32.byte32ToArray(address));
        if(res.equals("7pWm5FZaNVV61wb4vQapqVixPaLC7Dh2C")){
            System.out.println("Test pass");
        }else {
            System.out.println("Test done");
        }
    }
}
