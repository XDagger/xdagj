package io.xdag.snapshot.db;

import static io.xdag.utils.BasicUtils.getHashlowByHash;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.KryoException;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import io.xdag.crypto.ECKeyPair;
import io.xdag.db.KVSource;
import io.xdag.db.execption.DeserializationException;
import io.xdag.db.execption.SerializationException;
import io.xdag.snapshot.core.BalanceData;
import io.xdag.snapshot.core.ExtStatsData;
import io.xdag.snapshot.core.StatsData;
import io.xdag.utils.BytesUtils;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.util.encoders.Hex;

@Slf4j
public class SnapshotStore {

    public static final byte STATS = 0x10;
    public static final byte BALANCE_DATA = 0x20;
    public static final byte PUBKEY = 0x30;
    public static final byte SIGNATURE = 0x40;
    public static final byte EXTSTATS = 0x50;
    public static final byte SNAPSHOT_TIME = 0x60;

    private final Kryo kryo;

    /**
     * <prefix-key,value>
     */
    private final KVSource<byte[], byte[]> snapshotSource;

    public SnapshotStore(KVSource<byte[], byte[]> snapshotSource) {
        this.snapshotSource = snapshotSource;
        this.kryo = new Kryo();
        kryoRegister();
    }

    private void kryoRegister() {
        kryo.register(BigInteger.class);
        kryo.register(byte[].class);
        kryo.register(long.class);
        kryo.register(int.class);
        kryo.register(ArrayList.class);
        kryo.register(BalanceData.class);
        kryo.register(StatsData.class);
        kryo.register(ExtStatsData.class);
    }

    private byte[] serialize(final Object obj) throws SerializationException {
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

    private Object deserialize(final byte[] bytes, Class<?> type) throws DeserializationException {
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

    public void init() {
        snapshotSource.init();
    }

    public void reset() {
        snapshotSource.reset();
    }


    // main block
    public void saveStatsData() {

    }

    // BalanceData
    public void saveBalanceData(BalanceData data, byte[] hashOrHashlow) {
        byte[] value = null;
        try {
            value = serialize(data);
        } catch (SerializationException e) {
            log.error(e.getMessage(), e);
        }
        snapshotSource.put(BytesUtils.merge(BALANCE_DATA, getHashlowByHash(hashOrHashlow)), value);
    }

    public BalanceData getBalanceData(byte[] hashOrHashlow) {
        BalanceData balanceData = null;
        byte[] data = snapshotSource.get(BytesUtils.merge(BALANCE_DATA, getHashlowByHash(hashOrHashlow)));
        if (data == null) {
            return null;
        }
        try {
            balanceData = (BalanceData) deserialize(data, BalanceData.class);
        } catch (DeserializationException e) {
            log.error(e.getMessage(), e);
        }
        return balanceData;
    }

    public void saveStats(StatsData statsData) {
        byte[] value = null;
        try {
            value = serialize(statsData);
        } catch (SerializationException e) {
            log.error(e.getMessage(), e);
        }
        snapshotSource.put(new byte[]{STATS}, value);
    }

    public StatsData getStats() {
        StatsData statsData = null;
        byte[] data = snapshotSource.get(new byte[]{STATS});
        if (data == null) {
            return null;
        }
        try {
            statsData = (StatsData) deserialize(data, StatsData.class);
        } catch (DeserializationException e) {
            log.error(e.getMessage(), e);
        }
        return statsData;
    }

    public void saveExtStats(ExtStatsData extStatsData) {
        byte[] value = null;
        try {
            value = serialize(extStatsData);
        } catch (SerializationException e) {
            log.error(e.getMessage(), e);
        }
        snapshotSource.put(new byte[]{EXTSTATS}, value);
    }

    public ExtStatsData getExtStats() {
        ExtStatsData extStatsData = null;
        byte[] data = snapshotSource.get(new byte[]{EXTSTATS});
        if (data == null) {
            return null;
        }
        try {
            extStatsData = (ExtStatsData) deserialize(data, ExtStatsData.class);
        } catch (DeserializationException e) {
            log.error(e.getMessage(), e);
        }
        return extStatsData;
    }

    public void savePubKey(byte[] hashOrHashlow, byte[] ecKeyPair) {
        snapshotSource.put(BytesUtils.merge(PUBKEY, getHashlowByHash(hashOrHashlow)), ecKeyPair);
    }

    public ECKeyPair getPubKey(byte[] hashOrHashlow) {
        ECKeyPair ecKeyPair = null;
        byte[] data = snapshotSource.get(BytesUtils.merge(PUBKEY, getHashlowByHash(hashOrHashlow)));
        if (data == null) {
            return null;
        }
        ecKeyPair = new ECKeyPair(null, new BigInteger(1, java.util.Arrays.copyOfRange(data, 1, data.length)));
        return ecKeyPair;
    }

    public void saveSignature(byte[] hashOrHashlow, byte[] sig) {
        snapshotSource.put(BytesUtils.merge(SIGNATURE, getHashlowByHash(hashOrHashlow)), sig);
    }

    public byte[] getSignature(byte[] hashOrHashlow) {
        byte[] data = snapshotSource.get(BytesUtils.merge(SIGNATURE, getHashlowByHash(hashOrHashlow)));
        if (data == null) {
            return null;
        }
        return data;
    }

    public boolean hasBlock(byte[] hashOrHashlow) {
        return getPubKey(hashOrHashlow) != null || getSignature(hashOrHashlow) != null;
    }

    public List<BalanceData> getBalanceDatas() {
        List<BalanceData> balanceDatas = new ArrayList<>();
        List<byte[]> datas = snapshotSource.prefixValueLookup(new byte[]{BALANCE_DATA});
        for (byte[] data : datas) {
            BalanceData balanceData = null;
            try {
                balanceData = (BalanceData) deserialize(data, BalanceData.class);
                balanceDatas.add(balanceData);
            } catch (DeserializationException e) {
                log.error(e.getMessage(), e);
            }
        }
        return balanceDatas;
    }

    public byte[] getSubData(byte[] hashOrHashlow) {
        return null;
    }


    public List<byte[]> getHashlowListFromPubkey() {
        List<byte[]> hashlowList = new ArrayList<>();
        List<byte[]> datas = snapshotSource.prefixKeyLookup(new byte[]{PUBKEY});
        for (byte[] data : datas) {
            byte[] hash = BytesUtils.subArray(data, 1, 32);
            hashlowList.add(hash);
        }
        return hashlowList;

    }

}
