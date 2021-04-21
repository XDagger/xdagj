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
package io.xdag.mine.handler;

import static io.xdag.config.Config.MAINNET;
import static io.xdag.core.XdagField.FieldType.XDAG_FIELD_HEAD_TEST;
import static io.xdag.net.handler.XdagBlockHandler.getMsgCode;
import static io.xdag.net.message.XdagMessageCodes.NEW_BALANCE;
import static io.xdag.net.message.XdagMessageCodes.TASK_SHARE;
import static io.xdag.utils.BasicUtils.crc32Verify;

import java.io.IOException;
import java.util.List;

import org.apache.commons.codec.binary.Hex;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageCodec;
import io.xdag.core.XdagBlock;
import io.xdag.core.XdagField;
import io.xdag.crypto.jni.Native;
import io.xdag.mine.MinerChannel;
import io.xdag.net.message.Message;
import io.xdag.net.message.MessageFactory;
import io.xdag.net.message.impl.NewBlockMessage;
import io.xdag.utils.BytesUtils;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MinerMessageHandler extends ByteToMessageCodec<byte[]> {

    private final MinerChannel channel;
    private MessageFactory messageFactory;

    /** 每一个字段的长度 */
    private final int DATA_SIZE = 32;

    public MinerMessageHandler(MinerChannel channel) {
        this.channel = channel;
    }

    public void setMessageFactory(MessageFactory messageFactory) {
        this.messageFactory = messageFactory;
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, byte[] bytes, ByteBuf out) {
        int len = bytes.length;
        long sectorNo = channel.getOutBound().get();
        if (len == DATA_SIZE) {
            log.debug("发送一个字段的消息");
            BytesUtils.arrayReverse(bytes);
            out.writeBytes(Native.dfslib_encrypt_array(bytes, 1, sectorNo));
            channel.getOutBound().add();
        } else if (len == 2 * DATA_SIZE) {
            log.debug("发送一个任务消息，消息内容为[{}]", Hex.encodeHexString(bytes));
            out.writeBytes(Native.dfslib_encrypt_array(bytes, 2, sectorNo));
            channel.getOutBound().add(2);
        } else if (len == 16 * DATA_SIZE) {
            out.writeBytes(Native.dfslib_encrypt_array(bytes, 16, sectorNo));
            channel.getOutBound().add(16);
        } else {
            log.debug("没有该长度字段类型的消息");
        }
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
        // 处理接收到的消息
        Message msg = null;
        long sectorNo = channel.getInBound().get();
        int len = in.readableBytes();
        // 接收到的是任务share
        if (len == DATA_SIZE) {
            log.debug("Received a message from the miner,msg len == 32");
            byte[] encryptData = new byte[DATA_SIZE];
            in.readBytes(encryptData);
            byte[] unCryptData = Native.dfslib_uncrypt_array(encryptData, 1, sectorNo);
            BytesUtils.arrayReverse(unCryptData);
            if (channel.isServer()) {
                // 如果是服务端 那么收到的一个字节的消息只能是task——share
                msg = messageFactory.create(TASK_SHARE.asByte(), unCryptData);
            } else {
                msg = messageFactory.create(NEW_BALANCE.asByte(), unCryptData);
            }
            channel.getInBound().add();
            // 两个字段 说明收到的是一个任务字段 只有可能是矿工收到新的任务
        } else if (len == 2 * DATA_SIZE) {
            log.debug("Received a message from the miner,msg len == 64");
            byte[] encryptData = new byte[64];
            in.readBytes(encryptData);
            byte[] unCryptData = Native.dfslib_uncrypt_array(encryptData, 2, sectorNo);

            msg = messageFactory.create(TASK_SHARE.asByte(), unCryptData);
            channel.getInBound().add(2);
            // 收到512个字节的消息 那就说明是收到一个区块 矿工发上来的一笔交易
        } else if (len == 16 * DATA_SIZE) {
            byte[] encryptData = new byte[512];
            in.readBytes(encryptData);
            byte[] unCryptData = Native.dfslib_uncrypt_array(encryptData, 16, sectorNo);
            long transportHeader = BytesUtils.bytesToLong(unCryptData, 0, true);
            int ttl = (int) ((transportHeader >> 8) & 0xff);
            int crc = BytesUtils.bytesToInt(unCryptData, 4, true);
            System.arraycopy(BytesUtils.longToBytes(0, true), 0, unCryptData, 4, 4);
            // 验证长度和crc校验
            if (!crc32Verify(unCryptData, crc)) {
                log.debug("receive not block");
            } else {
                System.arraycopy(BytesUtils.longToBytes(0, true), 0, unCryptData, 0, 8);
                XdagBlock xdagBlock = new XdagBlock(unCryptData);
                byte first_field_type = getMsgCode(xdagBlock, 0);
                XdagField.FieldType netType = MAINNET ? XdagField.FieldType.XDAG_FIELD_HEAD : XDAG_FIELD_HEAD_TEST;
                if (netType.asByte() == first_field_type) {
                    msg = new NewBlockMessage(xdagBlock, ttl);
                }
                channel.getInBound().add(16);
            }
        } else {
            System.out.println("接收到的数据的长度为 " + len/16);
            log.debug("There is no type message of corresponding length, please check......");
            throw new IllegalArgumentException(
                    "There is no type message of corresponding length, please check");
        }

        if (msg != null) {
            out.add(msg);
        } else {
            throw new IllegalArgumentException("receive unknown block, msg len = [{}]");
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        if (cause instanceof IOException) {
            log.debug("远程主机关闭了一个连接");
            ctx.channel().closeFuture();
        } else {
            cause.printStackTrace();
        }
        channel.onDisconnect();
    }
}
