package io.xdag.core;

import lombok.Getter;
import lombok.Setter;

import java.math.BigInteger;

@Getter
@Setter
public class PreBlockInfo {

    public long type;
    public int flags;
    private long height;
    private BigInteger difficulty;
    private byte[] ref;
    private byte[] maxDiffLink;
    private long fee;
    private byte[] remark;
    private byte[] hash;
    private byte[] hashlow;
    private long amount;
    private long timestamp;

    // snapshot
    private boolean isSnapshot = false;
    private SnapshotInfo snapshotInfo = null;

}
