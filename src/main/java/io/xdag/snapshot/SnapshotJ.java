package io.xdag.snapshot;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.KryoException;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import io.xdag.core.*;

import io.xdag.crypto.Hash;
import io.xdag.db.execption.DeserializationException;
import io.xdag.db.execption.SerializationException;
import io.xdag.db.rocksdb.RocksdbKVSource;
import io.xdag.db.store.BlockStore;
import io.xdag.snapshot.core.SnapshotInfo;

import io.xdag.utils.BytesUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.crypto.SECP256K1;
import org.bouncycastle.util.encoders.Hex;
import org.rocksdb.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.util.List;

import static io.xdag.config.Constants.BI_OURS;
import static io.xdag.db.store.BlockStore.*;

@Slf4j
public class SnapshotJ extends RocksdbKVSource {

    public static final Kryo kryo = new Kryo();
    private long ourBalance = 0L;
    private long nextTime = 0;
    private long height = 0;

    static {
        kryoRegister();
    }

    public SnapshotJ(String name) {
        super(name);
    }

    public void makeSnapshot(RocksdbKVSource blockSource, RocksdbKVSource snapshotSource) {

        try (RocksIterator iter = getDb().newIterator()) {
            for (iter.seek(new byte[]{HASH_BLOCK_INFO}); iter.key()[0] < 0x40; iter.next()) {
                BlockInfo blockInfo = new BlockInfo();
                if (iter.value() != null) {
                    try {
                        blockInfo = (BlockInfo) deserialize(iter.value(), BlockInfo.class);
                    } catch (DeserializationException e) {
                        log.error("hash low:" + Hex.toHexString(blockInfo.getHashlow()));
                        log.error("can't deserialize data:{}", Hex.toHexString(iter.value()));
                        log.error(e.getMessage(), e);
                    }
                    //Has public key or block data
                    if (blockInfo.getSnapshotInfo() != null) {
                        int flag = blockInfo.getFlags();
                        flag &= ~BI_OURS;
                        blockInfo.setFlags(flag);
                        blockInfo.setSnapshot(true);
                        save(iter, blockInfo, snapshotSource);
                    } else { //Storage block data without public key and balance
                        if (blockInfo.getAmount() != 0) {
                            blockInfo.setSnapshot(true);
                            blockInfo.setSnapshotInfo(new SnapshotInfo(false, blockSource.get(BytesUtils.subArray(iter.key(), 1, 32))));
                            int flag = blockInfo.getFlags();
                            flag &= ~BI_OURS;
                            blockInfo.setFlags(flag);
                            save(iter, blockInfo, snapshotSource);
                        }
                    }
                }
                if (blockInfo.getHeight() >= height) {
                    height = blockInfo.getHeight();
                    nextTime = blockInfo.getTimestamp();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        byte[] preSeed = this.get(new byte[]{SNAPSHOT_PRESEED});
        snapshotSource.put(new byte[]{SNAPSHOT_PRESEED}, preSeed);
    }

    public void saveSnapshotToIndex(BlockStore blockStore, List<SECP256K1.KeyPair> keys) {
        try (RocksIterator iter = getDb().newIterator()) {
            for (iter.seekToFirst(); iter.isValid(); iter.next()) {
                if (iter.key()[0] == 0x30) {
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
                                    SECP256K1.KeyPair key = keys.get(i);
                                    if (Bytes.wrap(key.getPublicKey().asEcPoint().getEncoded(true)).compareTo(Bytes.wrap(ecKeyPair)) == 0) {
                                        flag |= BI_OURS;
                                        keyIndex = i;
                                        ourBalance += blockInfo.getAmount();
                                        break;
                                    }
                                }
                            } else {    //Verify signature
                                Block block = new Block(new XdagBlock(snapshotInfo.getData()));
                                SECP256K1.Signature outSig = block.getOutsig();
                                for (int i = 0; i < keys.size(); i++) {
                                    SECP256K1.KeyPair keyPair = keys.get(i);
                                    byte[] publicKeyBytes = keyPair.getPublicKey().asEcPoint().getEncoded(true);
                                    Bytes digest = Bytes
                                            .wrap(block.getSubRawData(block.getOutsigIndex() - 2), Bytes.wrap(publicKeyBytes));
                                    Bytes32 hash = Hash.hashTwice(Bytes.wrap(digest));
                                    if (SECP256K1.verify(hash, outSig, keyPair.getPublicKey())) {
                                        flag |= BI_OURS;
                                        keyIndex = i;
                                        ourBalance += blockInfo.getAmount();
                                        break;
                                    }
                                }
                            }
                        }
                        blockInfo.setFlags(flag);
                        if ((flag & BI_OURS) != 0 && keyIndex > -1) {
                            blockStore.saveOurBlock(keyIndex, blockInfo.getHashlow());
                        }
                        blockStore.saveBlockInfo(blockInfo);
                    }
                } else if (iter.key()[0] == (byte) 0x90) {
                    blockStore.savePreSeed(iter.value());
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void save(RocksIterator iter, BlockInfo blockInfo, RocksdbKVSource snapshotSource) {
        byte[] value = null;
        try {
            value = serialize(blockInfo);
        } catch (SerializationException e) {
            log.error(e.getMessage(), e);
        }
        snapshotSource.put(iter.key(), value);
    }

    public long getOurBalance() {
        return this.ourBalance;
    }

    public long getNextTime() {
        return nextTime;
    }

    public long getHeight() {
        return height;
    }

    public static Object deserialize(final byte[] bytes, Class<?> type) throws DeserializationException {
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

    public static byte[] serialize(final Object obj) throws SerializationException {
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

    public static void kryoRegister() {
        kryo.register(BigInteger.class);
        kryo.register(byte[].class);
        kryo.register(BlockInfo.class);
        kryo.register(XdagStats.class);
        kryo.register(XdagTopStatus.class);
        kryo.register(SnapshotInfo.class);
    }
}
