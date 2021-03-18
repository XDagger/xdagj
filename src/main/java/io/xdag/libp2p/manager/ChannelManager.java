package io.xdag.libp2p.manager;

import io.xdag.core.BlockWrapper;
import io.xdag.libp2p.Libp2pChannel;
import io.xdag.net.node.Node;
import lombok.extern.slf4j.Slf4j;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

@Slf4j
public class ChannelManager {
    protected ConcurrentHashMap<InetSocketAddress, Libp2pChannel> channels = new ConcurrentHashMap<>();
    protected ConcurrentHashMap<String, Libp2pChannel> activeChannels = new ConcurrentHashMap<>();
    /** Queue with new blocks from other peers */
    private BlockingQueue<BlockWrapper> newForeignBlocks = new LinkedBlockingQueue<>();
    // 广播区块
    private Thread blockDistributeThread;

    public ChannelManager( ) {
        this.blockDistributeThread = new Thread(this::newBlocksDistributeLoop, "NewSyncThreadBlocks");
        blockDistributeThread.start();
    }

    private void newBlocksDistributeLoop() {
        while (!Thread.currentThread().isInterrupted()) {
            BlockWrapper wrapper = null;
            try {
//                System.out.println("newBlocksDistributeLoop");
//                System.out.println("no problem..");
                wrapper = newForeignBlocks.take();
                System.out.println("wrapper = "+wrapper.getBlock().getHash().toString());
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

        Node receive = null;
        System.out.println("sendNewBlock");
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
    public void onChannelActive(Libp2pChannel channel, Node node){
        channel.setActive(true);
        activeChannels.put(node.getHexId(), channel);
        System.out.println("activeChannel size:"+ activeChannels.size());
    }
    public void add(Libp2pChannel ch){
        log.info("xdag libp2pchannel manager->Channel added: remoteAddress = {}:{}", ch.getNode().getAddress());
        channels.put(ch.getNode().getAddress(), ch);
    }
    public void remove(Libp2pChannel ch) {
        log.debug("Channel removed: remoteAddress = {}", ch.getIp());
        channels.remove(ch.getIp());
        if (ch.isActive()) {
            activeChannels.remove(ch.getIp());
            ch.setActive(false);
        }
    }

    public void onNewForeignBlock(BlockWrapper blockWrapper) {
        newForeignBlocks.add(blockWrapper);
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
