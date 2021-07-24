package io.xdag.snapshot.core;

import static io.xdag.utils.BasicUtils.amount2xdag;
import static io.xdag.utils.BasicUtils.hash2Address;

import io.xdag.utils.XdagTime;
import java.io.IOException;
import java.nio.ByteOrder;
import lombok.Data;
import org.apache.commons.lang3.time.FastDateFormat;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.bouncycastle.util.Arrays;
import org.xerial.snappy.Snappy;

@Data
public class BalanceData {

    protected long amount;
    protected long time;
    // we dont need storage_pos
    protected long storage_pos;
    protected byte[] hash;

    protected int flags;

    public BalanceData() {

    }

    public BalanceData(long amount, long time, byte[] hash, int flags) {
        this.amount = amount;
        this.time = time;
        this.hash = Arrays.reverse(hash);
        this.flags = flags;
    }

    public static BalanceData parse(Bytes key, Bytes value) {
        // 未压缩
        if (key.size() == 32) {
            long flags = value.getLong(0, ByteOrder.LITTLE_ENDIAN);
            long amount = value.getLong(8, ByteOrder.LITTLE_ENDIAN);
            long time = value.getLong(16, ByteOrder.LITTLE_ENDIAN);
            Bytes32 hash = Bytes32.wrap(key.reverse());
            return new BalanceData(amount, time, hash.toArray(), (int) flags);
        } else if (key.size() == 4) {
            // 1. 解压缩
            try {
                Bytes uncompressed = Bytes.wrap(Snappy.uncompress(value.toArray()));
                long flags = uncompressed.getLong(0, ByteOrder.LITTLE_ENDIAN);
                long amount = uncompressed.getLong(8, ByteOrder.LITTLE_ENDIAN);
                long time = uncompressed.getLong(16, ByteOrder.LITTLE_ENDIAN);
                Bytes32 hash = Bytes32.wrap(uncompressed.slice(24));
                return new BalanceData(amount, time, hash.toArray(), (int) flags);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return "BalanceData{" +
                "amount=" + amount2xdag(amount) +
                ", time=" + FastDateFormat.getInstance("yyyy-MM-dd HH:mm:ss.SSS")
                .format(XdagTime.xdagTimestampToMs(time)) +
                ", storage_pos=" + storage_pos +
                ", hash=" + (hash != null ? hash2Address(hash) : "") +
                '}';
    }
}
