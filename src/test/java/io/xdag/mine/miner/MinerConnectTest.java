/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2020-2030 The XdagJ Developers
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package io.xdag.mine.miner;

import static io.xdag.BlockBuilder.generateAddressBlock;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import io.xdag.db.*;
import io.xdag.utils.BasicUtils;
import io.xdag.utils.ByteArrayToByte32;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.MutableBytes;
import org.bouncycastle.util.encoders.Hex;
import org.checkerframework.checker.units.qual.A;
import org.hyperledger.besu.crypto.KeyPair;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.embedded.EmbeddedChannel;
import io.xdag.Kernel;
import io.xdag.config.Config;
import io.xdag.config.DevnetConfig;
import io.xdag.core.Block;
import io.xdag.core.BlockchainImpl;
import io.xdag.core.ImportResult;
import io.xdag.core.XdagBlock;
import io.xdag.crypto.Keys;
import io.xdag.crypto.SampleKeys;
import io.xdag.crypto.Sign;
import io.xdag.crypto.jni.Native;
import io.xdag.db.rocksdb.RocksdbFactory;
import io.xdag.mine.MinerChannel;
import io.xdag.mine.handler.MinerHandShakeHandler;
import io.xdag.utils.BytesUtils;
import io.xdag.wallet.Wallet;

public class MinerConnectTest {

    @Rule
    public TemporaryFolder root = new TemporaryFolder();

    Config config = new DevnetConfig();
    Wallet wallet;
    String pwd;
    Kernel kernel;
    DatabaseFactory dbFactory;
    AddressStore addressStore;
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
        pwd = "password";
        wallet = new Wallet(config);
        wallet.unlock(pwd);
        KeyPair key = KeyPair.create(SampleKeys.SRIVATE_KEY, Sign.CURVE, Sign.CURVE_NAME);
        wallet.setAccounts(Collections.singletonList(key));
        wallet.flush();

        kernel = new Kernel(config);
        dbFactory = new RocksdbFactory(config);

        BlockStore blockStore = new BlockStore(
                dbFactory.getDB(DatabaseName.INDEX),
                dbFactory.getDB(DatabaseName.TIME),
                dbFactory.getDB(DatabaseName.BLOCK),
                dbFactory.getDB(DatabaseName.TXHISTORY));

        blockStore.reset();

        AddressStore addressStore = new AddressStore(dbFactory.getDB(DatabaseName.ADDRESS));

        addressStore.reset();

        OrphanPool orphanPool = new OrphanPool(dbFactory.getDB(DatabaseName.ORPHANIND));
        orphanPool.reset();

        kernel.setBlockStore(blockStore);
        kernel.setOrphanPool(orphanPool);
        kernel.setWallet(wallet);

        blockchain = new BlockchainImpl(kernel);

        channel = new MinerChannel(kernel, false);
    }

    @After
    public void tearDown() throws IOException {
        wallet.delete();
    }

    @Test
    public void testMinerConnect()
            throws InvalidAlgorithmParameterException, NoSuchAlgorithmException, NoSuchProviderException {
//        Native.crypt_start();
        KeyPair key = Keys.createEcKeyPair();
        Block address = generateAddressBlock(config, key, new Date().getTime());
        MutableBytes encoded = address.getXdagBlock().getData();
        byte[] data = Native.dfslib_encrypt_array(encoded.toArray(), 16, 0);

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
        ByteBuf readInbound = embeddedChannel.readInbound();
        assertEquals(1, readInbound.readInt());

        String fake = "0000000000000000510500000000000011100b07790100000000000000000000913c141ee4175a018a3412ba52f827d2fd67da7c0c581641e9f48a81e9dbd8f2486fac9f54560465e53a20f21940a335414f3949fc807f187fb57f51a48611220000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000";
        address = new Block(new XdagBlock(Hex.decode(fake)));
        encoded = address.getXdagBlock().getData();
        data = Native.dfslib_encrypt_array(encoded.toArray(), 16, 0);

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
        readInbound = embeddedChannel.readInbound();
        assertEquals(0, readInbound.readInt());

        //释放资源
        buf.release();
    }
    @Test
    public void testNewMinerConnect()
            throws InvalidAlgorithmParameterException, NoSuchAlgorithmException, NoSuchProviderException {
//        Native.crypt_start();
        KeyPair key = Keys.createEcKeyPair();
        byte[] address = Keys.toBytesAddress(key);

//        byte[] data = Native.dfslib_encrypt_array(encoded.toArray(), 16, 0);

        ByteBuf buf = Unpooled.buffer();
        buf.writeBytes(address);
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
        ByteBuf readInbound = embeddedChannel.readInbound();
        assertEquals(1, readInbound.readInt());

//        String fake = "0000000000000000510500000000000011100b07790100000000000000000000913c141ee4175a018a3412ba52f827d2fd67da7c0c581641e9f48a81e9dbd8f2486fac9f54560465e53a20f21940a335414f3949fc807f187fb57f51a48611220000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000";
//        address = new Block(new XdagBlock(Hex.decode(fake)));
//        encoded = address.getXdagBlock().getData();
//        data = Native.dfslib_encrypt_array(encoded.toArray(), 16, 0);

//        buf.clear();
//        buf.writeBytes(data);
//        buf1 = buf.duplicate();
//        embeddedChannel = new EmbeddedChannel(new MockMinerHandshakeHandler(channel, kernel));
//        //3、将数据写入 EmbeddedChannel
//        writeInbound = embeddedChannel.writeInbound(buf1.retain());
//        assertTrue(writeInbound);
//        //4、标记 Channel 为已完成状态
//        finish = embeddedChannel.finish();
//        assertTrue(finish);
//
//        //5、读取数据
//        readInbound = embeddedChannel.readInbound();
//        assertEquals(0, readInbound.readInt());
//
//        //释放资源
//        buf.release();
    }
}
    class MockMinerHandshakeHandler extends MinerHandShakeHandler {

        public MockMinerHandshakeHandler(MinerChannel channel, Kernel kernel) {
            super(channel, kernel);
        }


//        @Override
        public boolean isDataIllegal(byte[] uncryptData) {
            return false;
        }

//        @Override
//        public ImportResult tryToConnect(Block addressBlock) {
//            return blockchain.tryToConnect(addressBlock);
//        }

        @Override
        public boolean initMiner(Bytes32 hash) {
            return true;
        }

        @Override
        protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
            if (in.readableBytes() >= 20) {
                ByteBuf byteBuf = Unpooled.buffer();

                byte[] address = new byte[20];
                in.readBytes(address);

//                long sectorNo = 0;

                /* 解密数据 */
//                byte[] uncryptData = Native.dfslib_uncrypt_array(address, 16, sectorNo);
//                if (isDataIllegal(uncryptData.clone())) {
//                    ctx.channel().closeFuture();
//                } else {
                    System.arraycopy(BytesUtils.longToBytes(0, true), 0, address, 0, 8);
//                    if (importResult.getErrorInfo() != null) {
//                        byteBuf = Unpooled.buffer();
//                        byteBuf.writeInt(0);
//                        out.add(byteBuf.retain());
//                    }
                    if (!initMiner(ByteArrayToByte32.arrayToByte32(address))) {
                        ctx.close();
                    }
                    byteBuf.writeInt(1);
                    out.add(byteBuf.retain());
                }
            }
//        }
}
