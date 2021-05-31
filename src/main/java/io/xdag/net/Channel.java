package io.xdag.net;

import io.netty.channel.socket.nio.NioSocketChannel;
import io.xdag.Kernel;
import io.xdag.core.BlockWrapper;
import io.xdag.net.handler.Xdag;
import io.xdag.net.message.MessageQueue;
import io.xdag.net.node.Node;

import java.net.InetSocketAddress;

/**
 * @author wawa
 */
public abstract class Channel {
    protected NioSocketChannel socket;
    protected InetSocketAddress inetSocketAddress;
    /** 该channel对应的节点 */
    protected Node node;

    public abstract InetSocketAddress getInetSocketAddress();

    public abstract void setActive(boolean b);

    public abstract boolean isActive();

    public abstract Node getNode();

    public abstract String getIp();

    public abstract void sendNewBlock(BlockWrapper blockWrapper);

    public abstract void onDisconnect();

    public abstract int getPort();

    public abstract void dropConnection();

    public abstract Xdag getXdag();

    public abstract boolean isDisconnected();

    public abstract MessageQueue getmessageQueue();

    public abstract Kernel getKernel();
}
