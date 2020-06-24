package io.xdag.mine.message;

import static io.xdag.net.message.XdagMessageCodes.TASK_SHARE;


import io.xdag.core.XdagField;
import io.xdag.net.message.Message;
import io.xdag.net.message.XdagMessageCodes;


/**
 * @Classname TaskShareMessage
 * @Description TODO
 * @Date 2020/5/7 22:24
 * @Created by Myron
 */
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
