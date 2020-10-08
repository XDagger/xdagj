package io.xdag.core;

import com.google.common.primitives.UnsignedLong;
import lombok.Getter;
import lombok.Setter;

import java.math.BigInteger;

@Getter
@Setter
public class BlockInfo {
    private long height;
    private byte[] hash;
    private byte[] hashlow;
    private long amount;
    public long type;
    private BigInteger difficulty;
    private byte[] ref;
    private byte[] maxDiffLink;
    public int flags;
    private long fee;
    private long timestamp;
}
