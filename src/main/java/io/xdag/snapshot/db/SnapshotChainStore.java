package io.xdag.snapshot.db;

import io.xdag.crypto.ECKeyPair;
import io.xdag.snapshot.core.SnapshotUnit;
import io.xdag.snapshot.core.StatsBlock;
import java.util.List;

public interface SnapshotChainStore {

    void init();

    void reset();

    void saveSnapshotUnit(byte[] key, SnapshotUnit snapshotUnit);

    SnapshotUnit getSnapshotUnit(byte[] key);

    List<SnapshotUnit> getAllSnapshotUnit();

    List<StatsBlock> getSnapshotStatsBlock();

    StatsBlock getLatestStatsBlock();

    byte[] getSnapshotPreSeed();

    void saveSnaptshotStatsBlock(int i, StatsBlock statsBlock);

    void saveGlobalBalance(long balance);

    long getGlobalBalance();

    StatsBlock getStatsBlockByIndex(int i);

    boolean loadFromSnapshotData(String filepath, boolean mainLag, List<ECKeyPair> ecKeyPairs);

}
