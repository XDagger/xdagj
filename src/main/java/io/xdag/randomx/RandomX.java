/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2020-2030 The XdagJ Developers
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package io.xdag.randomx;

import io.xdag.config.Config;
import io.xdag.config.MainnetConfig;
import io.xdag.core.Block;
import io.xdag.core.Blockchain;
import io.xdag.utils.FastByteComparisons;
import io.xdag.utils.XdagTime;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.util.Arrays;
import org.bouncycastle.util.encoders.Hex;

import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static io.xdag.config.RandomXConstants.*;
import static io.xdag.crypto.jni.RandomX.*;


@Slf4j
@Data
public class RandomX {

    protected final RandomXMemory[] globalMemory = new RandomXMemory[2];
    protected final ReadWriteLock[] globalMemoryLock = new ReentrantReadWriteLock[2];

    protected boolean isTestNet = true;
    protected int mineType;
    protected long randomXForkSeedHeight;
    protected long randomXForkLag;

    // 默认为最大值
    protected long randomXForkTime = Long.MAX_VALUE;

    protected long randomXPoolMemIndex;
    protected long randomXHashEpochIndex;

    protected Blockchain blockchain;

    protected boolean is_full_mem;
    protected boolean is_Large_pages;

    public RandomX(Config config) {
        if (config instanceof MainnetConfig) {
            isTestNet = false;
        }
        this.mineType = XDAG_RANDOMX;
    }

    public void setBlockchain(Blockchain blockchain) {
        this.blockchain = blockchain;
    }

    // 外部使用
    public boolean isRandomxFork(long epoch) {
        return mineType == XDAG_RANDOMX && epoch > randomXForkTime;
    }

    // 外部使用
    public void randomXSetForkTime(Block block) {
        long seedEpoch = isTestNet ? SEEDHASH_EPOCH_TESTNET_BLOCKS:SEEDHASH_EPOCH_BLOCKS;
        seedEpoch -= 1;
        if (block.getInfo().getHeight() >= randomXForkSeedHeight) {
            long nextMemIndex = randomXHashEpochIndex + 1;
            RandomXMemory nextMemory = globalMemory[(int) (nextMemIndex) & 1];
            if (block.getInfo().getHeight() == randomXForkSeedHeight) {
                randomXForkTime = XdagTime.getEpoch(block.getTimestamp()) + randomXForkLag;
                log.debug("From block height:{}, time:{}, set fork time to:{}",block.getInfo().getHeight(),block.getTimestamp(), randomXForkTime);
            }

            byte[] hashlow;
            if ( (block.getInfo().getHeight() & seedEpoch) == 0) {
                nextMemory.switchTime = XdagTime.getEpoch(block.getTimestamp()) + randomXForkLag + 1;
                nextMemory.seedTime = block.getTimestamp();
                nextMemory.seedHeight = block.getInfo().getHeight();
                log.debug("Set switch time to {}", Long.toHexString(nextMemory.switchTime));

                hashlow = blockchain.getBlockByHeight(block.getInfo().getHeight() - randomXForkLag).getInfo().getHashlow().clone();
                if (nextMemory.seed == null || !FastByteComparisons.equalBytes(nextMemory.seed,hashlow)) {
                    nextMemory.seed = Arrays.reverse(hashlow);
                    log.debug("Next Memory Seed:{}",Hex.toHexString(hashlow));
                    randomXPoolUpdateSeed(nextMemIndex);
                }
                randomXHashEpochIndex = nextMemIndex;
                nextMemory.isSwitched = 0;
            }
        }
    }

    // 外部使用
    public void randomXUnsetForkTime(Block block) {
        long seedEpoch = isTestNet ? SEEDHASH_EPOCH_TESTNET_BLOCKS:SEEDHASH_EPOCH_BLOCKS;
        seedEpoch -= 1;
        if (block.getInfo().getHeight() >= randomXForkSeedHeight) {
            if (block.getInfo().getHeight() == randomXForkSeedHeight ){
                randomXForkTime = -1;
            }
            if ((block.getInfo().getHeight() & seedEpoch) == 0) {
                RandomXMemory memory = globalMemory[(int) (randomXHashEpochIndex & 1)];
                randomXHashEpochIndex -= 1;
                memory.seedTime = -1;
                memory.seedHeight = -1;
                memory.switchTime = -1;
                memory.isSwitched = -1;
            }
        }
    }


    // 系统初始化时使用
    // 钱包 type为fast 矿池为 light
    public void init() {
        if (isTestNet) {
            randomXForkSeedHeight = RANDOMX_TESTNET_FORK_HEIGHT;
            randomXForkLag = SEEDHASH_EPOCH_TESTNET_LAG;
        } else {
            randomXForkSeedHeight = RANDOMX_FORK_HEIGHT;
            randomXForkLag = SEEDHASH_EPOCH_LAG;
        }

        long seedEpoch = isTestNet ? SEEDHASH_EPOCH_TESTNET_BLOCKS : SEEDHASH_EPOCH_BLOCKS;
        if ((randomXForkSeedHeight & (seedEpoch -1)) != 0) {
            // TODO:
            return;
        }

        // init memory and lock
        for (int i = 0 ; i < 2; i++) {
            globalMemoryLock[i] = new ReentrantReadWriteLock();
            globalMemory[i] = new RandomXMemory();
        }
    }


    // 矿池初始化dataset
    public void randomXPoolInitDataset(long rxCache, long rxDataset, int threadsNum) {
        initDataSet(rxCache, rxDataset, threadsNum);
    }


    // 计算出hash
    public byte[] randomXPoolCalcHash(byte[] data, int dataSize, long taskTime) {
        byte[] hash;
        RandomXMemory memory = globalMemory[(int) (randomXPoolMemIndex)&1];
        ReadWriteLock readWriteLock;
        if (taskTime < memory.switchTime) {
            readWriteLock = globalMemoryLock[(int) (randomXPoolMemIndex-1) & 1];
            memory = globalMemory[(int) (randomXPoolMemIndex-1) & 1];
        } else {
            readWriteLock = globalMemoryLock[(int) (randomXPoolMemIndex) & 1];
        }

        readWriteLock.writeLock().lock();
        try {
            hash = calculateHash(memory.poolVm, data, dataSize);
        } finally {
            readWriteLock.writeLock().unlock();
        }

        return hash;
    }


    public byte[] randomXBlockHash(byte[] data, int dataSize, long blockTime) {
        byte[] hash;
        ReadWriteLock readWriteLock;
        RandomXMemory memory;
        // no seed
        if (randomXHashEpochIndex == 0) {
            return null;
        } else if (randomXHashEpochIndex == 1) { // first seed
            memory = globalMemory[(int) (randomXHashEpochIndex) & 1];
            if (blockTime < memory.switchTime) {
                // block time less then switchtime
                log.debug("Block time {} less then switchtime {}" , Long.toHexString(blockTime), Long.toHexString(memory.switchTime));
                return null;
            } else {
                readWriteLock = globalMemoryLock[(int) (randomXHashEpochIndex) & 1];
            }
        }else {
            memory = globalMemory[(int) (randomXHashEpochIndex) & 1];
            if (blockTime < memory.switchTime) {
                readWriteLock = globalMemoryLock[(int) (randomXHashEpochIndex-1) & 1];
                memory = globalMemory[(int) (randomXHashEpochIndex-1) & 1];
            } else {
                readWriteLock = globalMemoryLock[(int) (randomXHashEpochIndex) & 1];
            }
        }


        readWriteLock.writeLock().lock();
        try{
            log.debug("Use seed {}",Hex.toHexString(Arrays.reverse(memory.seed)));
            hash = calculateHash(memory.blockVm, data, dataSize);
        } finally {
            readWriteLock.writeLock().unlock();
        }


        return hash;
    }


    public long randomXUpdateVm(RandomXMemory randomXMemory, boolean isPoolVm) {
        if (isPoolVm) {
            randomXMemory.poolVm = createVm(randomXMemory.rxCache, randomXMemory.rxDataset, 4);
            return randomXMemory.poolVm;
        } else {
            randomXMemory.blockVm = createVm(randomXMemory.rxCache, randomXMemory.rxDataset, 4);
            return randomXMemory.blockVm;
        }
    }


    public void randomXPoolUpdateSeed(long memIndex) {
        ReadWriteLock readWriteLock = globalMemoryLock[(int) (memIndex) &1];
        readWriteLock.writeLock().lock();
        try {
            RandomXMemory rx_memory = globalMemory[(int) (memIndex) &1];
            if(rx_memory.rxCache == 0) {
                rx_memory.rxCache = allocCache();
                if (rx_memory.rxCache == 0) {
                    // fail alloc
                    log.debug("Failed alloc cache");
                    return;
                }
            }
            // 分配成功
            initCache(rx_memory.rxCache,rx_memory.seed,rx_memory.seed.length);

            if (rx_memory.rxDataset == 0) {
                // 分配dataset
                rx_memory.rxDataset = allocDataSet();
                if (rx_memory.rxDataset == 0) {
                    //分配失败
                    log.debug("Failed alloc dataset");
                    return;
                }
            }

            randomXPoolInitDataset(rx_memory.rxCache, rx_memory.rxDataset, 4);

            if (randomXUpdateVm(rx_memory, true) <= 0) {
                // update failed
                log.debug("Update pool vm failed");
                return;
            }

            // update finished
            if (randomXUpdateVm(rx_memory, false) <= 0) {
                // update failed
                log.debug("Update block vm failed");
            }

            // update finished

        } finally {
            readWriteLock.writeLock().unlock();
        }
    }

    // 释放 ，用于程序关闭时
    public void randomXPoolReleaseMem() {
        for (int i = 0; i < 2; i++ ) {
            globalMemoryLock[i].writeLock().lock();
            try {
                RandomXMemory rx_memory = globalMemory[i];
                if (rx_memory.poolVm != 0) {
                    destroyVm(rx_memory.poolVm);
                }
                if (rx_memory.blockVm != 0) {
                    destroyVm(rx_memory.blockVm);
                }
                if (rx_memory.rxCache != 0) {
                    releaseCache(rx_memory.rxCache);
                }
                if (rx_memory.rxDataset != 0) {
                    releaseDataSet(rx_memory.rxDataset);
                }
            } finally {
                globalMemoryLock[i].writeLock().unlock();
            }
        }
    }

    public void randomXLoadingForkTime() {
        Block block;
        if (blockchain.getXdagStats().nmain >= randomXForkSeedHeight) {
            block = blockchain.getBlockByHeight(randomXForkSeedHeight);
            randomXForkTime = XdagTime.getEpoch(block.getTimestamp()) + randomXForkLag;

            long seedEpoch = isTestNet ? SEEDHASH_EPOCH_TESTNET_BLOCKS : SEEDHASH_EPOCH_BLOCKS;
            seedEpoch -= 1;
            long seedHeight = blockchain.getXdagStats().nmain & ~seedEpoch;
            long preSeedHeight = seedHeight - seedEpoch - 1;

            if (preSeedHeight >= randomXForkSeedHeight) {
                randomXHashEpochIndex = 0;
                randomXPoolMemIndex = -1;

                block = blockchain.getBlockByHeight(preSeedHeight);
                long memoryIndex = randomXHashEpochIndex + 1;
                RandomXMemory memory = globalMemory[(int) (memoryIndex) & 1];
                memory.seed = Arrays.reverse(blockchain.getBlockByHeight(preSeedHeight - randomXForkLag).getInfo().getHashlow().clone());
                memory.switchTime = XdagTime.getEpoch(block.getTimestamp()) + randomXForkLag + 1;
                memory.seedTime = block.getTimestamp();
                memory.seedHeight = block.getInfo().getHeight();

                randomXPoolUpdateSeed(memoryIndex);
                randomXHashEpochIndex = memoryIndex;
                memory.isSwitched = 1;
            }

            if (seedHeight >= randomXForkSeedHeight) {
                block = blockchain.getBlockByHeight(seedHeight);
                long memoryIndex = randomXHashEpochIndex + 1;
                RandomXMemory memory = globalMemory[(int) (memoryIndex) & 1];
                memory.seed = Arrays.reverse(blockchain.getBlockByHeight(seedHeight - randomXForkLag).getInfo().getHashlow().clone());
                memory.switchTime = XdagTime.getEpoch(block.getTimestamp()) + randomXForkLag + 1;
                memory.seedTime =block.getTimestamp();
                memory.seedHeight = block.getInfo().getHeight();

                randomXPoolUpdateSeed(memoryIndex);
                randomXHashEpochIndex = memoryIndex;
//                memory.isSwitched = 0;
                if(XdagTime.getEpoch(blockchain.getBlockByHeight(blockchain.getXdagStats().nmain).getTimestamp()) >= memory.getSwitchTime()) {
                    memory.isSwitched = 1;
                } else {
                    memory.isSwitched = 0;
                }
            }
        }
    }


}
