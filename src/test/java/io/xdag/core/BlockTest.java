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

package io.xdag.core;

//import static io.xdag.db.BlockStore.BLOCK_AMOUNT;

import io.xdag.utils.BytesUtils;
import io.xdag.utils.SimpleEncoder;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.MutableBytes32;
import org.bouncycastle.util.encoders.Hex;
import org.junit.Test;

import java.nio.ByteOrder;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;

public class BlockTest {

     @Test
     public void testTransferXAmount(){
          XAmount inFee = XAmount.of(1000,XUnit.MILLI_XDAG);
          byte[] fee = BytesUtils.longToBytes(Long.parseLong(inFee.toString()), true);
          byte[] transport = new byte[8];
          byte[] inputByte = BytesUtils.merge(transport, fee, fee, fee);


          SimpleEncoder encoder = new SimpleEncoder();
          encoder.writeField(inputByte);
          byte[] encoded = encoder.toBytes();
          Bytes32 outputByte = Bytes32.wrap(encoded);
          XAmount outFee =XAmount.of(outputByte.getLong(8, ByteOrder.LITTLE_ENDIAN), XUnit.NANO_XDAG);
          assertEquals(inFee, outFee);
     }

     @Test public void generateBlock() {
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
          first.getInfo().setFee(XAmount.of(100,XUnit.MILLI_XDAG));
          assertEquals(first.getXdagBlock().getData(), new XdagBlock(Hex.decode(blockRawdata)).getData());//A 'block' create by rawdata, its xdagblock will not change.
     }


    /**
     Config config = new Config();
     Wallet xdagWallet;

     //
     @Before public void setUp() throws Exception {
     config.setStoreDir("/Users/punk/testRocksdb/XdagDB");
     config.setStoreBackupDir("/Users/punk/testRocksdb/XdagDB/backupdata");

     Native.init();
     if (Native.dnet_crypt_init() < 0) {
     throw new Exception("dnet crypt init failed");
     }
     xdagWallet = new WalletImpl();
     xdagWallet.init(config);
     }

     @Test public void generateBlock() {
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

     printBlockInfo(first);

     System.out.println(
     "=====================================first block use key1========================================");

     long time = XdagTime.getEndOfEpoch(XdagTime.getCurrentTimestamp());
     List<Address> pending = new ArrayList<>();
     pending.add(new Address(first.getHashLow()));
     Block txfirst = new Block(time, first.getFirstOutput(), null, pending, false, null, -1);
     ECKey ecKey1 = new ECKey();
     txfirst.signOut(ecKey1);
     printBlockInfo(txfirst);

     System.out.println(
     "=====================================second block use key2========================================");

     time = XdagTime.getEndOfEpoch(XdagTime.getCurrentTimestamp());
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
     Block main = new Block(time, first.getFirstOutput(), null, pending, true, null, -1);
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
     Block transaction1 = new Block(time, first.getFirstOutput(), links, null, false, keys, 0);
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
     Block transaction2 = new Block(time, first.getFirstOutput(), links, null, false, keys, -1);
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
     Block transaction3 = new Block(time, first.getFirstOutput(), links, null, false, keys, -1);
     // 跟输入用的不是同一把密钥
     transaction3.signIn(ecKey1);
     transaction3.signIn(ecKey2);
     transaction3.signOut(ecKey3);
     printBlockInfo(transaction1);

     System.out.println(
     "=====================================verify transaction1 sig========================================");
     List<Block> input = new ArrayList<>();
     input.add(txfirst);
     System.out.println("can use input?:" + canUseInput(transaction1, input));
     System.out.println(
     "=====================================verify transaction2 sig========================================");
     System.out.println("can use input?:" + canUseInput(transaction2, input));
     System.out.println(
     "=====================================verify transaction3 sig========================================");
     input.add(txsecond);
     System.out.println("can use input?:" + canUseInput(transaction3, input));
     }

     @Test public void TestgenerateTask() {
     String raw = "00000000000000003833550000000040ffff89507a0100000000000000000000"
     + "c86357a2f57bb9df4f8b43b7a60e24d1ccc547c606f2d7980000000000000000"
     + "07488d5de5ee0058014320b25d700d8b3ba4d08c532cf3950000000000000000"
     + "c983f2413c65c4bfee0379919e8d1f67f133f98929bc267b0000000000000000"
     + "a1f4af8d31449d5bb1acf7b6a27124eff6c348968ad427e676529d71c0d8bc0c"
     + "4ca945ca7cf4ec778be7c9afb9076f75361cee1124cd6a438b73ac47a7e2a6f1"
     + "0000000000000000000000000000000000000000000000000000000000000000"
     + "0000000000000000000000000000000000000000000000000000000000000000"
     + "0000000000000000000000000000000000000000000000000000000000000000"
     + "0000000000000000000000000000000000000000000000000000000000000000"
     + "0000000000000000000000000000000000000000000000000000000000000000"
     + "0000000000000000000000000000000000000000000000000000000000000000"
     + "0000000000000000000000000000000000000000000000000000000000000000"
     + "0000000000000000000000000000000000000000000000000000000000000000"
     + "0000000000000000000000000000000000000000000000000000000000000000"
     + "1aae3b19aa0b8c24f6837c10bfc981a303b54f978a314c5baf69e7a4f44eb5a6";

     Block main = new Block(new XdagBlock(Hex.decode(raw)));
     XdagField[] res = createTaskByNewBlock(main);
     for (XdagField field : res) {
     System.out.println(Hex.toHexString(field.getData()));
     }
     }

     public XdagField[] createTaskByNewBlock(Block block) {
     XdagField[] task = new XdagField[2];
     task[1] = block.getXdagBlock().getField(14);

     byte[] data = new byte[448];
     System.arraycopy(block.getXdagBlock().getData(), 0, data, 0, 448);
     XdagSha256Digest currentTaskDigest = new XdagSha256Digest();

     try {
     currentTaskDigest.sha256Update(data);
     byte[] state = currentTaskDigest.getState();
     task[0] = new XdagField(state);
     currentTaskDigest.sha256Update(block.getXdagBlock().getField(14).getData());

     } catch (IOException e) {
     e.printStackTrace();
     }

     return task;
     }

     public boolean canUseInput(Block transaction, List<Block> input) {
     List<ECKey> ecKeys = transaction.verifiedKeys();
     for (Block inBlock : input) {
     boolean canUse = false;
     // 获取签名与hash
     byte[] subdata = inBlock.getSubRawData(inBlock.getOutsigIndex() - 2);
     System.out.println(Hex.toHexString(subdata));

     ECKey.ECDSASignature sig = inBlock.getOutsig();
     for (ECKey ecKey : ecKeys) {
     byte[] hash = Sha256Hash.hashTwice(BytesUtils.merge(subdata, ecKey.getPubKeybyCompress()));
     if (ecKey.verify(hash, sig)) {
     canUse = true;
     }
     }
     if (!canUse) {
     return false;
     }
     }
     return true;
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
     //        System.out.println("blockdiff:" + block.getDifficulty());
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

     @Test public void TestStore() {
     DatabaseFactory factory = new RocksdbFactory(config);
     KVSource<byte[], byte[]> blocksource = factory.getDB(DatabaseName.BLOCK); // <block-hash,block-info>
     KVSource<byte[], byte[]> accountsource = factory.getDB(DatabaseName.ACCOUNT); // <hash,info>
     KVSource<byte[], byte[]> indexsource = factory.getDB(DatabaseName.INDEX); // <hash,info>
     KVSource<byte[], byte[]> orphansource = factory.getDB(DatabaseName.ORPHANIND); // <hash,info>
     KVSource<byte[], byte[]> timesource = factory.getDB(DatabaseName.TIME); // <hash,info>

     blocksource.reset();
     accountsource.reset();
     indexsource.reset();
     orphansource.reset();
     timesource.reset();

     BlockStore blockStore = new BlockStore(indexsource, blocksource, timesource, null);

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

     long time = XdagTime.getEndOfEpoch(XdagTime.getCurrentTimestamp());
     List<Address> pending = new ArrayList<>();
     pending.add(new Address(first.getHashLow()));
     Block txfirst = new Block(time, first.getFirstOutput(), null, pending, false, null, -1);
     ECKey ecKey1 = new ECKey();
     txfirst.signOut(ecKey1);
     printBlockInfo(txfirst);

     System.out.println(
     "=====================================second block use key2========================================");

     time = XdagTime.getEndOfEpoch(XdagTime.getCurrentTimestamp());
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
     Block main = new Block(time, first.getFirstOutput(), null, pending, true, null, -1);
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
     Block transaction1 = new Block(time, first.getFirstOutput(), links, null, false, keys, 0);
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
     Block transaction2 = new Block(time, first.getFirstOutput(), links, null, false, keys, -1);
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
     time = XdagTime.getCurrentTimestamp();
     Block transaction3 = new Block(time, first.getFirstOutput(), links, null, false, keys, -1);
     // 跟输入用的不是同一把密钥
     transaction3.signIn(ecKey1);
     transaction3.signIn(ecKey2);
     transaction3.signOut(ecKey3);
     printBlockInfo(transaction3);

     System.out.println(
     "=====================================verify transaction1 sig========================================");
     List<Block> input = new ArrayList<>();
     input.add(txfirst);
     System.out.println("can use input?:" + canUseInput(transaction1, input));
     System.out.println(
     "=====================================verify transaction2 sig========================================");
     System.out.println("can use input?:" + canUseInput(transaction2, input));
     System.out.println(
     "=====================================verify transaction3 sig========================================");
     input.add(txsecond);
     System.out.println("can use input?:" + canUseInput(transaction3, input));

     blockStore.saveBlock(txfirst);
     blockStore.saveBlock(txsecond);
     blockStore.saveBlock(main);
     blockStore.saveBlock(transaction1);
     blockStore.saveBlock(transaction2);
     blockStore.saveBlock(transaction3);

     transaction1.getInfo().setAmount(1024);
     //        blockStore.updateBlockInfo(BLOCK_AMOUNT, transaction1);
     blockStore.saveBlock(transaction1);

     System.out.println(
     "=====================================get block from store raw========================================");
     System.out.println("transaction1.hashlow:" + Hex.toHexString(transaction1.getHashLow()));
     Block transactionRaw = blockStore.getBlockByHash(transaction1.getHashLow(), true);
     printBlockInfo(transactionRaw);
     System.out.println(
     "=====================================verify block sig========================================");
     List<Block> inputRaw = new ArrayList<>();
     inputRaw.add(txfirst);
     System.out.println("can use input?:" + canUseInput(transactionRaw, inputRaw));

     System.out.println(
     "=====================================get block from store info========================================");
     Block transactionInfo = blockStore.getBlockByHash(transaction1.getHashLow(), false);
     System.out.println("diff:" + transactionInfo.getInfo().getDifficulty());
     System.out.println("time:" + Long.toHexString(transactionInfo.getTimestamp()));
     System.out.println("ref:" + transactionInfo.getInfo().getRef());
     System.out.println("amount:" + transactionInfo.getInfo().getAmount());

     System.out.println(
     "=====================================get blocks from store========================================");
     List<Block> blocks = blockStore.getBlocksByTime(txfirst.getTimestamp());
     for (Block block : blocks) {
     System.out.println(
     "=====================================each block from store========================================");
     printBlockInfo(block);
     }
     }

     @Test public void TestAddressBlock() {
     DatabaseFactory factory = new RocksdbFactory(config);
     KVSource<byte[], byte[]> blocksource = factory.getDB(DatabaseName.BLOCK); // <block-hash,block-info>
     KVSource<byte[], byte[]> accountsource = factory.getDB(DatabaseName.ACCOUNT); // <hash,info>
     KVSource<byte[], byte[]> indexsource = factory.getDB(DatabaseName.INDEX); // <hash,info>
     KVSource<byte[], byte[]> orphansource = factory.getDB(DatabaseName.ORPHANIND); // <hash,info>
     KVSource<byte[], byte[]> timesource = factory.getDB(DatabaseName.TIME); // <hash,info>

     blocksource.reset();
     accountsource.reset();
     indexsource.reset();
     orphansource.reset();
     timesource.reset();

     BlockStore blockStore = new BlockStore(indexsource, blocksource, timesource, null);
     AccountStore accountStore = new AccountStore(xdagWallet, blockStore, accountsource);
     long time = XdagTime.getCurrentTimestamp();

     Block firstAccount = new Block(time, null, null, null, false, null, -1);
     Block secondAccount = new Block(time + 100, null, null, null, false, null, -1);
     // 第一把密钥
     ECKey ecKey = xdagWallet.getKeyByIndex(0);
     System.out.println(
     "=====================================Sign Key========================================");

     System.out.println(ecKey);

     firstAccount.signOut(ecKey);
     secondAccount.signOut(ecKey);

     System.out.println(
     "=====================================First Account========================================");
     printBlockInfo(firstAccount);
     System.out.println(
     "=====================================Second Account========================================");
     printBlockInfo(secondAccount);

     blockStore.saveBlock(firstAccount);
     blockStore.saveBlock(secondAccount);
     accountStore.addFirstAccount(firstAccount, 0);
     accountStore.addNewAccount(secondAccount, 0);

     Block fromaccountR = accountStore.getAccountBlockByHash(firstAccount.getHashLow(), true);
     System.out.println(
     "=====================================First Account Raw========================================");
     printBlockInfo(fromaccountR);
     Block fromaccount = accountStore.getAccountBlockByHash(firstAccount.getHashLow(), false);

     System.out.println(
     "=====================================get block from store info========================================");
     System.out.println("diff:" + fromaccount.getInfo().getDifficulty());
     System.out.println("time:" + Long.toHexString(fromaccount.getTimestamp()));
     System.out.println("ref:" + fromaccount.getInfo().getRef());
     System.out.println("amount:" + fromaccount.getInfo().getAmount());

     System.out.println(
     "=====================================Test Account========================================");

     // 更新金额
     secondAccount.getInfo().setAmount(1024);
     //        blockStore.updateBlockInfo(BLOCK_AMOUNT, secondAccount);
     blockStore.saveBlock(secondAccount);

     Map<Address, ECKey> ans = accountStore.getAccountListByAmount(1000);
     if (ans == null || ans.size() == 0) {
     System.out.println("No match account");
     } else {
     System.out.println("Match account size is:" + ans.size());
     for (Address key : ans.keySet()) {
     System.out.println(
     "=====================================Match Address========================================");
     System.out.println(
     "Send Amount:"
     + key.getAmount()
     + " hashlow:"
     + Hex.toHexString(key.getHashLow())
     + " type:"
     + key.getType());
     System.out.println(
     "=====================================Key Index========================================");
     System.out.println(ans.get(key));
     System.out.println(
     "=====================================Match block========================================");
     Block block = blockStore.getBlockByHash(key.getHashLow(), true);

     printBlockInfo(block);
     }
     }
     }
     **/
}
