package io.xdag.utils;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.MutableBytes32;

/**
 * @author Dcj_Cory
 * @date 2022/11/7 4:47 PM
 */
public class ByteArrayToByte32 {
    public static MutableBytes32 arrayToByte32(byte[] value){
        MutableBytes32 mutableBytes32 = MutableBytes32.wrap(new byte[32]);
        mutableBytes32.set(8, Bytes.wrap(value));
        return mutableBytes32;
    }
    public static byte[] byte32ToArray(MutableBytes32 value){
        return value.mutableCopy().slice(8,20).toArray();
    }

}
