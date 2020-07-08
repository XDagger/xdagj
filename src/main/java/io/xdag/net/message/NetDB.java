package io.xdag.net.message;

import static io.xdag.utils.BytesUtils.isFullZero;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.xdag.config.Config;
import io.xdag.net.node.Node;
import io.xdag.utils.BytesUtils;

public class NetDB {
  private static final Logger logger = LoggerFactory.getLogger(NetDB.class);

  /**remote*/
  List<IP> ipList = new ArrayList<>();
  Config config;

  public NetDB() {}

  public NetDB(Config config) {
    this.config = config;
  }

  /**从remote节点获取的iplist*/
  public NetDB(byte[] data) {
    parse(data);
  }

  public void addNewIP(String address) {
    String ip = address.split(":")[0];
    int port = Integer.parseInt(address.split(":")[1]);
    ipList.add(new IP(ip, port));
  }

  public void addNewIP(String ip, int port) {
    ipList.add(new IP(ip, port));
  }

  public void addNewIP(byte[] ip, byte[] port) {
    try {
      ipList.add(
          new IP(
              InetAddress.getByAddress(ip),
              Short.toUnsignedInt(BytesUtils.bytesToShort(port, 0, true))));

    } catch (UnknownHostException e) {
      e.printStackTrace();
    }
  }

  /**address 6字节 4字节ip+2字节port*/
  public void addNewIP(byte[] address) {
    byte[] ip = BytesUtils.subArray(address, 0, 4);
    byte[] port = BytesUtils.subArray(address, 4, 2);
    if (isFullZero(ip) || isFullZero(port)) {
      return;
    }
    addNewIP(ip, port);
  }

  /** 获取remote接收到的新IP*/
  public Set<Node> getIPList() {
    Set<Node> res = new HashSet<>();
    if (ipList.size() != 0) {
      for (IP ip : ipList) {
        res.add(new Node(ip.getIp(), ip.getPort()));
      }
    }
    return res;
  }

  /**
   * 解析字节数组到List中
   *
   * @param data 消息内容
   */
  public void parse(byte[] data) {
    int size = data.length / 6;
    for (int i = 0; i < size; i++) {
      byte[] ipdata = BytesUtils.subArray(data, i * 6, 6);
      addNewIP(ipdata);
    }
  }


  public byte[] getEncoded() {
    return encode(ipList);
  }

  public byte[] getEncoded(List<IP> input) {
    return encode(input);
  }

  public byte[] encode(List<IP> ipList) {
    int length = ipList.size();
    byte[] res = new byte[length * 6];
    for (int i = 0; i < length; i++) {
      System.arraycopy(ipList.get(i).getData(), 0, res, i * 6, 6);
    }
    return res;
  }

  class IP {
    InetAddress ip;
    int port;

    public IP(InetAddress ip, int port) {
      this.ip = ip;
      this.port = port;
    }

    public IP(String ip, int port) {
      try {
        this.ip = InetAddress.getByName(ip);
        this.port = port;
      } catch (UnknownHostException e) {
        e.printStackTrace();
      }
    }

    public InetAddress getIp() {
      return ip;
    }

    public void setIp(InetAddress ip) {
      this.ip = ip;
    }

    public int getPort() {
      return port;
    }

    public void setPort(int port) {
      this.port = port;
    }

    public byte[] getData() {
      return BytesUtils.merge(ip.getAddress(), BytesUtils.shortToBytes((short) port, true));
    }

    @Override
    public String toString() {
      return ip.toString() + ":" + port;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      IP ip1 = (IP) o;
      return port == ip1.port && ip.equals(ip1.ip);
    }

    @Override
    public int hashCode() {
      return Objects.hash(ip, port);
    }
  }

  public int getSize() {
    return ipList.size();
  }

  public List<IP> getIpList() {
    return ipList;
  }

  public void updateNetDB(NetDB netDB) {
    // TODO:更新netdb

  }

  @Override
  public String toString() {
    StringBuilder stringBuilder = new StringBuilder();
    for (IP ip : ipList) {
      stringBuilder.append(ip).append("\n");
    }
    return stringBuilder.toString();
  }

  public void appendNetDB(NetDB netDB) {
    if (netDB.ipList.size() == 0) {
      logger.debug("size 0");
      return;
    }
    for (IP ip : netDB.ipList) {
      if (this.ipList.contains(ip)) {
        continue;
      }
      this.ipList.add(ip);
    }
  }

  public boolean contains(InetSocketAddress address) {
    IP ip = new IP(address.getAddress(), address.getPort());
    return this.ipList.contains(ip);
  }
}
