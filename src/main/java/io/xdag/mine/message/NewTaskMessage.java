package io.xdag.mine.message;

import static io.xdag.net.message.XdagMessageCodes.NEW_TASK;

import io.xdag.core.XdagField;
import io.xdag.net.message.Message;
import io.xdag.net.message.XdagMessageCodes;
import io.xdag.utils.BytesUtils;

public class NewTaskMessage extends Message {

    private XdagField[] xdagFields = new XdagField[2];

    public NewTaskMessage(byte[] bytes) {
        super(bytes);
        xdagFields[0] = new XdagField(BytesUtils.subArray(bytes, 0, 32));
        xdagFields[1] = new XdagField(BytesUtils.subArray(bytes, 32, 32));
    }

    @Override
    public byte[] getEncoded() {

        byte[] data = new byte[64];
        System.arraycopy(xdagFields[0].getData(), 0, data, 0, 32);
        System.arraycopy(xdagFields[1].getData(), 0, data, 32, 32);

        return data;
    }

    @Override
    public Class<?> getAnswerMessage() {
        return null;
    }

    @Override
    public XdagMessageCodes getCommand() {
        return NEW_TASK;
    }

    @Override
    public String toString() {
        return null;
    }
}
