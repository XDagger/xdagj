package io.xdag.net.message.impl;

import static io.xdag.net.message.XdagMessageCodes.SUMS_REQUEST;

import io.xdag.net.message.AbstractMessage;
import io.xdag.net.message.NetStatus;
import io.xdag.net.message.XdagMessageCodes;
import io.xdag.utils.BytesUtils;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = false)
@Data
public class SumRequestMessage extends AbstractMessage {
    public SumRequestMessage(long starttime, long endtime, NetStatus netStatus) {
        super(
                SUMS_REQUEST,
                starttime,
                endtime,
                BytesUtils.bytesToLong(BytesUtils.generateRandomBytes(), 0, true),
                netStatus);
        updateCrc();
    }

    public SumRequestMessage(byte[] bytes) {
        super(bytes);
    }

    @Override
    public byte[] getEncoded() {
        return encoded;
    }

    @Override
    public Class<?> getAnswerMessage() {
        return SumReplyMessage.class;
    }

    @Override
    public XdagMessageCodes getCommand() {
        return XdagMessageCodes.SUMS_REQUEST;
    }

    @Override
    public String toString() {
        if (!parsed) {
            parse();
        }
        return "["
                + this.getCommand().name()
                + " starttime="
                + starttime
                + " endtime="
                + this.endtime
                + " netstatus="
                + netStatus;
    }
}
