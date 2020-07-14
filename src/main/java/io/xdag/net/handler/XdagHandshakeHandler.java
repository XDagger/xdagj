package io.xdag.net.handler;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.xdag.Kernel;
import io.xdag.config.Config;
import io.xdag.crypto.jni.Native;
import io.xdag.net.XdagChannel;
import io.xdag.net.XdagVersion;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Hex;

@Slf4j
public class XdagHandshakeHandler extends ByteToMessageDecoder {
    private Config config;
    private XdagChannel channel;
    private Kernel kernel;
    private boolean isServer;

    public XdagHandshakeHandler(Kernel kernel, Config config, XdagChannel channel) {
        this.kernel = kernel;
        this.config = config;
        this.channel = channel;
    }

    public void setServer(boolean isServer) {
        this.isServer = isServer;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        InetSocketAddress remoteAddress = (InetSocketAddress) ctx.channel().remoteAddress();
        channel.initWithNode(
                // 连接到对方的channel 并将对方记录为node
                remoteAddress.getHostName(), remoteAddress.getPort());
        // TODO:如果为服务器端 发送pubkey
        if (isServer) {
            channel.sendPubkey(ctx);
        }
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        if (channel.getNode().getStat().Inbound.get() < 4) {
            // 如果还未发送pubkey
            if (channel.getNode().getStat().Inbound.get() < 1) {
                byte[] remotePubKey = new byte[1024];
                try {
                    in.readBytes(remotePubKey);
                } catch (Exception e) {
                    return;
                }
                // 发送过pubkey后加2
                channel.getNode().getStat().Inbound.add(2);
                if (!checkDnetPubkey(remotePubKey)) {
                    throw new RuntimeException("dnet key error!");
                }
                // 发送pubkey
                if (isServer) {
                    // 如果已经发送了pubkey
                    if (channel.getNode().getStat().Outbound.get() == 2) {
                        channel.sendPassword(ctx);
                    }
                } else {
                    channel.sendPubkey(ctx);
                }
                // 如果已经接收过pubkey
            } else if (channel.getNode().getStat().Inbound.get() >= 1) {
                // 读取set0
                byte[] word = new byte[512];
                in.readBytes(word);
                channel.getNode().getStat().Inbound.add();
                if (!isServer) {
                    // 如果已经发送了pubkey
                    if (channel.getNode().getStat().Outbound.get() == 2) {
                        channel.sendPassword(ctx);
                    }
                }
                if (channel.getNode().getStat().Inbound.get() >= 3) {

                    log.info("connect a new pool,host[{}]", channel.getInetSocketAddress().toString());

                    log.debug("握手协议结束，开始传输数据");
                    // 握手协议通过
                    kernel.getChannelManager().onChannelActive(channel, channel.getNode());
                    ctx.pipeline().remove(this);
                    channel.activateXdag(ctx, XdagVersion.V03);

                    // test connect whether send a address
                    byte[] remoteAddress = new byte[512];
                    if (in.isReadable() && in.readableBytes() == 512) {
                        log.debug("有地址块发送");
                        in.readBytes(remoteAddress);
                        log.debug("Hex :" + Hex.encodeHexString(remoteAddress));
                    }
                }
            }

        } else {
            byte[] read = new byte[512];
            in.readBytes(read);
            log.debug("接受区块：" + Hex.encodeHexString(read));
            // 接受区块
            byte[] uncryptData = Native.dfslib_uncrypt_byte_sector(
                    read, read.length, channel.getNode().getStat().Inbound.get() - 3 + 1);
            log.debug(
                    "in="
                            + channel.getNode().getStat().Inbound.get()
                            + ", after  dfslib_uncrypt_sector : "
                            + Hex.encodeHexString(uncryptData));
            channel.getNode().getStat().Inbound.add();
        }
    }

    public boolean checkDnetPubkey(byte[] pubkey) {
        if (Arrays.equals(config.getXKeys().pub, pubkey)) {
            return true;
        }
        return false;
    }
}
