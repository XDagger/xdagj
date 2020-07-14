package io.xdag.core;

import static io.xdag.config.Constants.BI_REF;
import static io.xdag.core.XdagField.FieldType.XDAG_FIELD_OUT;
import static io.xdag.utils.BasicUtils.amount2xdag;
import static io.xdag.utils.BasicUtils.xdag2amount;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.Before;
import org.junit.Test;
import org.spongycastle.util.encoders.Hex;

import io.xdag.Kernel;
import io.xdag.config.Config;
import io.xdag.crypto.ECKey;
import io.xdag.crypto.jni.Native;
import io.xdag.db.DatabaseFactory;
import io.xdag.db.DatabaseName;
import io.xdag.db.rocksdb.RocksdbFactory;
import io.xdag.db.store.AccountStore;
import io.xdag.db.store.BlockStore;
import io.xdag.db.store.OrphanPool;
import io.xdag.net.message.NetStatus;
import io.xdag.utils.BytesUtils;
import io.xdag.utils.XdagTime;
import io.xdag.wallet.Wallet;
import io.xdag.wallet.WalletImpl;

public class BlockchainTest {
    Config config = new Config();
    Wallet xdagWallet;
    Kernel kernel;
    DatabaseFactory dbFactory;

    //
    @Before
    public void setUp() throws Exception {
        config.setStoreDir("/Users/punk/testRocksdb/XdagDB");
        config.setStoreBackupDir("/Users/punk/testRocksdb/XdagDB/backupdata");

        Native.init();
        if (Native.dnet_crypt_init() < 0) {
            throw new Exception("dnet crypt init failed");
        }
        xdagWallet = new WalletImpl();
        xdagWallet.init(config);

        kernel = new Kernel(config);
        dbFactory = new RocksdbFactory(config);

        BlockStore blockStore = new BlockStore(
                dbFactory.getDB(DatabaseName.INDEX),
                dbFactory.getDB(DatabaseName.BLOCK),
                dbFactory.getDB(DatabaseName.TIME),
                dbFactory.getSumsDB());
        blockStore.reset();
        AccountStore accountStore = new AccountStore(xdagWallet, blockStore, dbFactory.getDB(DatabaseName.ACCOUNT));
        accountStore.reset();
        OrphanPool orphanPool = new OrphanPool(dbFactory.getDB(DatabaseName.ORPHANIND));
        orphanPool.reset();

        kernel.setBlockStore(blockStore);
        kernel.setAccountStore(accountStore);
        kernel.setOrphanPool(orphanPool);
        kernel.setWallet(xdagWallet);
        NetStatus netStatus = new NetStatus();
        kernel.setNetStatus(netStatus);
    }

    @Test
    public void blockchainTest() {

        String blockRawdata = "000000000000000038324654050000004d3782fa780100000000000000000000"
                + "c86357a2f57bb9df4f8b43b7a60e24d1ccc547c606f2d7980000000000000000"
                + "afa5fec4f56f7935125806e235d5280d7092c6840f35b397000000000a000000"
                + "a08202c3f60123df5e3a973e21a2dd0418b9926a2eb7c4fc000000000a000000"
                + "08b65d2e2816c0dea73bf1b226c95c2ae3bc683574f559bbc5dd484864b1dbeb"
                + "f02a041d5f7ff83a69c0e35e7eeeb64496f76f69958485787d2c50fd8d9614e6"
                + "7c2b69c79eddeff5d05b2bfc1ee487b9c691979d315586e9928c04ab3ace15bb"
                + "3866f1a25ed00aa18dde715d2a4fc05147d16300c31fefc0f3ebe4d77c63fcbb"
                + "ec6ece350f6be4c84b8705d3b49866a83986578a3a20e876eefe74de0c094bac"
                + "0000000000000000000000000000000000000000000000000000000000000000"
                + "0000000000000000000000000000000000000000000000000000000000000000"
                + "0000000000000000000000000000000000000000000000000000000000000000"
                + "0000000000000000000000000000000000000000000000000000000000000000"
                + "0000000000000000000000000000000000000000000000000000000000000000"
                + "0000000000000000000000000000000000000000000000000000000000000000"
                + "0000000000000000000000000000000000000000000000000000000000000000";
        Block first = new Block(new XdagBlock(Hex.decode(blockRawdata)));

        System.out.println(
                "=====================================first block use key1========================================");

        long time = XdagTime.getEndOfEpoch(XdagTime.getCurrentTimestamp()); // extra
        List<Address> pending = new ArrayList<>();
        pending.add(new Address(first.getHashLow()));
        Block txfirst = new Block(time, first.getFirstOutput(), null, pending, false, null, -1);
        ECKey ecKey1 = new ECKey();
        txfirst.signOut(ecKey1);
        printBlockInfo(txfirst);

        System.out.println(
                "=====================================second block use key2========================================");
        time = XdagTime.getEndOfEpoch(XdagTime.getCurrentTimestamp()); // extra
        pending = new ArrayList<>();
        pending.add(new Address(first.getHashLow()));
        Block txsecond = new Block(time, first.getFirstOutput(), null, pending, false, null, -1);
        ECKey ecKey2 = new ECKey();
        txsecond.signOut(ecKey2);
        printBlockInfo(txsecond);

        System.out.println(
                "=====================================main block use key2========================================");
        pending = new ArrayList<>();
        pending.add(new Address(txfirst.getHashLow()));
        pending.add(new Address(txsecond.getHashLow()));
        Block main = new Block(time, new Address(first.getHashLow()), null, pending, true, null, -1); // extra
        main.signOut(ecKey2);
        byte[] minShare = new byte[32];
        new Random().nextBytes(minShare);
        main.setNonce(minShare);
        printBlockInfo(main);

        System.out.println(
                "=====================================transaction1 block use key1========================================");
        List<Address> links = new ArrayList<>();
        links.add(new Address(txfirst.getHashLow(), XdagField.FieldType.XDAG_FIELD_IN, 10)); // key1
        links.add(new Address(txsecond.getHashLow(), XDAG_FIELD_OUT, 10));
        List<ECKey> keys = new ArrayList<>();
        keys.add(ecKey1);
        time = XdagTime.getCurrentTimestamp();
        Block transaction1 = new Block(time, first.getFirstOutput(), links, null, false, keys, 0); // orphan
        // 跟输入用的同一把密钥
        transaction1.signOut(ecKey1);
        printBlockInfo(transaction1);

        System.out.println(
                "=====================================transaction2 block use key3========================================");
        links = new ArrayList<>();
        links.add(new Address(txfirst.getHashLow(), XdagField.FieldType.XDAG_FIELD_IN, 10)); // key1
        links.add(new Address(txsecond.getHashLow(), XDAG_FIELD_OUT, 10));
        keys = new ArrayList<>();
        keys.add(ecKey1);
        Block transaction2 = new Block(time, first.getFirstOutput(), links, null, false, keys, -1); // orphan
        // 跟输入用的不是同一把密钥
        ECKey ecKey3 = new ECKey();
        transaction2.signIn(ecKey1);
        transaction2.signOut(ecKey3);
        printBlockInfo(transaction2);

        System.out.println(
                "=====================================transaction3 block use key3========================================");
        links = new ArrayList<>();
        links.add(new Address(txfirst.getHashLow(), XdagField.FieldType.XDAG_FIELD_IN, 10)); // key1
        links.add(new Address(txsecond.getHashLow(), XdagField.FieldType.XDAG_FIELD_IN, 10)); // key2
        links.add(new Address(main.getHashLow(), XdagField.FieldType.XDAG_FIELD_IN, 20));
        keys = new ArrayList<>();
        keys.add(ecKey1);
        keys.add(ecKey2);
        Block transaction3 = new Block(time, first.getFirstOutput(), links, null, false, keys, -1); // orphan
        // 跟输入用的不是同一把密钥
        transaction3.signIn(ecKey1);
        transaction3.signIn(ecKey2);
        transaction3.signOut(ecKey3);
        printBlockInfo(transaction1);

        BlockchainImpl blockchain = new BlockchainImpl(kernel, dbFactory);
        blockchain.tryToConnect(first);
        blockchain.updateBlockFlag(first, BI_REF, true);
        blockchain.tryToConnect(txfirst);
        blockchain.tryToConnect(txsecond);
        blockchain.tryToConnect(main);
        blockchain.tryToConnect(transaction1);
        blockchain.tryToConnect(transaction2);
        blockchain.tryToConnect(transaction3);

        System.out.println("Orphan size:" + blockchain.getOrphanSize()); // 5
        System.out.println("Extra size:" + blockchain.getExtraSize()); // 1

        blockchain.removeOrphan(main, BlockchainImpl.OrphanRemoveActions.ORPHAN_REMOVE_NORMAL);
        System.out.println("Orphan size:" + blockchain.getOrphanSize()); // 3
        System.out.println("Extra size:" + blockchain.getExtraSize()); // 3
    }

    public void printBlockInfo(Block block) {
        System.out.println("timestamp:" + Long.toHexString(block.getTimestamp()));
        printHash(block.getHash(), "blockhash:");
        printHash(block.getHashLow(), "blockhashlow:");
        System.out.println("type:" + block.getType());
        if (block.getFirstOutput() != null)
            printHash(block.getFirstOutput().getHashLow(), "firstoutput:");
        System.out.println("inputs:" + block.getInputs().size());
        printListAddress(block.getInputs());
        System.out.println("outputs:" + block.getOutputs().size());
        printListAddress(block.getOutputs());
        System.out.println("keys size:");
        System.out.println(block.getPubKeys().size());
        System.out.println("verified keys size");
        System.out.println(block.verifiedKeys().size());
        System.out.println("blockdiff:" + block.getDifficulty());
        printXdagBlock(block.getXdagBlock(), "xdagblock:");
        printListKeys(block.getPubKeys());
        printHash(block.getOutsig().toByteArray(), "outsig:");
        System.out.println("outsigindex:" + block.getOutsigIndex());
        printMapInsig(block.getInsigs());
        if (block.getNonce() != null) {
            System.out.println("nonce:" + Hex.toHexString(block.getNonce()));
        }
    }

    public void printXdagBlock(XdagBlock block, String prefix) {
        System.out.println(prefix);
        for (XdagField field : block.getFields()) {
            System.out.println(Hex.toHexString(field.getData()));
        }
    }

    public void printMapInsig(Map<ECKey.ECDSASignature, Integer> input) {
        for (ECKey.ECDSASignature sig : input.keySet()) {
            System.out.println("inputsig:" + sig.toHex());
            System.out.println("inputsigindex:" + input.get(sig));
        }
    }

    public void printHash(byte[] hash, String prefix) {
        System.out.println(prefix + Hex.toHexString(hash));
    }

    public void printListAddress(List<Address> input) {
        for (Address address : input) {
            System.out.println("address data:" + Hex.toHexString(address.getData()));
            System.out.println("address hashlow:" + Hex.toHexString(address.getHashLow()));
            System.out.println("address amount:" + address.getAmount());
        }
    }

    public void printListKeys(List<ECKey> input) {
        for (ECKey ecKey : input) {
            printHash(ecKey.getPubKeybyCompress(), "key:");
        }
    }

    @Test
    public void Testblockload() {
        BlockchainImpl blockchain = new BlockchainImpl(kernel, dbFactory);
        loadBlockchain(config.getOriginStoreDir(), 1563368095744L, 1649267441664L, blockchain);
        printBlockchainInfo(blockchain);

        System.out.println("Balance:" + amount2xdag(kernel.getAccountStore().getGBalance()));

        System.out.println("========Minedblocks========");
        List<Block> blocks = blockchain.listMinedBlocks(20);
        // List<Block> mainblocks = blockchain.listMainBlocks(20);
        System.out.println(blocks.size());

        for (int i = 0; i < blocks.size(); i++) {
            System.out.println(Hex.toHexString(blocks.get(i).getHashLow()));
        }

        System.out.println("========Xfer========");

        Map<Address, ECKey> pairs = kernel.getAccountStore().getAccountListByAmount(xdag2amount(100));

        for (Address input : pairs.keySet()) {
            System.out.println("Input:" + input.getType());
            System.out.println("Input:" + input.getAmount());
            System.out.println("Input:" + Hex.toHexString(input.getHashLow()));
            System.out.println("Input data:" + Hex.toHexString(input.getData()));
        }

        byte[] to = Hex.decode("0000000000000000a968f33f0396f13cfd95171dd83866a321aa466e5f2042bc");
        List<Address> tos = new ArrayList<>();
        tos.add(new Address(to, XDAG_FIELD_OUT, 100));
        Block transaction = blockchain.createNewBlock(pairs, tos, false);
        for (ECKey ecKey : pairs.values()) {
            if (ecKey.equals(kernel.getWallet().getDefKey().ecKey)) {
                transaction.signOut(ecKey);
            } else {
                transaction.signIn(ecKey);
            }
        }
        System.out.println("Transaction hash:" + Hex.toHexString(transaction.getHashLow()));
        System.out.println("Transaction data:" + Hex.toHexString(transaction.getXdagBlock().getData()));
    }

    @Test
    public void TestLoadBlocksByTime() {
        BlockchainImpl blockchain = new BlockchainImpl(kernel, dbFactory);
        loadBlockchain(config.getOriginStoreDir(), 1563368095744L, 1627725496320L, blockchain);
        printBlockchainInfo(blockchain);
        System.out.println(
                "=====================================Load Blocks from blockchain========================================");

        // List<Block> blocks =
        // blockchain.getBlockByTime(1614907703296L,1627792605183L);
        // System.out.println("=====================================Block
        // size:"+blocks.size()+"========================================");

    }

    public void printBlockchainInfo(BlockchainImpl blockchain) {
        System.out.println(
                "=====================================Blockchain Info========================================");
        System.out.println("blocks:" + blockchain.getBlockSize());
        System.out.println("main blocks:" + blockchain.getMainBlockSize());
        System.out.println("extra blocks:" + blockchain.getExtraSize());
        System.out.println("orphan blocks:" + blockchain.getOrphanSize());
        System.out.println("chain difficulty:" + blockchain.getTopDiff().toString(16));
        System.out.println("XDAG supply:" + blockchain.getMainBlockSize() * 1024);
        if (blockchain.getOrphanSize() > 0) {
            for (int i = 0; i < blockchain.getOrphanSize(); i++) {
                System.out.println(
                        "orphan block:"
                                + Hex.toHexString(
                                        blockchain
                                                .getBlockFromOrphanPool((int) blockchain.getOrphanSize())
                                                .get(i)
                                                .getHashLow()));
            }
        }
    }

    // 01780000 0179ffff
    public long loadBlockchain(
            String srcFilePath, long starttime, long endtime, BlockchainImpl blockchain) {
        ByteBuffer buffer = ByteBuffer.allocate(512);
        StringBuffer file = new StringBuffer(srcFilePath);
        FileInputStream inputStream = null;
        FileChannel channel = null;
        starttime |= 0x00000;
        endtime |= 0xffff;
        File fileImpl = null;
        long res = 0;

        while (starttime < endtime) {
            List<String> filename = getFileName(starttime);
            String blockfile = Hex.toHexString(BytesUtils.byteToBytes((byte) ((starttime >> 16) & 0xff), true));
            file.append(filename.get(filename.size() - 1)).append(blockfile).append(".dat");
            fileImpl = new File(file.toString());
            if (!fileImpl.exists()) {
                starttime += 0x10000;
                file = new StringBuffer(srcFilePath);
                continue;
            }
            System.out.println("Block from:" + file.toString());
            try {

                inputStream = new FileInputStream(fileImpl);
                channel = inputStream.getChannel();
                while (true) {
                    int eof = channel.read(buffer);
                    if (eof == -1) {
                        break;
                    }
                    buffer.flip();
                    res++;
                    if (blockchain != null) {
                        Block block = new Block(new XdagBlock(buffer.array().clone()));
                        blockchain.tryToConnect(block);
                    }
                    buffer.clear();
                }

            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                try {
                    if (channel != null) {
                        channel.close();
                    }
                    if (inputStream != null) {
                        inputStream.close();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            starttime += 0x10000;
            file = new StringBuffer(srcFilePath);
        }

        return res;
    }

    public List<String> getFileName(long time) {
        List<String> file = new ArrayList<>();
        file.add("");
        StringBuffer stringBuffer = new StringBuffer(
                Hex.toHexString(BytesUtils.byteToBytes((byte) ((time >> 40) & 0xff), true)));
        stringBuffer.append("/");
        file.add(String.valueOf(stringBuffer));
        stringBuffer.append(
                Hex.toHexString(BytesUtils.byteToBytes((byte) ((time >> 32) & 0xff), true)));
        stringBuffer.append("/");
        file.add(String.valueOf(stringBuffer));
        stringBuffer.append(
                Hex.toHexString(BytesUtils.byteToBytes((byte) ((time >> 24) & 0xff), true)));
        stringBuffer.append("/");
        file.add(String.valueOf(stringBuffer));
        return file;
    }

    @Test
    public void testXdagAmount() {
        System.out.println(xdag2amount(10.99));
        System.out.println(xdag2amount(1024));
        System.out.println(amount2xdag(xdag2amount(10.99)));
        System.out.println(amount2xdag(4000000001L));

        System.out.println(xdag2amount(500.2));
        System.out.println(xdag2amount(1024 - 500.2));
        System.out.println(amount2xdag(xdag2amount(1024 - 500.2) + xdag2amount(500.2)));
        System.out.println(
                xdag2amount(1024 - 500.2 - 234.4 - 312.2)
                        + xdag2amount(500.2)
                        + xdag2amount(234.4)
                        + xdag2amount(312.2));
        System.out.println(xdag2amount(1024));

        System.out.println(
                amount2xdag(
                        xdag2amount(
                                1024 - 500.2 - 234.4 - 312.2 - 10.3 - 1.1 - 2.2 - 3.3 - 2.2 - 4.4 - 10.3 - 1.1
                                        - 2.2 - 3.3 - 2.2 - 4.4)
                                + xdag2amount(500.2)
                                + xdag2amount(234.4)
                                + xdag2amount(312.2)
                                + xdag2amount(10.3)
                                + xdag2amount(1.1)
                                + xdag2amount(2.2)
                                + xdag2amount(3.3)
                                + xdag2amount(2.2)
                                + xdag2amount(4.4)
                                + xdag2amount(10.3)
                                + xdag2amount(1.1)
                                + xdag2amount(2.2)
                                + xdag2amount(3.3)
                                + xdag2amount(2.2)
                                + xdag2amount(4.4)));
        System.out.println(amount2xdag(xdag2amount(1024)));
    }
}