package io.xdag.core;

import io.xdag.crypto.ECKey;
import io.xdag.utils.ByteArrayWrapper;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public interface Blockchain {

    /**连接一个新区块*/
    ImportResult tryToConnect(Block block);

    Block createNewBlock(Map<Address, ECKey> pairs, List<Address> to, boolean mining);

    Block getBlockByHash(byte[] hash,boolean isRaw);

    BigInteger getTopDiff();

    BigInteger getPretopDiff();

    boolean hasBlock(byte[] hash);

    byte[] getTop_main_chain();

    long getMainBlockSize();
    long getBlockSize();
    long getOrphanSize();
    long getExtraSize();

    List<Block> getBlockByTime(long starttime, long endtime);

    void checkNewMain();

    List<Block> listMainBlocks(int count);
    List<Block> listMinedBlocks(int count);

    List<byte[]> getAllAccount();
    Map<ByteArrayWrapper,Integer> getMemAccount();

    ReentrantReadWriteLock getStateLock();

    Block getExtraBlock(byte[] hash);
}
