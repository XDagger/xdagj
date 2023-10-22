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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.hyperledger.besu.crypto.SecureRandomProvider;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.xdag.Network;
import io.xdag.config.Config;
import io.xdag.config.spec.NodeSpec;
import io.xdag.core.BlockHeader;
import io.xdag.core.BlockPart;
import io.xdag.DagKernel;
import io.xdag.core.Dagchain;
import io.xdag.core.MainBlock;
import io.xdag.core.PendingManager;
import io.xdag.core.PowManager;
import io.xdag.core.SyncManager;
import io.xdag.net.message.Message;
import io.xdag.net.message.MessageQueue;
import io.xdag.net.message.ReasonCode;
import io.xdag.net.message.consensus.EpochMessage;
import io.xdag.net.message.consensus.GetMainBlockHeaderMessage;
import io.xdag.net.message.consensus.GetMainBlockMessage;
import io.xdag.net.message.consensus.GetMainBlockPartsMessage;
import io.xdag.net.message.consensus.MainBlockHeaderMessage;
import io.xdag.net.message.consensus.MainBlockMessage;
import io.xdag.net.message.consensus.MainBlockPartsMessage;
import io.xdag.net.message.p2p.DisconnectMessage;
import io.xdag.net.message.p2p.HelloMessage;
import io.xdag.net.message.p2p.InitMessage;
import io.xdag.net.message.p2p.PingMessage;
import io.xdag.net.message.p2p.PongMessage;
import io.xdag.net.message.p2p.TransactionMessage;
import io.xdag.net.message.p2p.WorldMessage;
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

    private final DagKernel kernel;
    private final Config config;
    private final NodeSpec nodeSpec;
    private final Dagchain dagChain;

    private final PendingManager pendingMgr;
    private final ChannelManager channelMgr;
    private final PeerClient client;

    private final SyncManager xdagSync;

    private final PowManager xdagPow;

    private final MessageQueue msgQueue;

    private final AtomicBoolean isHandshakeDone = new AtomicBoolean(false);

    private ScheduledFuture<?> getNodes = null;
    private ScheduledFuture<?> pingPong = null;

    private byte[] secret = SecureRandomProvider.publicSecureRandom().generateSeed(InitMessage.SECRET_LENGTH);
    private long timestamp = System.currentTimeMillis();

    public XdagP2pHandler(Channel channel, DagKernel kernel) {
        this.channel = channel;
        this.kernel = kernel;
        this.config = kernel.getConfig();
        this.nodeSpec = kernel.getConfig().getNodeSpec();

        this.dagChain = kernel.getDagchain();
        this.pendingMgr = kernel.getPendingManager();
        this.channelMgr = kernel.getChannelManager();
        this.client = kernel.getClient();
        this.xdagSync = kernel.getXdagSync();
        this.xdagPow = kernel.getXdagPow();
        this.msgQueue = channel.getMsgQueue();
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
            case DISCONNECT:
                onDisconnect(ctx, (DisconnectMessage) msg);
                break;
            case PING:
                onPing();
                break;
            case PONG:
                onPong();
                break;
            case HANDSHAKE_INIT:
                onHandshakeInit((InitMessage) msg);
                break;
            case HANDSHAKE_HELLO:
                onHandshakeHello((HelloMessage) msg);
                break;
            case HANDSHAKE_WORLD:
                onHandshakeWorld((WorldMessage) msg);
                break;
            case TRANSACTION:
                onTransaction((TransactionMessage) msg);
                break;

            /* new dag sync */
            case GET_MAIN_BLOCK:
            case MAIN_BLOCK:
            case GET_MAIN_BLOCK_HEADER:
            case MAIN_BLOCK_HEADER:
            case GET_MAIN_BLOCK_PARTS:
            case MAIN_BLOCK_PARTS:
                    onSync(msg);
                break;
                /* pow */
            case EPOCH_BLOCK:
                onEpochBlock((EpochMessage)msg);
                break;
            default:ctx.fireChannelRead(msg);
                break;
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
                dagChain.getLatestMainBlock(),
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
                dagChain.getLatestMainBlock(),
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

    protected void onTransaction(TransactionMessage msg) {
        pendingMgr.addTransaction(msg.getTransaction());
    }

    protected void onEpochBlock(EpochMessage msg) {
        if (!isHandshakeDone.get()) {
            return;
        }
        xdagPow.onMessage(channel, msg);
    }

    protected void onSync(Message msg) {
        if (!isHandshakeDone.get()) {
            return;
        }

        switch (msg.getCode()) {
        case GET_MAIN_BLOCK: {
            GetMainBlockMessage m = (GetMainBlockMessage) msg;
            MainBlock block = dagChain.getMainBlockByNumber(m.getNumber());
            channel.getMsgQueue().sendMessage(new MainBlockMessage(block));
            break;
        }
        case GET_MAIN_BLOCK_HEADER: {
            GetMainBlockHeaderMessage m = (GetMainBlockHeaderMessage) msg;
            BlockHeader header = dagChain.getBlockHeader(m.getNumber());
            channel.getMsgQueue().sendMessage(new MainBlockHeaderMessage(header));
            break;
        }
        case GET_MAIN_BLOCK_PARTS: {
            GetMainBlockPartsMessage m = (GetMainBlockPartsMessage) msg;
            long number = m.getNumber();
            int parts = m.getParts();

            List<byte[]> partsSerialized = new ArrayList<>();
            MainBlock block = dagChain.getMainBlockByNumber(number);
            for (BlockPart part : BlockPart.decode(parts)) {
                switch (part) {
                case HEADER:
                    partsSerialized.add(block.getEncodedHeader());
                    break;
                case TRANSACTIONS:
                    partsSerialized.add(block.getEncodedTransactions());
                    break;
                case RESULTS:
                    partsSerialized.add(block.getEncodedResults());
                    break;
                default:
                    throw new UnreachableException();
                }
            }

            channel.getMsgQueue().sendMessage(new MainBlockPartsMessage(number, parts, partsSerialized));
            break;
        }
        case MAIN_BLOCK:
        case MAIN_BLOCK_HEADER:
        case MAIN_BLOCK_PARTS: {
            xdagSync.onMessage(channel, msg);
            break;
        }
        default:
            throw new UnreachableException();
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
//            log.debug("client.getPeerId().equals(peer.getPeerId()) = {}", client.getPeerId().equals(peer.getPeerId()));
//            log.debug("channelMgr.isActivePeer(peer.getPeerId()) = {}", channelMgr.isActivePeer(peer.getPeerId()));
//            return ReasonCode.DUPLICATED_PEER_ID;
//        }

        // validator can't share IP address
        if (channelMgr.isActiveIP(channel.getRemoteIp()) // already connected
                && nodeSpec.getNetwork() == Network.MAINNET) { // on main net
            return ReasonCode.VALIDATOR_IP_LIMITED;
        }

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

    public void sendMessage(Message message) {
        msgQueue.sendMessage(message);
    }

}
