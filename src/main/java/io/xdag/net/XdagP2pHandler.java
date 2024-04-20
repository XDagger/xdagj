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
package io.xdag.net;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.lang3.time.FastDateFormat;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.MutableBytes;
import org.apache.tuweni.bytes.MutableBytes32;
import org.hyperledger.besu.crypto.SecureRandomProvider;

import com.google.common.util.concurrent.SettableFuture;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.xdag.Kernel;
import io.xdag.Network;
import io.xdag.config.Config;
import io.xdag.config.spec.NodeSpec;
import io.xdag.consensus.SyncManager;
import io.xdag.core.Block;
import io.xdag.core.BlockWrapper;
import io.xdag.core.Blockchain;
import io.xdag.core.XdagStats;
import io.xdag.net.message.Message;
import io.xdag.net.message.MessageQueue;
import io.xdag.net.message.ReasonCode;
import io.xdag.net.message.consensus.BlockExtRequestMessage;
import io.xdag.net.message.consensus.BlockRequestMessage;
import io.xdag.net.message.consensus.BlocksReplyMessage;
import io.xdag.net.message.consensus.BlocksRequestMessage;
import io.xdag.net.message.consensus.NewBlockMessage;
import io.xdag.net.message.consensus.SumReplyMessage;
import io.xdag.net.message.consensus.SumRequestMessage;
import io.xdag.net.message.consensus.SyncBlockMessage;
import io.xdag.net.message.consensus.SyncBlockRequestMessage;
import io.xdag.net.message.consensus.XdagMessage;
import io.xdag.net.message.p2p.DisconnectMessage;
import io.xdag.net.message.p2p.HelloMessage;
import io.xdag.net.message.p2p.InitMessage;
import io.xdag.net.message.p2p.PingMessage;
import io.xdag.net.message.p2p.PongMessage;
import io.xdag.net.message.p2p.WorldMessage;
import io.xdag.net.node.NodeManager;
import io.xdag.utils.XdagTime;
import io.xdag.utils.exception.UnreachableException;
import lombok.extern.slf4j.Slf4j;

/**
 * Xdag P2P message handler
 */
@Slf4j
public class XdagP2pHandler extends SimpleChannelInboundHandler<Message> {

    private static final ScheduledExecutorService exec = Executors
            .newSingleThreadScheduledExecutor(new ThreadFactory() {
                private final AtomicInteger cnt = new AtomicInteger(0);

                @Override
                public Thread newThread(Runnable r) {
                    return new Thread(r, "p2p-" + cnt.getAndIncrement());
                }
            });

    private final Channel channel;

    private final Kernel kernel;
    private final Config config;
    private final NodeSpec nodeSpec;
    private final Blockchain chain;
    private final ChannelManager channelMgr;
    private final NodeManager nodeMgr;
    private final PeerClient client;
    private final SyncManager syncMgr;

    private final NetDBManager netdbMgr;
    private final MessageQueue msgQueue;

    private final AtomicBoolean isHandshakeDone = new AtomicBoolean(false);

    private ScheduledFuture<?> getNodes = null;
    private ScheduledFuture<?> pingPong = null;

    private byte[] secret = SecureRandomProvider.publicSecureRandom().generateSeed(InitMessage.SECRET_LENGTH);
    private long timestamp = System.currentTimeMillis();

    public XdagP2pHandler(Channel channel, Kernel kernel) {
        this.channel = channel;
        this.kernel = kernel;
        this.config = kernel.getConfig();
        this.nodeSpec = kernel.getConfig().getNodeSpec();

        this.chain = kernel.getBlockchain();
        this.channelMgr = kernel.getChannelMgr();
        this.nodeMgr = kernel.getNodeMgr();
        this.client = kernel.getClient();

        this.syncMgr = kernel.getSyncMgr();
        this.netdbMgr = kernel.getNetDBMgr();
        this.msgQueue = channel.getMessageQueue();
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        log.debug("P2P handler active, remoteIp = {}, remotePort = {}", channel.getRemoteIp(), channel.getRemotePort());

        // activate message queue
        msgQueue.activate(ctx);

        // disconnect if too many connections
        if (channel.isInbound() && channelMgr.size() >= config.getNodeSpec().getNetMaxInboundConnections()) {
            msgQueue.disconnect(ReasonCode.TOO_MANY_PEERS);
            return;
        }

        if (channel.isInbound()) {
            msgQueue.sendMessage(new InitMessage(secret, timestamp));
        }
        super.channelActive(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        log.debug("P2P handler inactive, remoteIp = {}", channel.getRemoteIp());

        // deactivate the message queue
        msgQueue.deactivate();

        // stop scheduled workers
        if (getNodes != null) {
            getNodes.cancel(false);
            getNodes = null;
        }

        if (pingPong != null) {
            pingPong.cancel(false);
            pingPong = null;
        }

        super.channelInactive(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.debug("Exception in P2P handler, remoteIp = {}, remotePort = {}", channel.getRemoteIp(), channel.getRemotePort(),cause);

        // close connection on exception
        ctx.close();
    }

    @Override
    public void channelRead0(final ChannelHandlerContext ctx, Message msg) {
        log.trace("Received message: {}", msg);

        switch (msg.getCode()) {
        /* p2p */
        case DISCONNECT -> onDisconnect(ctx, (DisconnectMessage) msg);
        case PING -> onPing();
        case PONG -> onPong();
        case HANDSHAKE_INIT -> onHandshakeInit((InitMessage) msg);
        case HANDSHAKE_HELLO -> onHandshakeHello((HelloMessage) msg);
        case HANDSHAKE_WORLD -> onHandshakeWorld((WorldMessage) msg);

        /* sync */
        case BLOCKS_REQUEST, BLOCKS_REPLY, SUMS_REQUEST, SUMS_REPLY, BLOCKEXT_REQUEST, BLOCKEXT_REPLY, BLOCK_REQUEST, NEW_BLOCK, SYNC_BLOCK, SYNCBLOCK_REQUEST ->
                onXdag(msg);
        default -> ctx.fireChannelRead(msg);
        }
    }

    protected void onDisconnect(ChannelHandlerContext ctx, DisconnectMessage msg) {
        ReasonCode reason = msg.getReason();
        log.info("Received a DISCONNECT message: reason = {}, remoteIP = {}",
                reason, channel.getRemoteIp());

        ctx.close();
    }

    protected void onHandshakeInit(InitMessage msg) {
        // unexpected
        if (channel.isInbound()) {
            return;
        }

        // check message
        if (!msg.validate()) {
            this.msgQueue.disconnect(ReasonCode.INVALID_HANDSHAKE);
            return;
        }

        // record the secret
        this.secret = msg.getSecret();
        this.timestamp = msg.getTimestamp();

        // send the HELLO message
        this.msgQueue.sendMessage(new HelloMessage(nodeSpec.getNetwork(), nodeSpec.getNetworkVersion(), client.getPeerId(),
                client.getPort(), config.getClientId(), config.getClientCapabilities().toArray(),
                chain.getLatestMainBlockNumber(),
                secret, client.getCoinbase()));
    }

    protected void onHandshakeHello(HelloMessage msg) {
        // unexpected
        if (channel.isOutbound()) {
            return;
        }
        Peer peer = msg.getPeer(channel.getRemoteIp());

        // check peer
        ReasonCode code = checkPeer(peer, true);
        if (code != null) {
            msgQueue.disconnect(code);
            return;
        }

        // check message
        if (!Arrays.equals(secret, msg.getSecret()) || !msg.validate(config)) {
            msgQueue.disconnect(ReasonCode.INVALID_HANDSHAKE);
            return;
        }

        // send the WORLD message
        this.msgQueue.sendMessage(new WorldMessage(nodeSpec.getNetwork(), nodeSpec.getNetworkVersion(), client.getPeerId(),
                client.getPort(), config.getClientId(), config.getClientCapabilities().toArray(),
                chain.getLatestMainBlockNumber(),
                secret, client.getCoinbase()));

        // handshake done
        onHandshakeDone(peer);
    }

    protected void onHandshakeWorld(WorldMessage msg) {
        // unexpected
        if (channel.isInbound()) {
            return;
        }
        Peer peer = msg.getPeer(channel.getRemoteIp());

        // check peer
        ReasonCode code = checkPeer(peer, true);
        if (code != null) {
            msgQueue.disconnect(code);
            return;
        }

        // check message
        if (!Arrays.equals(secret, msg.getSecret()) || !msg.validate(config)) {
            msgQueue.disconnect(ReasonCode.INVALID_HANDSHAKE);
            return;
        }

        // handshake done
        onHandshakeDone(peer);
    }

    private long lastPing;

    protected void onPing() {
        PongMessage pong = new PongMessage();
        msgQueue.sendMessage(pong);
        lastPing = System.currentTimeMillis();
    }

    protected void onPong() {
        if (lastPing > 0) {
            long latency = System.currentTimeMillis() - lastPing;
            channel.getRemotePeer().setLatency(latency);
        }
    }

    protected void onXdag(Message msg) {
        if (!isHandshakeDone.get()) {
            return;
        }

        switch (msg.getCode()) {
            case NEW_BLOCK -> processNewBlock((NewBlockMessage) msg);
            case BLOCK_REQUEST -> processBlockRequest((BlockRequestMessage) msg);
            case BLOCKS_REQUEST -> processBlocksRequest((BlocksRequestMessage) msg);
            case BLOCKS_REPLY -> processBlocksReply((BlocksReplyMessage) msg);
            case SUMS_REQUEST -> processSumsRequest((SumRequestMessage) msg);
            case SUMS_REPLY -> processSumsReply((SumReplyMessage) msg);
            case BLOCKEXT_REQUEST -> processBlockExtRequest((BlockExtRequestMessage) msg);
            case SYNC_BLOCK -> processSyncBlock((SyncBlockMessage) msg);
            case SYNCBLOCK_REQUEST -> processSyncBlockRequest((SyncBlockRequestMessage) msg);
            default -> throw new UnreachableException();
        }
    }

    /**
     * Check whether the peer is valid to connect.
     */
    private ReasonCode checkPeer(Peer peer, boolean newHandShake) {
        // has to be same network
        if (newHandShake && !nodeSpec.getNetwork().equals(peer.getNetwork())) {
            return ReasonCode.BAD_NETWORK;
        }

        // has to be compatible version
        if (nodeSpec.getNetworkVersion() != peer.getNetworkVersion()) {
            return ReasonCode.BAD_NETWORK_VERSION;
        }

        // not connected
//        if (client.getPeerId().equals(peer.getPeerId()) || channelMgr.isActivePeer(peer.getPeerId())) {
//            return ReasonCode.DUPLICATED_PEER_ID;
//        }

        // validator can't share IP address
//        if (channelMgr.isActiveIP(channel.getRemoteIp()) // already connected
//                && nodeSpec.getNetwork() == Network.MAINNET) { // on main net
//            return ReasonCode.VALIDATOR_IP_LIMITED;
//        }

        return null;
    }

    private void onHandshakeDone(Peer peer) {
        if (isHandshakeDone.compareAndSet(false, true)) {
            // register into channel manager
            channelMgr.onChannelActive(channel, peer);

            // start ping pong
            pingPong = exec.scheduleAtFixedRate(() -> msgQueue.sendMessage(new PingMessage()),
                    channel.isInbound() ? 1 : 0, 1, TimeUnit.MINUTES);
        } else {
            msgQueue.disconnect(ReasonCode.HANDSHAKE_EXISTS);
        }
    }

    /**
     * ********************** Message Processing * ***********************
     */
    protected void processNewBlock(NewBlockMessage msg) {
        Block block = msg.getBlock();
        if (syncMgr.isSyncOld()) {
            return;
        }

        log.debug("processNewBlock:{} from node {}", block.getHashLow(), channel.getRemoteAddress());
        BlockWrapper bw = new BlockWrapper(block, msg.getTtl() - 1, channel.getRemotePeer(), false);
        syncMgr.validateAndAddNewBlock(bw);
    }

    protected void processSyncBlock(SyncBlockMessage msg) {
        Block block = msg.getBlock();

        log.debug("processSyncBlock:{}  from node {}", block.getHashLow(), channel.getRemoteAddress());
        BlockWrapper bw = new BlockWrapper(block, msg.getTtl() - 1, channel.getRemotePeer(), true);
        syncMgr.validateAndAddNewBlock(bw);
    }

    /**
     * 区块请求响应一个区块 并开启一个线程不断发送一段时间内的区块 *
     */
    protected void processBlocksRequest(BlocksRequestMessage msg) {
        // 更新全网状态
        updateXdagStats(msg);
        long startTime = msg.getStarttime();
        long endTime = msg.getEndtime();
        long random = msg.getRandom();

        // TODO: paulochen 处理多区块请求
        //        // 如果大于快照点的话 我可以发送
        //        if (startTime > 1658318225407L) {
        //            // TODO: 如果请求时间间隔过大，启动新线程发送，目的是避免攻击
        log.debug("Send blocks between {} and {} to node {}",
                FastDateFormat.getInstance("yyyy-MM-dd HH:mm:ss.SSS").format(XdagTime.xdagTimestampToMs(startTime)),
                FastDateFormat.getInstance("yyyy-MM-dd HH:mm:ss.SSS").format(XdagTime.xdagTimestampToMs(endTime)),
                channel.getRemoteAddress());
        List<Block> blocks = chain.getBlocksByTime(startTime, endTime);
        for (Block block : blocks) {
            SyncBlockMessage blockMsg = new SyncBlockMessage(block, 1);
            msgQueue.sendMessage(blockMsg);
        }
        msgQueue.sendMessage(new BlocksReplyMessage(startTime, endTime, random, chain.getXdagStats(), netdbMgr.getNetDB()));
    }

    protected void processBlocksReply(BlocksReplyMessage msg) {
        updateXdagStats(msg);
        long randomSeq = msg.getRandom();
        SettableFuture<Bytes> sf = kernel.getSync().getBlocksRequestMap().get(randomSeq);
        if (sf != null) {
            sf.set(Bytes.wrap(new byte[]{0}));
        }
    }

    /**
     * 将sumRequest的后8个字段填充为自己的sum 修改type类型为reply 发送
     */
    protected void processSumsRequest(SumRequestMessage msg) {
        updateXdagStats(msg);
        MutableBytes sums = MutableBytes.create(256);
        // TODO: paulochen 处理sum请求
        kernel.getBlockStore().loadSum(msg.getStarttime(),msg.getEndtime(),sums);
        SumReplyMessage reply = new SumReplyMessage(msg.getEndtime(), msg.getRandom(),
                chain.getXdagStats(), sums, netdbMgr.getNetDB());
        msgQueue.sendMessage(reply);
    }

    protected void processSumsReply(SumReplyMessage msg) {
        updateXdagStats(msg);
        long randomSeq = msg.getRandom();
        SettableFuture<Bytes> sf = kernel.getSync().getSumsRequestMap().get(randomSeq);
        if (sf != null) {
            sf.set(msg.getSum());
        }
    }

    protected void processBlockExtRequest(BlockExtRequestMessage msg) {
    }

    protected void processBlockRequest(BlockRequestMessage msg) {
        Bytes hash = msg.getHash();
        Block block = chain.getBlockByHash(Bytes32.wrap(hash), true);
        int ttl = config.getNodeSpec().getTTL();
        if (block != null) {
            log.debug("processBlockRequest: findBlock" + Bytes32.wrap(hash).toHexString());
            NewBlockMessage message = new NewBlockMessage(block, ttl);
            msgQueue.sendMessage(message);
        }
    }

    private void processSyncBlockRequest(SyncBlockRequestMessage msg) {
        Bytes hash = msg.getHash();
        Block block = chain.getBlockByHash(Bytes32.wrap(hash), true);
        if (block != null) {
            log.debug("processSyncBlockRequest, findBlock: {}, to node: {}", Bytes32.wrap(hash).toHexString(), channel.getRemoteAddress());
            SyncBlockMessage message = new SyncBlockMessage(block, 1);
            msgQueue.sendMessage(message);
        }
    }

    /**
     * ********************** Xdag Message ************************
     */
    public void sendNewBlock(Block newBlock, int TTL) {
        log.debug("send block:{} to node:{}", newBlock.getHashLow(), channel.getRemoteAddress());
        NewBlockMessage msg = new NewBlockMessage(newBlock, TTL);
        sendMessage(msg);
    }

    public long sendGetBlocks(long startTime, long endTime) {
        log.debug("Request blocks between {} and {} from node {}",
                FastDateFormat.getInstance("yyyy-MM-dd HH:mm:ss.SSS").format(XdagTime.xdagTimestampToMs(startTime)),
                FastDateFormat.getInstance("yyyy-MM-dd HH:mm:ss.SSS").format(XdagTime.xdagTimestampToMs(endTime)),
                channel.getRemoteAddress());
        BlocksRequestMessage msg = new BlocksRequestMessage(startTime, endTime, chain.getXdagStats(),
                netdbMgr.getNetDB());
        sendMessage(msg);
        return msg.getRandom();
    }

    public long sendGetBlock(MutableBytes32 hash, boolean isOld) {
        XdagMessage msg;
        //        log.debug("sendGetBlock:[{}]", Hex.toHexString(hash));
        msg = isOld ? new SyncBlockRequestMessage(hash, kernel.getBlockchain().getXdagStats(), netdbMgr.getNetDB())
                : new BlockRequestMessage(hash, kernel.getBlockchain().getXdagStats(), netdbMgr.getNetDB());
        log.debug("Request block {} isold: {} from node {}", hash, isOld,channel.getRemoteAddress());
        sendMessage(msg);
        return msg.getRandom();
    }

    public long sendGetSums(long startTime, long endTime) {
        SumRequestMessage msg = new SumRequestMessage(startTime, endTime, chain.getXdagStats(),
                netdbMgr.getNetDB());
        sendMessage(msg);
        return msg.getRandom();
    }

    public void sendMessage(Message message) {
        msgQueue.sendMessage(message);
    }

    public void updateXdagStats(XdagMessage message) {
        // Confirm that the remote stats has been updated, used to check local state.
        syncMgr.getIsUpdateXdagStats().compareAndSet(false, true);
        XdagStats remoteXdagStats = message.getXdagStats();
        chain.getXdagStats().update(remoteXdagStats);
        netdbMgr.updateNetDB(message.getRemoteNetdb());
    }

}
