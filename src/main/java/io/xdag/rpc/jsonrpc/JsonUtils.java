package io.xdag.rpc.jsonrpc;

import org.bouncycastle.util.encoders.Hex;

import java.math.BigInteger;

import static io.xdag.utils.BasicUtils.unifiedNumericToBigInteger;
import static io.xdag.utils.BytesUtils.EMPTY_BYTE_ARRAY;
import static io.xdag.utils.BytesUtils.stripLeadingZeroes;

public class JsonUtils {
    public static byte[] parseVarData(String data){
        if (data == null || data.equals("")) {
            return EMPTY_BYTE_ARRAY;
        }
        if (data.startsWith("0x")) {
            data = data.substring(2);
            if (data.equals("")) {
                return EMPTY_BYTE_ARRAY;
            }

            if (data.length() % 2 == 1) {
                data = "0" + data;
            }

            return Hex.decode(data);
        }

        return parseNumericData(data);
    }


    public static byte[] parseData(String data) {
        if (data == null) {
            return EMPTY_BYTE_ARRAY;
        }
        if (data.startsWith("0x")) {
            data = data.substring(2);
        }
        return Hex.decode(data);
    }

    public static byte[] parseNumericData(String data){

        if (data == null || data.equals("")) {
            return EMPTY_BYTE_ARRAY;
        }
        byte[] dataB = unifiedNumericToBigInteger(data).toByteArray();
        return stripLeadingZeroes(dataB);
    }

    public static long parseLong(String data) {
        boolean hex = data.startsWith("0x");
        if (hex) {
            data = data.substring(2);
        }
        if (data.equals("")) {
            return 0;
        }
        return new BigInteger(data, hex ? 16 : 10).longValue();
    }

    public static byte parseByte(String data) {
        if (data.startsWith("0x")) {
            data = data.substring(2);
            return data.equals("") ? 0 : Byte.parseByte(data, 16);
        } else {
            return data.equals("") ? 0 : Byte.parseByte(data);
        }
    }


    public static String parseUnidentifiedBase(String number) {
        if (number.startsWith("0x")) {
            number = new BigInteger(number.substring(2), 16).toString(10);
        }
        return number;
    }
}

