package io.xdag.snapshot.core;

import io.xdag.core.BlockInfo;
import java.math.BigInteger;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Data
@Slf4j
public class SnapshotUnit {

    protected byte[] pubkey;
    protected BalanceData balanceData;
    // data means block data
    protected byte[] data;
    protected boolean hasPubkey = false;
    protected int type;

    protected byte[] hash;

    public SnapshotUnit() {

    }

    public SnapshotUnit(byte[] pubkey, BalanceData balanceData, byte[] data, byte[] hash) {
        if (pubkey == null && data != null) {
            this.type = 0; //BI_DATA_BALANCE;
        } else if (pubkey != null && balanceData != null) {
            this.type = 1; //Type.BI_PUBKEY_BALANCE;
        } else {
            this.type = 2; //Type.BI_PUBKEY;
        }
        this.pubkey = pubkey;
        this.balanceData = balanceData;
        this.data = data;
        this.hash = hash;
    }

    public static BlockInfo trasferToBlockInfo(SnapshotUnit snapshotUnit) {
        BlockInfo blockInfo = new BlockInfo();
        blockInfo.setDifficulty(BigInteger.ZERO);
        blockInfo.setFlags(snapshotUnit.getBalanceData().flags);
        if (snapshotUnit.type == 2) {
            blockInfo.setAmount(0);
            blockInfo.setTimestamp(0);
        } else {
            blockInfo.setAmount(snapshotUnit.getBalanceData().getAmount());
            blockInfo.setTimestamp(snapshotUnit.getBalanceData().getTime());
        }
        blockInfo.setHash(snapshotUnit.getHash());
        byte[] hashLow = new byte[32];
        System.arraycopy(snapshotUnit.getHash(), 8, hashLow, 8, 24);
        blockInfo.setHashlow(hashLow);
        blockInfo.setSnapshot(true);
        if (snapshotUnit.type == 0) {
            blockInfo.setSnapshotInfo(new SnapshotInfo(false, snapshotUnit.getData()));
        } else {
            blockInfo.setSnapshotInfo(new SnapshotInfo(true, snapshotUnit.getPubkey()));
        }
        return blockInfo;
    }

    public boolean hasPubkey() {
        return hasPubkey;
    }

    public boolean hasData() {
        return data != null;
    }


}
