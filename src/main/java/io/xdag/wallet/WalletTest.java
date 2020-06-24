package io.xdag.wallet;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import org.spongycastle.util.encoders.Hex;

import io.xdag.config.Config;
import io.xdag.crypto.jni.Native;

/**
 * @Classname WalletTest
 * @Description TODO
 * @Date 2020/6/16 22:41
 * @Created by Myron
 */
public class WalletTest {

    public static void main(String[] args) throws Exception {
        Config config = new Config();
        Native.init();
        if(Native.dnet_crypt_init() < 0){
            throw new Exception("dnet crypt init failed");
        }

        WalletImpl wallet = new WalletImpl();
        wallet.init(config);



        //WalletTest.readDat("D:\\Program\\xdagj\\dnet_key.dat");
    }


    public static void readDat(String path) throws IOException {
        File file = new File(path);

        FileInputStream inputStream = new FileInputStream(file);

        byte[] buffer = new byte[2048];

        try {
            while (true) {
                int len = inputStream.read(buffer);
                if (len == -1) {
                    break;
                }
                System.out.println(Hex.toHexString(buffer));
            }
        }finally {
            inputStream.close();
        }
    }
}
