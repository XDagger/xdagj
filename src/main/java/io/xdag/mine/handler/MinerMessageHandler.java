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

import static io.xdag.net.handler.XdagBlockHandler.getMsgCode;
import static io.xdag.net.message.XdagMessageCodes.NEW_BALANCE;
import static io.xdag.net.message.XdagMessageCodes.TASK_SHARE;
import static io.xdag.net.message.XdagMessageCodes.WORKER_NAME;
import static io.xdag.utils.BasicUtils.crc32Verify;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageCodec;
import io.xdag.core.XdagBlock;
import io.xdag.core.XdagField;
import io.xdag.mine.MinerChannel;
import io.xdag.net.message.Message;
import io.xdag.net.message.MessageFactory;
import io.xdag.net.message.impl.NewBlockMessage;
import io.xdag.utils.BytesUtils;
import io.xdag.utils.PubkeyAddressUtils;
import java.io.IOException;
import java.math.BigInteger;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Hex;
import org.apache.tuweni.bytes.MutableBytes;

@Slf4j
public class MinerMessageHandler extends ByteToMessageCodec<byte[]> {

    private final MinerChannel channel;
    private final int DATA_SIZE = 32;// length of each field
    private static final long WORKERNAME_HEADER_WORD = 0xf46b9853;
    private MessageFactory messageFactory;

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
            log.debug("Send a message for miner: {} ip&port:{} with sectorNo={},length={}",
                    PubkeyAddressUtils.toBase58(channel.getAccountAddressHashByte()),channel.getInetAddress().toString(),sectorNo, len);
            BytesUtils.arrayReverse(bytes);
            out.writeBytes(bytes);
            channel.getOutBound().add();
        } else if (len == 2 * DATA_SIZE) {
            log.debug("Send a message for miner:{} ip&port:{} with sectorNo={},length={}, hex is[{}]",
                    channel.getAddressHash(),channel.getInetAddress().toString(),sectorNo, len, Hex.encodeHexString(bytes));
            out.writeBytes(bytes);
            channel.getOutBound().add(2);
        } else {
            log.debug("Send a error message of this length:{} field type to miner:{} ip&port:{}.",
                    len,channel.getAddressHash(),channel.getInetAddress().toString());
        }
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
        Message msg = null;
        int len = in.readableBytes();
        // The length of the received message is 32 bytes
        if (len == DATA_SIZE) {
            log.debug("Received a message from the miner:{} ip&port:{},msg len == 32",
                    PubkeyAddressUtils.toBase58(channel.getAccountAddressHashByte()),
                    channel.getInetAddress().toString());
            byte[] data = new byte[DATA_SIZE];
            in.readBytes(data);
            BytesUtils.arrayReverse(data);
            //The message received is the worker_name
            if(BytesUtils.compareTo(data,28,4, BigInteger.valueOf(WORKERNAME_HEADER_WORD).toByteArray(),0,4)==0){
                msg = messageFactory.create(WORKER_NAME.asByte(),MutableBytes.wrap(data));
            }else {
                if (channel.isServer()) {
                    // If it is the server, the one-byte message received can only be task-share
                    msg = messageFactory.create(TASK_SHARE.asByte(), MutableBytes.wrap(data));
                } else {
                    msg = messageFactory.create(NEW_BALANCE.asByte(), MutableBytes.wrap(data));
                }
            }
            channel.getInBound().add();
            // When a message of 512 bytes is received, it means that a transaction is sent from a miner after receiving a block.
        } else {
            log.error("There is no type information from the message with length:{} from Address:{} ip&port:{}",
                    len,channel.getAddressHash(),channel.getInetAddress().toString());
            return;
        }

        if (msg != null) {
            out.add(msg);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        if (cause instanceof IOException) {
            log.debug("The remote host closed a connection,whose address is : {} ",
                    channel.getAddressHash());
        } else {
            log.error(cause.getMessage(), cause);
        }
        channel.onDisconnect();

    }
}
