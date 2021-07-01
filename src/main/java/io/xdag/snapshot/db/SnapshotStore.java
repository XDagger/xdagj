package io.xdag.snapshot.db;

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
import lombok.extern.slf4j.Slf4j;
import org.apache.tuweni.bytes.Bytes32;
import org.bouncycastle.util.encoders.Hex;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.util.ArrayList;

@Slf4j
public class SnapshotStore {
    public static final byte STATS                                 =  0x10;
    public static final byte BALANCE_DATA                          =  0x20;
    public static final byte PUBKEY                                =  0x30;
    public static final byte SIGNATURE                             =  0x40;
    public static final byte EXTSTATS                              =  0x50;
    public static final byte SNAPSHOT_TIME                         =  0x60;

    private final Kryo kryo;

    /** <prefix-key,value> */
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


    // BalanceData
    public void saveBalanceData(BalanceData data, Bytes32 hash) {
        byte[] value = null;
        try {
            value = serialize(data);
        } catch (SerializationException e) {
            log.error(e.getMessage(), e);
        }
        snapshotSource.put(BytesUtils.merge(BALANCE_DATA,hash.toArray()), value);
    }

    public BalanceData getBalanceData(Bytes32 hash) {
        BalanceData balanceData = null;
        byte[] data = snapshotSource.get(BytesUtils.merge(BALANCE_DATA, hash.toArray()));
        if (data == null) {
            return null;
        }
        try {
            balanceData = (BalanceData) deserialize(data,BalanceData.class);
        }   catch (DeserializationException e) {
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
        snapshotSource.put(new byte[] {STATS}, value);
    }

    public StatsData getStats() {
        StatsData statsData = null;
        byte[] data = snapshotSource.get(new byte[] {STATS});
        if (data == null) {
            return null;
        }
        try {
            statsData = (StatsData) deserialize(data,StatsData.class);
        }   catch (DeserializationException e) {
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
        snapshotSource.put(new byte[] {EXTSTATS}, value);
    }

    public ExtStatsData getExtStats() {
        ExtStatsData extStatsData = null;
        byte[] data = snapshotSource.get(new byte[] {EXTSTATS});
        if (data == null) {
            return null;
        }
        try {
            extStatsData = (ExtStatsData) deserialize(data,ExtStatsData.class);
        }   catch (DeserializationException e) {
            log.error(e.getMessage(), e);
        }
        return extStatsData;
    }

    public void savePubKey(Bytes32 hash, byte[] ecKeyPair) {
        snapshotSource.put(BytesUtils.merge(PUBKEY,hash.toArray()), ecKeyPair);
    }

    public ECKeyPair getPubKey(Bytes32 hash) {
        ECKeyPair ecKeyPair = null;
        byte[] data = snapshotSource.get(BytesUtils.merge(PUBKEY,hash.toArray()));
        if (data == null) {
            return null;
        }
        ecKeyPair = new ECKeyPair(null,new BigInteger(1, java.util.Arrays.copyOfRange(data, 1, data.length)));
        return ecKeyPair;
    }

    public void saveSignature(Bytes32 hash, byte[] sig) {
        snapshotSource.put(BytesUtils.merge(SIGNATURE,hash.toArray()), sig);
    }

    public byte[] getSignature(Bytes32 hash) {
        byte[] data = snapshotSource.get(BytesUtils.merge(SIGNATURE,hash.toArray()));
        if (data == null) {
            return null;
        }
        return data;
    }


}
