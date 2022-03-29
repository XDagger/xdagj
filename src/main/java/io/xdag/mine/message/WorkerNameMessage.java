package io.xdag.mine.message;


import io.xdag.core.XdagField;
import io.xdag.net.message.Message;
import io.xdag.net.message.XdagMessageCodes;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.MutableBytes;

import static io.xdag.net.message.XdagMessageCodes.WORKER_NAME;

public class WorkerNameMessage extends Message {

    private final XdagField xdagField;

    public WorkerNameMessage(MutableBytes encoded) {
        super(encoded);
        this.xdagField = new XdagField(encoded);
    }

    @Override
    public Bytes getEncoded() {
        return xdagField.getData();
    }

    @Override
    public Class<?> getAnswerMessage() {
        return null;
    }

    @Override
    public XdagMessageCodes getCommand() {
        return WORKER_NAME;
    }

    @Override
    public String toString() {
        return null;
    }
}
