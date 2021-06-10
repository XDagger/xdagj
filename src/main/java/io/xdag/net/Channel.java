package io.xdag.net;

import io.netty.channel.socket.nio.NioSocketChannel;
import io.xdag.Kernel;
import io.xdag.core.BlockWrapper;
import io.xdag.net.handler.Xdag;
import io.xdag.net.message.MessageQueue;
import io.xdag.net.node.Node;
import lombok.Getter;
import lombok.Setter;

import java.net.InetSocketAddress;

/**
 * @author wawa
 */
@Getter
@Setter
public abstract class Channel {
    protected NioSocketChannel socket;
    protected InetSocketAddress inetSocketAddress;
    protected Node node;
    protected MessageQueue messageQueue;
    protected Kernel kernel;
    protected boolean isActive;
    protected boolean isDisconnected = false;

    public abstract InetSocketAddress getInetSocketAddress();

    public abstract void setActive(boolean b);

    public abstract boolean isActive();

    public abstract Node getNode();

    public abstract void sendNewBlock(BlockWrapper blockWrapper);

    public abstract void onDisconnect();

    public abstract void dropConnection();

    public abstract Xdag getXdag();

    public abstract boolean isDisconnected();

    public abstract MessageQueue getMessageQueue();

    public abstract Kernel getKernel();
}
