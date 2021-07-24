package io.xdag.snapshot.core;

import static io.xdag.utils.BasicUtils.hash2Address;

import java.math.BigInteger;
import java.nio.ByteOrder;
import lombok.Data;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.bouncycastle.util.Arrays;

@Data
public class StatsBlock {

    protected long height;
    protected long time;
    protected byte[] hash;
    protected BigInteger difficulty;

    public StatsBlock() {

    }

    public StatsBlock(long height, long time, byte[] hash, BigInteger difficulty) {
        this.height = height;
        this.time = time;
        this.hash = Arrays.reverse(hash);
        this.difficulty = difficulty;
    }

    public static StatsBlock parse(Bytes key, Bytes value) {
        Bytes uncompressed = value;
        long height = uncompressed.getLong(0, ByteOrder.LITTLE_ENDIAN);
        long time = uncompressed.getLong(8, ByteOrder.LITTLE_ENDIAN);
        Bytes32 hash = Bytes32.wrap(uncompressed.slice(16, 32));
        BigInteger diff = uncompressed.slice(48, 16).toUnsignedBigInteger(ByteOrder.LITTLE_ENDIAN);
        return new StatsBlock(height, time, hash.toArray(), diff);
    }


    @Override
    public String toString() {
        return "StatsBlock{" +
                "height=" + height +
                ", time=" + time +
                ", hash=" + hash2Address(hash) +
                ", difficulty=" + difficulty.toString(16) +
                '}';
    }
}
