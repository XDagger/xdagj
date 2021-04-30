package io.xdag.mine.miner;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.embedded.EmbeddedChannel;
import io.xdag.Kernel;
import io.xdag.config.Config;
import io.xdag.config.DevnetConfig;
import io.xdag.core.*;
import io.xdag.crypto.ECKeyPair;
import io.xdag.crypto.Keys;
import io.xdag.crypto.jni.Native;
import io.xdag.db.DatabaseFactory;
import io.xdag.db.DatabaseName;
import io.xdag.db.rocksdb.RocksdbFactory;
import io.xdag.db.store.BlockStore;
import io.xdag.db.store.OrphanPool;
import io.xdag.mine.MinerChannel;
import io.xdag.mine.handler.MinerHandShakeHandler;
import io.xdag.utils.BytesUtils;
import io.xdag.wallet.OldWallet;
import org.bouncycastle.util.encoders.Hex;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.util.Date;
import java.util.List;

import static io.xdag.BlockBuilder.generateAddressBlock;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class MinerConnectTest {

    @Rule
    public TemporaryFolder root = new TemporaryFolder();

    Config config = new DevnetConfig();
    OldWallet xdagWallet;
    Kernel kernel;
    DatabaseFactory dbFactory;
    MinerChannel channel;
    BlockchainImpl blockchain;

    @Before
    public void setUp() throws Exception {
        config.getNodeSpec().setStoreDir(root.newFolder().getAbsolutePath());
        config.getNodeSpec().setStoreBackupDir(root.newFolder().getAbsolutePath());

        Native.init(config);
        if (Native.dnet_crypt_init() < 0) {
            throw new Exception("dnet crypt init failed");
        }
        xdagWallet = new OldWallet();
        xdagWallet.init(config);

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
        kernel.setWallet(xdagWallet);

        blockchain = new BlockchainImpl(kernel);

        channel = new MinerChannel(kernel,null,false);
    }

    class MockMinerHandshakeHandler extends MinerHandShakeHandler{

        public MockMinerHandshakeHandler(MinerChannel channel, Kernel kernel) {
            super(channel, kernel);
        }


        @Override
        public boolean isDataIllegal(byte[] uncryptData) {
            return false;
        }

        @Override
        public ImportResult tryToConnect(Block addressBlock) {
            return blockchain.tryToConnect(addressBlock);
        }

        @Override
        public boolean initMiner(byte[] hash) {
            return true;
        }

        @Override
        protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
            if (in.readableBytes() >= XdagBlock.XDAG_BLOCK_SIZE) {
                Native.crypt_start();
                ByteBuf byteBuf = Unpooled.buffer();

                byte[] address = new byte[512];
                in.readBytes(address);

                long sectorNo = 0;

                /* 解密数据 */
                byte[] uncryptData = Native.dfslib_uncrypt_array(address, 16, sectorNo);
                if (isDataIllegal(uncryptData.clone())) {
                    ctx.channel().closeFuture();
                } else {
                    System.arraycopy(BytesUtils.longToBytes(0, true), 0, uncryptData, 0, 8);
                    Block addressBlock = new Block(new XdagBlock(uncryptData));
                    ImportResult importResult = tryToConnect(addressBlock);
                    if (importResult.getErrorInfo()!=null) {
                        byteBuf = Unpooled.buffer();
                        byteBuf.writeInt(0);
                        out.add(byteBuf.retain());
                    }
                    if (!initMiner(addressBlock.getHash())) {
                        ctx.close();
                    }
                    byteBuf.writeInt(1);
                    out.add(byteBuf.retain());
                }
            } else {
            }
        }
    }

    @Test
    public void testMinerConnect() {
        Native.crypt_start();

        ECKeyPair key = Keys.createEcKeyPair();
        Block address = generateAddressBlock(config, key, new Date().getTime());
        byte[] encoded = address.getXdagBlock().getData();
        byte[] data = Native.dfslib_encrypt_array(encoded,16,0);

        ByteBuf buf = Unpooled.buffer();
        buf.writeBytes(data);
        ByteBuf buf1 = buf.duplicate();
        //2、创建EmbeddedChannel，并添加一个MinerHandshakeHandler
        EmbeddedChannel embeddedChannel = new EmbeddedChannel(new MockMinerHandshakeHandler(channel, kernel));

        //3、将数据写入 EmbeddedChannel
        boolean writeInbound = embeddedChannel.writeInbound(buf1.retain());
        assertTrue(writeInbound);
        //4、标记 Channel 为已完成状态
        boolean finish = embeddedChannel.finish();
        assertTrue(finish);

        //5、读取数据
        ByteBuf readInbound =  embeddedChannel.readInbound();
        assertEquals(1, readInbound.readInt());


        String fake = "0000000000000000510500000000000011100b07790100000000000000000000913c141ee4175a018a3412ba52f827d2fd67da7c0c581641e9f48a81e9dbd8f2486fac9f54560465e53a20f21940a335414f3949fc807f187fb57f51a48611220000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000";
        address = new Block(new XdagBlock(Hex.decode(fake)));
        encoded = address.getXdagBlock().getData();
        data = Native.dfslib_encrypt_array(encoded,16,0);

        buf.clear();
        buf.writeBytes(data);
        buf1 = buf.duplicate();
        embeddedChannel = new EmbeddedChannel(new MockMinerHandshakeHandler(channel, kernel));
        //3、将数据写入 EmbeddedChannel
        writeInbound = embeddedChannel.writeInbound(buf1.retain());
        assertTrue(writeInbound);
        //4、标记 Channel 为已完成状态
        finish = embeddedChannel.finish();
        assertTrue(finish);

        //5、读取数据
        readInbound =  embeddedChannel.readInbound();
        assertEquals(0, readInbound.readInt());

        //释放资源
        buf.release();
    }
}
