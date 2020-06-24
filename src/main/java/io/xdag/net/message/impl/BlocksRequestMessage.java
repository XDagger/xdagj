package io.xdag.net.message.impl;

import io.xdag.net.message.AbstractMessage;
import io.xdag.net.message.NetStatus;
import io.xdag.net.message.XdagMessageCodes;
import io.xdag.utils.BytesUtils;
import lombok.Data;
import lombok.EqualsAndHashCode;


import static io.xdag.net.message.XdagMessageCodes.BLOCKS_REQUEST;

@EqualsAndHashCode(callSuper = false)
@Data
public class BlocksRequestMessage extends AbstractMessage {

    public BlocksRequestMessage(byte[] bytes) {
        super(bytes);
    }

    public BlocksRequestMessage(long starttime, long endtime, NetStatus netStatus) {
        //调用jni的generate_random
        super(BLOCKS_REQUEST, starttime, endtime,BytesUtils.bytesToLong(BytesUtils.generateRandomBytes(),0,true),netStatus);
        updateCrc();
    }

    @Override
    public byte[] getEncoded() {
        if (encoded == null) {
            encode();
        }
        return encoded;
    }

    @Override
    public Class<BlocksReplyMessage> getAnswerMessage() {
        return BlocksReplyMessage.class;
    }

    @Override
    public XdagMessageCodes getCommand() {
        return XdagMessageCodes.BLOCKS_REQUEST;
    }

    @Override
    public String toString() {
        if (!parsed) {
            parse();
        }
        return "[" + this.getCommand().name() + " starttime="
                + this.starttime + " endtime=" + this.endtime + "]" ;
    }
}
