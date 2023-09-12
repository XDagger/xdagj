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

import static org.junit.Assert.assertEquals;

import io.xdag.net.message.MessageCode;
import io.xdag.net.message.consensus.SumReplyMessage;
import io.xdag.net.message.consensus.XdagMessage;
import io.xdag.utils.BytesUtils;
import java.net.InetAddress;
import java.net.UnknownHostException;
import org.bouncycastle.util.encoders.Hex;
import org.junit.Test;

public class MessageTest {

    // blocksrequest
    // 8b010002f91eb6eb 0000000000000000 0000000000000000 0000000000100000
    // 修改starttime
    // 257b369b5a89d009 0000000000000000 0000000000000000 0000000000000000" +
    // 生成随机八个字节
    // difficulty max_difficulty
    // 511e9e9274800c75 0200000000000000 511e9e9274800c75 0200000000000000" + 2
    // status
    // nblocks total_nblocks nmain total_nmain
    // ee00000000000000 ee00000000000000 d400000000000000 d400000000000000" +
    // nhosts/total_nhosts maintime 后续的368个字节通过netdbsend生成 发送给对方我连接的ip跟host
    // 0400000004000000 3ef4780100000000 7f000001 611e 7f000001 b822 7f000001"
    // 7f000001 611e7f00
    // 0001b822 7f000001 net dbsend修改
    // ip port
    // 5f 767f000001 d49d 000000000000000000000000000000000000000000000000" +
    // 0000000000000000000000000000000000000000000000000000000000000000" +
    // 0000000000000000000000000000000000000000000000000000000000000000" +
    // 0000000000000000000000000000000000000000000000000000000000000000" + 8
    // loadsums
    // 0000000000000000000000000000000000000000000000000000000000000000" +
    // 0000000000000000000000000000000000000000000000000000000000000000" +
    // 0000000000000000000000000000000000000000000000000000000000000000" +
    // 0000000000000000000000000000000000000000000000000000000000000000" +
    // 0000000000000000000000000000000000000000000000000000000000000000" +
    // 0000000000000000000000000000000000000000000000000000000000000000" +
    // 0000000000000000000000000000000000000000000000000000000000000000";

    // 7f000001 611e 7f000001 b822 7f000001 5f76"+
    // 7f000001 d49d 000000000000000000000000000000000000000000000000" +

    /**
     * @Test public void messageTest() {
     *         String sumsRequest = "8b010002f91eb6eb200000000000000000000000000000000000000000000100"
     *         + "257b369b5a89d009000000000000000000000000000000000000000000000000"
     *         + "511e9e9274800c750200000000000000511e9e9274800c750200000000000000"
     *         + "ee00000000000000ee00000000000000d400000000000000d400000000000000"
     *         + "04000000040000003ef47801000000007f000001611e7f000001b8227f000001"
     *         + "5f767f000001d49d000000000000000000000000000000000000000000000000"
     *         + "0000000000000000000000000000000000000000000000000000000000000000"
     *         + "0000000000000000000000000000000000000000000000000000000000000000"
     *         + "0000000000000000000000000000000000000000000000000000000000000000"
     *         + "0000000000000000000000000000000000000000000000000000000000000000"
     *         + "0000000000000000000000000000000000000000000000000000000000000000"
     *         + "0000000000000000000000000000000000000000000000000000000000000000"
     *         + "0000000000000000000000000000000000000000000000000000000000000000"
     *         + "0000000000000000000000000000000000000000000000000000000000000000"
     *         + "0000000000000000000000000000000000000000000000000000000000000000"
     *         + "0000000000000000000000000000000000000000000000000000000000000000";
     *
     *         String sumsReply = "8b010002b43c544230000000000000000100000000000000000000606c010000"
     *         + "aa26b306438b7079000000000000000000000000000000000000000000000000"
     *         + "a99a1482d88ca4944503000000000000a99a1482d88ca4944503000000000000"
     *         + "e706000000000000e706000000000000f50d000000000000f50d000000000000"
     *         + "04000000040000003ef47801000000007f000001611e7f000001b8227f000001"
     *         + "5f767f000001d49d000000000000000000000000000000000000000000000000"
     *         + "0000000000000000000000000000000000000000000000000000000000000000"
     *         + "0000000000000000000000000000000000000000000000000000000000000000"
     *         + "0000000000000000000000000000000000000000000000000000000000000000"
     *         + "0000000000000000000000000000000000000000000000000000000000000000"
     *         + "8e6d98d2a548f5ab000200000000000000000000000000000000000000000000"
     *         + "0000000000000000000000000000000000000000000000000000000000000000"
     *         + "0000000000000000000000000000000000000000000000000000000000000000"
     *         + "0000000000000000000000000000000000000000000000000000000000000000"
     *         + "0000000000000000000000000000000000000000000000000000000000000000"
     *         + "0000000000000000000000000000000000000000000000000000000000000000";
     *
     *         // blockrequest
     *         String blockrequest = "0000000000000000600000000000000000000000000000000000000000000000"
     *         + "64754af0ca122910ff6b54cea0c60c7cfa1c3f67ee71095e0000000000000000"
     *         + "000000000000000000000000000000007dbe92f2ea8ca4944503000000000000"
     *         + "0000000000000000100e0000000000000000000000000000ef06000000000000"
     *         + "0000000004000000cdd27901000000007f000001611e7f0000015f767f000001"
     *         + "d49d7f000001b822000000000000000000000000000000000000000000000000"
     *         + "0000000000000000000000000000000000000000000000000000000000000000"
     *         + "0000000000000000000000000000000000000000000000000000000000000000"
     *         + "0000000000000000000000000000000000000000000000000000000000000000"
     *         + "0000000000000000000000000000000000000000000000000000000000000000"
     *         + "0000000000000000000000000000000000000000000000000000000000000000"
     *         + "0000000000000000000000000000000000000000000000000000000000000000"
     *         + "0000000000000000000000000000000000000000000000000000000000000000"
     *         + "0000000000000000000000000000000000000000000000000000000000000000"
     *         + "0000000000000000000000000000000000000000000000000000000000000000"
     *         + "0000000000000000000000000000000000000000000000000000000000000000";
     *
     *         String blocksRequest = "8b010002f91eb6eb000000000000000000000000000000000000000000100000"
     *         + "257b369b5a89d009000000000000000000000000000000000000000000000000"
     *         + "511e9e9274800c750200000000000000511e9e9274800c750200000000000000"
     *         + "ee00000000000000ee00000000000000d400000000000000d400000000000000"
     *         + "04000000040000003ef47801000000007f000001611e7f000001b8227f000001"
     *         + "5f767f000001d49d000000000000000000000000000000000000000000000000"
     *         + "0000000000000000000000000000000000000000000000000000000000000000"
     *         + "0000000000000000000000000000000000000000000000000000000000000000"
     *         + "0000000000000000000000000000000000000000000000000000000000000000"
     *         + "0000000000000000000000000000000000000000000000000000000000000000"
     *         + "0000000000000000000000000000000000000000000000000000000000000000"
     *         + "0000000000000000000000000000000000000000000000000000000000000000"
     *         + "0000000000000000000000000000000000000000000000000000000000000000"
     *         + "0000000000000000000000000000000000000000000000000000000000000000"
     *         + "0000000000000000000000000000000000000000000000000000000000000000"
     *         + "0000000000000000000000000000000000000000000000000000000000000000";
     *
     *         byte[] uncryptData = Hex.decode(sumsRequest);
     *         System.arraycopy(BytesUtils.longToBytes(0, true), 0, uncryptData, 0, 8);
     *
     *         // 全部都有的是
     *         // status netdb一获取到新message 就更新status,netdb starttime endtime
     *         // sumreply 只更新6个 因为后面8个字段是sum 其余更新14个字段
     *
     *         // System.out.println(Hex.toHexString(uncryptData));
     *         SumRequestMessage sumRequestMessage = new SumRequestMessage(Hex.decode(sumsRequest));
     *         SumReplyMessage sumReplyMessage = new SumReplyMessage(Hex.decode(sumsReply));
     *         BlockRequestMessage blockRequestMessage = new BlockRequestMessage(Hex.decode(blockrequest));
     *         BlocksRequestMessage blocksRequestMessage = new BlocksRequestMessage(Hex.decode(blocksRequest));
     *
     *         System.out.println(
     *         "=====================================sum request message========================================");
     *         printMessage(sumRequestMessage);
     *         System.out.println(
     *         "=====================================sum reply message========================================");
     *
     *         printMessage(sumReplyMessage);
     *         System.out.println(
     *         "=====================================block request message========================================");
     *
     *         printMessage(blockRequestMessage);
     *         System.out.println(
     *         "=====================================blocks request message========================================");
     *         printMessage(blocksRequestMessage);
     *
     *         System.out.println(
     *         "=====================================Test generate message========================================");
     *
     *         long current = XdagTime.getCurrentTimestamp();
     *         XdagStats xdagStats = new XdagStats();
     *         xdagStats.setMaxdifficulty(new BigInteger(String.valueOf(100000)));
     *         xdagStats.setDifficulty(new BigInteger(String.valueOf(100000)));
     *         xdagStats.setTotalnmain(100);
     *         xdagStats.setNmain(100);
     *         xdagStats.setTotalnblocks(200);
     *         xdagStats.setNblocks(200);
     *         byte[] hash = Hex.decode("0000000000000000c86357a2f57bb9df4f8b43b7a60e24d1ccc547c606f2d798");
     *         SumRequestMessage sumRequestMessage1 = new SumRequestMessage(0, current, xdagStats);
     *         SumReplyMessage sumReplyMessage1 = new SumReplyMessage(current, sumRequestMessage1.getRandom(),
     *         xdagStats,
     *         new byte[256]);
     *         BlocksRequestMessage blocksRequestMessage1 = new BlocksRequestMessage(0, current, xdagStats);
     *         BlockRequestMessage blockRequestMessage1 = new BlockRequestMessage(hash, xdagStats);
     *
     *         System.out.println(
     *         "=====================================sum request message========================================");
     *         printMessage(sumRequestMessage1);
     *         System.out.println("rawdata:" + Hex.toHexString(sumRequestMessage1.getEncoded()));
     *
     *         System.out.println(
     *         "=====================================sum reply message========================================");
     *         printMessage(sumReplyMessage1);
     *         System.out.println("rawdata:" + Hex.toHexString(sumReplyMessage1.getEncoded()));
     *
     *         System.out.println(
     *         "=====================================sum request message========================================");
     *         printMessage(blocksRequestMessage1);
     *         System.out.println("rawdata:" + Hex.toHexString(blocksRequestMessage1.getEncoded()));
     *
     *         System.out.println(
     *         "=====================================sum request message========================================");
     *         printMessage(blockRequestMessage1);
     *         System.out.println("rawdata:" + Hex.toHexString(blockRequestMessage1.getEncoded()));
     *
     *         String blockRawdata = "000000000000000038324654050000004d3782fa780100000000000000000000"
     *         + "c86357a2f57bb9df4f8b43b7a60e24d1ccc547c606f2d7980000000000000000"
     *         + "afa5fec4f56f7935125806e235d5280d7092c6840f35b397000000000a000000"
     *         + "a08202c3f60123df5e3a973e21a2dd0418b9926a2eb7c4fc000000000a000000"
     *         + "08b65d2e2816c0dea73bf1b226c95c2ae3bc683574f559bbc5dd484864b1dbeb"
     *         + "f02a041d5f7ff83a69c0e35e7eeeb64496f76f69958485787d2c50fd8d9614e6"
     *         + "7c2b69c79eddeff5d05b2bfc1ee487b9c691979d315586e9928c04ab3ace15bb"
     *         + "3866f1a25ed00aa18dde715d2a4fc05147d16300c31fefc0f3ebe4d77c63fcbb"
     *         + "ec6ece350f6be4c84b8705d3b49866a83986578a3a20e876eefe74de0c094bac"
     *         + "0000000000000000000000000000000000000000000000000000000000000000"
     *         + "0000000000000000000000000000000000000000000000000000000000000000"
     *         + "0000000000000000000000000000000000000000000000000000000000000000"
     *         + "0000000000000000000000000000000000000000000000000000000000000000"
     *         + "0000000000000000000000000000000000000000000000000000000000000000"
     *         + "0000000000000000000000000000000000000000000000000000000000000000"
     *         + "0000000000000000000000000000000000000000000000000000000000000000";
     *         Block first = new Block(new XdagBlock(Hex.decode(blockRawdata)));
     *
     *         System.out.println(
     *         "=====================================first block use key1========================================");
     *
     *         long time = XdagTime.getEndOfEpoch(XdagTime.getCurrentTimestamp());
     *         List<Address> pending = new ArrayList<>();
     *         pending.add(new Address(first.getHashLow()));
     *         Block txfirst = new Block(time, first.getFirstOutput(), null, pending, false, null, -1);
     *         ECKey ecKey1 = new ECKey();
     *         txfirst.signOut(ecKey1);
     *
     *         NewBlockMessage newBlockMessage1 = new NewBlockMessage(first, 5);
     *         NewBlockMessage newBlockMessage2 = new NewBlockMessage(txfirst, 5);
     *         NewBlockMessage newBlockMessage3 = new NewBlockMessage(Hex.decode(blockRawdata));
     *         NewBlockMessage newBlockMessage4 = new NewBlockMessage(new XdagBlock(Hex.decode(blockRawdata)), 5);
     *         System.out.println(
     *         "=====================================new block message1========================================");
     *
     *         System.out.println("new block message1:" + newBlockMessage1);
     *         System.out.println(
     *         "=====================================new block message2========================================");
     *         System.out.println("new block message2:" + newBlockMessage2);
     *         System.out.println(
     *         "=====================================new block message3========================================");
     *         System.out.println("new block message3:" + newBlockMessage3);
     *         System.out.println(
     *         "=====================================new block message4========================================");
     *         System.out.println("new block message4:" + newBlockMessage4);
     *         }
     **/

    public void printMessage(XdagMessage message) {
        System.out.println(message.getCode().name());
        System.out.println("starttime:" + message.getStarttime());
        System.out.println("endtime:" + message.getEndtime());
        System.out.println("status:" + message.getXdagStats());
        System.out.println("netdb:" + message.getRemoteNetdb());
        if (message.getCode() == MessageCode.BLOCK_REQUEST) {
            System.out.println("request hash:" + message.getHash().toHexString());
        } else {
            System.out.println("random:" + message.getRandom());
        }
        if (message.getCode() == MessageCode.SUMS_REPLY) {
            SumReplyMessage sumReplyMessage = (SumReplyMessage) message;
            System.out.println("sum:" + sumReplyMessage.getSum().toHexString());
        }
    }

    @Test
    public void testIP() {
        String ip = "7f000001";
        InetAddress inetAddress = null;
        try {
            inetAddress = InetAddress.getByName("127.0.0.1");
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
        assert inetAddress != null;
        assertEquals(ip, Hex.toHexString(inetAddress.getAddress()));

        String port = "b822";
        byte[] portbyte = Hex.decode(port);

        assertEquals(8888, BytesUtils.bytesToShort(portbyte, 0, true));

        byte[] res = BytesUtils.merge(inetAddress.getAddress(), BytesUtils.shortToBytes((short) 4444, true));
        assertEquals("7f0000015c11", Hex.toHexString(res));
    }
}
