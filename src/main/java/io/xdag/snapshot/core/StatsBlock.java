package io.xdag.snapshot.core;

import com.google.common.primitives.UnsignedLong;
import java.math.BigInteger;
import java.util.Arrays;
import lombok.Data;
import org.apache.tuweni.bytes.Bytes32;

@Data
public class StatsBlock {

    protected long height;
    protected long time;
    protected byte[] hash;
    protected BigInteger difficulty;

    public StatsBlock() {

    }

    public StatsBlock(long height, UnsignedLong time, Bytes32 hash, BigInteger difficulty) {
        this.height = height;
        this.time = time.longValue();
        this.hash = hash.toArray();
        this.difficulty = difficulty;
    }

    @Override
    public String toString() {
        return "StatsBlock{" +
                "height=" + height +
                ", time=" + time +
                ", hash=" + Arrays.toString(hash) +
                ", difficulty=" + difficulty +
                '}';
    }
}
