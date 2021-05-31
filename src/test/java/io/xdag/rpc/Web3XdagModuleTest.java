package io.xdag.rpc;

import io.xdag.Kernel;
import io.xdag.config.Config;
import io.xdag.config.DevnetConfig;
import io.xdag.core.Blockchain;
import io.xdag.crypto.ECKeyPair;
import io.xdag.crypto.SampleKeys;
import io.xdag.crypto.jni.Native;
import io.xdag.db.DatabaseFactory;
import io.xdag.db.DatabaseName;
import io.xdag.db.rocksdb.RocksdbFactory;
import io.xdag.db.store.BlockStore;
import io.xdag.db.store.OrphanPool;
import io.xdag.rpc.modules.web3.Web3XdagModule;
import io.xdag.rpc.modules.web3.Web3XdagModuleImpl;
import io.xdag.rpc.modules.xdag.XdagModule;
import io.xdag.rpc.modules.xdag.XdagModuleTransactionEnabled;
import io.xdag.rpc.modules.xdag.XdagModuleWalletDisabled;
import io.xdag.utils.Numeric;
import io.xdag.wallet.Wallet;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.math.BigInteger;
import java.util.Collections;

public class Web3XdagModuleTest {

    @Rule
    public TemporaryFolder root = new TemporaryFolder();

    Config config = new DevnetConfig();
    Wallet wallet;
    String pwd;
    Kernel kernel;
    DatabaseFactory dbFactory;

    BigInteger private_1 = new BigInteger("c85ef7d79691fe79573b1a7064c19c1a9819ebdbd1faaab1a8ec92344438aaf4", 16);
    BigInteger private_2 = new BigInteger("10a55f0c18c46873ddbf9f15eddfc06f10953c601fd144474131199e04148046", 16);

    @Before
    public void setUp() throws Exception {
        config.getNodeSpec().setStoreDir(root.newFolder().getAbsolutePath());
        config.getNodeSpec().setStoreBackupDir(root.newFolder().getAbsolutePath());

        Native.init(config);
        if (Native.dnet_crypt_init() < 0) {
            throw new Exception("dnet crypt init failed");
        }
        pwd = "password";
        wallet = new Wallet(config);
        wallet.unlock(pwd);
        ECKeyPair key = ECKeyPair.create(Numeric.toBigInt(SampleKeys.PRIVATE_KEY_STRING));
        wallet.setAccounts(Collections.singletonList(key));
        wallet.flush();

        kernel = new Kernel(config);
        dbFactory = new RocksdbFactory(config);

        BlockStore blockStore = new BlockStore(
                dbFactory.getDB(DatabaseName.INDEX),
                dbFactory.getDB(DatabaseName.TIME),
                dbFactory.getDB(DatabaseName.BLOCK));

        blockStore.reset();
        OrphanPool orphanPool = new OrphanPool(dbFactory.getDB(DatabaseName.ORPHANIND));
        orphanPool.reset();

        kernel.setBlockStore(blockStore);
        kernel.setOrphanPool(orphanPool);
        kernel.setWallet(wallet);
    }

    @Test
    public void syncingTest() {

        XdagModule xdagModule = new XdagModule((byte) 0x1,new XdagModuleWalletDisabled(),new XdagModuleTransactionEnabled(kernel.getBlockchain()));
        Web3XdagModule web3XdagModule = createWeb3XdagModule(kernel,xdagModule);


    }

    private Web3XdagModule createWeb3XdagModule(Kernel kernel,XdagModule module) {

        return new Web3XdagModuleImpl(module,kernel);
    }
}
