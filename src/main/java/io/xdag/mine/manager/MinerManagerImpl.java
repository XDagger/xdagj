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

package io.xdag.mine.manager;

import io.xdag.Kernel;
import io.xdag.consensus.PoW;
import io.xdag.consensus.Task;
import io.xdag.mine.MinerChannel;
import io.xdag.mine.miner.Miner;
import io.xdag.mine.miner.MinerStates;
import io.xdag.net.message.Message;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.concurrent.BasicThreadFactory;
import org.apache.tuweni.bytes.Bytes;

@Slf4j
public class MinerManagerImpl implements MinerManager, Runnable {

    /**
     * 存放任务的阻塞队列
     */
    private final BlockingQueue<Task> taskQueue = new LinkedBlockingQueue<>(100);
    private final Kernel kernel;
    private final ScheduledExecutorService scheduledExecutor = new ScheduledThreadPoolExecutor(3, new BasicThreadFactory.Builder()
            .namingPattern("MinerManager-Scheduled-Thread-%d")
            .daemon(true)
            .build());

    private final ExecutorService mainExecutor = Executors.newSingleThreadExecutor(new BasicThreadFactory.Builder()
            .namingPattern("MinerManager-Main-Thread-%d")
            .daemon(true)
            .build());

    private final ExecutorService workerExecutor = Executors.newCachedThreadPool(new BasicThreadFactory.Builder()
            .namingPattern("MinerManager-Worker-Thread-%d")
            .daemon(true)
            .build());

    private volatile boolean isRunning = false;
    /**
     * 保存活跃的channel
     */
    protected Map<InetSocketAddress, MinerChannel> activateMinerChannels = new ConcurrentHashMap<>();
    /**
     * 根据miner的地址保存的数组 activate 代表的是一个已经注册的矿工
     */
    protected final Map<Bytes, Miner> activateMiners = new ConcurrentHashMap<>(200);
    private volatile Task currentTask;
    @Setter
    private PoW poW;
    private ScheduledFuture<?> updateFuture;
    private ScheduledFuture<?> cleanChannelFuture;
    private ScheduledFuture<?> cleanMinerFuture;

    public MinerManagerImpl(Kernel kernel) {
        this.kernel = kernel;
    }

    private final Object obj1 = new Object();
    private final Object obj2 = new Object();

    @Override
    public void run() {
        while (isRunning) {
            updateNewTaskandBroadcast();
        }
    }

    @Override
    public void start() {
        isRunning = true;
        init();
        mainExecutor.execute(this);
        log.debug("MinerManager started.");
    }

    @Override
    public void stop() {
        isRunning = false;
        close();
        log.debug("MinerManager closed.");
    }


    /**
     * 启动 函数 开启遍历和server
     */
    public void init() {
        updateFuture = scheduledExecutor.scheduleAtFixedRate(this::updataBalance, 64, 32, TimeUnit.SECONDS);
        cleanChannelFuture = scheduledExecutor.scheduleAtFixedRate(this::cleanUnactivateChannel, 64, 32, TimeUnit.SECONDS);
        cleanMinerFuture = scheduledExecutor.scheduleAtFixedRate(this::cleanUnactivateMiner, 64, 32, TimeUnit.SECONDS);
    }

    private void updataBalance() {
        synchronized (obj1) {
            try {
                activateMinerChannels.values().stream()
                        .filter(MinerChannel::isActive)
                        .forEach(mc -> workerExecutor.submit(mc::sendBalance));
            } catch (Exception e) {
                log.error("An exception occurred in updataBalance: Exception->{}", e.toString());
            }
        }
    }

    @Override
    public void addActivateChannel(MinerChannel channel) {
        log.debug("add a new active channel");
        synchronized (obj1) {
            activateMinerChannels.put(channel.getInetAddress(), channel);
        }
    }


    public void close() {
        if (updateFuture != null) {
            updateFuture.cancel(true);
        }
        if (cleanChannelFuture != null) {
            cleanChannelFuture.cancel(true);
        }
        if (cleanMinerFuture != null) {
            cleanMinerFuture.cancel(true);
        }
        mainExecutor.shutdown();
        scheduledExecutor.shutdown();
        closeMiners();
    }

    private void closeMiners() {
        synchronized (obj1) {
            activateMinerChannels.values().forEach(
                    mc -> workerExecutor.submit(mc::dropConnection)
            );
        }
    }

    @Override
    public void removeUnactivateChannel(MinerChannel channel) {
        if (channel != null && !channel.isActive()) {
            log.debug("remove a channel");
            kernel.getChannelsAccount().getAndDecrement();
            activateMinerChannels.remove(channel.getInetAddress(), channel);

            synchronized (obj2) {
                Miner miner = activateMiners.get(Bytes.of(channel.getAccountAddressHash().toArray()));
                if (miner == null) {
                    return;
                }
                miner.removeChannel(channel.getInetAddress());
                if (miner.getChannels().size() == 0) {
                    log.debug("a mine remark MINER_ARCHIVE，miner Address=[{}] ", miner.getAddressHash().toHexString());
                    miner.setMinerStates(MinerStates.MINER_ARCHIVE);
                }
            }
        }
    }

    public void cleanUnactivateChannel() {
        synchronized (obj1) {
            try {
                activateMinerChannels.values().forEach(
                        mc -> workerExecutor.submit(() -> removeUnactivateChannel(mc))
                );
            } catch (Exception e) {
                log.error("An exception occurred in cleanUnactivateChannel: Exception->{}", e.toString());
            }
        }
    }

    public void cleanUnactivateMiner() {
        synchronized (obj2) {
            try {
                activateMiners.entrySet().removeIf(entry -> entry.getValue().canRemove());
            } catch (Exception e) {
                log.error("An exception occurred in cleanUnactivateMiner: Exception->{}", e.toString());
            }
        }
    }


    @Override
    public void updateTask(Task task) {
        if (!taskQueue.offer(task)) {
            log.debug("Failed to add a task to the queue!");
        }
    }

    @Override
    public void addActiveMiner(Miner miner) {
        synchronized (activateMiners) {
            activateMiners.put(miner.getAddressHash(), miner);
        }
    }

    /**
     * When each round of tasks is just sent out, this will be used to update all miner's status
     */
    public void updateNewTaskandBroadcast() {
        Task task = null;
        try {
            task = taskQueue.poll(1, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            log.error(" can not take the task from taskQueue" + e.getMessage(), e);
        }
        if (task != null) {
            currentTask = task;
            synchronized (obj1) {
                activateMinerChannels.values().stream()
                        .filter(MinerChannel::isActive)
                        .forEach(c -> workerExecutor.submit(() -> {
                            c.setTaskIndex(currentTask.getTaskIndex());
                            c.sendTaskToMiner(currentTask.getTask());
                            c.setSharesCounts(0);
                            log.debug("Send task:{},task time:{},task index:{}, to address: {}",
                                    currentTask.getTask().toString(),currentTask.getTaskIndex(),currentTask.getTaskIndex(),c.getAddressHash());
                        }));
            }
        }
    }

    @Override
    public Map<Bytes, Miner> getActivateMiners() {
        return activateMiners;
    }

    @Override
    public void onNewShare(MinerChannel channel, Message msg) {
        if (currentTask == null) {
            log.info("currentTask is empty");
        } else if (currentTask.getTaskIndex() == channel.getTaskIndex()) {
            poW.receiveNewShare(channel, msg);
        }
    }

    @Override
    public MinerChannel getChannelByHost(InetSocketAddress host) {
        return this.activateMinerChannels.get(host);
    }

    @Override
    public Map<InetSocketAddress, MinerChannel> getActivateMinerChannels() {
        return this.activateMinerChannels;
    }
}
