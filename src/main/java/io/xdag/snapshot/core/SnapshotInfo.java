package io.xdag.snapshot.core;

import lombok.Data;

@Data
public class SnapshotInfo {

    protected Type type;
    protected byte[] data;// 区块数据 / pubkey

    public SnapshotInfo(Type type, byte[] data) {
        this.type = type;
        this.data = data;

    }

    public enum Type {
        PUBKEY,
        BLOCK_DATA
    }
}
