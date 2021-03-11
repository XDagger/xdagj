package io.xdag.utils.discoveryutils;

public class PeerDiscoveryPacketDecodingException extends RuntimeException {
    public PeerDiscoveryPacketDecodingException(final String message) {
        super(message);
    }

    public PeerDiscoveryPacketDecodingException(final String message, final Throwable cause) {
        super(message, cause);
    }
}