package io.xdag.mine.message;

import static io.xdag.net.message.XdagMessageCodes.TASK_SHARE;

import io.xdag.core.XdagField;
import io.xdag.net.message.Message;
import io.xdag.net.message.XdagMessageCodes;

public class TaskShareMessage extends Message {

    private XdagField xdagField;

    public TaskShareMessage(byte[] encoded) {
        super(encoded);
        this.xdagField = new XdagField(encoded);
    }

    @Override
    public byte[] getEncoded() {
        return xdagField.getData();
    }

    @Override
    public Class<?> getAnswerMessage() {
        return null;
    }

    @Override
    public XdagMessageCodes getCommand() {
        return TASK_SHARE;
    }

    @Override
    public String toString() {
        return null;
    }
}
