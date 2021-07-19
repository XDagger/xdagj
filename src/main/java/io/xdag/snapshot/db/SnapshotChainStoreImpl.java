package io.xdag.snapshot.db;

import static io.xdag.utils.BasicUtils.getHashlowByHash;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.KryoException;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import io.xdag.db.KVSource;
import io.xdag.db.execption.DeserializationException;
import io.xdag.db.execption.SerializationException;
import io.xdag.snapshot.core.SnapshotUnit;
import io.xdag.snapshot.core.StatsBlock;
import io.xdag.utils.BytesUtils;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.util.encoders.Hex;

@Slf4j
public class SnapshotChainStoreImpl implements SnapshotChainStore {

    public static final byte SNAPTSHOT_UNIT = 0x10;
    public static final byte SNAPTSHOT_STATS = 0x20;

    private final Kryo kryo;

    /**
     * <prefix-key,value>
     */
    private final KVSource<byte[], byte[]> snapshotSource;

    public SnapshotChainStoreImpl(KVSource<byte[], byte[]> snapshotSource) {
        this.snapshotSource = snapshotSource;
        this.kryo = new Kryo();
        kryoRegister();
    }

    private void kryoRegister() {
        kryo.register(SnapshotUnit.class);
        kryo.register(StatsBlock.class);
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

    @Override
    public void init() {
        snapshotSource.init();
    }

    @Override
    public void reset() {
        snapshotSource.reset();
    }

    @Override
    public void saveSnapshotUnit(byte[] hashOrHashlow, SnapshotUnit snapshotUnit) {
        byte[] value = null;
        try {
            value = serialize(snapshotUnit);
        } catch (SerializationException e) {
            log.error(e.getMessage(), e);
        }
        snapshotSource.put(BytesUtils.merge(SNAPTSHOT_UNIT, getHashlowByHash(hashOrHashlow)), value);
    }

    @Override
    public SnapshotUnit getSnapshotUnit(byte[] hashOrHashlow) {
        SnapshotUnit snapshotUnit = null;
        byte[] data = snapshotSource.get(BytesUtils.merge(SNAPTSHOT_UNIT, getHashlowByHash(hashOrHashlow)));
        if (data == null) {
            return null;
        }
        try {
            snapshotUnit = (SnapshotUnit) deserialize(data, SnapshotUnit.class);
        } catch (DeserializationException e) {
            log.error(e.getMessage(), e);
        }
        return snapshotUnit;
    }

    @Override
    public List<SnapshotUnit> getAllSnapshotUnit() {
        List<SnapshotUnit> snapshotUnits = new ArrayList<>();
        List<byte[]> datas = snapshotSource.prefixValueLookup(new byte[]{SNAPTSHOT_UNIT});
        for (byte[] data : datas) {
            SnapshotUnit snapshotUnit;
            try {
                snapshotUnit = (SnapshotUnit) deserialize(data, SnapshotUnit.class);
                snapshotUnits.add(snapshotUnit);
            } catch (DeserializationException e) {
                log.error(e.getMessage(), e);
            }
        }
        return snapshotUnits;
    }

    public List<StatsBlock> getSnapshotStatsBlock() {
        List<StatsBlock> statsBlocks = new ArrayList<>();
        return statsBlocks;
    }

    public void saveSnaptshotStatsBlock(int i, StatsBlock statsBlock) {
        byte[] value = null;
        try {
            value = serialize(statsBlock);
        } catch (SerializationException e) {
            log.error(e.getMessage(), e);
        }
        snapshotSource.put(BytesUtils.merge(SNAPTSHOT_STATS, BytesUtils.intToBytes(i, false)), value);
    }

    public StatsBlock getStatsBlockByIndex(int i) {
        StatsBlock statsBlock = null;
        byte[] data = snapshotSource.get(BytesUtils.merge(SNAPTSHOT_STATS, BytesUtils.intToBytes(i, false)));
        if (data == null) {
            return null;
        }
        try {
            statsBlock = (StatsBlock) deserialize(data, StatsBlock.class);
        } catch (DeserializationException e) {
            log.error(e.getMessage(), e);
        }
        return statsBlock;
    }

    public StatsBlock getLatestStatsBlock() {
        return getStatsBlockByIndex(0);
    }

}
