package io.xdag.listener;

import io.xdag.config.Constants.MessageType;
import org.apache.tuweni.bytes.Bytes;

public class PretopMessage implements Message {

    Bytes data;
    MessageType type;

    public PretopMessage(Bytes data, MessageType type) {
        this.data = data;
        this.type = type;
    }

    @Override
    public MessageType getType() {
        return type;
    }

    @Override
    public Bytes getData() {
        return data;
    }
}
