package io.xdag.nat;

public class NatPortMapping {

    private final NetworkProtocol protocol;
    private final String internalHost;
    private final String remoteHost;
    private final int externalPort;
    private final int internalPort;
    private final NatServiceType natServiceType;

    public NatPortMapping(
            final NatServiceType natServiceType,
            final NetworkProtocol protocol,
            final String internalHost,
            final String remoteHost,
            final int externalPort,
            final int internalPort) {
        this.natServiceType = natServiceType;
        this.protocol = protocol;
        this.internalHost = internalHost;
        this.remoteHost = remoteHost;
        this.externalPort = externalPort;
        this.internalPort = internalPort;
    }

    public NatServiceType getNatServiceType() {
        return natServiceType;
    }

    public NetworkProtocol getProtocol() {
        return protocol;
    }

    public int getExternalPort() {
        return externalPort;
    }

    public int getInternalPort() {
        return internalPort;
    }

    @Override
    public String toString() {
        return String.format(
                "[%s] %s:%d ==> %s:%d", protocol, internalHost, internalPort, remoteHost, externalPort);
    }
}

