package io.xdag.net.node;

import java.math.BigInteger;
import java.net.InetAddress;
import java.net.InetSocketAddress;

import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang3.RandomUtils;

import io.xdag.utils.BytesUtils;
import lombok.Getter;

public class Node {

    @Getter
    private final String host;
    
    @Getter
    private final int port;
    
    @Getter
    private byte[] id;
    
    @Getter
    private NodeStat stat = new NodeStat();

    public Node(String host, int port) {
        this.host = host;
        this.port = port;
        this.id = BytesUtils.longToBytes(RandomUtils.nextLong(), true);
    }

    public Node(byte[] id, String host, int port) {
        this.id = id;
        this.host = host;
        this.port = port;
    }

    public Node(InetAddress address, int port) {
        this.host = address.getHostAddress();
        this.port = port;
    }

    public static String getNodeIdShort(String nodeId) {
        return nodeId == null ? "<null>" : nodeId.substring(0, 8);
    }

    public String getHexId() {
        return Hex.encodeHexString(id);
    }

    public InetSocketAddress getAddress() {
        return new InetSocketAddress(this.getHost(), this.getPort());
    }

    public String getAddressAsString() {
        InetSocketAddress address = this.getAddress();
        InetAddress addr = address.getAddress();
        // addr == null if the hostname can't be resolved
        return (addr == null ? address.getHostString() : addr.getHostAddress())
                + ":"
                + address.getPort();
    }

    public String getHexIdShort() {
        return getNodeIdShort(getHexId());
    }

    public BigInteger getTotalDifficulty() {
        return null;
    }

    public double getAvgLatency() {
        return 0.0;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Node && getAddress().equals(((Node) o).getAddress());
    }

    @Override
    public int hashCode() {
        return getAddress().hashCode();
    }
}
