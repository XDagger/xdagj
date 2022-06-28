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

package io.xdag.rpc.dto;


import java.util.List;
import lombok.Builder;
import lombok.Data;

//@Data
////TODO: Return xdagblock info
//public class BlockResultDTO {
//
//    // blockInfo
//    // rawData
//    String height;
//    String data;
//
//    public BlockResultDTO(long height) {
//        this.height = Long.toHexString(height);
//        this.data = "0x";
//    }
//
//
//    public static BlockResultDTO fromBlock(Block b, boolean raw) {
//
//        return null;
//    }
//}

@Data
@Builder
public class BlockResultDTO {

    private long height;
    private String balance;
    private long blockTime;
    private long timeStamp; // xdagTime
    private String state;
    private String hash;
    private String address;
    private String remark;
    private String diff;
    private String type;
    private String flags;
    private List<Link> refs; // means all the ref block
    private List<TxLink> transactions; // means transaction a wallet have


    @Data
    @Builder
    public static class TxLink {

        private int direction; // 0 input 1 output 2 earning
        private String hashlow;
        private String address;
        private String amount;
        private long time;
        private String remark;
    }

    @Data
    @Builder
    public static class Link {

        private int direction; // 0 input 1 output 2 fee
        private String address;
        private String hashlow;
        private String amount;
    }


}


