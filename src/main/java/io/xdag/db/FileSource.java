package io.xdag.db;

import io.xdag.core.Block;

public interface FileSource {

    void saveBlockSums(Block block);

    byte[] loadSum(long starttime, long endtime);
}
