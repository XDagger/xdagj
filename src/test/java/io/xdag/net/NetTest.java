package io.xdag.net;

import static io.xdag.utils.BytesUtils.isFullZero;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Objects;

import org.junit.Before;
import org.junit.Test;
import org.spongycastle.util.encoders.Hex;

import io.xdag.Kernel;
import io.xdag.config.Config;
import io.xdag.net.message.NetDB;
import io.xdag.net.message.impl.SumRequestMessage;
import io.xdag.utils.BytesUtils;

public class NetTest {

    Config config;
    Kernel kernel;

    @Before
    public void setUp() throws Exception {
        config = new Config();
        // config.initKeys();
        kernel = new Kernel(config);
        kernel.testStart();
    }

    @Test
    public void testNetDB() {
        NetDB netDB = new NetDB();
        String first = "7f000001611e";
        String second = "7f0000015f76";
        String third = "7f000001d49d";
        String fourth = "7f000001b822";
        netDB.addNewIP(Hex.decode(first));
        netDB.addNewIP(Hex.decode(second));
        netDB.addNewIP(Hex.decode(third));
        netDB.addNewIP(Hex.decode(fourth));

        System.out.println(netDB.getSize());
        System.out.println(Hex.toHexString(netDB.getEncoded()));

        String all = "7f000001611e7f0000015f767f000001" + "d49d7f000001b822";

        NetDB netDB1 = new NetDB(Hex.decode(all));
        System.out.println(netDB1.getSize());
        System.out.println(Hex.toHexString(netDB1.getEncoded()));

        System.out.println(netDB);
    }

    public void TestNetIPGet() {
        NetDB netDB = new NetDB();
        String first = "7f000001611e";
        String second = "7f0000015f76";
        String third = "7f000001d49d";
        String fourth = "7f000001b822";
        netDB.addNewIP(Hex.decode(first));
        netDB.addNewIP(Hex.decode(second));
        netDB.addNewIP(Hex.decode(third));
        netDB.addNewIP(Hex.decode(fourth));

        byte[] hash = netDB.encode(netDB.getIpList());
        System.out.println(Hex.toHexString(hash));
    }

    @Test
    public void TestNetUpdate() {
        String ip = "127.0.0.1:40404";
        NetDB netDB = new NetDB();
        netDB.addNewIP(ip);
        System.out.println(netDB);
        System.out.println(Hex.toHexString(netDB.getEncoded()));
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

        public String toString() {
            return ip.toString() + ":" + port;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (o == null || getClass() != o.getClass())
                return false;
            IP ip1 = (IP) o;
            return port == ip1.port && ip.equals(ip1.ip);
        }

        @Override
        public int hashCode() {
            return Objects.hash(ip, port);
        }
    }

    @Test
    public void TestIP() throws UnknownHostException {
        byte[] address = Hex.decode("7f000001d49d");
        byte[] ip = BytesUtils.subArray(address, 0, 4);
        byte[] port = BytesUtils.subArray(address, 4, 2);
        IP ip1 = new IP(
                InetAddress.getByAddress(ip),
                Short.toUnsignedInt(BytesUtils.bytesToShort(port, 0, true)));
        System.out.println(ip1);

        String address1 = "127.0.0.1:40404";
        String ip2 = address1.split(":")[0];
        int port2 = Integer.parseInt(address1.split(":")[1]);
        IP ip3 = new IP(ip2, port2);
        System.out.println(ip3);

        System.out.println(Hex.toHexString(ip3.getData()));

        byte[] temp = new byte[6];
        byte[] temp2 = new byte[2];
        System.out.println(isFullZero(temp));
        System.out.println(isFullZero(temp2));
    }

    @Test
    public void TestParse() {
        String res = "7f000001611e7f000001b8227f0000015f767f000001d49d000000000000000000000000";
        NetDB netDB = new NetDB(Hex.decode(res));
        System.out.println(netDB);
        System.out.println(netDB.getSize());
    }

    @Test
    public void TestIPequals() {
        IP ip1 = new IP("127.0.0.1", 3333);
        IP ip2 = new IP("127.0.0.1", 3333);
        System.out.println(ip1.equals(ip2));
    }

    @Test
    public void TestNetDBParse() {
        String sumsRequest = "8b010002f91eb6eb200000000000000000000000000000000000000000000100"
                + "257b369b5a89d009000000000000000000000000000000000000000000000000"
                + "511e9e9274800c750200000000000000511e9e9274800c750200000000000000"
                + "ee00000000000000ee00000000000000d400000000000000d400000000000000"
                + "04000000040000003ef47801000000007f000001611e7f000001b8227f000001"
                + "5f767f000001d49d000000000000000000000000000000000000000000000000"
                + "0000000000000000000000000000000000000000000000000000000000000000"
                + "0000000000000000000000000000000000000000000000000000000000000000"
                + "0000000000000000000000000000000000000000000000000000000000000000"
                + "0000000000000000000000000000000000000000000000000000000000000000"
                + "0000000000000000000000000000000000000000000000000000000000000000"
                + "0000000000000000000000000000000000000000000000000000000000000000"
                + "0000000000000000000000000000000000000000000000000000000000000000"
                + "0000000000000000000000000000000000000000000000000000000000000000"
                + "0000000000000000000000000000000000000000000000000000000000000000"
                + "0000000000000000000000000000000000000000000000000000000000000000";

        SumRequestMessage sumRequestMessage = new SumRequestMessage(Hex.decode(sumsRequest));
        NetDB netDB = sumRequestMessage.getNetDB();
        System.out.println(netDB);
    }
}
