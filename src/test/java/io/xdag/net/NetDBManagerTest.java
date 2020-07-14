package io.xdag.net;

import org.junit.Before;

import io.xdag.config.Config;
import io.xdag.crypto.jni.Native;

public class NetDBManagerTest {
    Config config = new Config();

    @Before
    public void setUp() throws Exception {
        config.setStoreDir("/Users/punk/testRocksdb/XdagDB");
        config.setStoreBackupDir("/Users/punk/testRocksdb/XdagDB/backupdata");

        Native.init();
        if (Native.dnet_crypt_init() < 0) {
            throw new Exception("dnet crypt init failed");
        }
    }
}
