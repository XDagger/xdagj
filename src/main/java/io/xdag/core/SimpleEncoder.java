package io.xdag.core;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * @ClassName SimpleEncoder
 * @Description encode区块
 * @Author punk
 * @Date 2020/4/12 16:29
 * @Version V1.0
 **/

public class SimpleEncoder {
    private final ByteArrayOutputStream out;


    public SimpleEncoder() {
        this.out = new ByteArrayOutputStream(512);
    }

    public void writeField(byte[] field){
        try {
            out.write(field);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void writeSignature(byte[] sig){
        try {
            out.write(sig);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void write(byte[] input){
        try {
            out.write(input);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public byte[] toBytes() {
        return out.toByteArray();
    }

    private int getWriteIndex() {
        return out.size();
    }

    public int getWriteFieldIndex(){
        return getWriteIndex()/32;
    }

}
