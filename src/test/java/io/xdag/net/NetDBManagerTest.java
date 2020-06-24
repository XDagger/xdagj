package io.xdag.net;

import org.junit.Before;

import io.xdag.config.Config;
import io.xdag.crypto.jni.Native;

/**
 * @ClassName NetDBManagerTest
 * @Description
 * @Author punk
 * @Date 2020/5/16 01:28
 * @Version V1.0
 **/
public class NetDBManagerTest {
    Config config = new Config();

    @Before
    public void setUp() throws Exception {
        config.setStoreMaxThreads(1);
        config.setStoreMaxOpenFiles(1024);
        config.setStoreDir("/Users/punk/testRocksdb/XdagDB");
        config.setStoreBackupDir("/Users/punk/testRocksdb/XdagDB/backupdata");

        Native.init();
        if(Native.dnet_crypt_init() < 0){
            throw new Exception("dnet crypt init failed");
        }
    }

}
