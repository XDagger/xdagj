package io.xdag.net.message;

public abstract class Message {

    protected boolean parsed;
    protected byte[] encoded;
    protected byte code;

    public Message() {
    }

    public Message(byte[] encoded) {
        this.encoded = encoded;
        parsed = false;
    }

    public abstract byte[] getEncoded();

    public abstract Class<?> getAnswerMessage();

    public abstract XdagMessageCodes getCommand();

    public byte getCode() {
        return code;
    }

    @Override
    public abstract String toString();
}
