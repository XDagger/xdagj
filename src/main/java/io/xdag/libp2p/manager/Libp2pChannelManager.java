package io.xdag.libp2p.manager;

import io.libp2p.core.PeerId;
import io.xdag.core.BlockWrapper;
import io.xdag.libp2p.Libp2pChannel;
import io.xdag.libp2p.Libp2pNode;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

@Slf4j
public class Libp2pChannelManager {
    protected ConcurrentHashMap<PeerId, Libp2pChannel> channels = new ConcurrentHashMap<>();
    protected ConcurrentHashMap<String, Libp2pChannel> activeChannels = new ConcurrentHashMap<>();
    /** Queue with new blocks from other peers */
    private BlockingQueue<BlockWrapper> newForeignBlocks = new LinkedBlockingQueue<>();
    // 广播区块
    private Thread blockDistributeThread;

    public Libp2pChannelManager( ) {
        this.blockDistributeThread = new Thread(this::newBlocksDistributeLoop, "NewSyncThreadBlocks");
        blockDistributeThread.start();
    }

    private void newBlocksDistributeLoop() {
        while (!Thread.currentThread().isInterrupted()) {
            BlockWrapper wrapper = null;
            try {
                wrapper = newForeignBlocks.take();
                log.debug("no problem..");
                sendNewBlock(wrapper);
            } catch (InterruptedException e) {
                break;
            } catch (Throwable e) {
                if (wrapper != null) {
                    log.error("Block dump: {}", wrapper.getBlock());
                } else {
                    log.error("Error broadcasting unknown block", e);
                }
            }
        }
    }

    public void sendNewBlock(BlockWrapper blockWrapper) {

        Libp2pNode receive = null;

        // 说明是自己产生的
        if (blockWrapper.getRemoteNode() != null) {
            Libp2pChannel receiveChannel = activeChannels.get(blockWrapper.getRemoteNode().getHexId());
            receive = receiveChannel != null ? receiveChannel.getNode() : null;
        }
        //广播
        for (Libp2pChannel channel : activeChannels.values()) {
            if (receive != null && channel.getNode().getHexId().equals(receive.getHexId())) {
                log.debug("不发送给他");
                continue;
            }
            log.debug("发送给除receive的节点");
            channel.sendNewBlock(blockWrapper);
        }
    }

    public void add(Libp2pChannel ch){
        log.info("xdag libp2pchannel manager->Channel added: remoteAddress = {}:{}", ch.getNode().getPeerId());
        channels.put(ch.getNode().getPeerId(), ch);
    }
    public void remove(Libp2pChannel ch) {
        log.debug("Channel removed: remoteAddress = {}", ch.getIp());
        channels.remove(ch.getIp());
        if (ch.isActive()) {
            activeChannels.remove(ch.getIp());
            ch.setActive(false);
        }
    }
    public List<Libp2pChannel> getactiveChannel(){
        return new ArrayList<>(activeChannels.values());
    }
    public void stop() {
        log.debug("Channel Manager stop...");
        if (blockDistributeThread != null) {
            // 中断
            blockDistributeThread.interrupt();
        }
        // 关闭所有连接
        for (Libp2pChannel channel : activeChannels.values()) {
            channel.onDisconnect();
        }
    }
}
