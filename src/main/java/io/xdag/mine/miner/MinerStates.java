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
package io.xdag.mine.miner;

public enum MinerStates {
    /** 未知 表示当前矿工还为注册 */
    MINER_UNKNOWN(0x00),
    /** 活跃 矿工已经注册 且持续工作 */
    MINER_ACTIVE(0x01),
    /** 矿工已经注册，但是没有在挖矿 仅仅是因为还有未支付的余额所以保留档案 */
    MINER_ARCHIVE(0x02),
    /** 服务端 表示这个矿工对象是一个矿池 */
    MINER_SERVICE(0x03);

    private final int cmd;

    MinerStates(int cmd) {
        this.cmd = cmd;
    }

    public static MinerStates fromByte(byte i) throws Exception {
        switch ((int) i) {
        case 0x00:
            return MINER_UNKNOWN;
        case 0x01:
            return MINER_ACTIVE;
        case 0x02:
            return MINER_ARCHIVE;
        case 0x03:
            return MINER_SERVICE;
        default:
            throw new Exception("can find the miner state......please check the param!!");
        }
    }

    public byte asByte() {
        return (byte) (cmd);
    }

    @Override
    public String toString() {
        switch (cmd) {
        case 0x00:
            return "MINER_UNKNOWN";
        case 0x01:
            return "MINER_ACTIVE";
        case 0x02:
            return "MINER_ARCHIVE";
        case 0x03:
            return "MINER_SERVICE";
        default:
            return "can find the miner state......please check the param!!";
        }
    }
}
