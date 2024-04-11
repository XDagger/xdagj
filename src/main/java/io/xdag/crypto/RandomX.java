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

package io.xdag.crypto;

import static io.xdag.config.RandomXConstants.RANDOMX_FORK_HEIGHT;
import static io.xdag.config.RandomXConstants.RANDOMX_TESTNET_FORK_HEIGHT;
import static io.xdag.config.RandomXConstants.SEEDHASH_EPOCH_BLOCKS;
import static io.xdag.config.RandomXConstants.SEEDHASH_EPOCH_LAG;
import static io.xdag.config.RandomXConstants.SEEDHASH_EPOCH_TESTNET_BLOCKS;
import static io.xdag.config.RandomXConstants.SEEDHASH_EPOCH_TESTNET_LAG;
import static io.xdag.config.RandomXConstants.XDAG_RANDOMX;
import static io.xdag.utils.BytesUtils.bytesToPointer;
import static io.xdag.utils.BytesUtils.equalBytes;

import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.bouncycastle.util.Arrays;
import org.bouncycastle.util.encoders.Hex;

import com.sun.jna.Memory;
import com.sun.jna.NativeLong;
import com.sun.jna.Pointer;

import io.xdag.config.Config;
import io.xdag.config.MainnetConfig;
import io.xdag.core.Block;
import io.xdag.core.Blockchain;
import io.xdag.crypto.randomx.NativeSize;
import io.xdag.crypto.randomx.RandomXFlag;
import io.xdag.crypto.randomx.RandomXJNA;
import io.xdag.crypto.randomx.RandomXUtils;
import io.xdag.utils.XdagTime;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;


@Slf4j
@Data
public class RandomX {

    protected final RandomXMemory[] globalMemory = new RandomXMemory[2];
    protected final ReadWriteLock[] globalMemoryLock = new ReentrantReadWriteLock[2];
    protected final Config config;
    protected boolean isTestNet = true;
    protected int mineType;
    protected int flags;
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
        this.config = config;
        if (config instanceof MainnetConfig) {
            isTestNet = false;
        }
        this.mineType = XDAG_RANDOMX;
        // get randomx flags
        if (config.getRandomxSpec().getRandomxFlag()) {
            flags = RandomXJNA.INSTANCE.randomx_get_flags() + RandomXFlag.LARGE_PAGES.getValue() + RandomXFlag.FULL_MEM.getValue();
        } else {
            flags = RandomXJNA.INSTANCE.randomx_get_flags();
        }
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
        long seedEpoch = isTestNet ? SEEDHASH_EPOCH_TESTNET_BLOCKS : SEEDHASH_EPOCH_BLOCKS;
        seedEpoch -= 1;
        if (block.getInfo().getHeight() >= randomXForkSeedHeight) {
            long nextMemIndex = randomXHashEpochIndex + 1;
            RandomXMemory nextMemory = globalMemory[(int) (nextMemIndex) & 1];
            if (block.getInfo().getHeight() == randomXForkSeedHeight) {
                randomXForkTime = XdagTime.getEpoch(block.getTimestamp()) + randomXForkLag;
                log.debug("From block height:{}, time:{}, set fork time to:{}", block.getInfo().getHeight(),
                        block.getTimestamp(), randomXForkTime);
            }

            byte[] hashlow;
            if ((block.getInfo().getHeight() & seedEpoch) == 0) {
                nextMemory.switchTime = XdagTime.getEpoch(block.getTimestamp()) + randomXForkLag + 1;
                nextMemory.seedTime = block.getTimestamp();
                nextMemory.seedHeight = block.getInfo().getHeight();
                log.debug("Set switch time to {}", Long.toHexString(nextMemory.switchTime));

                hashlow = blockchain.getBlockByHeight(block.getInfo().getHeight() - randomXForkLag).getInfo()
                        .getHashlow();
                if (nextMemory.seed == null || !equalBytes(nextMemory.seed, hashlow)) {
                    nextMemory.seed = Arrays.reverse(hashlow);
                    log.debug("Next Memory Seed:{}", Hex.toHexString(hashlow));
                    randomXPoolUpdateSeed(nextMemIndex);
                }
                randomXHashEpochIndex = nextMemIndex;
                nextMemory.isSwitched = 0;
            }
        }
    }

    // 外部使用
    public void randomXUnsetForkTime(Block block) {
        long seedEpoch = isTestNet ? SEEDHASH_EPOCH_TESTNET_BLOCKS : SEEDHASH_EPOCH_BLOCKS;
        seedEpoch -= 1;
        if (block.getInfo().getHeight() >= randomXForkSeedHeight) {
            if (block.getInfo().getHeight() == randomXForkSeedHeight) {
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
        if ((randomXForkSeedHeight & (seedEpoch - 1)) != 0) {
            // TODO:
            return;
        }

        // init memory and lock
        for (int i = 0; i < 2; i++) {
            globalMemoryLock[i] = new ReentrantReadWriteLock();
            globalMemory[i] = new RandomXMemory();
        }
    }


    // 矿池初始化dataset
    public void randomXPoolInitDataset(Pointer rxCache, Pointer rxDataset) {
        RandomXJNA.INSTANCE.randomx_init_dataset(rxDataset, rxCache, new NativeLong(0), RandomXJNA.INSTANCE.randomx_dataset_item_count());
    }


    // 计算出hash
    public Bytes32 randomXPoolCalcHash(Bytes data, int dataSize, long taskTime) {
        Bytes32 hash;
        RandomXMemory memory = globalMemory[(int) (randomXPoolMemIndex) & 1];
        ReadWriteLock readWriteLock;
        if (taskTime < memory.switchTime) {
            readWriteLock = globalMemoryLock[(int) (randomXPoolMemIndex - 1) & 1];
            memory = globalMemory[(int) (randomXPoolMemIndex - 1) & 1];
        } else {
            readWriteLock = globalMemoryLock[(int) (randomXPoolMemIndex) & 1];
        }

        readWriteLock.writeLock().lock();
        try {
            Pointer hashPointer = new Memory(RandomXUtils.HASH_SIZE);
            RandomXJNA.INSTANCE.randomx_calculate_hash(memory.poolVm, bytesToPointer(data.toArray()), new NativeSize(dataSize), hashPointer);
            hash = Bytes32.wrap(hashPointer.getByteArray(0, RandomXUtils.HASH_SIZE));
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
                log.debug("Block time {} less then switchtime {}", Long.toHexString(blockTime),
                        Long.toHexString(memory.switchTime));
                return null;
            } else {
                readWriteLock = globalMemoryLock[(int) (randomXHashEpochIndex) & 1];
            }
        } else {
            memory = globalMemory[(int) (randomXHashEpochIndex) & 1];
            if (blockTime < memory.switchTime) {
                readWriteLock = globalMemoryLock[(int) (randomXHashEpochIndex - 1) & 1];
                memory = globalMemory[(int) (randomXHashEpochIndex - 1) & 1];
            } else {
                readWriteLock = globalMemoryLock[(int) (randomXHashEpochIndex) & 1];
            }
        }

        readWriteLock.writeLock().lock();
        try {
            log.debug("Use seed {}", Hex.toHexString(Arrays.reverse(memory.seed)));
            Pointer hashPointer = new Memory(RandomXUtils.HASH_SIZE);
            RandomXJNA.INSTANCE.randomx_calculate_hash(memory.blockVm, bytesToPointer(data), new NativeSize(dataSize), hashPointer);
            hash = hashPointer.getByteArray(0, RandomXUtils.HASH_SIZE);
        } finally {
            readWriteLock.writeLock().unlock();
        }

        return hash;
    }


    public Pointer randomXUpdateVm(RandomXMemory randomXMemory, boolean isPoolVm) {
        if (isPoolVm) {
            randomXMemory.poolVm = RandomXJNA.INSTANCE.randomx_create_vm(flags, randomXMemory.rxCache, randomXMemory.rxDataset);
            return randomXMemory.poolVm;
        } else {
            randomXMemory.blockVm = RandomXJNA.INSTANCE.randomx_create_vm(flags, randomXMemory.rxCache, randomXMemory.rxDataset);
            return randomXMemory.blockVm;
        }
    }


    public void randomXPoolUpdateSeed(long memIndex) {
        ReadWriteLock readWriteLock = globalMemoryLock[(int) (memIndex) & 1];
        readWriteLock.writeLock().lock();
        try {
            RandomXMemory rx_memory = globalMemory[(int) (memIndex) & 1];
            if (rx_memory.rxCache == null) {
                rx_memory.rxCache = RandomXJNA.INSTANCE.randomx_alloc_cache(flags);
                if (rx_memory.rxCache == null) {
                    // fail alloc
                    log.debug("Failed alloc cache");
                    return;
                }
            }
            // 分配成功
            RandomXJNA.INSTANCE.randomx_init_cache(rx_memory.rxCache, bytesToPointer(rx_memory.seed), new NativeSize(rx_memory.seed.length));

            if (config.getRandomxSpec().getRandomxFlag()) {
                if (rx_memory.rxDataset == null) {
                    // 分配dataset
                    rx_memory.rxDataset = RandomXJNA.INSTANCE.randomx_alloc_dataset(flags);
                    if (rx_memory.rxDataset == null) {
                        //分配失败
                        log.debug("Failed alloc dataset");
                        return;
                    }
                }

                randomXPoolInitDataset(rx_memory.rxCache, rx_memory.rxDataset);
            } else {
                rx_memory.rxDataset = null;
            }


            if (randomXUpdateVm(rx_memory, true) == null) {
                // update failed
                log.debug("Update pool vm failed");
                return;
            }

            // update finished
            if (randomXUpdateVm(rx_memory, false) == null) {
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
        for (int i = 0; i < 2; i++) {
            globalMemoryLock[i].writeLock().lock();
            try {
                RandomXMemory rx_memory = globalMemory[i];
                if (rx_memory.poolVm != null) {
                    RandomXJNA.INSTANCE.randomx_destroy_vm(rx_memory.poolVm);
                }
                if (rx_memory.blockVm != null) {
                    RandomXJNA.INSTANCE.randomx_destroy_vm(rx_memory.blockVm);
                }
                if (rx_memory.rxCache != null) {
                    RandomXJNA.INSTANCE.randomx_release_cache(rx_memory.rxCache);
                }
                if (rx_memory.rxDataset != null) {
                    RandomXJNA.INSTANCE.randomx_release_dataset(rx_memory.rxDataset);
                }
            } finally {
                globalMemoryLock[i].writeLock().unlock();
            }
        }
    }

    public void randomXLoadingSnapshot(byte[] preseed, long forkTime) {
        // TODO:
        long firstMemIndex = randomXHashEpochIndex + 1;
        randomXPoolMemIndex = -1;
        RandomXMemory firstMemory = globalMemory[(int) (firstMemIndex) & 1];
        firstMemory.seed = preseed;
        randomXPoolUpdateSeed(firstMemIndex);
        randomXHashEpochIndex = firstMemIndex;
        firstMemory.isSwitched = 0;

        long lag = isTestNet ? SEEDHASH_EPOCH_TESTNET_LAG : SEEDHASH_EPOCH_LAG;
        randomXForkTime = XdagTime
                .getEpoch(
                        blockchain.getBlockByHeight(config.getSnapshotSpec().getSnapshotHeight() - lag).getTimestamp());
        Block block;
        for (long i = lag; i >= 0; i--) {
            block = blockchain.getBlockByHeight(config.getSnapshotSpec().getSnapshotHeight() - i);
            if (block == null) {
                continue;
            }
            randomXSetForkTime(block);
        }
    }

    public void randomXLoadingForkTimeSnapshot(byte[] preseed, long forkTime) {
        // 如果快照在还没切到下一个seed更换周期时就重启，那么还是第一个seed是初始的preseed
        if (blockchain.getXdagStats().nmain < config.getSnapshotSpec().getSnapshotHeight() + (isTestNet
                ? SEEDHASH_EPOCH_TESTNET_BLOCKS : SEEDHASH_EPOCH_BLOCKS)) {
            randomXLoadingSnapshot(preseed, forkTime);
        } else {
            this.randomXLoadingSnapshot();
        }
    }

    public void randomXLoadingSnapshot(){
        Block block;
        long seedEpoch = isTestNet ? SEEDHASH_EPOCH_TESTNET_BLOCKS : SEEDHASH_EPOCH_BLOCKS;
        if (blockchain.getXdagStats().nmain >= config.getSnapshotSpec().getSnapshotHeight()) {
            block = blockchain.getBlockByHeight(
                    config.getSnapshotSpec().getSnapshotHeight());
            randomXForkTime = XdagTime.getEpoch(block.getTimestamp()) + randomXForkLag;

            seedEpoch -= 1;
            long seedHeight = blockchain.getXdagStats().nmain & ~seedEpoch;
            long preSeedHeight = seedHeight - seedEpoch - 1;

            if (preSeedHeight >= randomXForkSeedHeight) {
                randomXHashEpochIndex = 0;
                randomXPoolMemIndex = -1;

                block = blockchain.getBlockByHeight(preSeedHeight);
                long memoryIndex = randomXHashEpochIndex + 1;
                RandomXMemory memory = globalMemory[(int) (memoryIndex) & 1];
                memory.seed = Arrays
                        .reverse(
                                blockchain.getBlockByHeight(preSeedHeight - randomXForkLag).getInfo().getHashlow());
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
                memory.seed = Arrays
                        .reverse(blockchain.getBlockByHeight(seedHeight - randomXForkLag).getInfo().getHashlow());
                memory.switchTime = XdagTime.getEpoch(block.getTimestamp()) + randomXForkLag + 1;
                memory.seedTime = block.getTimestamp();
                memory.seedHeight = block.getInfo().getHeight();

                randomXPoolUpdateSeed(memoryIndex);
                randomXHashEpochIndex = memoryIndex;
//                memory.isSwitched = 0;
                if (XdagTime.getEpoch(blockchain.getBlockByHeight(blockchain.getXdagStats().nmain).getTimestamp())
                        >= memory.getSwitchTime()) {
                    memory.isSwitched = 1;
                } else {
                    memory.isSwitched = 0;
                }
            }
        }
    }

    public void randomXLoadingSnapshotJ(){
        Block block;
        long seedEpoch = isTestNet ? SEEDHASH_EPOCH_TESTNET_BLOCKS : SEEDHASH_EPOCH_BLOCKS;
        if (blockchain.getXdagStats().nmain >= config.getSnapshotSpec().getSnapshotHeight()) {
            if(config.getSnapshotSpec().getSnapshotHeight()>RANDOMX_FORK_HEIGHT) {
                block = blockchain.getBlockByHeight(
                        config.getSnapshotSpec().getSnapshotHeight() - config.getSnapshotSpec().getSnapshotHeight() % seedEpoch);
                randomXForkTime = XdagTime.getEpoch(block.getTimestamp()) + randomXForkLag;
            }
            seedEpoch -= 1;
            long seedHeight = blockchain.getXdagStats().nmain & ~seedEpoch;
            long preSeedHeight = seedHeight - seedEpoch - 1;

            if (preSeedHeight >= randomXForkSeedHeight) {
                randomXHashEpochIndex = 0;
                randomXPoolMemIndex = -1;

                block = blockchain.getBlockByHeight(preSeedHeight);
                long memoryIndex = randomXHashEpochIndex + 1;
                RandomXMemory memory = globalMemory[(int) (memoryIndex) & 1];
                memory.seed = Arrays
                        .reverse(
                                blockchain.getBlockByHeight(preSeedHeight - randomXForkLag).getInfo().getHashlow());
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
                memory.seed = Arrays
                        .reverse(blockchain.getBlockByHeight(seedHeight - randomXForkLag).getInfo().getHashlow());
                memory.switchTime = XdagTime.getEpoch(block.getTimestamp()) + randomXForkLag + 1;
                memory.seedTime = block.getTimestamp();
                memory.seedHeight = block.getInfo().getHeight();

                randomXPoolUpdateSeed(memoryIndex);
                randomXHashEpochIndex = memoryIndex;
//                memory.isSwitched = 0;
                if (XdagTime.getEpoch(blockchain.getBlockByHeight(blockchain.getXdagStats().nmain).getTimestamp())
                        >= memory.getSwitchTime()) {
                    memory.isSwitched = 1;
                } else {
                    memory.isSwitched = 0;
                }
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
                memory.seed = Arrays
                        .reverse(blockchain.getBlockByHeight(preSeedHeight - randomXForkLag).getInfo().getHashlow());
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
                memory.seed = Arrays
                        .reverse(blockchain.getBlockByHeight(seedHeight - randomXForkLag).getInfo().getHashlow());
                memory.switchTime = XdagTime.getEpoch(block.getTimestamp()) + randomXForkLag + 1;
                memory.seedTime = block.getTimestamp();
                memory.seedHeight = block.getInfo().getHeight();

                randomXPoolUpdateSeed(memoryIndex);
                randomXHashEpochIndex = memoryIndex;
//                memory.isSwitched = 0;
                if (XdagTime.getEpoch(blockchain.getBlockByHeight(blockchain.getXdagStats().nmain).getTimestamp())
                        >= memory.getSwitchTime()) {
                    memory.isSwitched = 1;
                } else {
                    memory.isSwitched = 0;
                }
            }
        }
    }


}
