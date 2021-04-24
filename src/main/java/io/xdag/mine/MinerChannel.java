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
package io.xdag.mine;

import static io.xdag.mine.miner.MinerStates.MINER_ACTIVE;

import java.net.InetSocketAddress;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

import io.xdag.mine.miner.MinerStates;
import org.apache.commons.codec.binary.Hex;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.xdag.Kernel;
import io.xdag.config.Config;
import io.xdag.core.Block;
import io.xdag.core.XdagField;
import io.xdag.db.store.BlockStore;
import io.xdag.mine.handler.ConnectionLimitHandler;
import io.xdag.mine.handler.Miner03;
import io.xdag.mine.handler.MinerHandShakeHandler;
import io.xdag.mine.handler.MinerMessageHandler;
import io.xdag.mine.manager.MinerManager;
import io.xdag.mine.message.MinerMessageFactory;
import io.xdag.mine.miner.Miner;
import io.xdag.net.XdagVersion;
import io.xdag.net.message.MessageFactory;
import io.xdag.utils.ByteArrayWrapper;
import io.xdag.utils.BytesUtils;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MinerChannel {
    /** 对应的是服务端的还是客户端的 */
    private final boolean isServer;
    private final Kernel kernel;
    private final Config config;
    /** 这个channel是否是活跃的 仅当连接成功后变为true */
    @Getter
    @Setter
    private boolean isActive;
    /** 记录对应的矿工对象 */
    @Getter
    @Setter
    private Miner miner;
    /** 当前接受的最近的任务编号 */
    @Getter
    @Setter
    private long taskIndex;
    private long taskTime = 0;
    /** 每一轮任务分享share的次数 接收一次加1 */
    @Getter
    @Setter
    private int sharesCounts;
    /** 上一次发送shares 的时间 接收到shares 的时候会更新 */
    @Getter
    @Setter
    private long lastSharesTime;
    /** 连接成功的时间 暂时只适用于打印 没有其他用途 */
    @Getter
    private Date connectTime;
    /** 如果是服务端端 则保存的是远程连接的客户端地址 若是客户端，则保存的是本地的地址 */
    @Getter
    @Setter
    private InetSocketAddress inetAddress;
    /** 发起连接的账户的地址块 */
    @Getter
    @Setter
    private byte[] accountAddressHash;
    /** 保存上一轮的share */
    @Getter
    @Setter
    private byte[] share = new byte[32];
    /** 跟新为当前最小的hash 每收到一个share 后跟新 */
    @Getter
    @Setter
    private byte[] lastMinHash = new byte[32];
    /** 记录prevDiff的次数 实际上类似于进行了多少次计算 */
    @Getter
    @Setter
    private int prevDiffCounts;

    @Setter
    private ChannelHandlerContext ctx;

    private final BlockStore blockStore;

    private final MinerManager minerManager;

    /** 存放的是连续16个任务本地计算的最大难度 每一轮放的都是最小hash 计算出来的diffs */
    private final List<Double> maxDiffs = new CopyOnWriteArrayList<>();

    /** 记录的是当前任务所有难度之和，每当接收到一个新的nonce 会更新这个 */
    @Getter
    @Setter
    private double prevDiff;

    @Getter
    @Setter
    private double meanLogDiff;

    @Getter
    @Setter
    private int boundedTaskCounter;

    /** 保存这个channel 最后的计算的hash */
    @Getter
    @Setter
    private byte[] minHash;
    /** 记录的是出入站的消息 */
    private final StatHandle inBound;
    private final StatHandle outBound;
    /** 各种处理器* */
    private MinerHandShakeHandler minerHandShakeHandler;
    private MinerMessageHandler minerMessageHandler;
    private Miner03 miner03;
    private ConnectionLimitHandler connectionLimitHandler;

    @Getter
    private boolean isMill = false;

    /** 初始化 同时需要判断是服务器端还是客户端 */
    public MinerChannel(Kernel kernel, NioSocketChannel socket, boolean isServer) {
        this.kernel = kernel;
        this.config = kernel.getConfig();
        this.inBound = new StatHandle();
        this.outBound = new StatHandle();
        this.isServer = isServer;

        this.blockStore = kernel.getBlockStore();
        this.minerManager = kernel.getMinerManager();

        // 容器的初始化
        for (int i = 0; i < 16; i++) {
            maxDiffs.add(0.0);
        }
    }

    /**
     * 初始化一个channel 并且注册到管道上
     *
     * @param pipeline
     *            注册的管道
     * @param inetSocketAddress
     *            若是矿池开启的channel 则对应的是远程矿工地址，若为客户端开启，则为本地地址
     */
    public void init(ChannelPipeline pipeline, InetSocketAddress inetSocketAddress) {
        this.inetAddress = inetSocketAddress;

        // 给管道添加各种消息处理器
        this.minerMessageHandler = new MinerMessageHandler(this);
        this.miner03 = new Miner03(this, kernel);

        if (isServer) {
            connectionLimitHandler = kernel.getConnectionLimitHandler();
            pipeline.addLast("connectionLimitHandler", connectionLimitHandler);
        }

        // 仅服务器端需要这个握手协议 接受
        this.minerHandShakeHandler = new MinerHandShakeHandler(this, kernel);
        pipeline.addLast("MinerHandShake", minerHandShakeHandler);
    }

    // 初始化这个channel

    /**
     * 根据版本创建一个信息工厂
     *
     * @param version
     *            对应的版本
     * @return 工厂
     */
    private MessageFactory createMinerMessageFactory(XdagVersion version) {
        switch (version) {
        case V03:
            return new MinerMessageFactory();

        default:
            throw new IllegalArgumentException("Xdag" + version + " is not supported");
        }
    }

    /**
     * 激活这个channel的各种handler 为管道添加各种处理器
     *
     * @param ctx
     *            上下文
     * @param version
     *            版本号
     */
    public void activateHadnler(ChannelHandlerContext ctx, XdagVersion version) {
        log.debug("激活其他处理器");
        MessageFactory messageFactory = createMinerMessageFactory(version);
        minerMessageHandler.setMessageFactory(messageFactory);
        ctx.pipeline().addLast("MinerMessageHandler", minerMessageHandler);
        ctx.pipeline().addLast("Miner03Handler", miner03);
    }

    public boolean initMiner(byte[] accountAddressHash) {
        this.accountAddressHash = accountAddressHash;
        log.debug("init a Miner:" + Hex.encodeHexString(accountAddressHash));
        // 判断这个矿工是否已经存在了
        if (minerManager.getActivateMiners().containsKey(new ByteArrayWrapper(accountAddressHash))) {
            // 存在 但是会不会超过限制数
            log.debug("已经存在一个对应的矿工了");
            this.miner = minerManager.getActivateMiners().get(new ByteArrayWrapper(accountAddressHash));
            if (miner.getConnChannelCounts() < config.getMaxMinerPerAccount()) {
                this.miner = minerManager.getActivateMiners().get(new ByteArrayWrapper(accountAddressHash));
                this.miner.addChannelCounts(1);
                this.miner.putChannel(this.inetAddress, this);
                this.miner.setMinerStates(MINER_ACTIVE);
                return true;
            } else {
                log.debug("同一个矿工连接了太多啦");
                return false;
            }
        } else {
            this.miner = new Miner(accountAddressHash);
            minerManager.getActivateMiners().put(new ByteArrayWrapper(accountAddressHash), miner);
            miner.addChannelCounts(1);
            this.miner.putChannel(this.inetAddress, this);
            this.miner.setMinerStates(MINER_ACTIVE);
            return true;
        }
    }

    /** 通知断开连接 */
    public void notifyDisconnect() {
        log.debug("Notify to disconnect" + this);
    }

    public void onDisconnect() {
        miner03.dropConnection();
    }

    public StatHandle getInBound() {
        return inBound;
    }

    public StatHandle getOutBound() {
        return outBound;
    }

    public boolean isServer() {
        return isServer;
    }

    public void setIsActivate(boolean bool) {
        isActive = bool;
    }

    public void setConnectTime(Date time) {
        connectTime = time;
    }

    public void addShareCounts(int i) {
        sharesCounts += i;
    }

    /** 矿池发送给矿工的任务 */
    public void sendTaskToMiner(XdagField[] fields) {
        byte[] bytes = BytesUtils.merge(fields[0].getData(), fields[1].getData());
        miner03.sendMessage(bytes);
    }

    /** 矿池发送余额给矿工 */
    public void sendBalance() {
        byte[] hashlow = new byte[32];
        System.arraycopy(accountAddressHash,8,hashlow,8,24);
        Block block = blockStore.getBlockByHash(hashlow, false);
        if (block == null) {
            log.debug("Can't found block,{}",Hex.encodeHex(hashlow));
            return;
        }
        long amount = block.getInfo().getAmount();
        byte[] data = BytesUtils.merge(BytesUtils.longToBytes(amount, false),
                BytesUtils.subArray(accountAddressHash, 8, 24));
        log.debug("update miner balance {}", Hex.encodeHexString(data));
        miner03.sendMessage(data);
    }

    public long getTaskTime() {
        return taskTime;
    }

    public void setTaskTime(long taskTime) {
        this.taskTime = taskTime;
    }

    public void addPrevDiff(double i) {
        prevDiff = +i;
    }

    public void addPrevDiffCounts() {
        this.prevDiffCounts++;
    }

    public void addMaxDiffs(int index, double diff) {
        maxDiffs.add(index, diff);
    }

    public double getMaxDiffs(int index) {
        return maxDiffs.get(index);
    }

    public void addBoundedTaskCounter() {
        this.boundedTaskCounter++;
    }

    @Override
    public String toString() {
        return "";
    }

    public void dropConnection() {
        miner03.dropConnection();
    }

    /** 内部类 用于计算这个channel 的入栈和出战信息 */
    public static class StatHandle {
        AtomicLong count = new AtomicLong(0);

        public void add() {
            count.getAndIncrement();
        }

        public void add(long delta) {
            count.addAndGet(delta);
        }

        public long get() {
            return count.get();
        }

        @Override
        public String toString() {
            return count.toString();
        }
    }


    public void updateMiner(Miner miner) {
        this.miner = miner;
        this.accountAddressHash = miner.getAddressHash();
        miner.putChannel(this.getInetAddress(), this);
        miner.addChannelCounts(1);
        miner.setMinerStates(MinerStates.MINER_ACTIVE);
        isMill = true;
    }
}
