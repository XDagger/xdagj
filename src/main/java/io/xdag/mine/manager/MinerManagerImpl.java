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
import io.xdag.utils.ByteArrayWrapper;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.concurrent.BasicThreadFactory;
import org.bouncycastle.util.encoders.Hex;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.*;

@Slf4j
public class MinerManagerImpl implements MinerManager, Runnable {
    /** 保存活跃的channel */
    protected Map<InetSocketAddress, MinerChannel> activateMinerChannels = new ConcurrentHashMap<>();

    /** 根据miner的地址保存的数组 activate 代表的是一个已经注册的矿工 */
    protected Map<ByteArrayWrapper, Miner> activateMiners = new ConcurrentHashMap<>(200);

    private Task currentTask;

    /**
     * 存放任务的阻塞队列
     */
    private final BlockingQueue<Task> taskQueue = new LinkedBlockingQueue<>();
    private Thread t;


    @Setter
    private PoW poW;
    private final Kernel kernel;
    private final ScheduledExecutorService server = new ScheduledThreadPoolExecutor(3, new BasicThreadFactory.Builder()
            .namingPattern("MinerManagerThread")
            .daemon(true)
            .build());

    private ScheduledFuture<?> updateFuture;
    private ScheduledFuture<?> cleanChannelFuture;
    private ScheduledFuture<?> cleanMinerFuture;

    public MinerManagerImpl(Kernel kernel) {
        this.kernel = kernel;
    }



    @Override
    public void run() {
        while (!Thread.currentThread().isInterrupted()){
            try {
                currentTask = taskQueue.take();
                log.debug("take a new Task from queue");
                updateNewTaskandBroadcast();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error(" can not take the task from taskQueue ");
                break;
            }
        }
    }

    @Override
    public synchronized void start() {
        log.debug("MinerManager start!!!");
        init();
        if (t == null){
            t = new Thread(this, "MinerManager");
            t.start();
        }
    }

    @Override
    public synchronized void stop() {
        if (t != null) {
            try {
                t.interrupt();
                t.join();
            }catch (InterruptedException e) {
                log.error("Failed to stop MinerManager");
                Thread.currentThread().interrupt();
            }
            t = null;
            close();
        }
    }



    /** 启动 函数 开启遍历和server */
    public void init() {
        log.debug("start futulre");
        updateFuture = server.scheduleAtFixedRate(this::updataBalance, 10, 10, TimeUnit.SECONDS);
        cleanChannelFuture = server.scheduleAtFixedRate(this::cleanUnactivateChannel, 64, 32, TimeUnit.SECONDS);
        cleanMinerFuture = server.scheduleAtFixedRate(this::cleanUnactivateMiner, 64, 32, TimeUnit.SECONDS);
    }

    private void updataBalance() {
        try {
            for (MinerChannel channel : activateMinerChannels.values()) {
                if (channel.isActive()) {
                    //log.debug("给channel发送余额");
                    channel.sendBalance();
                }
            }
        } catch (Exception e) {
            log.warn("update balance error");
            e.printStackTrace();
        }
    }

    @Override
    public void addActivateChannel(MinerChannel channel) {
        log.debug("add a new active channel");
        // 一般来讲 地址可能相同 但是端口不同
        activateMinerChannels.put(channel.getInetAddress(), channel);
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
        server.shutdown();
        closeMiners();
    }

    private void closeMiners() {
        // 关闭所有连接
        for (MinerChannel channel : activateMinerChannels.values()) {
            channel.dropConnection();
        }
    }

    @Override
    public void removeUnactivateChannel(MinerChannel channel) {

        if (!channel.isActive()) {
            log.debug("remove a channel");
            activateMinerChannels.remove(channel.getInetAddress(), channel);
            Miner miner = activateMiners.get(new ByteArrayWrapper(channel.getAccountAddressHash().toArray()));
            miner.removeChannel(channel.getInetAddress());
            miner.subChannelCounts();
            kernel.getChannelsAccount().getAndDecrement();
            if (miner.getConnChannelCounts() == 0) {
                log.debug("a mine remark MINER_ARCHIVE，miner Address=[{}] ", miner.getAddressHash().toHexString());
                miner.setMinerStates(MinerStates.MINER_ARCHIVE);
            }
        }
    }

    /** 清除当前所有不活跃的channel */
    public void cleanUnactivateChannel() {
        for (MinerChannel channel : activateMinerChannels.values()) {
            removeUnactivateChannel(channel);
        }
    }

    /** 清理minger */
    public void cleanUnactivateMiner() {
        for (Miner miner : activateMiners.values()) {
            if (miner.canRemove()) {
                log.debug("remove a miner,miner address=[{}]", miner.getAddressHash().toHexString());
                activateMiners.remove(new ByteArrayWrapper(miner.getAddressHash().toArray()));
            }
        }
    }


    @Override
    public void updateTask(Task task) {
        if (!taskQueue.offer(task)) {
            System.out.println("Failed to add a task to the queue!");
            log.debug("Failed to add a task to the queue!");
        }
    }

    @Override
    public void addActiveMiner(Miner miner) {
        activateMiners.put(new ByteArrayWrapper(miner.getAddressHash().toArray()), miner);
    }

    /** 每一轮任务刚发出去的时候 会用这个跟新所有miner的额情况 */
    public void updateNewTaskandBroadcast() {
        for (MinerChannel channel : activateMinerChannels.values()) {
            if (channel.isActive()) {

                channel.setTaskIndex(currentTask.getTaskIndex());
                channel.sendTaskToMiner(currentTask.getTask());
                channel.setSharesCounts(0);
            }
        }
    }

    @Override
    public Map<ByteArrayWrapper, Miner> getActivateMiners() {
        return activateMiners;
    }

    @Override
    public void onNewShare(MinerChannel channel, Message msg) {
        if (currentTask == null){
            System.out.println("currentTask 为空");
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
