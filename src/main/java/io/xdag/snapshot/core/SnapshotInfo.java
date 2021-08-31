package io.xdag.snapshot.core;

import lombok.Data;

@Data
public class SnapshotInfo {

    protected boolean type; // true PUBKEY false BLOCK_DATA
    protected byte[] data;// 区块数据 / pubkey

    public SnapshotInfo() {

    }

    public SnapshotInfo(boolean type, byte[] data) {
        this.type = type;
        this.data = data;
    }

    public boolean getType() {
        return type;
    }
}
