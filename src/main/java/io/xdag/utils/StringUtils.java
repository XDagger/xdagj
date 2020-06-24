package io.xdag.utils;

import java.math.BigDecimal;

import org.spongycastle.util.encoders.Hex;

/**
 * @Classname StringUtils
 * @Description TODO
 * @Date 2020/6/18 17:10
 * @Created by Myron
 */
public class StringUtils {

    public static double getDouble(String value){
        double num = Double.parseDouble(value);
        BigDecimal bigDecimal = new BigDecimal(num);
        return bigDecimal.setScale(2, BigDecimal.ROUND_HALF_UP).doubleValue();
    }


    public static byte[] getHash(String address){
        byte[] hash = null;
        if(address!=null){
            hash = Hex.decode(address);
        }
        return hash;
    }

    public static int getNum(String value){
        int num = 20;
        if(value != null){
            num = Integer.parseInt(value);
        }
        return num;
    }
}
