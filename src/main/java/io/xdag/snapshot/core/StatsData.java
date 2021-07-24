package io.xdag.snapshot.core;

import java.math.BigInteger;
import java.nio.ByteOrder;
import org.apache.tuweni.bytes.Bytes;

public class StatsData {

    public BigInteger difficulty; //16
    public BigInteger maxdifficulty; //16
    public long nblocks; //8
    public long totalnblocks; //8
    public long nmain; //8
    public long totalnmain; //8
    public int nhosts; //4
    public int totalnhosts; //4
    public long reverse; //8

    public StatsData() {
    }

    public StatsData(BigInteger difficulty, BigInteger maxdifficulty, long nblocks, long totalnblocks,
            long nmain, long totalnmain, int nhosts, int totalnhosts, long reverse) {
        this.difficulty = difficulty;
        this.maxdifficulty = maxdifficulty;
        this.nblocks = nblocks;
        this.totalnblocks = totalnblocks;
        this.nmain = nmain;
        this.totalnmain = totalnmain;
        this.nhosts = nhosts;
        this.totalnhosts = totalnhosts;
        this.reverse = reverse;
    }

    public static StatsData parse(Bytes data) {
        BigInteger diff = data.slice(0, 16).toUnsignedBigInteger(ByteOrder.LITTLE_ENDIAN);
        BigInteger maxDiff = data.slice(16, 16).toUnsignedBigInteger(ByteOrder.LITTLE_ENDIAN);

        long nblocks = data.getLong(32, ByteOrder.LITTLE_ENDIAN);
        long totalNblocks = data.getLong(40, ByteOrder.LITTLE_ENDIAN);
        long nmain = data.getLong(48, ByteOrder.LITTLE_ENDIAN);
        long totalNmain = data.getLong(56, ByteOrder.LITTLE_ENDIAN);
        int nhosts = data.getInt(64, ByteOrder.LITTLE_ENDIAN);
        int totalNhosts = data.getInt(68, ByteOrder.LITTLE_ENDIAN);
        long reverse = data.getLong(72, ByteOrder.LITTLE_ENDIAN);
        return new StatsData(diff, maxDiff, nblocks, totalNblocks, nmain, totalNmain, nhosts, totalNhosts, reverse);
    }

    @Override
    public String toString() {
        return "StatsData{" +
                "difficulty=" + difficulty +
                ", maxdifficulty=" + maxdifficulty +
                ", nblocks=" + nblocks +
                ", totalnblocks=" + totalnblocks +
                ", nmain=" + nmain +
                ", totalnmain=" + totalnmain +
                ", nhosts=" + nhosts +
                ", totalnhosts=" + totalnhosts +
                ", reverse=" + reverse +
                '}';
    }
}
