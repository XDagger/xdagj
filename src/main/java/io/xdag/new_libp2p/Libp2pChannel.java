package io.xdag.new_libp2p;

import io.libp2p.core.Connection;
import io.xdag.core.BlockWrapper;
import io.xdag.new_libp2p.peer.Libp2pNode;
import io.xdag.new_libp2p.message.MessageQueueLib;
import io.xdag.net.handler.Xdag;
import io.xdag.net.handler.XdagAdapter;
import io.xdag.new_libp2p.RPCHandler.RPCHandler;
import lombok.extern.slf4j.Slf4j;
import org.spongycastle.util.encoders.Hex;

@Slf4j
public class Libp2pChannel {
    private final Connection connection;
    private boolean isActive;
    private boolean isDisconnected = false;
    private Libp2pNode remotenode;
    private MessageQueueLib messageQueue;
    RPCHandler handler;
    Xdag xdag = new XdagAdapter();

    public Libp2pChannel(Connection connection, RPCHandler handler) {
        this.connection = connection;
        this.handler = handler;
    }


    //存放连接的节点地址
    public void init(){
        remotenode = new Libp2pNode(connection.secureSession().getRemoteId());
    }
    public void sendNewBlock(BlockWrapper blockWrapper) {
        log.debug("send a block hash is:+" + Hex.toHexString(blockWrapper.getBlock().getHashLow()));
        log.debug("ttl:" + blockWrapper.getTtl());
        this.messageQueue = new MessageQueueLib(this);
        xdag.sendNewBlock(blockWrapper.getBlock(), blockWrapper.getTtl());
    }
    //获取对方的ip
    public String getIp(){
        return connection.remoteAddress().toString();
    }

    public void onDisconnect() {
        isDisconnected = true;
    }

    public boolean isDisconnected() {
        return isDisconnected;
    }

    public Libp2pNode getNode(){
        return remotenode;
    }
    public RPCHandler getHandler(){
        return handler;
    }
    public void setActive(boolean b) {
        isActive = b;
    }

    public boolean isActive() {
        return isActive;
    }
    public void dropConnection() {

    }
}
