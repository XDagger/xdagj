package io.xdag.mine.message;

import org.spongycastle.util.encoders.Hex;

import io.xdag.net.XdagVersion;
import io.xdag.net.message.Message;
import io.xdag.net.message.MessageFactory;
import io.xdag.net.message.XdagMessageCodes;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MinerMessageFactory implements MessageFactory {

    @Override
    public Message create(byte code, byte[] encoded) {
        // 从当前版本中获取到有用的信息
        XdagMessageCodes receivedCommand = XdagMessageCodes.fromByte(code, XdagVersion.V03);

        switch (receivedCommand) {
        case TASK_SHARE:
            return new TaskShareMessage(encoded);
        case NEW_TASK:
            return new NewTaskMessage(encoded);
        case NEW_BALANCE:
            return new NewBalanceMessage(encoded);
        default:
            log.debug(Hex.toHexString(encoded));
            throw new IllegalArgumentException("No such message code" + receivedCommand);
        }
    }
}
