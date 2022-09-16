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

package io.xdag.net.handler;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.xdag.Kernel;
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

    private final XdagChannel channel;
    private final Kernel kernel;
    private boolean isServer;

    public XdagHandshakeHandler(Kernel kernel, XdagChannel channel) {
        this.kernel = kernel;
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
        log.debug("connect with node:{}",remoteAddress.toString());
        // TODO:如果为服务器端 发送pubKey
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
                    log.debug("illegal address from node:{}",channel.getInetSocketAddress().toString());
                    return;
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

                    log.info("connect a new pool with node:{}", channel.getInetSocketAddress().toString());

                    // handshake ok
                    kernel.getChannelMgr().onChannelActive(channel, channel.getNode());
                    ctx.pipeline().remove(this);
                    channel.activateXdag(ctx, XdagVersion.V03);

                    // test connect whether send an address
                    byte[] remoteAddress = new byte[512];
                    if (in.isReadable() && in.readableBytes() == 512) {
                        log.debug("hava address blocks need to send to:{}",Hex.encodeHexString(remoteAddress));
                        in.readBytes(remoteAddress);
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
        return Arrays.equals(kernel.getConfig().getNodeSpec().getXKeys().pub, pubkey);
    }
}
