package io.xdag.pool;

import org.apache.tuweni.bytes.Bytes32;

public interface PoolAwardManager {

    void start();

    void stop();
    void addAwardBlock(Bytes32 share, Bytes32 preHash,Bytes32 hash, long generateTime);

}
