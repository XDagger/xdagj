package io.xdag.net.message;

public interface MessageFactory {

    public Message create(byte code, byte[] encoded);
}
