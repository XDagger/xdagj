/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2020-2030 The XdagJ Developers
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package io.xdag.net;

import io.xdag.net.message.NetDB;
import io.xdag.net.message.impl.SumRequestMessage;
import io.xdag.utils.BytesUtils;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.MutableBytes;
import org.bouncycastle.util.encoders.Hex;
import org.junit.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Objects;

import static io.xdag.utils.BytesUtils.isFullZero;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class NetTest {
    @Test
    public void testNetDB() {
        String expected = "7f000001611e7f0000015f767f000001d49d7f000001b822";
        NetDB netDB = new NetDB();
        String first = "7f000001611e";
        String second = "7f0000015f76";
        String third = "7f000001d49d";
        String fourth = "7f000001b822";

        int size = 4;
        netDB.addNewIP(Hex.decode(first));
        netDB.addNewIP(Hex.decode(second));
        netDB.addNewIP(Hex.decode(third));
        netDB.addNewIP(Hex.decode(fourth));

        assertEquals(size,netDB.getSize());

        assertEquals(expected,Hex.toHexString(netDB.getEncoded()));

        String all = "7f000001611e7f0000015f767f000001" + "d49d7f000001b822";

        NetDB netDB1 = new NetDB(Hex.decode(all));
        assertEquals(size,netDB1.getSize());
        assertEquals(all,Hex.toHexString(netDB1.getEncoded()));

    }

//    public void TestNetIPGet() {
//        NetDB netDB = new NetDB();
//        String first = "7f000001611e";
//        String second = "7f0000015f76";
//        String third = "7f000001d49d";
//        String fourth = "7f000001b822";
//        netDB.addNewIP(Hex.decode(first));
//        netDB.addNewIP(Hex.decode(second));
//        netDB.addNewIP(Hex.decode(third));
//        netDB.addNewIP(Hex.decode(fourth));
//
//        byte[] hash = netDB.encode(netDB.getIpList());
//        System.out.println(Hex.toHexString(hash));
//    }

    @Test
    public void testNetUpdate() {
        String ip = "127.0.0.1:40404";
        NetDB netDB = new NetDB();
        netDB.addNewIP(ip);
        assertEquals(1,netDB.getSize());
        assertEquals("7f000001d49d",Hex.toHexString(netDB.getEncoded()));
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

//    @Test
//    public void TestIP() throws UnknownHostException {
//        byte[] address = Hex.decode("7f000001d49d");
//        byte[] ip = BytesUtils.subArray(address, 0, 4);
//        byte[] port = BytesUtils.subArray(address, 4, 2);
//        IP ip1 = new IP(
//                InetAddress.getByAddress(ip),
//                Short.toUnsignedInt(BytesUtils.bytesToShort(port, 0, true)));
//        System.out.println(ip1);
//
//        String address1 = "127.0.0.1:40404";
//        String ip2 = address1.split(":")[0];
//        int port2 = Integer.parseInt(address1.split(":")[1]);
//        IP ip3 = new IP(ip2, port2);
//        System.out.println(ip3);
//
//        assertEquals(Hex.toHexString(address),Hex.toHexString(ip3.getData()));
//
//    }


    @Test
    public void testNetDBParse() {
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

    //        SumRequestMessage sumRequestMessage = new SumRequestMessage(Hex.decode(sumsRequest));
        SumRequestMessage sumRequestMessage = new SumRequestMessage(Bytes.fromHexString(sumsRequest).mutableCopy());
        NetDB netDB = sumRequestMessage.getNetDB();
        assertEquals(4,netDB.getSize());
    }
}
