package io.xdag.net.node;

import io.xdag.utils.BytesUtils;
import lombok.Data;
import org.apache.commons.codec.binary.Hex;

import java.math.BigInteger;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Random;

@Data
public class Node {

  private byte[] id;
  private final String host;
  private final int port;
  private NodeStat stat = new NodeStat();

  public Node(String host, int port) {
    this.host = host;
    this.port = port;
    this.id = BytesUtils.longToBytes(new Random().nextLong(), true);
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

  public static String getNodeIdShort(String nodeId) {
    return nodeId == null ? "<null>" : nodeId.substring(0, 8);
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
