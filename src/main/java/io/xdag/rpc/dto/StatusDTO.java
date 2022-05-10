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

import lombok.Builder;
import lombok.Data;

@Data
@Builder
//TODO: return xdag status
public class StatusDTO {

    // status 状态信息
    private final String nblock;
    private final String totalNblocks;
    private final String nmain;
    private final String totalNnmain;
    private final String curDiff;
    private final String netDiff;
    private final String hashRateOurs;
    private final String hashRateTotal;

    private final String supply;

//    public StatusDTO(long nblocks, long nmain, BigInteger diff, double supply) {
//        this.nblock = toQuantityJsonHex(nblocks);
//        this.nmain = toQuantityJsonHex(nmain);
//        this.curDiff = toQuantityJsonHex(diff);
//        this.supply = toQuantityJsonHex(supply);
//    }

//
//    public StatusDTO(long nblock, long totalNblocks, long nmain, long totalNnmain, BigInteger curDiff,
//            BigInteger netDiff, double hashrateOurs, double hashrateTotal, double supply) {
//        this.nblock = toQuantityJsonHex(nblock);
//        this.totalNblocks = toQuantityJsonHex(totalNblocks);
//        this.nmain = toQuantityJsonHex(nmain);
//        this.totalNnmain = toQuantityJsonHex(totalNnmain);
//        this.curDiff = toQuantityJsonHex(curDiff);
//        this.netDiff = toQuantityJsonHex(netDiff);
//        this.hashRateOurs = toQuantityJsonHex(hashrateOurs);
//        this.hashRateTotal = toQuantityJsonHex(hashrateTotal);
//        this.supply = toQuantityJsonHex(supply);
//    }
}
