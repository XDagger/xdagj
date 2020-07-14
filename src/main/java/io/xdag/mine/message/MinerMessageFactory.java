package io.xdag.mine.message;

import io.xdag.mine.handler.MinerMessageHandler;
import io.xdag.net.XdagVersion;
import io.xdag.net.message.Message;
import io.xdag.net.message.MessageFactory;
import io.xdag.net.message.XdagMessageCodes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.util.encoders.Hex;

public class MinerMessageFactory implements MessageFactory {

    private static final Logger logger = LoggerFactory.getLogger(MinerMessageHandler.class);

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
            logger.debug(Hex.toHexString(encoded));
            throw new IllegalArgumentException("No such message code" + receivedCommand);
        }
    }
}
