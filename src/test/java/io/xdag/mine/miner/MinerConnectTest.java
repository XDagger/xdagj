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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.embedded.EmbeddedChannel;
import io.xdag.Kernel;
import io.xdag.config.Config;
import io.xdag.config.DevnetConfig;
import io.xdag.core.BlockchainImpl;
import io.xdag.crypto.Keys;
import io.xdag.crypto.SampleKeys;
import io.xdag.crypto.Sign;
import io.xdag.db.AddressStore;
import io.xdag.db.BlockStore;
import io.xdag.db.OrphanBlockStore;
import io.xdag.db.rocksdb.AddressStoreImpl;
import io.xdag.db.rocksdb.BlockStoreImpl;
import io.xdag.db.rocksdb.DatabaseFactory;
import io.xdag.db.rocksdb.DatabaseName;
import io.xdag.db.rocksdb.OrphanBlockStoreImpl;
import io.xdag.db.rocksdb.RocksdbFactory;
import io.xdag.mine.MinerChannel;
import io.xdag.mine.handler.MinerHandShakeHandler;
import io.xdag.utils.BytesUtils;
import io.xdag.Wallet;
import java.io.IOException;
import java.math.BigInteger;
import java.security.InvalidAlgorithmParameterException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.util.Collections;
import java.util.List;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.crypto.KeyPair;
import org.hyperledger.besu.crypto.SECPPrivateKey;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

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
    BigInteger private_1 = new BigInteger("c85ef7d79691fe79573b1a7064c19c1a9819ebdbd1faaab1a8ec92344438aaf4", 16);
    SECPPrivateKey secretkey_1 = SECPPrivateKey.create(private_1, Sign.CURVE_NAME);
    @Before
    public void setUp() throws Exception {
        KeyPair addrKey = KeyPair.create(secretkey_1, Sign.CURVE, Sign.CURVE_NAME);
        config.getNodeSpec().setStoreDir(root.newFolder().getAbsolutePath());
        config.getNodeSpec().setStoreBackupDir(root.newFolder().getAbsolutePath());

        pwd = "password";
        wallet = new Wallet(config);
        wallet.unlock(pwd);
        KeyPair key = KeyPair.create(SampleKeys.SRIVATE_KEY, Sign.CURVE, Sign.CURVE_NAME);
        wallet.setAccounts(Collections.singletonList(key));
        wallet.flush();

        kernel = new Kernel(config);
        dbFactory = new RocksdbFactory(config);

        BlockStore blockStore = new BlockStoreImpl(
                dbFactory.getDB(DatabaseName.INDEX),
                dbFactory.getDB(DatabaseName.TIME),
                dbFactory.getDB(DatabaseName.BLOCK));

        blockStore.reset();

        AddressStore addressStore = new AddressStoreImpl(dbFactory.getDB(DatabaseName.ADDRESS));

        addressStore.reset();

        OrphanBlockStore orphanBlockStore = new OrphanBlockStoreImpl(dbFactory.getDB(DatabaseName.ORPHANIND));
        orphanBlockStore.reset();

        kernel.setBlockStore(blockStore);
        kernel.setOrphanBlockStore(orphanBlockStore);
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
        byte[] address = Keys.toBytesAddress(key);

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
    }
}
    class MockMinerHandshakeHandler extends MinerHandShakeHandler {

        public MockMinerHandshakeHandler(MinerChannel channel, Kernel kernel) {
            super(channel, kernel);
        }

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

                    System.arraycopy(BytesUtils.longToBytes(0, true), 0, address, 0, 8);

                    if (!initMiner(BytesUtils.arrayToByte32(address))) {
                        ctx.close();
                    }
                    byteBuf.writeInt(1);
                    out.add(byteBuf.retain());
                }
            }
}
