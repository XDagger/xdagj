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
package io.xdag.consensus;

import com.google.common.collect.Queues;
import io.libp2p.core.PeerId;
import io.xdag.Kernel;
import io.xdag.config.Config;
import io.xdag.config.MainnetConfig;
import io.xdag.core.*;
import io.xdag.discovery.peers.DiscoveryPeer;
import io.xdag.discovery.peers.PeerTable;
import io.xdag.libp2p.Libp2pChannel;
import io.xdag.libp2p.manager.ChannelManager;
import io.xdag.libp2p.peer.LibP2PNodeId;
import io.xdag.net.XdagChannel;
import io.xdag.net.manager.XdagChannelManager;
import io.xdag.net.node.Node;
import io.xdag.utils.ByteArrayWrapper;
import io.xdag.utils.BytesUtils;
import io.xdag.utils.XdagTime;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.util.encoders.Hex;

import javax.annotation.Nonnull;
import java.math.BigInteger;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

import static io.xdag.core.ImportResult.*;
import static io.xdag.utils.FastByteComparisons.equalBytes;

@Slf4j
@Getter
@Setter
public class SyncManager {

    private static final ThreadFactory factory = new ThreadFactory() {
        private final AtomicInteger cnt = new AtomicInteger(0);

        @Override
        public Thread newThread(@Nonnull Runnable r) {
            return new Thread(r, "node-" + cnt.getAndIncrement());
        }
    };

    private Kernel kernel;
    private Blockchain blockchain;
    private long importStart;
    private AtomicLong importIdleTime = new AtomicLong();
    private boolean syncDone = false;
    private XdagChannelManager channelMgr;
    private ChannelManager channelManager;
    private final ScheduledExecutorService exec;
    private ScheduledFuture<?> connectlibp2pFuture;
    private Set<DiscoveryPeer> hadConnectnode = new HashSet<>();


    // 监听是否需要自己启动
    private StateListener stateListener;

    public SyncManager(Kernel kernel) {
        this.kernel = kernel;
        this.blockchain = kernel.getBlockchain();
        this.channelMgr = kernel.getChannelMgr();
        this.channelManager = kernel.getChannelManager();

        this.stateListener = new StateListener();
        this.exec = new ScheduledThreadPoolExecutor(1, factory);

    }

    /** Queue with validated blocks to be added to the blockchain */
    private Queue<BlockWrapper> blockQueue = new ConcurrentLinkedQueue<>();

    /** Queue for the link block don't exist */
    private ConcurrentHashMap<ByteArrayWrapper, Queue<BlockWrapper>> syncMap = new ConcurrentHashMap<>();

    public void start() {
        log.debug("Download receiveBlock run...");
        new Thread(this.stateListener, "xdag-stateListener").start();
    }


    private class StateListener implements Runnable {

        boolean isRunning = false;

        @Override
        public void run() {
            this.isRunning = true;
            while (this.isRunning) {
                if (isTimeToStart()) {
                    makeSyncDone();
                }
                try {
                    Thread.sleep(10000);
                } catch (InterruptedException e) {
                    log.error(e.getMessage(), e);
                }
            }
        }
    }


    /**
     * 监听kernel状态 判断是否该自启
     */
    public boolean isTimeToStart() {
        boolean res = false;
        Config config = kernel.getConfig();
        if( config instanceof MainnetConfig) {
            if (kernel.getXdagState() != XdagState.CONN && (XdagTime.getCurrentEpoch() > kernel.getStartEpoch()+ config.getPoolSpec().getWaitEpoch())) {
                res = true;
            }
        } else {
            if (kernel.getXdagState() != XdagState.CTST && (XdagTime.getCurrentEpoch() > kernel.getStartEpoch()+ config.getPoolSpec().getWaitEpoch())) {
                makeSyncDone();
                res = true;
            }
        }
        return res;
    }

    /** Processing the queue adding blocks to the chain. */
    //todo:修改共识
    public ImportResult importBlock(BlockWrapper blockWrapper) {
        log.debug("importBlock:{}", BytesUtils.toHexString(blockWrapper.getBlock().getHash()));
        ImportResult importResult = blockchain.tryToConnect(blockWrapper.getBlock());

        if (importResult == EXIST) {
            log.error("Block have exist:" + Hex.toHexString(blockWrapper.getBlock().getHash()));
        }

        Config config = kernel.getConfig();
        if (importResult == IMPORTED_BEST || importResult == IMPORTED_NOT_BEST) {
            // 状态设置为正在同步
            if (!syncDone) {
                if (config instanceof MainnetConfig) {
                    kernel.setXdagState(XdagState.CONN);
                } else {
                    kernel.setXdagState(XdagState.CTST);
                }
            }

            BigInteger currentDiff = blockchain.getXdagTopStatus().getTopDiff();
            if (!syncDone
                    && ((blockchain.getXdagStats().getMaxdifficulty().compareTo(BigInteger.ZERO)>0
                    && currentDiff.compareTo(blockchain.getXdagStats().getMaxdifficulty()) >= 0)
                    )
            ) {
                makeSyncDone();
            }
        }

        if (syncDone && (importResult == IMPORTED_BEST || importResult == IMPORTED_NOT_BEST)) {
            // 如果是自己产生的区块则在pow的时候已经广播 这里不需要重复
            if (blockWrapper.getRemoteNode() == null
                    || !blockWrapper.getRemoteNode().equals(kernel.getClient().getNode())) {
                if (blockWrapper.getTtl() > 0) {
                    distributeBlock(blockWrapper);
                }
            }
        }
        return  importResult;
    }

    public boolean isSyncDone() {
        return syncDone;
    }

    public synchronized ImportResult validateAndAddNewBlock(BlockWrapper blockWrapper) {
        blockWrapper.getBlock().parse();
        ImportResult result = importBlock(blockWrapper);
        log.info("validateAndAddNewBlock:{}, {}", Hex.toHexString(blockWrapper.getBlock().getHashLow()), result);
        switch (result) {
            case IMPORTED_BEST:
            case IMPORTED_NOT_BEST:
                syncPopBlock(blockWrapper);
                break;
            case NO_PARENT: {
                if (syncPushBlock(blockWrapper, result.getHashlow())) {
                    log.error("push block:{}, NO_PARENT {}", Hex.toHexString(blockWrapper.getBlock().getHashLow()),
                        Hex.toHexString(result.getHashlow()));
                    List<XdagChannel> channels = channelMgr.getActiveChannels();
                    for (XdagChannel channel : channels) {
                        if(channel.getNode().equals(blockWrapper.getRemoteNode())) {
                            channel.getXdag().sendGetBlock(result.getHashlow());

                        }
                    }
//                    for(Libp2pChannel libp2pChannel : channelManager.getactiveChannel()){
//                        if(libp2pChannel.getNode().equals(blockWrapper.getRemoteNode())){
//                            libp2pChannel.getHandler().getController().sendGetBlock(result.getHashLow());
//                        }
//                    }
                }
                break;
            }
            case INVALID_BLOCK: {
//                log.error("invalid block:{}", Hex.toHexString(blockWrapper.getBlock().getHashLow()));
                break;
            }
            default:
                break;
        }
        return result;
    }

    /**
     * 同步缺失区块
     *
     * @param blockWrapper
     *            新区块
     * @param hashLow
     *            缺失的parent哈希
     */
    public boolean syncPushBlock(BlockWrapper blockWrapper, byte[] hashLow) {
        AtomicBoolean r = new AtomicBoolean(true);
        long now = System.currentTimeMillis();
        ByteArrayWrapper refKey = new ByteArrayWrapper(hashLow);
        Queue<BlockWrapper> newQueue = Queues.newConcurrentLinkedQueue();
        blockWrapper.setTime(now);
        newQueue.add(blockWrapper);
        blockchain.getXdagStats().nwaitsync++;
        syncMap.merge(refKey, newQueue,
                (oldQ, newQ) -> {
                    blockchain.getXdagStats().nwaitsync--;
                    for(BlockWrapper b : oldQ) {
                        if (equalBytes(b.getBlock().getHashLow(), blockWrapper.getBlock().getHashLow())) {
                            // after 64 sec must resend block request
                            if(now - b.getTime() > 64 * 1000) {
                                b.setTime(now);
                                r.set(true);
                            } else {
                            //TODO should be consider timeout not received request block
                                r.set(false);
                            }
                            return oldQ;
                        }
                    }
                    oldQ.add(blockWrapper);
                    r.set(true);
                    return oldQ;
                });
        return r.get();
    }

    /**
     *  根据接收到的区块，将子区块释放
     *
     * @param blockWrapper 接收到的区块
     */
    public void syncPopBlock(BlockWrapper blockWrapper) {
        Block block = blockWrapper.getBlock();
        ByteArrayWrapper key = new ByteArrayWrapper(block.getHashLow());
        syncMap.computeIfPresent(key, (k, v)->{
            blockchain.getXdagStats().nwaitsync--;
            v.forEach(bw -> {
                ImportResult importResult = importBlock(bw);
                switch (importResult) {
                    case EXIST:
                    case IMPORTED_BEST:
                    case IMPORTED_NOT_BEST:
                        // TODO import成功后都需要移除
                        syncPopBlock(bw);
                        v.remove(bw);
                        break;
                    case NO_PARENT:
                        if (syncPushBlock(bw, importResult.getHashlow())) {
                            log.error("push block:{}, NO_PARENT {}", Hex.toHexString(bw.getBlock().getHashLow()),
                                    Hex.toHexString(importResult.getHashlow()));
                            List<XdagChannel> channels = channelMgr.getActiveChannels();
                            for (XdagChannel channel : channels) {
                                if (channel.getNode().equals(bw.getRemoteNode())) {
                                    channel.getXdag().sendGetBlock(importResult.getHashlow());
                                }
                            }
                        }
                        break;
                    default:
                        break;
                }
            });
            if(v.size() == 0) {
                syncMap.remove(k);
                return null;
            }
            return v;
        });
    }

    public void makeSyncDone() {
//        log.debug("Sync Done");
        if (syncDone) {
            return;
        }
        syncDone = true;

        System.out.println("Sync done");
        // 关闭状态检测进程
        this.stateListener.isRunning = false;
        Config config = kernel.getConfig();
        if (config instanceof MainnetConfig) {
            if (kernel.getXdagState() != XdagState.SYNC){
                kernel.setXdagState(XdagState.SYNC);
            }
        } else {
            if (kernel.getXdagState() != XdagState.STST) {
                kernel.setXdagState(XdagState.STST);
            }
        }

        log.info("sync finish! tha last mainBlock number = {}", blockchain.getXdagStats().nmain);

        SimpleDateFormat formatter= new SimpleDateFormat("yyyy-MM-dd 'at' HH:mm:ss z");
        Date date = new Date(System.currentTimeMillis());
        System.out.println("Start PoW at:"+formatter.format(date));

        // 检查主块链
        kernel.getMinerServer().start();
        kernel.getPow().start();
//        kernel.getBlockchain().registerListener(kernel.getPow());
        kernel.getLibp2pNetwork().start();
//        connectlibp2pFuture = exec.scheduleAtFixedRate(this::doConnectlibp2p,10,10, TimeUnit.SECONDS);

    }

    public void doConnectlibp2p(){
        List<Libp2pChannel> libp2pChannels = kernel.getChannelManager().getactiveChannel();
        Stream<Node> nodes = libp2pChannels.stream().map(a->a.getNode());
        PeerTable peerTable = kernel.getDiscoveryController().getPeerTable();
        Collection<DiscoveryPeer> discoveryPeers = peerTable.getAllPeers();
        List<DiscoveryPeer> discoveryPeers1 = new ArrayList<>(discoveryPeers);
        for (DiscoveryPeer d : discoveryPeers1) {
            if ((d.getEndpoint().getHost().equals(kernel.getDiscoveryController().getMynode().getHost()) &&
                    (d.getEndpoint().getTcpPort().equals(kernel.getDiscoveryController().getMynode().getTcpPort())))
                    || hadConnectnode.contains(d) ||
                    nodes.anyMatch(a -> a.equals(new Node(d.getEndpoint().getHost(), d.getEndpoint().getTcpPort().getAsInt())))) {
                continue;
            }
            StringBuilder stringBuilder = new StringBuilder();
//       连接格式 ("/ip4/192.168.3.5/tcp/11112/ipfs/16Uiu2HAmRfT8vNbCbvjQGsfqWUtmZvrj5y8XZXiyUz6HVSqZW8gy")
            String id = new LibP2PNodeId(PeerId.fromHex(Hex.toHexString(d.getId().extractArray()))).toString();
            stringBuilder.append("/ip4/").append(d.getEndpoint().getHost()).append("/tcp/").append(d.getEndpoint().getTcpPort().getAsInt()).
                    append("/ipfs/").append(id);
            kernel.getLibp2pNetwork().dail(stringBuilder.toString());
            hadConnectnode.add(d);
        }
    }
    public void stop() {
        log.debug("sync manager stop");
    }

    public void distributeBlock(BlockWrapper blockWrapper) {
        channelMgr.onNewForeignBlock(blockWrapper);
        channelManager.onNewForeignBlock(blockWrapper);
    }

}
