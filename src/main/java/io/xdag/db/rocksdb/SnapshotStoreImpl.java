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
package io.xdag.db.rocksdb;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.KryoException;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryo.util.DefaultInstantiatorStrategy;
import io.xdag.core.*;
import io.xdag.crypto.Hash;
import io.xdag.crypto.Sign;
import io.xdag.db.AddressStore;
import io.xdag.db.BlockStore;
import io.xdag.db.SnapshotStore;
import io.xdag.db.TransactionHistoryStore;
import io.xdag.db.execption.DeserializationException;
import io.xdag.db.execption.SerializationException;
import io.xdag.utils.BasicUtils;
import io.xdag.utils.BytesUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt64;
import org.bouncycastle.util.encoders.Hex;
import org.hyperledger.besu.crypto.KeyPair;
import org.hyperledger.besu.crypto.SECPSignature;
import org.objenesis.strategy.StdInstantiatorStrategy;
import org.rocksdb.RocksIterator;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static io.xdag.config.Constants.BI_OURS;
import static io.xdag.db.AddressStore.ADDRESS_SIZE;
import static io.xdag.db.AddressStore.AMOUNT_SUM;
import static io.xdag.db.BlockStore.*;
import static io.xdag.utils.BasicUtils.compareAmountTo;

@Slf4j
public class SnapshotStoreImpl implements SnapshotStore {

    private final RocksdbKVSource snapshotSource;

    private final Kryo kryo;
    private XAmount ourBalance = XAmount.ZERO;
    private XAmount allBalance = XAmount.ZERO;
    private long nextTime;
    private long height;


    public SnapshotStoreImpl(RocksdbKVSource snapshotSource) {
        this.snapshotSource = snapshotSource;
        this.kryo = new Kryo();
        kryoRegister();
    }

    @Override
    public void init() {
        snapshotSource.init();
    }

    @Override
    public void reset() {
        snapshotSource.reset();
    }

    public void setBlockInfo(BlockInfo blockInfo, PreBlockInfo preBlockInfo) {
        blockInfo.setSnapshot(preBlockInfo.isSnapshot());
        blockInfo.setSnapshotInfo(preBlockInfo.getSnapshotInfo());
        blockInfo.setFee(preBlockInfo.getFee());
        blockInfo.setHash(preBlockInfo.getHash());
        blockInfo.setDifficulty(preBlockInfo.getDifficulty());
        blockInfo.setAmount(XAmount.ofXAmount(preBlockInfo.getAmount().toLong()));
        blockInfo.setHashlow(preBlockInfo.getHashlow());
        blockInfo.setFlags(preBlockInfo.getFlags());
        blockInfo.setHeight(preBlockInfo.getHeight());
        blockInfo.setMaxDiffLink(preBlockInfo.getMaxDiffLink());
        blockInfo.setRef(preBlockInfo.getRef());
        blockInfo.setRemark(preBlockInfo.getRemark());
        blockInfo.setTimestamp(preBlockInfo.getTimestamp());
        blockInfo.setType(preBlockInfo.getType());
    }

    public void makeSnapshot(RocksdbKVSource blockSource, RocksdbKVSource indexSource, boolean b) {
        try (RocksIterator iter = indexSource.getDb().newIterator()) {
            for (iter.seek(new byte[]{HASH_BLOCK_INFO}); iter.isValid() && iter.key()[0] < SUMS_BLOCK_INFO; iter.next()) {
                PreBlockInfo preBlockInfo;
                BlockInfo blockInfo = new BlockInfo();
                if (iter.value() != null) {
                    try {
                        if (b) {
                            preBlockInfo = (PreBlockInfo) deserialize(iter.value(), PreBlockInfo.class);
                            setBlockInfo(blockInfo, preBlockInfo);
                        } else {
                            blockInfo = (BlockInfo) deserialize(iter.value(), BlockInfo.class);
                        }
                    } catch (DeserializationException e) {
//                        log.error("hash low:{}", Hex.toHexString(blockInfo.getHashlow()));
                        log.error("can't deserialize data:{}", Hex.toHexString(iter.value()));
                        log.error(e.getMessage(), e);
                    }
                    // Has public key or block data
                    if (blockInfo.getSnapshotInfo() != null) {
                        int flag = blockInfo.getFlags();
                        flag &= ~BI_OURS;
                        blockInfo.setFlags(flag);
                        blockInfo.setSnapshot(true);
                        save(iter, blockInfo);
                    } else { // Storage block data without public key and balance
                        if ((blockInfo.getAmount() != null && compareAmountTo(blockInfo.getAmount(), XAmount.ZERO) != 0)) {
//                        if (blockInfo.getAmount() != 0) {
                            blockInfo.setSnapshot(true);
                            blockInfo.setSnapshotInfo(new SnapshotInfo(false, blockSource.get(
                                    BytesUtils.subArray(iter.key(), 1, 32))));
                            int flag = blockInfo.getFlags();
                            flag &= ~BI_OURS;
                            blockInfo.setFlags(flag);
                            save(iter, blockInfo);
                        }
                    }
                }
                if (blockInfo.getHeight() >= height) {
                    height = blockInfo.getHeight();
                    nextTime = blockInfo.getTimestamp();
                }
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }

        byte[] preSeed = snapshotSource.get(new byte[]{SNAPSHOT_PRESEED});
        snapshotSource.put(new byte[]{SNAPSHOT_PRESEED}, preSeed);
    }

    public void saveSnapshotToIndex(BlockStore blockStore, TransactionHistoryStore txHistoryStore, List<KeyPair> keys,long snapshotTime) {
        try (RocksIterator iter = snapshotSource.getDb().newIterator()) {
            for (iter.seekToFirst(); iter.isValid(); iter.next()) {
                if (iter.key()[0] == HASH_BLOCK_INFO) {
                    BlockInfo blockInfo = new BlockInfo();
                    if (iter.value() != null) {
                        try {
                            blockInfo = (BlockInfo) deserialize(iter.value(), BlockInfo.class);
                        } catch (DeserializationException e) {
                            log.error("hash low:" + Hex.toHexString(blockInfo.getHashlow()));
                            log.error("can't deserialize data:{}", Hex.toHexString(iter.value()));
                            log.error(e.getMessage(), e);
                        }
                        int flag = blockInfo.getFlags();
                        int keyIndex = -1;
                        //Determine if it is your own address
                        SnapshotInfo snapshotInfo = blockInfo.getSnapshotInfo();
                        if (blockInfo.getSnapshotInfo() != null) {
                            //public key exists
                            if (snapshotInfo.getType()) {
                                byte[] ecKeyPair = snapshotInfo.getData();
                                for (int i = 0; i < keys.size(); i++) {
                                    KeyPair key = keys.get(i);
                                    if (Bytes.wrap(key.getPublicKey().asEcPoint(Sign.CURVE).getEncoded(true)).compareTo(Bytes.wrap(ecKeyPair)) == 0) {
                                        flag |= BI_OURS;
                                        keyIndex = i;
                                        ourBalance = ourBalance.add(blockInfo.getAmount());
                                        break;
                                    }
                                }
                            } else {    //Verify signature
                                Block block = new Block(new XdagBlock(snapshotInfo.getData()));
                                SECPSignature outSig = block.getOutsig();
                                for (int i = 0; i < keys.size(); i++) {
                                    KeyPair keyPair = keys.get(i);
                                    byte[] publicKeyBytes = keyPair.getPublicKey().asEcPoint(Sign.CURVE).getEncoded(true);
                                    Bytes digest = Bytes
                                            .wrap(block.getSubRawData(block.getOutsigIndex() - 2), Bytes.wrap(publicKeyBytes));
                                    Bytes32 hash = Hash.hashTwice(Bytes.wrap(digest));
                                    if (Sign.SECP256K1.verify(hash, Sign.toCanonical(outSig), keyPair.getPublicKey())) {
                                        flag |= BI_OURS;
                                        keyIndex = i;
                                        ourBalance = ourBalance.add(blockInfo.getAmount());
                                        break;
                                    }
                                }
                            }
                        }
                        blockInfo.setFlags(flag);
                        if ((flag & BI_OURS) != 0 && keyIndex > -1) {
                            blockStore.saveOurBlock(keyIndex, blockInfo.getHashlow());
                        }
                        allBalance = allBalance.add(blockInfo.getAmount());
                        blockStore.saveBlockInfo(blockInfo);

                        if(txHistoryStore != null) {
                            XdagField.FieldType fieldType = XdagField.FieldType.XDAG_FIELD_SNAPSHOT;
                            Address address = new Address(Bytes32.wrap(blockInfo.getHashlow()), fieldType, blockInfo.getAmount(),false);

                            TxHistory txHistory = new TxHistory();
                            txHistory.setAddress(address);
                            txHistory.setHash(BasicUtils.hash2Address(address.getAddress()));
                            if(blockInfo.getRemark() != null) {
                                txHistory.setRemark(new String(blockInfo.getRemark(), StandardCharsets.UTF_8));
                            }
                            txHistory.setTimestamp(snapshotTime);
                            txHistoryStore.batchSaveTxHistory(txHistory);
                        }
                    }
                } else if (iter.key()[0] == SNAPSHOT_PRESEED) {
                    blockStore.savePreSeed(iter.value());
                }
            }
            if (txHistoryStore != null) {
                txHistoryStore.batchSaveTxHistory(null);
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
    }


    @Override
    public void saveAddress(BlockStore blockStore, AddressStore addressStore, TransactionHistoryStore txHistoryStore, List<KeyPair> keys, long snapshotTime) {
        try (RocksIterator iter = snapshotSource.getDb().newIterator()) {
            for (iter.seekToFirst(); iter.isValid(); iter.next()) {
                if (iter.key().length < 20) {
                    if (iter.key()[0] == ADDRESS_SIZE) {
                        addressStore.saveAddressSize(iter.value());
                    } else if (iter.key()[0] == AMOUNT_SUM) {
                        UInt64 u64v = UInt64.fromBytes(Bytes.wrap(iter.value()));
                        addressStore.savaAmountSum(XAmount.ofXAmount(u64v.toLong()));
                        allBalance = addressStore.getAllBalance();
                    }
                } else {
                    byte[] address = iter.key();
                    XAmount balance = XAmount.ofXAmount(UInt64.fromBytes(Bytes.wrap(iter.value())).toLong());
                    for (KeyPair keyPair : keys) {
                        byte[] publicKeyBytes = keyPair.getPublicKey().asEcPoint(Sign.CURVE).getEncoded(true);
                        byte[] myAddress = Hash.sha256hash160(Bytes.wrap(publicKeyBytes));
                        if (BytesUtils.compareTo(address, 1, 20, myAddress, 0, 20) == 0) {
                            ourBalance = ourBalance.add(balance);
                        }
                    }
                    addressStore.snapshotAddress(address, balance);
                    if (txHistoryStore != null) {
                        XdagField.FieldType fieldType = XdagField.FieldType.XDAG_FIELD_SNAPSHOT;
                        Address addr = new Address(BytesUtils.arrayToByte32(Arrays.copyOfRange(address, 1, 21)),
                                fieldType, balance, true);
                        TxHistory txHistory = new TxHistory();
                        txHistory.setAddress(addr);
                        txHistory.setHash(BasicUtils.hash2PubAddress(addr.getAddress()));
                        txHistory.setRemark("snapshot");
                        txHistory.setTimestamp(snapshotTime);
                        txHistoryStore.saveTxHistory(txHistory);
                    }
                }
            }
        }
    }

    public void save(RocksIterator iter, BlockInfo blockInfo) {
        byte[] value = null;
        try {
            value = serialize(blockInfo);
        } catch (SerializationException e) {
            log.error(e.getMessage(), e);
        }
        snapshotSource.put(iter.key(), value);
    }

    public XAmount getOurBalance() {
        return this.ourBalance;
    }

    public XAmount getAllBalance() {
        return this.allBalance;
    }

    public long getNextTime() {
        return nextTime;
    }

    public long getHeight() {
        return height;
    }


    public Object deserialize(final byte[] bytes, Class<?> type) throws DeserializationException {
        synchronized (kryo) {
            try {
                final ByteArrayInputStream inputStream = new ByteArrayInputStream(bytes);
                final Input input = new Input(inputStream);
                return kryo.readObject(input, type);
            } catch (final IllegalArgumentException | KryoException | NullPointerException exception) {
                log.debug("Deserialize data:{}", Hex.toHexString(bytes));
                throw new DeserializationException(exception.getMessage(), exception);
            }
        }
    }

    public byte[] serialize(final Object obj) throws SerializationException {
        synchronized (kryo) {
            try {
                final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                final Output output = new Output(outputStream);
                kryo.writeObject(output, obj);
                output.flush();
                output.close();
                return outputStream.toByteArray();
            } catch (final IllegalArgumentException | KryoException exception) {
                throw new SerializationException(exception.getMessage(), exception);
            }
        }
    }

    private void kryoRegister() {
        kryo.setInstantiatorStrategy(new DefaultInstantiatorStrategy(new StdInstantiatorStrategy()));
        kryo.register(BigInteger.class);
        kryo.register(byte[].class);
        kryo.register(BlockInfo.class);
        kryo.register(XdagStats.class);
        kryo.register(XdagTopStatus.class);
        kryo.register(SnapshotInfo.class);
        kryo.register(UInt64.class);
        kryo.register(XAmount.class);
        kryo.register(PreBlockInfo.class);
    }

}
