package io.xdag.snapshot.db;

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

    void saveSnaptshotStatsBlock(int i, StatsBlock statsBlock);

    StatsBlock getStatsBlockByIndex(int i);

}
